package dev.bobble.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where the face is in the source photo (px). Used to place the manual oval. */
data class FaceHint(val cx: Float, val cy: Float, val rx: Float)

sealed interface CutoutResult {
    data class Success(val head: Bitmap, val hint: FaceHint) : CutoutResult
    data class Failure(val reason: String, val hint: FaceHint?) : CutoutResult
}

/** Loads the picked photo, EXIF-rotated and downscaled. */
object PhotoDecoder {
    const val MAX_DIM = 1280

    fun decode(context: Context, uri: Uri, maxDim: Int = MAX_DIM): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = maxDim.toFloat() / max(w, h)
            if (scale < 1f) {
                decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
            }
        }
        if (bmp.config == Bitmap.Config.ARGB_8888) return bmp
        val copy = bmp.copy(Bitmap.Config.ARGB_8888, false)
        bmp.recycle()
        return copy
    }
}

/**
 * Head cutout: a plain cut-and-paste. The face's pixels are never changed; only alpha is set.
 * 1. ML Kit Face Detection finds the largest face.
 * 2. The face box is padded (top for hair, sides for ears) to get the head box.
 * 3. ML Kit Selfie Segmentation removes the background. Below the chin the cut fades out
 *    over a short distance so the neck/shoulders don't come along.
 * 4. The result is trimmed and capped at 512 px. A white outline is optional ([withOutline]).
 * If anything fails, the caller falls back to [manualOval].
 */
object HeadCutter {

    const val MAX_HEAD_PX = 512

    /** Manual oval: height = width * OVAL_RATIO. */
    const val OVAL_RATIO = 1.25f

    /** Sticker outline thickness, as a fraction of the head's longest side. */
    private const val OUTLINE_FRACTION = 0.022f

    /** Neck fade length below the chin, as a fraction of the face height. */
    private const val CHIN_FADE = 0.12f

    /** Minimum share of the head box that must be "person" for the auto cutout to count. */
    private const val MIN_COVERAGE = 0.12f

    suspend fun cutout(src: Bitmap): CutoutResult = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(src, 0)

        val face = try {
            detectLargestFace(image)
        } catch (e: Exception) {
            return@withContext CutoutResult.Failure("face detection error: ${e.message}", null)
        } ?: return@withContext CutoutResult.Failure("no face found", null)

        val box = headBox(face, src.width, src.height)
        val hint = FaceHint(face.exactCenterX(), face.exactCenterY() - face.height() * 0.15f, face.width() * 0.8f)

        val mask = try {
            segment(image)
        } catch (e: Exception) {
            return@withContext CutoutResult.Failure("segmentation error: ${e.message}", hint)
        }

        val masked = applyMask(src, box, face, mask)
            ?: return@withContext CutoutResult.Failure("could not separate the head from the background", hint)
        val head = finish(masked)
        masked.recycle()
        if (head == null) CutoutResult.Failure("cutout was empty", hint) else CutoutResult.Success(head, hint)
    }

    /** Manual fallback: feathered oval centered at (cx, cy) with radii (rx, ry), in source px. */
    fun manualOval(src: Bitmap, cx: Float, cy: Float, rx: Float, ry: Float): Bitmap? {
        val w = (2 * rx).roundToInt().coerceAtLeast(8)
        val h = (2 * ry).roundToInt().coerceAtLeast(8)
        val tmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tmp)
        canvas.drawBitmap(src, -(cx - rx), -(cy - ry), Paint(Paint.FILTER_BITMAP_FLAG))

        val feather = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                0f, 0f, rx,
                intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                floatArrayOf(0f, 0.88f, 1f),
                Shader.TileMode.CLAMP,
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.save()
        canvas.translate(w / 2f, h / 2f)
        canvas.scale(1f, ry / rx)
        canvas.drawPaint(feather) // keeps the oval, clears everything outside it
        canvas.restore()

        val out = finish(tmp)
        tmp.recycle()
        return out
    }

    private suspend fun detectLargestFace(image: InputImage): Rect? {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setMinFaceSize(0.08f)
            .build()
        val detector = FaceDetection.getClient(options)
        try {
            return detector.process(image).await()
                .maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                ?.boundingBox
        } finally {
            detector.close()
        }
    }

    private class Mask(val width: Int, val height: Int, val confidence: FloatArray)

    private suspend fun segment(image: InputImage): Mask {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter = Segmentation.getClient(options)
        try {
            val m = segmenter.process(image).await()
            val buffer = m.buffer
            buffer.rewind()
            val floats = buffer.asFloatBuffer()
            val arr = FloatArray(m.width * m.height)
            floats.get(arr)
            return Mask(m.width, m.height, arr)
        } finally {
            segmenter.close()
        }
    }

    /** Face box padded for hair (top), ears (sides) and chin (bottom), clamped to the image. */
    private fun headBox(face: Rect, imgW: Int, imgH: Int): Rect {
        val fw = face.width()
        val fh = face.height()
        val cx = face.exactCenterX()
        // Generous on top and sides: the person mask removes the background, so a big box
        // only means less hair gets clipped.
        return Rect(
            (cx - fw * 1.0f).toInt().coerceAtLeast(0),
            (face.top - fh * 0.8f).toInt().coerceAtLeast(0),
            (cx + fw * 1.0f).toInt().coerceAtMost(imgW),
            (face.bottom + fh * 0.15f).toInt().coerceAtMost(imgH),
        )
    }

    /**
     * Keeps the photo's pixels exactly; only sets alpha. Alpha = person mask (background removed,
     * hair and ears kept), plus a short fade just below the chin so neck/shoulders don't show.
     */
    private fun applyMask(src: Bitmap, box: Rect, face: Rect, mask: Mask): Bitmap? {
        val bw = box.width()
        val bh = box.height()
        if (bw < 16 || bh < 16) return null
        val px = IntArray(bw * bh)
        src.getPixels(px, 0, bw, box.left, box.top, bw, bh)

        // Neck cut: fully kept down to the chin, gone by CHIN_FADE x face height below it.
        val cutStart = (face.bottom - box.top).toFloat()
        val cutEnd = cutStart + face.height() * CHIN_FADE

        val alpha = FloatArray(bw * bh)
        var covered = 0
        for (y in 0 until bh) {
            val my = ((box.top + y) * mask.height / src.height).coerceIn(0, mask.height - 1)
            val neck = 1f - smoothstep(cutStart, cutEnd, y.toFloat())
            for (x in 0 until bw) {
                val mx = ((box.left + x) * mask.width / src.width).coerceIn(0, mask.width - 1)
                val person = smoothstep(0.4f, 0.6f, mask.confidence[my * mask.width + mx])
                val a = person * neck
                alpha[y * bw + x] = a
                if (a > 0.5f) covered++
            }
        }
        if (covered < bw * bh * MIN_COVERAGE) return null

        for (i in px.indices) {
            val a = (alpha[i] * 255f).roundToInt().coerceIn(0, 255)
            px[i] = (a shl 24) or (px[i] and 0x00FFFFFF)
        }
        return Bitmap.createBitmap(px, bw, bh, Bitmap.Config.ARGB_8888)
    }

    /**
     * Trim transparent borders and cap the size at MAX_HEAD_PX. No outline, no colour changes.
     * Always returns a new bitmap (never [src]), so callers may recycle [src].
     */
    private fun finish(src: Bitmap): Bitmap? {
        val trimmed = BitmapUtil.trimTransparent(src) ?: return null
        val longest = max(trimmed.width, trimmed.height)
        val scale = min(1f, MAX_HEAD_PX.toFloat() / longest)
        if (scale >= 1f) {
            return if (trimmed === src) src.copy(Bitmap.Config.ARGB_8888, false) else trimmed
        }
        val scaled = BitmapUtil.scaleSmooth(
            trimmed,
            (trimmed.width * scale).roundToInt(),
            (trimmed.height * scale).roundToInt(),
        )
        if (trimmed !== src && trimmed !== scaled) trimmed.recycle()
        return scaled
    }

    /** Optional white sticker border around the cut-out head (off by default). Returns a new bitmap. */
    fun withOutline(head: Bitmap): Bitmap {
        val stroke = max(3f, max(head.width, head.height) * OUTLINE_FRACTION)
        return StickerOutline.apply(head, stroke, ceil(stroke).toInt() + 2)
    }
}

package dev.bobble.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private const val INVALID_ID = AppWidgetManager.INVALID_APPWIDGET_ID

/**
 * The only screen in the app: pick a photo, preview the bobblehead, tap Done.
 * Opened when the widget is added, on long-press > reconfigure, from a tap on a widget
 * that has no photo yet, or from the app icon.
 */
class ConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, INVALID_ID) ?: INVALID_ID
        // If the user backs out of the first setup, the launcher removes the widget.
        setResult(RESULT_CANCELED, resultIntent(initialId))

        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    ConfigScreen(initialId, onDone = ::saveAndFinish)
                }
            }
        }
    }

    /** Saves the head, updates the widget, finishes. Returns an error message on failure. */
    private suspend fun saveAndFinish(appWidgetId: Int, head: Bitmap, bodyId: String): String? = try {
        val path = withContext(Dispatchers.IO) { HeadStore.save(this@ConfigActivity, appWidgetId, head) }
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(this, glanceId) {
            it[WidgetKeys.HEAD_PATH] = path
            it[WidgetKeys.BODY_ID] = bodyId
            it[WidgetKeys.ANGLE] = 0f
            it[WidgetKeys.FRAME_INDEX] = -1
        }
        BobbleWidget().update(this, glanceId)
        AnimPrefs.setLite(this, false)
        setResult(RESULT_OK, resultIntent(appWidgetId))
        finish()
        null
    } catch (e: Exception) {
        "Could not save: ${e.message}"
    }

    companion object {
        fun intentFor(context: Context, appWidgetId: Int): Intent =
            Intent(context, ConfigActivity::class.java)
                .setData(Uri.parse("bobble://widget/$appWidgetId")) // unique per widget
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private fun resultIntent(appWidgetId: Int) =
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
}

@Composable
private fun ConfigScreen(initialId: Int, onDone: suspend (Int, Bitmap, String) -> String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var widgetId by remember { mutableIntStateOf(initialId) }
    var placedIds by remember { mutableStateOf<List<Int>?>(null) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var head by remember { mutableStateOf<Bitmap?>(null) }
    var bodyId by remember { mutableStateOf(BodyCatalog.DEFAULT.id) }
    var outline by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<FaceHint?>(null) }
    var manual by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Opened from the app icon: find the placed widget(s).
    LaunchedEffect(Unit) {
        if (widgetId == INVALID_ID) {
            val mgr = GlanceAppWidgetManager(context)
            val ids = mgr.getGlanceIds(BobbleWidget::class.java).map { mgr.getAppWidgetId(it) }
            placedIds = ids
            if (ids.size == 1) widgetId = ids[0]
        }
    }

    // Reconfiguring: show the current head and body.
    LaunchedEffect(widgetId) {
        if (widgetId != INVALID_ID && head == null) {
            val prefs: Preferences? = runCatching {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)
                getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
            }.getOrNull()
            prefs?.get(WidgetKeys.BODY_ID)?.let { bodyId = BodyCatalog.byId(it).id }
            val path = prefs?.get(WidgetKeys.HEAD_PATH)
            if (path != null) head = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                message = "Cutting out the head…"
                try {
                    val bmp = withContext(Dispatchers.IO) { PhotoDecoder.decode(context, uri) }
                    source = bmp
                    when (val r = HeadCutter.cutout(bmp)) {
                        is CutoutResult.Success -> {
                            head = r.head; hint = r.hint; manual = false; message = null
                        }
                        is CutoutResult.Failure -> {
                            head = null; hint = r.hint; manual = true
                            message = "Automatic cutout failed (${r.reason}). Drag the oval over the head and set its size."
                        }
                    }
                } catch (e: Exception) {
                    message = "Could not open that photo: ${e.message}"
                } finally {
                    busy = false
                }
            }
        }
    }
    val pickPhoto = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Bobblehead widget", style = MaterialTheme.typography.headlineSmall)

        if (widgetId == INVALID_ID) {
            WidgetChooser(placedIds) { widgetId = it }
            return@Column
        }

        val src = source
        if (manual && src != null) {
            message?.let { Text(it, textAlign = TextAlign.Center) }
            ManualCropEditor(
                src = src,
                hint = hint,
                busy = busy,
                onApply = { cx, cy, rx ->
                    scope.launch {
                        busy = true
                        val cut = withContext(Dispatchers.Default) {
                            HeadCutter.manualOval(src, cx, cy, rx, rx * HeadCutter.OVAL_RATIO)
                        }
                        busy = false
                        if (cut != null) {
                            head = cut; manual = false; message = null
                        } else {
                            message = "The oval is empty. Move it onto the photo."
                        }
                    }
                },
                onCancel = { manual = false },
            )
        } else {
            // The cut-out is used as-is; the white border is only added if switched on.
            val shown = remember(head, outline) {
                head?.let { if (outline) HeadCutter.withOutline(it) else it }
            }
            val skin = remember(head) { SkinTone.estimate(head) }
            FigurePreview(shown, bodyId, skin)
            if (busy) CircularProgressIndicator()
            message?.let { Text(it, textAlign = TextAlign.Center) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = pickPhoto, enabled = !busy) {
                    Text(if (head == null) "Pick photo" else "Change photo")
                }
                if (src != null) {
                    OutlinedButton(onClick = { manual = true }, enabled = !busy) { Text("Adjust manually") }
                }
            }
            if (head != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("White outline around head")
                    Switch(checked = outline, onCheckedChange = { outline = it })
                }
            }
            Text("Choose a body", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            BodyPicker(head = shown, skin = skin, selectedId = bodyId, onSelect = { bodyId = it })
            Button(
                onClick = {
                    shown?.let { h ->
                        scope.launch {
                            busy = true
                            message = onDone(widgetId, h, bodyId)
                            busy = false
                        }
                    }
                },
                enabled = head != null && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Done") }
        }
    }
}

@Composable
private fun WidgetChooser(placedIds: List<Int>?, onPick: (Int) -> Unit) {
    when {
        placedIds == null -> CircularProgressIndicator()
        placedIds.isEmpty() -> Text(
            "No Bobblehead widget on the home screen yet.\n\n" +
                "Long-press an empty spot on the home screen, open Widgets, find Bobblehead and add it. " +
                "This screen opens automatically.",
            textAlign = TextAlign.Center,
        )
        else -> {
            Text("Which widget do you want to change?")
            placedIds.forEachIndexed { i, id ->
                OutlinedButton(onClick = { onPick(id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Bobblehead ${i + 1}")
                }
            }
        }
    }
}

private val wallpaper = Brush.linearGradient(listOf(Color(0xFF3A4A6B), Color(0xFF8E6E8E)))

/** Avatar-style catalog: every body rendered with the user's head, 4 per row. */
@Composable
private fun BodyPicker(head: Bitmap?, skin: Int, selectedId: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BodyCatalog.ALL.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { spec ->
                    val thumb = remember(head, skin, spec.id) {
                        FrameRenderer.renderPreview(head, spec.id, skin, 0f, 220).asImageBitmap()
                    }
                    val selected = spec.id == selectedId
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(16.dp),
                            )
                            .clickable { onSelect(spec.id) }
                            .padding(bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            bitmap = thumb,
                            contentDescription = spec.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(wallpaper),
                        )
                        Text(spec.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Live preview on a wallpaper-ish background. Tap to test the shake. */
@Composable
private fun FigurePreview(head: Bitmap?, bodyId: String, skin: Int) {
    val scope = rememberCoroutineScope()
    var angle by remember { mutableFloatStateOf(0f) }
    var shaking by remember { mutableStateOf(false) }
    val frame = remember(head, bodyId, skin, angle) {
        FrameRenderer.renderPreview(head, bodyId, skin, angle, 720).asImageBitmap()
    }
    Box(
        modifier = Modifier
            .size(280.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(wallpaper)
            .clickable(enabled = !shaking && head != null) {
                scope.launch {
                    shaking = true
                    for (a in ShakeMath.angles(ShakeMath.LIVE_FRAMES)) {
                        angle = a
                        delay(ShakeMath.LIVE_FRAME_MS)
                    }
                    angle = 0f
                    shaking = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(bitmap = frame, contentDescription = "Widget preview", modifier = Modifier.fillMaxSize().padding(12.dp))
    }
    Text(
        if (head == null) "Pick a photo of a person." else "Tap the preview to test the shake.",
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Fallback: drag an oval over the head and size it with the slider. */
@Composable
private fun ManualCropEditor(
    src: Bitmap,
    hint: FaceHint?,
    busy: Boolean,
    onApply: (cx: Float, cy: Float, rx: Float) -> Unit,
    onCancel: () -> Unit,
) {
    val image = remember(src) { src.asImageBitmap() }
    val w = src.width.toFloat()
    val h = src.height.toFloat()
    val minDim = min(w, h)
    val minR = minDim * 0.08f
    val maxR = minDim * 0.6f
    var cx by remember(src) { mutableFloatStateOf(hint?.cx ?: (w / 2f)) }
    var cy by remember(src) { mutableFloatStateOf(hint?.cy ?: (h / 2.5f)) }
    var rx by remember(src) { mutableFloatStateOf((hint?.rx ?: (minDim * 0.25f)).coerceIn(minR, maxR)) }

    Text("Drag the oval over the head. Use the slider to resize it.", textAlign = TextAlign.Center)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(w / h)
            .pointerInput(src) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val s = w / size.width
                    cx = (cx + drag.x * s).coerceIn(0f, w)
                    cy = (cy + drag.y * s).coerceIn(0f, h)
                }
            },
    ) {
        val scale = size.width / w
        drawImage(image = image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
        val ry = rx * HeadCutter.OVAL_RATIO
        val topLeft = Offset((cx - rx) * scale, (cy - ry) * scale)
        val ovalSize = Size(2 * rx * scale, 2 * ry * scale)
        val oval = Path().apply { addOval(Rect(topLeft, ovalSize)) }
        clipPath(oval, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.55f))
        }
        drawOval(Color.White, topLeft, ovalSize, style = Stroke(width = 3.dp.toPx()))
    }
    Slider(value = rx, onValueChange = { rx = it }, valueRange = minR..maxR)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel, enabled = !busy) { Text("Cancel") }
        Button(onClick = { onApply(cx, cy, rx) }, enabled = !busy) { Text("Use this crop") }
    }
}

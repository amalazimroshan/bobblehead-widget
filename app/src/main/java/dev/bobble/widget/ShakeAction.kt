package dev.bobble.widget

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Tap on the widget. */
class ShakeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        ShakeController.shake(context.applicationContext, glanceId, appWidgetId)
    }
}

/**
 * Plays the wobble by pushing one widget update per frame.
 *
 * Live mode (default): 12 frames x 75 ms, each rendered on the fly from the angle in Glance state.
 * Lite mode: if the launcher throttles updates (most frames late), the next shakes use
 * 8 pre-rendered smaller frames at 125 ms. Picking a new photo resets to live mode.
 */
object ShakeController {

    /** Widget ids currently animating: guards against double taps. */
    private val running: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    suspend fun shake(context: Context, glanceId: GlanceId, appWidgetId: Int) {
        if (!running.add(appWidgetId)) return // already shaking: ignore the tap
        try {
            if (AnimPrefs.isLite(context)) {
                playPrerendered(context, glanceId, appWidgetId)
            } else {
                playLive(context, glanceId)
            }
        } finally {
            // Always end upright, even if cancelled.
            withContext(NonCancellable) {
                FrameCache.clear(appWidgetId)
                updateAppWidgetState(context, glanceId) {
                    it[WidgetKeys.ANGLE] = 0f
                    it[WidgetKeys.FRAME_INDEX] = -1
                }
                BobbleWidget().update(context, glanceId)
            }
            running.remove(appWidgetId)
        }
    }

    private suspend fun playLive(context: Context, glanceId: GlanceId) {
        val angles = ShakeMath.angles(ShakeMath.LIVE_FRAMES)
        var late = 0
        for (angle in angles) {
            val start = SystemClock.uptimeMillis()
            updateAppWidgetState(context, glanceId) {
                it[WidgetKeys.ANGLE] = angle
                it[WidgetKeys.FRAME_INDEX] = -1
            }
            BobbleWidget().update(context, glanceId)
            val spent = SystemClock.uptimeMillis() - start
            if (ShakeMath.isLate(spent, ShakeMath.LIVE_FRAME_MS)) late++
            delay((ShakeMath.LIVE_FRAME_MS - spent).coerceAtLeast(0L))
        }
        if (ShakeMath.shouldFallBack(late, angles.size)) AnimPrefs.setLite(context, true)
    }

    private suspend fun playPrerendered(context: Context, glanceId: GlanceId, appWidgetId: Int) {
        val prefs: Preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val headPath = prefs[WidgetKeys.HEAD_PATH]
        val bodyId = prefs[WidgetKeys.BODY_ID]
        val angles = ShakeMath.angles(ShakeMath.LITE_FRAMES)
        val frames = withContext(Dispatchers.Default) {
            angles.map { FrameRenderer.renderWidgetFrame(headPath, bodyId, it, FrameRenderer.LITE_PX) }
        }
        FrameCache.put(appWidgetId, frames)
        for (i in frames.indices) {
            updateAppWidgetState(context, glanceId) { it[WidgetKeys.FRAME_INDEX] = i }
            BobbleWidget().update(context, glanceId)
            delay(ShakeMath.LITE_FRAME_MS)
        }
    }
}

/**
 * Pre-rendered lite-mode frames, per widget, only while a shake is playing.
 * Not recycled explicitly: RemoteViews may still be parceling the last one; they are
 * dropped from the map after the shake and garbage-collected.
 */
object FrameCache {
    private val frames = ConcurrentHashMap<Int, List<Bitmap>>()

    fun put(appWidgetId: Int, list: List<Bitmap>) {
        frames[appWidgetId] = list
    }

    fun get(appWidgetId: Int, index: Int): Bitmap? =
        if (index < 0) null else frames[appWidgetId]?.getOrNull(index)

    fun clear(appWidgetId: Int) {
        frames.remove(appWidgetId)
    }
}

/** Device-wide animation mode flag. */
object AnimPrefs {
    private const val FILE = "anim"
    private const val KEY_LITE = "lite"

    fun isLite(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_LITE, false)

    fun setLite(context: Context, lite: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_LITE, lite).apply()
    }
}

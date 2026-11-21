package dev.bobble.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import java.io.File

/** Per-widget Glance state (DataStore). Survives reboot. */
object WidgetKeys {
    /** Absolute path of the head PNG in app-private storage. */
    val HEAD_PATH = stringPreferencesKey("head_path")

    /** Chosen BodyCatalog id. */
    val BODY_ID = stringPreferencesKey("body_id")

    /** Current head angle in degrees (0 = upright). */
    val ANGLE = floatPreferencesKey("angle")

    /** Index into FrameCache for lite-mode playback; -1 = render [ANGLE] live. */
    val FRAME_INDEX = intPreferencesKey("frame_index")
}

class BobbleWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent { BobbleContent(context, appWidgetId) }
    }
}

@Composable
private fun BobbleContent(context: Context, appWidgetId: Int) {
    val prefs = currentState<Preferences>()
    val headPath = prefs[WidgetKeys.HEAD_PATH]?.takeIf { File(it).exists() }
    val bodyId = prefs[WidgetKeys.BODY_ID]
    val angle = prefs[WidgetKeys.ANGLE] ?: 0f
    val frameIndex = prefs[WidgetKeys.FRAME_INDEX] ?: -1

    val frame = FrameCache.get(appWidgetId, frameIndex)
        ?: FrameRenderer.renderWidgetFrame(headPath, bodyId, angle, FrameRenderer.FRAME_PX)

    // No photo yet: tap opens the picker. Otherwise tap shakes.
    val onClick: Action = if (headPath == null) {
        actionStartActivity(ConfigActivity.intentFor(context, appWidgetId))
    } else {
        actionRunCallback<ShakeAction>()
    }

    // No background anywhere: only the figure is visible (sticker look).
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(frame),
            contentDescription = "Bobblehead. Tap to shake.",
            modifier = GlanceModifier.fillMaxSize().clickable(onClick),
            contentScale = ContentScale.Fit,
        )
    }
}

class BobbleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BobbleWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach {
            HeadStore.deleteFor(context, it)
            FrameCache.clear(it)
        }
    }
}

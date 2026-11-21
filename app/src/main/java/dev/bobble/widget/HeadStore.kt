package dev.bobble.widget

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/** Head PNGs in app-private storage: files/heads/head_<appWidgetId>_<timestamp>.png */
object HeadStore {

    private fun dir(context: Context) = File(context.filesDir, "heads").apply { mkdirs() }

    private fun prefix(appWidgetId: Int) = "head_${appWidgetId}_"

    /** Saves a new PNG for this widget, deletes older ones, returns the absolute path. */
    fun save(context: Context, appWidgetId: Int, head: Bitmap): String {
        val file = File(dir(context), "${prefix(appWidgetId)}${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { head.compress(Bitmap.CompressFormat.PNG, 100, it) }
        dir(context).listFiles { f -> f.name.startsWith(prefix(appWidgetId)) && f != file }
            ?.forEach { it.delete() }
        return file.absolutePath
    }

    fun deleteFor(context: Context, appWidgetId: Int) {
        dir(context).listFiles { f -> f.name.startsWith(prefix(appWidgetId)) }?.forEach { it.delete() }
    }
}

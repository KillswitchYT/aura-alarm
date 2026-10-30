package com.killswitch.aura

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews

class ClockWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        for (id in ids) m.updateAppWidget(id, views(c))
    }

    companion object {
        fun views(c: Context): RemoteViews {
            val v = RemoteViews(c.packageName, R.layout.widget)
            val t = Store.get(c, "nextT").toLongOrNull() ?: 0L
            val h24 = Settings.is24(c)
            val f = if (h24) "HH:mm" else "h:mm"
            v.setCharSequence(R.id.wc, "setFormat12Hour", f)
            v.setCharSequence(R.id.wc, "setFormat24Hour", f)
            v.setTextViewText(R.id.wn, if (t > 0) "Next alarm  " + DateFormat.format(if (h24) "EEE HH:mm" else "EEE h:mm a", t) else "No alarm set")
            v.setOnClickPendingIntent(R.id.wr, PendingIntent.getActivity(c, 5, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            return v
        }

        fun refresh(c: Context) {
            try {
                val m = AppWidgetManager.getInstance(c)
                for (id in m.getAppWidgetIds(ComponentName(c, ClockWidget::class.java))) m.updateAppWidget(id, views(c))
            } catch (e: Exception) { }
        }
    }
}

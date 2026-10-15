package app.medrecordatorio

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate

class NextDoseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.load(context)
        for (id in ids) manager.updateAppWidget(id, Widget.build(context))
    }
}

object Widget {
    fun refresh(c: Context) {
        try {
            val m = AppWidgetManager.getInstance(c)
            val ids = m.getAppWidgetIds(ComponentName(c, NextDoseWidget::class.java))
            for (id in ids) m.updateAppWidget(id, build(c))
        } catch (_: Exception) {
        }
    }

    fun build(c: Context): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_next)
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        var next: Dose? = null
        var best = Long.MAX_VALUE
        for (d in today..(today + 7)) {
            for (dose in Store.dosesFor(d)) {
                if (Store.entryFor(dose) != null) continue
                val t = dose.millis()
                if (t >= now - 60_000 && t < best) {
                    best = t
                    next = dose
                }
            }
        }
        var pending = 0
        for (dose in Store.dosesFor(today)) {
            if (Store.entryFor(dose) == null && dose.millis() < now - 60_000) pending++
        }
        if (next == null) {
            v.setTextViewText(R.id.w_main, "Sin tomas")
            v.setTextViewText(R.id.w_sub, "No hay tomas próximas")
        } else {
            val when_ = when (next.day - today) {
                0L -> "Hoy"
                1L -> "Mañana"
                else -> "En " + (next.day - today) + " días"
            }
            v.setTextViewText(R.id.w_main, fmtTime(next.minute) + " · " + next.med.name)
            val extra = if (pending > 0) " · $pending sin registrar" else ""
            val dose = if (next.med.dose.isNotBlank()) next.med.dose + " · " else ""
            v.setTextViewText(R.id.w_sub, dose + when_ + extra)
        }
        val open = PendingIntent.getActivity(
            c, 0,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        v.setOnClickPendingIntent(R.id.widget_root, open)
        return v
    }
}

package app.medrecordatorio

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate

object Scheduler {
    const val ACTION_NEXT = "app.medrecordatorio.NEXT"
    const val ACTION_SNOOZE_FIRE = "app.medrecordatorio.SNOOZE_FIRE"
    private const val REQ_NEXT = 1

    private fun nextIntent(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, REQ_NEXT,
        Intent(c, AlarmReceiver::class.java).setAction(ACTION_NEXT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun canExact(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun setAt(c: Context, at: Long, pi: PendingIntent) {
        val am = c.getSystemService(AlarmManager::class.java)
        try {
            if (canExact(c)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Programa la alarma de la próxima toma pendiente. */
    fun rescheduleAll(c: Context) {
        Store.load(c)
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = nextIntent(c)
        am.cancel(pi)
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        var best = Long.MAX_VALUE
        for (d in today..(today + 8)) {
            for (dose in Store.dosesFor(d)) {
                if (Store.entryFor(dose) != null) continue
                val t = dose.millis()
                if (t > now && t < best) best = t
            }
        }
        if (best != Long.MAX_VALUE) setAt(c, best, pi)
        Widget.refresh(c)
    }

    /** Se llama al editar medicinas: no avisa de tomas ya pasadas y reprograma. */
    fun touch(c: Context) {
        Prefs.setLastFired(c, maxOf(Prefs.lastFired(c), System.currentTimeMillis()))
        rescheduleAll(c)
    }

    /** Avisa de las tomas que acaban de llegar (o se perdieron en las últimas 6 h). */
    fun fireDue(c: Context) {
        Store.load(c)
        Notifier.channels(c)
        val now = System.currentTimeMillis()
        val from = maxOf(Prefs.lastFired(c), now - 6 * 3600_000L)
        val to = now + 2000
        val today = LocalDate.now().toEpochDay()
        for (d in (today - 1)..today) {
            for (dose in Store.dosesFor(d)) {
                val t = dose.millis()
                if (t > from && t <= to && Store.entryFor(dose) == null) Notifier.show(c, dose)
            }
        }
        Prefs.setLastFired(c, to)
    }

    fun snooze(c: Context, d: Dose, minutes: Int) {
        val i = Intent(c, AlarmReceiver::class.java).setAction(ACTION_SNOOZE_FIRE)
            .putExtra("med", d.med.id).putExtra("day", d.day).putExtra("min", d.minute)
        val pi = PendingIntent.getBroadcast(
            c, Notifier.idFor(d), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAt(c, System.currentTimeMillis() + minutes * 60_000L, pi)
    }
}

object Notifier {
    const val CH = "tomas"
    const val CH_STOCK = "stock"
    const val ACTION_TAKE = "app.medrecordatorio.TAKE"
    const val ACTION_SKIP = "app.medrecordatorio.SKIP"
    const val ACTION_SNOOZE = "app.medrecordatorio.SNOOZE"

    fun idFor(d: Dose): Int =
        (d.med.id.hashCode() * 31 + d.day.hashCode()) * 31 + d.minute

    fun channels(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CH, "Avisos de tomas", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "Avisos para tomar tus medicinas"
        ch.enableVibration(true)
        nm.createNotificationChannel(ch)
        val st = NotificationChannel(CH_STOCK, "Poco stock", NotificationManager.IMPORTANCE_DEFAULT)
        st.description = "Avisa cuando te quedan pocas unidades"
        nm.createNotificationChannel(st)
    }

    fun show(c: Context, d: Dose) {
        channels(c)
        val id = idFor(d)
        val open = PendingIntent.getActivity(
            c, id,
            Intent(c, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun act(action: String, n: Int, label: String): NotificationCompat.Action {
            val i = Intent(c, ActionReceiver::class.java).setAction(action)
                .putExtra("med", d.med.id).putExtra("day", d.day).putExtra("min", d.minute)
            val pi = PendingIntent.getBroadcast(
                c, id * 3 + n, i,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Action.Builder(0, label, pi).build()
        }

        val text = (if (d.med.dose.isNotBlank()) d.med.dose + " · " else "") + fmtTime(d.minute)
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle("Toca: " + d.med.name)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setContentIntent(open)
            .addAction(act(ACTION_TAKE, 0, "Tomada"))
            .addAction(act(ACTION_SNOOZE, 1, "Posponer"))
            .addAction(act(ACTION_SKIP, 2, "Omitir"))
            .build()
        try {
            NotificationManagerCompat.from(c).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    fun lowStock(c: Context, m: Med) {
        channels(c)
        val n = NotificationCompat.Builder(c, CH_STOCK)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle("Te queda poco: " + m.name)
            .setContentText(if (m.stock == 0) "Ya no te quedan unidades." else "Quedan " + m.stock + " unidades.")
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(c).notify(("stock" + m.id).hashCode(), n)
        } catch (_: SecurityException) {
        }
    }

    fun cancel(c: Context, d: Dose) {
        NotificationManagerCompat.from(c).cancel(idFor(d))
    }
}

object Actions {
    fun take(c: Context, d: Dose) {
        Store.record(c, d, Store.TAKEN)
        val m = d.med
        if (m.stock in 0..m.stockAlert) Notifier.lowStock(c, m)
        Notifier.cancel(c, d)
    }

    fun skip(c: Context, d: Dose) {
        Store.record(c, d, Store.SKIPPED)
        Notifier.cancel(c, d)
    }
}

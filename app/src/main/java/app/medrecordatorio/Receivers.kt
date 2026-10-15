package app.medrecordatorio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Store.load(c)
        Notifier.channels(c)
        if (i.action == Scheduler.ACTION_SNOOZE_FIRE) {
            val med = Store.meds.firstOrNull { it.id == i.getLongExtra("med", -1L) }
            if (med != null) {
                val dose = Dose(med, i.getLongExtra("day", 0L), i.getIntExtra("min", 0))
                if (Store.entryFor(dose) == null) Notifier.show(c, dose)
            }
        } else {
            Scheduler.fireDue(c)
        }
        Scheduler.rescheduleAll(c)
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Store.load(c)
        val med = Store.meds.firstOrNull { it.id == i.getLongExtra("med", -1L) } ?: return
        val dose = Dose(med, i.getLongExtra("day", 0L), i.getIntExtra("min", 0))
        when (i.action) {
            Notifier.ACTION_TAKE -> Actions.take(c, dose)
            Notifier.ACTION_SKIP -> Actions.skip(c, dose)
            Notifier.ACTION_SNOOZE -> {
                Scheduler.snooze(c, dose, Prefs.snooze(c))
                Notifier.cancel(c, dose)
            }
        }
        Scheduler.rescheduleAll(c)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Store.load(c)
        Notifier.channels(c)
        if (i.action == Intent.ACTION_BOOT_COMPLETED) Scheduler.fireDue(c)
        Scheduler.rescheduleAll(c)
    }
}

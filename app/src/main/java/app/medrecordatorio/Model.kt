package app.medrecordatorio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Una medicina. days: bits 0..6 = lunes..domingo. stock -1 = sin control de stock. */
class Med(
    val id: Long,
    var name: String,
    var dose: String,
    var times: List<Int>,
    var days: Int,
    var color: Int,
    var stock: Int,
    var stockAlert: Int,
    var start: Long,
    var end: Long,
)

/** Una toma concreta: una medicina, un día (epochDay) y una hora (minutos desde medianoche). */
class Dose(val med: Med, val day: Long, val minute: Int) {
    fun millis(): Long = LocalDate.ofEpochDay(day)
        .atStartOfDay(ZoneId.systemDefault())
        .plusMinutes(minute.toLong())
        .toInstant()
        .toEpochMilli()
}

/** Registro de una toma: status 1 = tomada, 2 = omitida. */
class Entry(val medId: Long, val day: Long, val minute: Int, val status: Int, val at: Long)

fun fmtTime(minute: Int): String = String.format("%02d:%02d", minute / 60, minute % 60)

object Prefs {
    private fun p(c: Context) =
        c.applicationContext.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    fun snooze(c: Context): Int = p(c).getInt("snooze", 10)
    fun setSnooze(c: Context, v: Int) {
        p(c).edit().putInt("snooze", v).apply()
    }

    fun lastFired(c: Context): Long = p(c).getLong("last", 0L)
    fun setLastFired(c: Context, v: Long) {
        p(c).edit().putLong("last", v).apply()
    }
}

object Store {
    const val TAKEN = 1
    const val SKIPPED = 2

    val meds = mutableListOf<Med>()
    val entries = mutableListOf<Entry>()
    private var loaded = false

    private fun file(c: Context) = File(c.applicationContext.filesDir, "datos.json")

    @Synchronized
    fun load(c: Context) {
        if (loaded) return
        val f = file(c)
        if (f.exists()) {
            try {
                parse(f.readText(), meds, entries)
            } catch (_: Exception) {
            }
        }
        loaded = true
    }

    private fun parse(text: String, m: MutableList<Med>, e: MutableList<Entry>) {
        val o = JSONObject(text)
        val ma = o.optJSONArray("meds") ?: JSONArray()
        for (i in 0 until ma.length()) {
            val j = ma.getJSONObject(i)
            val ta = j.optJSONArray("times") ?: JSONArray()
            val times = (0 until ta.length()).map { ta.getInt(it) }
            m.add(
                Med(
                    j.getLong("id"), j.optString("name"), j.optString("dose"), times,
                    j.optInt("days", 127), j.optInt("color", 0), j.optInt("stock", -1),
                    j.optInt("alert", 5), j.optLong("start", 0L), j.optLong("end", -1L)
                )
            )
        }
        val ea = o.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until ea.length()) {
            val j = ea.getJSONObject(i)
            e.add(Entry(j.getLong("med"), j.getLong("day"), j.getInt("min"), j.getInt("st"), j.optLong("at", 0L)))
        }
    }

    @Synchronized
    fun json(): String {
        val ma = JSONArray()
        for (m in meds) {
            val ta = JSONArray()
            for (t in m.times) ta.put(t)
            ma.put(
                JSONObject().put("id", m.id).put("name", m.name).put("dose", m.dose)
                    .put("times", ta).put("days", m.days).put("color", m.color)
                    .put("stock", m.stock).put("alert", m.stockAlert)
                    .put("start", m.start).put("end", m.end)
            )
        }
        val ea = JSONArray()
        for (e in entries) {
            ea.put(
                JSONObject().put("med", e.medId).put("day", e.day).put("min", e.minute)
                    .put("st", e.status).put("at", e.at)
            )
        }
        return JSONObject().put("meds", ma).put("entries", ea).toString()
    }

    @Synchronized
    fun save(c: Context) {
        try {
            val f = file(c)
            val tmp = File(f.parentFile, "datos.tmp")
            tmp.writeText(json())
            if (!tmp.renameTo(f)) f.writeText(json())
        } catch (_: Exception) {
        }
        Widget.refresh(c)
    }

    @Synchronized
    fun importJson(c: Context, text: String): Boolean {
        return try {
            val m = mutableListOf<Med>()
            val e = mutableListOf<Entry>()
            parse(text, m, e)
            meds.clear(); meds.addAll(m)
            entries.clear(); entries.addAll(e)
            loaded = true
            save(c)
            true
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun upsert(c: Context, m: Med) {
        val i = meds.indexOfFirst { it.id == m.id }
        if (i >= 0) meds[i] = m else meds.add(m)
        save(c)
    }

    @Synchronized
    fun delete(c: Context, id: Long) {
        meds.removeAll { it.id == id }
        entries.removeAll { it.medId == id }
        save(c)
    }

    @Synchronized
    fun entryFor(d: Dose): Entry? =
        entries.firstOrNull { it.medId == d.med.id && it.day == d.day && it.minute == d.minute }

    @Synchronized
    fun record(c: Context, d: Dose, status: Int) {
        val prev = entryFor(d)
        entries.removeAll { it.medId == d.med.id && it.day == d.day && it.minute == d.minute }
        entries.add(Entry(d.med.id, d.day, d.minute, status, System.currentTimeMillis()))
        if (prev?.status == TAKEN && status != TAKEN && d.med.stock >= 0) d.med.stock += 1
        if (status == TAKEN && prev?.status != TAKEN && d.med.stock > 0) d.med.stock -= 1
        save(c)
    }

    @Synchronized
    fun undo(c: Context, d: Dose) {
        val e = entryFor(d) ?: return
        entries.remove(e)
        if (e.status == TAKEN && d.med.stock >= 0) d.med.stock += 1
        save(c)
    }

    /** Todas las tomas previstas de un día, ordenadas por hora. */
    @Synchronized
    fun dosesFor(day: Long): List<Dose> {
        val dow = LocalDate.ofEpochDay(day).dayOfWeek.value - 1
        return meds
            .filter { it.start <= day && (it.end < 0 || day <= it.end) && ((it.days shr dow) and 1) == 1 }
            .flatMap { m -> m.times.map { Dose(m, day, it) } }
            .sortedBy { it.minute }
    }

    /** 0 = sin tomas, 1 = todo tomado/omitido, 2 = hay tomas sin registrar ya pasadas, 3 = pendiente por venir. */
    fun dayState(day: Long, now: Long): Int {
        val ds = dosesFor(day)
        if (ds.isEmpty()) return 0
        var missed = false
        var upcoming = false
        for (d in ds) {
            if (entryFor(d) != null) continue
            if (d.millis() < now) missed = true else upcoming = true
        }
        return if (missed) 2 else if (upcoming) 3 else 1
    }

    @Synchronized
    fun csv(): String {
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val df = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val tf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val sb = StringBuilder("fecha;hora;medicina;dosis;estado;registrado\n")
        for (e in entries.sortedWith(compareBy({ it.day }, { it.minute }))) {
            val m = meds.firstOrNull { it.id == e.medId } ?: continue
            sb.append(LocalDate.ofEpochDay(e.day).format(df)).append(';')
                .append(fmtTime(e.minute)).append(';')
                .append(q(m.name)).append(';')
                .append(q(m.dose)).append(';')
                .append(if (e.status == TAKEN) "Tomada" else "Omitida").append(';')
                .append(Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault()).format(tf))
                .append('\n')
        }
        return sb.toString()
    }
}

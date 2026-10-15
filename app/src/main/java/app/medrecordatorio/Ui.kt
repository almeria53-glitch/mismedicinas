@file:OptIn(ExperimentalMaterial3Api::class)

package app.medrecordatorio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayStyle
import java.util.Locale

// ---------------------------------------------------------------- tema

val Blue = Color(0xFF1E88E5)
val Green = Color(0xFF43A047)
val Orange = Color(0xFFF57C00)
val Red = Color(0xFFE53935)
val PillColors = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFF4511E), Color(0xFF8E24AA),
    Color(0xFFFFB300), Color(0xFF00ACC1), Color(0xFFD81B60)
)
val ES = Locale("es", "ES")

fun pillColor(i: Int): Color = PillColors[((i % PillColors.size) + PillColors.size) % PillColors.size]

@Composable
fun MedTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFF64B5F6), onPrimary = Color(0xFF00263F),
            background = Color(0xFF0F141A), surface = Color(0xFF1A2129),
            surfaceVariant = Color(0xFF2A333D), onSurfaceVariant = Color(0xFFB8C4D0),
        )
    } else {
        lightColorScheme(
            primary = Blue, onPrimary = Color.White,
            background = Color(0xFFF2F6FB), surface = Color.White,
            surfaceVariant = Color(0xFFE3ECF6), onSurfaceVariant = Color(0xFF44505C),
        )
    }
    androidx.compose.material3.MaterialTheme(colorScheme = scheme, content = content)
}

fun dayTitle(d: Long): String {
    val today = LocalDate.now().toEpochDay()
    return when (d - today) {
        0L -> "Hoy"
        1L -> "Mañana"
        -1L -> "Ayer"
        else -> LocalDate.ofEpochDay(d).format(DateTimeFormatter.ofPattern("EEEE", ES))
            .replaceFirstChar { it.uppercase() }
    }
}

fun dayLong(d: Long): String =
    LocalDate.ofEpochDay(d).format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES))

fun hhmm(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
fun PillIcon(color: Color, size: Int = 44) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Medication, contentDescription = null, tint = color, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
fun cardColors() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)

// ---------------------------------------------------------------- HOY

@Composable
fun TodayScreen(modifier: Modifier, tick: Int, onChange: () -> Unit) {
    val ctx = LocalContext.current
    val today = LocalDate.now().toEpochDay()
    var selected by rememberSaveable { mutableStateOf(today) }
    var sheet by remember { mutableStateOf<Dose?>(null) }
    val now = System.currentTimeMillis()
    val doses = remember(selected, tick) { Store.dosesFor(selected) }
    val done = doses.count { Store.entryFor(it) != null }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 27)

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            Text(dayTitle(selected), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(dayLong(selected), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(61) { i ->
                val d = today - 30 + i
                DayChip(d, d == selected, d == today, Store.dayState(d, now)) { selected = d }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (doses.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No hay tomas este día.\nAñade tus medicinas con el botón +",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val groups = doses.groupBy { it.minute }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Card(colors = cardColors(), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("$done de ${doses.size} tomas", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { done.toFloat() / doses.size },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                color = if (done == doses.size) Green else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
                groups.forEach { (minute, list) ->
                    item {
                        Text(
                            fmtTime(minute),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        )
                    }
                    items(list) { dose -> DoseCard(dose, now) { sheet = dose } }
                }
            }
        }
    }

    val s = sheet
    if (s != null) {
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            val e = Store.entryFor(s)
            Column(
                Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillIcon(pillColor(s.med.color), 52)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(s.med.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        val sub = (if (s.med.dose.isNotBlank()) s.med.dose + " · " else "") + fmtTime(s.minute)
                        Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (e == null) {
                    Button(
                        onClick = { Actions.take(ctx, s); sheet = null; onChange() },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Tomada")
                    }
                    OutlinedButton(
                        onClick = { Actions.skip(ctx, s); sheet = null; onChange() },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("Omitir") }
                    if (s.day == today) {
                        val mins = Prefs.snooze(ctx)
                        TextButton(
                            onClick = {
                                Scheduler.snooze(ctx, s, mins)
                                Notifier.cancel(ctx, s)
                                Toast.makeText(ctx, "Te aviso en $mins min", Toast.LENGTH_SHORT).show()
                                sheet = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Posponer $mins min") }
                    }
                } else {
                    val txt = if (e.status == Store.TAKEN) "Tomada a las ${hhmm(e.at)}" else "Omitida"
                    Text(
                        txt,
                        color = if (e.status == Store.TAKEN) Green else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = { Store.undo(ctx, s); sheet = null; onChange() },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("Deshacer") }
                }
            }
        }
    }
}

@Composable
fun DayChip(day: Long, selected: Boolean, isToday: Boolean, state: Int, onClick: () -> Unit) {
    val date = LocalDate.ofEpochDay(day)
    val label = date.dayOfWeek.getDisplayName(DayStyle.SHORT, ES).replace(".", "").take(3)
    val dot = when (state) {
        1 -> Green
        2 -> Orange
        3 -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    Column(
        Modifier.width(48.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
        var m = Modifier.size(38.dp).clip(CircleShape).background(bg)
        if (!selected && isToday) m = m.border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), CircleShape)
        Box(m, contentAlignment = Alignment.Center) {
            Text(
                date.dayOfMonth.toString(),
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
    }
}

@Composable
fun DoseCard(dose: Dose, now: Long, onClick: () -> Unit) {
    val e = Store.entryFor(dose)
    val overdue = e == null && dose.millis() < now
    Card(
        onClick = onClick,
        colors = cardColors(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PillIcon(pillColor(dose.med.color))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(dose.med.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                if (dose.med.dose.isNotBlank()) {
                    Text(dose.med.dose, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
            when {
                e != null && e.status == Store.TAKEN -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Check, contentDescription = "Tomada", tint = Green)
                        Text(hhmm(e.at), fontSize = 11.sp, color = Green)
                    }
                }
                e != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Close, contentDescription = "Omitida", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Omitida", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                overdue -> Text("Pendiente", color = Orange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- MEDICINAS

@Composable
fun MedsScreen(modifier: Modifier, tick: Int, onEdit: (Med) -> Unit) {
    val meds = remember(tick) { Store.meds.toList() }
    Column(modifier.fillMaxSize()) {
        Text(
            "Mis medicinas", fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)
        )
        if (meds.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Todavía no has añadido ninguna medicina.\nPulsa + para empezar.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(meds) { m ->
                    Card(
                        onClick = { onEdit(m) },
                        colors = cardColors(),
                        shape = RoundedCornerShape(18.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            PillIcon(pillColor(m.color))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                if (m.dose.isNotBlank()) {
                                    Text(m.dose, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                }
                                Text(
                                    m.times.sorted().joinToString("  ") { fmtTime(it) },
                                    color = MaterialTheme.colorScheme.primary, fontSize = 14.sp
                                )
                            }
                            if (m.stock >= 0) {
                                val low = m.stock <= m.stockAlert
                                Text(
                                    "Quedan ${m.stock}",
                                    color = if (low) Red else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (low) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- EDITAR

@Composable
fun TimeDialog(initial: Int, onDismiss: () -> Unit, onOk: (Int) -> Unit) {
    val st = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = st)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    TextButton(onClick = { onOk(st.hour * 60 + st.minute) }) { Text("Aceptar") }
                }
            }
        }
    }
}

@Composable
fun EditMedScreen(med: Med, onClose: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val isNew = remember { Store.meds.none { it.id == med.id } }
    var name by remember { mutableStateOf(med.name) }
    var dose by remember { mutableStateOf(med.dose) }
    var color by remember { mutableStateOf(med.color) }
    var times by remember { mutableStateOf(med.times) }
    var days by remember { mutableStateOf(med.days) }
    var useStock by remember { mutableStateOf(med.stock >= 0) }
    var stock by remember { mutableStateOf(if (med.stock >= 0) med.stock.toString() else "") }
    var alert by remember { mutableStateOf(med.stockAlert.toString()) }
    var timeIdx by remember { mutableStateOf(-2) } // -2 = cerrado, -1 = añadir, >=0 = editar
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        if (name.isBlank()) { error = "Pon el nombre de la medicina"; return }
        if (days == 0) { error = "Elige al menos un día"; return }
        val st = if (useStock) (stock.toIntOrNull() ?: 0) else -1
        val m = Med(
            med.id, name.trim(), dose.trim(), times.distinct().sorted(), days, color,
            st, alert.toIntOrNull() ?: 5, med.start, med.end
        )
        Store.upsert(ctx, m)
        onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Nueva medicina" else "Editar medicina") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Borrar", tint = Red)
                        }
                    }
                }
            )
        }
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Nombre de la medicina") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = dose, onValueChange = { dose = it },
                label = { Text("Dosis / nota (ej. 1 pastilla, 500 mg)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            Text("Color", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PillColors.forEachIndexed { i, c ->
                    var m = Modifier.size(38.dp).clip(CircleShape).background(c).clickable { color = i }
                    if (color == i) m = m.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
                    Box(m)
                }
            }

            Text("Horas de toma", fontWeight = FontWeight.SemiBold)
            Card(colors = cardColors(), shape = RoundedCornerShape(18.dp)) {
                Column {
                    times.sorted().forEachIndexed { _, t ->
                        Row(
                            Modifier.fillMaxWidth().clickable { timeIdx = times.indexOf(t) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(fmtTime(t), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (times.size > 1) {
                                IconButton(onClick = { times = times.filter { it != t } }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Quitar hora")
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = { timeIdx = -1 },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) { Text("+ Añadir hora") }
                }
            }

            Text("Días", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, n ->
                    val on = ((days shr i) and 1) == 1
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { days = days xor (1 shl i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            n, fontWeight = FontWeight.Bold,
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Controlar stock", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Te aviso cuando te queden pocas unidades",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = useStock, onCheckedChange = { useStock = it })
            }
            if (useStock) {
                OutlinedTextField(
                    value = stock, onValueChange = { stock = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("Unidades que tengo ahora") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = alert, onValueChange = { alert = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text("Avisar cuando queden") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val err = error
            if (err != null) Text(err, color = Red, fontWeight = FontWeight.SemiBold)

            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("Guardar", fontSize = 17.sp) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (timeIdx != -2) {
        val initial = if (timeIdx >= 0 && timeIdx < times.size) times[timeIdx] else 8 * 60
        TimeDialog(
            initial = initial,
            onDismiss = { timeIdx = -2 },
            onOk = { t ->
                times = if (timeIdx >= 0 && timeIdx < times.size) {
                    times.toMutableList().also { it[timeIdx] = t }
                } else {
                    times + t
                }
                timeIdx = -2
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar esta medicina?") },
            text = { Text("Se borrará también su historial.") },
            confirmButton = {
                TextButton(onClick = {
                    Store.delete(ctx, med.id)
                    confirmDelete = false
                    onSaved()
                }) { Text("Borrar", color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}

// ---------------------------------------------------------------- HISTORIAL

@Composable
fun HistoryScreen(modifier: Modifier, tick: Int) {
    val now = System.currentTimeMillis()
    val today = LocalDate.now().toEpochDay()

    fun rate(days: Int): String {
        var total = 0
        var ok = 0
        for (d in (today - days + 1)..today) {
            for (x in Store.dosesFor(d)) {
                if (x.millis() <= now) {
                    total++
                    if (Store.entryFor(x)?.status == Store.TAKEN) ok++
                }
            }
        }
        return if (total == 0) "—" else "${ok * 100 / total} %"
    }

    val r7 = remember(tick) { rate(7) }
    val r30 = remember(tick) { rate(30) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text("Historial", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Últimos 7 días", r7, Modifier.weight(1f))
                StatCard("Últimos 30 días", r30, Modifier.weight(1f))
            }
        }
        for (d in today downTo (today - 13)) {
            val ds = Store.dosesFor(d)
            if (ds.isEmpty()) continue
            item {
                Text(
                    dayTitle(d) + " · " + dayLong(d),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp)
                )
            }
            items(ds) { x ->
                val e = Store.entryFor(x)
                Card(colors = cardColors(), shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtTime(x.minute), fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                        Text(x.med.name, modifier = Modifier.weight(1f))
                        when {
                            e != null && e.status == Store.TAKEN ->
                                Text("Tomada ${hhmm(e.at)}", color = Green, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            e != null ->
                                Text("Omitida", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            x.millis() < now ->
                                Text("Sin registrar", color = Red, fontSize = 13.sp)
                            else ->
                                Text("Pendiente", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, colors = cardColors(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("cumplimiento", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- AJUSTES

@Composable
fun SettingsScreen(modifier: Modifier, tick: Int, onChange: () -> Unit) {
    val ctx = LocalContext.current
    var snooze by remember(tick) { mutableStateOf(Prefs.snooze(ctx)) }

    val notifOk = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val notifEnabled = notifOk && NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    val exactOk = Scheduler.canExact(ctx)

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChange() }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Store.json().toByteArray()) }
                Toast.makeText(ctx, "Copia guardada", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(ctx, "No se pudo guardar", Toast.LENGTH_LONG).show()
            }
        }
    }
    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } ?: ""
                Store.importJson(ctx, text)
            } catch (_: Exception) {
                false
            }
            if (ok) {
                Scheduler.touch(ctx)
                onChange()
            }
            Toast.makeText(ctx, if (ok) "Copia restaurada" else "Ese archivo no es una copia válida", Toast.LENGTH_LONG).show()
        }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Store.csv().toByteArray()) }
                Toast.makeText(ctx, "Historial exportado", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(ctx, "No se pudo guardar", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Ajustes", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))

        Card(colors = cardColors(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Avisos", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (notifEnabled) "Notificaciones: activadas" else "Notificaciones: desactivadas",
                        color = if (notifEnabled) Green else Red, modifier = Modifier.weight(1f)
                    )
                    if (!notifEnabled) {
                        TextButton(onClick = {
                            if (Build.VERSION.SDK_INT >= 33 && !notifOk) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                )
                            }
                        }) { Text("Activar") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (exactOk) "Alarmas exactas: permitidas" else "Alarmas exactas: no permitidas",
                        color = if (exactOk) Green else Red, modifier = Modifier.weight(1f)
                    )
                    if (!exactOk && Build.VERSION.SDK_INT >= 31) {
                        TextButton(onClick = {
                            ctx.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))
                            )
                        }) { Text("Permitir") }
                    }
                }
                Text("Posponer aviso", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30).forEach { m ->
                        FilterChip(
                            selected = snooze == m,
                            onClick = { snooze = m; Prefs.setSnooze(ctx, m) },
                            label = { Text("$m min") }
                        )
                    }
                }
            }
        }

        Card(colors = cardColors(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Copia de seguridad", fontWeight = FontWeight.Bold)
                Text(
                    "Tus datos solo están en este móvil. Guarda una copia por si cambias de teléfono.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { saveBackup.launch("mis_medicinas_copia.json") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Guardar copia")
                }
                OutlinedButton(onClick = { restoreBackup.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Restaurar copia")
                }
                OutlinedButton(onClick = { saveCsv.launch("historial_medicinas.csv") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Exportar historial (CSV)")
                }
            }
        }

        Card(colors = cardColors(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Si no te llegan los avisos", fontWeight = FontWeight.Bold)
                Text(
                    "Mantén permitidas las notificaciones y las alarmas exactas, y en Ajustes del móvil > Aplicaciones > Mis medicinas > Batería, elige \"Sin restricciones\".",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        Scheduler.touch(ctx)
                        Toast.makeText(ctx, "Alarmas reprogramadas", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Reprogramar alarmas") }
            }
        }

        Text(
            "Funciona sin Internet. La app no envía ningún dato fuera de tu móvil.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
    }
}

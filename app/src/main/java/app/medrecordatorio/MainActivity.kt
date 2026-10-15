package app.medrecordatorio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.load(this)
        Notifier.channels(this)
        Scheduler.rescheduleAll(this)
        setContent { MedTheme { AppRoot() } }
    }
}

fun newMed(): Med = Med(
    id = System.currentTimeMillis(),
    name = "",
    dose = "",
    times = listOf(8 * 60),
    days = 127,
    color = 0,
    stock = -1,
    stockAlert = 5,
    start = LocalDate.now().toEpochDay(),
    end = -1L,
)

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Med?>(null) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val current = editing
    if (current != null) {
        BackHandler { editing = null }
        EditMedScreen(
            med = current,
            onClose = { editing = null },
            onSaved = {
                Scheduler.touch(ctx)
                tick++
                editing = null
            }
        )
    } else {
        val onChange: () -> Unit = {
            Scheduler.rescheduleAll(ctx)
            tick++
        }
        Scaffold(
            floatingActionButton = {
                if (tab == 0 || tab == 1) {
                    FloatingActionButton(
                        onClick = { editing = newMed() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) { Icon(Icons.Filled.Add, contentDescription = "Añadir medicina") }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.Today, contentDescription = null) },
                        label = { Text("Hoy") })
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.Medication, contentDescription = null) },
                        label = { Text("Medicinas") })
                    NavigationBarItem(
                        selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Filled.History, contentDescription = null) },
                        label = { Text("Historial") })
                    NavigationBarItem(
                        selected = tab == 3, onClick = { tab = 3 },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Ajustes") })
                }
            }
        ) { inner ->
            val m = Modifier.padding(inner)
            when (tab) {
                0 -> TodayScreen(m, tick, onChange)
                1 -> MedsScreen(m, tick) { editing = it }
                2 -> HistoryScreen(m, tick)
                else -> SettingsScreen(m, tick, onChange)
            }
        }
    }
}

package dev.rtrcompanion.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import dev.rtrcompanion.data.Prefs
import dev.rtrcompanion.log.EventLog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        EventLog.init(this)
        enableEdgeToEdge()
        setContent {
            RtrTheme { AppRoot() }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Ride", Icons.Filled.Home),
    Tab("Diagnostics", Icons.Filled.Build),
    Tab("Log", Icons.Filled.Menu),
    Tab("Bike", Icons.Filled.Settings),
)

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(Perms.bluetoothGranted(ctx)) }
    // Start on the Bike tab until a bike has been chosen.
    var tab by rememberSaveable { mutableIntStateOf(if (Prefs.lastAddress == null) 3 else 0) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        EventLog.i("UI", "Permissions: " + result.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" })
        granted = Perms.bluetoothGranted(ctx)
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Perms.all())
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> RideScreen()
                1 -> DiagnosticsScreen()
                2 -> LogScreen()
                else -> BikeScreen(
                    permissionsGranted = granted,
                    onRequestPermissions = { launcher.launch(Perms.all()) },
                )
            }
        }
    }
}

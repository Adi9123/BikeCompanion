package dev.rtrcompanion.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
        MapSetup.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            RtrTheme { AppRoot() }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Ride", Icons.Filled.Home),
    Tab("Rides", Icons.AutoMirrored.Filled.List),
    Tab("Diagnostics", Icons.Filled.Build),
    Tab("Bike", Icons.Filled.Settings),
)

private const val TAB_RIDE = 0
private const val TAB_RIDES = 1
private const val TAB_BIKE = 3

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(Perms.bluetoothGranted(ctx)) }
    // Start on the Bike tab until a bike has been chosen.
    var tab by rememberSaveable { mutableIntStateOf(if (Prefs.lastAddress == null) TAB_BIKE else TAB_RIDE) }
    var openRide by rememberSaveable { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        EventLog.i("UI", "Permissions: " + result.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" })
        granted = Perms.bluetoothGranted(ctx)
    }

    LaunchedEffect(Unit) {
        if (!Perms.allGranted(ctx)) launcher.launch(Perms.all())
    }

    RideDialogs(onViewRide = { id ->
        openRide = id
        tab = TAB_RIDES
    })

    Scaffold(
        containerColor = Rtr.Bg,
        bottomBar = {
            NavigationBar(containerColor = Rtr.Panel) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = {
                            if (tab == i && i == TAB_RIDES) openRide = null
                            tab = i
                        },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Rtr.Text,
                            selectedTextColor = Rtr.Text,
                            indicatorColor = Rtr.RedDeep,
                            unselectedIconColor = Rtr.Muted,
                            unselectedTextColor = Rtr.Muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Rtr.Bg)
                .padding(padding),
        ) {
            when (tab) {
                TAB_RIDE -> RideScreen(onOpenBike = { tab = TAB_BIKE })
                TAB_RIDES -> RidesScreen(openRide) { openRide = it }
                2 -> DiagnosticsScreen()
                else -> BikeScreen(
                    permissionsGranted = granted,
                    onRequestPermissions = { launcher.launch(Perms.all()) },
                )
            }
        }
    }
}

package dev.bikecompanion.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
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
import dev.bikecompanion.data.Prefs
import dev.bikecompanion.link.LinkService
import dev.bikecompanion.log.EventLog

/** The rider's theme choice, observable so a change in Settings applies at once. */
object Appearance {
    var mode by mutableStateOf("system")

    fun set(m: String) {
        mode = m
        Prefs.themeMode = m
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        EventLog.init(this)
        MapSetup.init(this)
        Motion.refresh(this)
        Appearance.mode = Prefs.themeMode
        setContent {
            BikeTheme(Appearance.mode) {
                val dark = Tone.isDark
                LaunchedEffect(dark) {
                    val style = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Motion.refresh(this)
    }
}

private val TAB_RIDE = Dest.RIDE.ordinal
private val TAB_RIDES = Dest.RIDES.ordinal
private val TAB_SETTINGS = Dest.SETTINGS.ordinal

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(Perms.bluetoothGranted(ctx)) }
    // Start on Settings until a bike has been chosen.
    var tab by rememberSaveable { mutableIntStateOf(if (Prefs.lastAddress == null) TAB_SETTINGS else TAB_RIDE) }
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
    // Reconnect to the remembered bike without a tap, as soon as Bluetooth is allowed.
    LaunchedEffect(granted) {
        if (granted) LinkService.autoConnect(ctx, "app opened")
    }

    FuelDialogs()
    RideDialogs(onViewRide = { id ->
        openRide = id
        tab = TAB_RIDES
    })

    Scaffold(
        containerColor = Tone.Bg,
        bottomBar = {
            TabBar(tab) { i ->
                if (tab == i && i == TAB_RIDES) openRide = null
                tab = i
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Tone.Bg)
                .padding(bottom = padding.calculateBottomPadding())
                .statusBarsPadding(),
        ) {
            // Screens slide a short way in the direction of travel and fade:
            // arrivals glide in, departures leave quickly.
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    if (Motion.reduced) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(Motion.Scene, easing = Ease.Glide)) { w -> dir * w / 10 } +
                            fadeIn(tween(Motion.Base, 60, Ease.Glide))) togetherWith
                            (slideOutHorizontally(tween(Motion.Quick, easing = Ease.Launch)) { w -> -dir * w / 14 } +
                                fadeOut(tween(Motion.Quick, easing = Ease.Launch)))
                    }
                },
                label = "tabs",
            ) { t ->
                when (Dest.entries[t]) {
                    Dest.RIDE -> RideScreen(onOpenBike = { tab = TAB_SETTINGS })
                    Dest.MAP -> MapScreen(onRequestPermissions = { launcher.launch(Perms.all()) })
                    Dest.RIDES -> RidesScreen(openRide) { openRide = it }
                    Dest.GARAGE -> GarageScreen()
                    Dest.SETTINGS -> MoreScreen(
                        permissionsGranted = granted,
                        onRequestPermissions = { launcher.launch(Perms.all()) },
                    )
                }
            }
        }
    }
}

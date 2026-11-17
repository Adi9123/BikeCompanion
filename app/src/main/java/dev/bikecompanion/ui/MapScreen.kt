package dev.bikecompanion.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Paint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.bikecompanion.data.AppState
import dev.bikecompanion.data.Prefs
import dev.bikecompanion.ride.RecPhase
import dev.bikecompanion.ride.RideRecorder
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where the bike was switched off, from [Prefs.parked]. */
data class Parked(val lat: Double, val lon: Double, val at: Long)

fun parkedSpot(): Parked? {
    val p = Prefs.parked.split(',')
    if (p.size < 3) return null
    return Parked(p[0].toDoubleOrNull() ?: return null, p[1].toDoubleOrNull() ?: return null, p[2].toLongOrNull() ?: 0L)
}

/** Best last-known position from any provider, newest first; null if the phone has none. */
@SuppressLint("MissingPermission")
fun lastKnownPoint(lm: LocationManager): GeoPoint? {
    val providers = buildList {
        if (android.os.Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.PASSIVE_PROVIDER)
    }
    return providers.mapNotNull { p ->
        try {
            lm.getLastKnownLocation(p)
        } catch (e: Exception) {
            null
        }
    }.maxByOrNull { it.time }?.let { GeoPoint(it.latitude, it.longitude) }
}

fun hasInternet(ctx: android.content.Context): Boolean = try {
    val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
    val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
    caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
} catch (e: Exception) {
    true
}

/** Centre of India, used only when there is no position, parked bike or ride to show. */
private val FALLBACK = GeoPoint(20.59, 78.96)

fun navigateTo(ctx: android.content.Context, lat: Double, lon: Double) {
    val uri = Uri.parse("google.navigation:q=$lat,$lon&mode=w")
    val i = Intent(Intent.ACTION_VIEW, uri)
    try {
        ctx.startActivity(i)
    } catch (e: Exception) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(My bike)")))
    }
}

/**
 * Live map: follows the phone, draws the ride being recorded, and marks
 * where the bike was parked.
 */
@SuppressLint("MissingPermission")
@Composable
fun MapScreen(onRequestPermissions: () -> Unit) {
    val ctx = LocalContext.current
    val readings by AppState.readings.collectAsState()
    val rec by RideRecorder.state.collectAsState()
    val nav by AppState.nav.collectAsState()
    var me by remember { mutableStateOf<GeoPoint?>(null) }
    var follow by remember { mutableStateOf(true) }
    val granted = Perms.locationGranted(ctx)
    val parked = remember { parkedSpot() }

    val map = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            overlayManager.tilesOverlay.setColorFilter(if (Tone.isDark) TilesOverlay.INVERT_COLORS else null)
            // Never start in the ocean at 0,0: begin on India until something better is known.
            controller.setZoom(5.0)
            controller.setCenter(FALLBACK)
        }
    }
    var centred by remember { mutableStateOf(false) }
    val online = remember { hasInternet(ctx) }
    val meMarker = remember { Marker(map).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); title = "You" } }
    val track = remember {
        Polyline(map).apply {
            outlinePaint.color = (if (Tone.isDark) Tone.Accent else Tone.AccentText).toArgb()
            outlinePaint.strokeWidth = 12f
            outlinePaint.strokeCap = Paint.Cap.ROUND
        }
    }

    DisposableEffect(granted) {
        map.onResume()
        val lm = ctx.getSystemService(LocationManager::class.java)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                me = GeoPoint(location.latitude, location.longitude)
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        if (granted && lm != null) {
            lastKnownPoint(lm)?.let { me = it }
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                try {
                    if (lm.isProviderEnabled(provider)) {
                        lm.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper())
                    }
                } catch (ignored: Exception) {
                }
            }
        }
        onDispose {
            try {
                lm?.removeUpdates(listener)
            } catch (ignored: Exception) {
            }
            map.onPause()
        }
    }
    DisposableEffect(Unit) { onDispose { map.onDetach() } }

    // Parked bike marker, once.
    LaunchedEffect(parked) {
        parked?.let { p ->
            map.overlays.add(Marker(map).apply {
                position = GeoPoint(p.lat, p.lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Bike parked here"
            })
            if (me == null) {
                map.controller.setZoom(16.0)
                map.controller.setCenter(GeoPoint(p.lat, p.lon))
                centred = true
            }
        }
        if (parked == null && me == null) {
            // Show the most recent ride's end point if there is one.
            val last = RideRecorder.lastFix()
            if (last != null) {
                map.controller.setZoom(15.0)
                map.controller.setCenter(GeoPoint(last.first, last.second))
                centred = true
            }
        }
        // Spots saved with the Info switch.
        val fmt = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
        dev.bikecompanion.garage.GarageStore(ctx).spots().takeLast(50).forEach { (t, la, lo) ->
            map.overlays.add(Marker(map).apply {
                position = GeoPoint(la, lo)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Saved spot"
                snippet = fmt.format(Date(t))
            })
        }
        map.overlays.add(track)
        map.overlays.add(meMarker)
        // OpenStreetMap's licence requires this credit on the map.
        map.overlays.add(CopyrightOverlay(ctx))
    }

    // Follow the phone.
    LaunchedEffect(me, follow) {
        val p = me ?: return@LaunchedEffect
        meMarker.position = p
        if (!centred) {
            // First fix: jump straight to street level on the rider.
            map.controller.setZoom(16.0)
            map.controller.setCenter(p)
            centred = true
        } else if (follow) {
            map.controller.animateTo(p)
        }
        map.invalidate()
    }

    // Redraw the ride track every few seconds while recording.
    LaunchedEffect(rec.phase) {
        while (true) {
            val pts = RideRecorder.snapshot().mapNotNull { s ->
                if (s.lat != null && s.lon != null) GeoPoint(s.lat, s.lon) else null
            }
            track.setPoints(pts)
            map.invalidate()
            if (rec.phase == RecPhase.IDLE) break
            delay(5_000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())

        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Panel(Modifier.fillMaxWidth(), color = Tone.Panel.copy(alpha = 0.92f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SPEED", style = MaterialTheme.typography.labelSmall, color = Tone.Muted)
                        Text(readings["speed"]?.raw?.let { "$it km/h" } ?: "—", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("RIDE", style = MaterialTheme.typography.labelSmall, color = Tone.Muted)
                        Text(
                            if (rec.phase == RecPhase.IDLE) "Not recording" else "${km(rec.distanceKm)} km",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                nav?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${it.maneuver.label} · ${it.distanceToTurnM?.let { d -> formatDistance(d) } ?: "—"}",
                        fontWeight = FontWeight.Bold,
                        color = Tone.Amber,
                    )
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!online) {
                Panel(Modifier.fillMaxWidth(), color = Tone.RedDeep) {
                    Text("No internet: map tiles can't load.", fontWeight = FontWeight.SemiBold)
                    Text("Areas you have viewed before still show from the cache.", style = MaterialTheme.typography.bodySmall, color = Tone.Muted)
                }
            }
            if (!granted) {
                Panel(Modifier.fillMaxWidth()) {
                    Text("Allow location to see where you are.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRequestPermissions) { Text("Allow location") }
                }
            } else if (me == null) {
                Panel(Modifier.fillMaxWidth(), color = Tone.Panel.copy(alpha = 0.92f)) {
                    Text("Finding your location…", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Make sure Location is switched on in quick settings. GPS can take a minute indoors.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Tone.Muted,
                    )
                }
            }
            parked?.let { p ->
                Panel(Modifier.fillMaxWidth(), color = Tone.Panel.copy(alpha = 0.92f)) {
                    Text("BIKE PARKED", style = MaterialTheme.typography.labelSmall, color = Tone.Muted)
                    Text(
                        SimpleDateFormat("EEE d MMM · h:mm a", Locale.getDefault()).format(Date(p.at)),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { navigateTo(ctx, p.lat, p.lon) }) { Text("Navigate to bike") }
                        FilledTonalButton(onClick = {
                            follow = false
                            map.controller.animateTo(GeoPoint(p.lat, p.lon))
                        }) { Text("Show") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    follow = true
                    me?.let { map.controller.animateTo(it) }
                }) { Text(if (follow) "Following you" else "Follow me") }
            }
        }
    }
}

package dev.bikecompanion.ui

import android.content.Context
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.bikecompanion.ride.RideSample
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

object MapSetup {
    /** OpenStreetMap needs an identifying user agent; tiles are cached in app storage. */
    fun init(ctx: Context) {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.filesDir, "osmdroid")
            osmdroidTileCache = File(ctx.filesDir, "osmdroid/tiles")
        }
    }
}

/** Colour of a route segment by speed. */
private fun speedColor(speed: Int) = when {
    speed < 20 -> Rtr.Teal
    speed < 40 -> Rtr.Green
    speed < 60 -> Rtr.Amber
    else -> Rtr.Red
}

/**
 * The ride's GPS track on a dark OpenStreetMap, coloured by speed
 * (teal < 20, green < 40, amber < 60, red above), with start and end markers.
 */
@Composable
fun RouteMap(samples: List<RideSample>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val track = remember(samples) {
        samples.filter { it.lat != null && it.lon != null }
            .map { GeoPoint(it.lat!!, it.lon!!) to (it.speed ?: 0) }
    }
    val map = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
            controller.setZoom(14.0)
        }
    }
    DisposableEffect(Unit) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    LaunchedEffect(track) {
        map.overlays.clear()
        if (track.isEmpty()) return@LaunchedEffect
        // Merge consecutive points of the same speed band into one line.
        var start = 0
        while (start < track.size - 1) {
            val band = speedColor(track[start].second)
            var end = start + 1
            while (end < track.size - 1 && speedColor(track[end].second) == band) end++
            val line = Polyline(map).apply {
                setPoints(track.subList(start, end + 1).map { it.first })
                outlinePaint.color = band.toArgb()
                outlinePaint.strokeWidth = 12f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
            }
            map.overlays.add(line)
            start = end
        }
        map.overlays.add(Marker(map).apply {
            position = track.first().first
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Start"
        })
        map.overlays.add(CopyrightOverlay(map.context))
        map.overlays.add(Marker(map).apply {
            position = track.last().first
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "End"
        })
        val points = track.map { it.first }
        val fit = {
            if (points.distinct().size < 2) {
                map.controller.setZoom(16.0)
                map.controller.setCenter(points.first())
            } else {
                map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, 80)
            }
            map.invalidate()
        }
        if (map.isLayoutOccurred) fit() else map.addOnFirstLayoutListener { _, _, _, _, _ -> fit() }
    }
    AndroidView(factory = { map }, modifier = modifier)
}

package dev.bikecompanion.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.bikecompanion.data.Prefs
import dev.bikecompanion.nav.ArrowStore
import dev.bikecompanion.protocol.Maneuver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private class AppRow(val pkg: String, val label: String, val icon: ImageBitmap?)

/** Pick which apps may show a sender's name on the cluster. Calls and Maps are separate switches. */
@Composable
fun RelayAppsPage() {
    val ctx = LocalContext.current
    var chosen by remember { mutableStateOf(Prefs.relayApps) }
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<AppRow>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcher, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .filter { it.first != ctx.packageName }
                .sortedBy { it.second.lowercase(Locale.getDefault()) }
                .map { (pkg, label) ->
                    val icon = try {
                        pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                    AppRow(pkg, label, icon)
                }
        }
    }

    Text(
        "Incoming calls and Google Maps directions have their own switches. Pick any other app here and the " +
            "cluster shows who a notification is from. The message itself is never shown.",
        style = MaterialTheme.typography.bodyMedium,
        color = Rtr.Muted,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        placeholder = { Text("Search apps") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    val list = apps
    if (list == null) {
        Text("Loading apps…", color = Rtr.Muted)
        return
    }
    val q = query.trim().lowercase(Locale.getDefault())
    val shown = list.filter { q.isEmpty() || it.label.lowercase(Locale.getDefault()).contains(q) }
        .sortedByDescending { it.pkg in chosen }
    Panel(Modifier.fillMaxWidth()) {
        shown.forEachIndexed { i, app ->
            val on = app.pkg in chosen
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        chosen = if (on) chosen - app.pkg else chosen + app.pkg
                        Prefs.relayApps = chosen
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val icon = app.icon
                if (icon != null) {
                    Image(icon, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
                } else {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Rtr.Panel2))
                }
                Spacer(Modifier.width(14.dp))
                Text(app.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Switch(checked = on, onCheckedChange = {
                    chosen = if (it) chosen + app.pkg else chosen - app.pkg
                    Prefs.relayApps = chosen
                })
            }
            if (i < shown.size - 1) Spacer(Modifier.height(2.dp))
        }
    }
}

/** Every Maps arrow the app has seen, what it took each to mean, and a way to correct it. */
@Composable
fun ArrowsPage() {
    val ctx = LocalContext.current
    remember { ArrowStore.init(ctx) }
    val version by ArrowStore.version.collectAsState()
    val entries = remember(version) { ArrowStore.all() }
    var editing by remember { mutableStateOf<ArrowStore.Entry?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Text(
        "Google Maps shows each turn as a picture. The app reads the picture, and remembers it whenever the " +
            "directions text names the turn. If an arrow here is wrong, tap it and pick the right one; the " +
            "app uses your choice from then on.",
        style = MaterialTheme.typography.bodyMedium,
        color = Rtr.Muted,
    )
    Spacer(Modifier.height(16.dp))
    if (entries.isEmpty()) {
        Panel(Modifier.fillMaxWidth()) {
            Text("No arrows yet", fontWeight = FontWeight.SemiBold)
            Text("Start a route in Google Maps; arrows appear here as you ride.", color = Rtr.Muted, style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    entries.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { e -> ArrowTile(e, Modifier.weight(1f)) { editing = e } }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(10.dp))
    }
    TextButton(onClick = { confirmClear = true }) { Text("Forget all arrows") }

    editing?.let { e ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("What does this arrow mean?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CHOICES.forEach { m ->
                        Text(
                            m.label,
                            fontWeight = if (m == e.maneuver) FontWeight.Bold else FontWeight.Normal,
                            color = if (m == e.maneuver) Rtr.Red else Rtr.Text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ArrowStore.setByRider(e.sig, m)
                                    editing = null
                                }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Forget all arrows?") },
            text = { Text("Your corrections are removed too. The app starts learning again on your next route.") },
            confirmButton = {
                TextButton(onClick = {
                    ArrowStore.clear()
                    confirmClear = false
                }) { Text("Forget") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private val CHOICES = listOf(
    Maneuver.STRAIGHT, Maneuver.LEFT, Maneuver.RIGHT, Maneuver.SLIGHT_LEFT, Maneuver.SLIGHT_RIGHT,
    Maneuver.SHARP_LEFT, Maneuver.SHARP_RIGHT, Maneuver.KEEP_LEFT, Maneuver.KEEP_RIGHT,
    Maneuver.UTURN_LEFT, Maneuver.UTURN_RIGHT, Maneuver.MERGE, Maneuver.ROUNDABOUT_LEFT,
    Maneuver.ROUNDABOUT_STRAIGHT, Maneuver.ROUNDABOUT_RIGHT, Maneuver.ROUNDABOUT_UTURN, Maneuver.ARRIVE,
)

@Composable
private fun ArrowTile(e: ArrowStore.Entry, modifier: Modifier, onClick: () -> Unit) {
    val image = remember(e.sig) {
        ArrowStore.iconFile(e.sig)?.let { f -> BitmapFactory.decodeFile(f.path)?.asImageBitmap() }
    }
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Rtr.Panel)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF15171C)),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) Image(image, contentDescription = null, modifier = Modifier.fillMaxWidth(0.7f).aspectRatio(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            e.maneuver?.label ?: "Unknown",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            e.via.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (e.via == ArrowStore.Via.YOU) Rtr.Green else Rtr.Muted,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

package dev.rtrcompanion.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.rtrcompanion.data.AppState
import dev.rtrcompanion.data.LinkPhase
import dev.rtrcompanion.data.Prefs
import dev.rtrcompanion.link.Found
import dev.rtrcompanion.link.LinkService
import dev.rtrcompanion.link.Scanner
import dev.rtrcompanion.protocol.Outbound
import dev.rtrcompanion.protocol.Scramble

@Composable
fun BikeScreen(permissionsGranted: Boolean, onRequestPermissions: () -> Unit) {
    val ctx = LocalContext.current
    val link by AppState.link.collectAsState()
    val scanner = remember { Scanner(ctx.applicationContext) }
    val results by scanner.results.collectAsState()
    val scanning by scanner.scanning.collectAsState()
    DisposableEffect(Unit) { onDispose { scanner.stop() } }

    var savedAddress by remember { mutableStateOf(Prefs.lastAddress) }
    var savedName by remember { mutableStateOf(Prefs.lastName) }
    val btOn = ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        StatusBanner(link)

        if (!permissionsGranted) {
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Bluetooth permission is needed to find and connect to the bike.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRequestPermissions) { Text("Grant") }
                        OutlinedButton(onClick = {
                            ctx.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                            )
                        }) { Text("App settings") }
                    }
                }
            }
        } else if (!btOn) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                try {
                    ctx.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                } catch (ignored: SecurityException) {
                }
            }) { Text("Turn Bluetooth on") }
        }

        // ---------------------------------------------------------- saved bike
        SectionTitle("Your bike")
        if (savedAddress == null) {
            Text("No bike saved yet. Scan below and tap your bike (its name usually starts with TVSBT).")
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(savedName ?: "Unnamed device", fontWeight = FontWeight.Bold)
                    Text(savedAddress ?: "", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val busy = link.phase != LinkPhase.IDLE && link.phase != LinkPhase.FAILED
                        Button(
                            enabled = permissionsGranted && !busy,
                            onClick = { savedAddress?.let { LinkService.connect(ctx, it) } },
                        ) { Text("Connect") }
                        OutlinedButton(
                            enabled = link.phase != LinkPhase.IDLE,
                            onClick = { LinkService.disconnect(ctx) },
                        ) { Text("Disconnect") }
                    }
                }
            }
        }

        // ---------------------------------------------------------- scan
        SectionTitle("Find the bike")
        Text(
            "Ignition ON. Force-stop the TVS Connect app and turn off Bluetooth on any other phone " +
                "that has paired with the bike: the cluster accepts only one connection at a time.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = permissionsGranted && btOn && !scanning, onClick = { scanner.start() }) {
                Text(if (scanning) "Scanning…" else "Scan (15 s)")
            }
            if (scanning) OutlinedButton(onClick = { scanner.stop() }) { Text("Stop") }
        }
        results.take(25).forEach { f ->
            DeviceRow(f) {
                scanner.stop()
                Prefs.lastAddress = f.address
                Prefs.lastName = f.name
                savedAddress = f.address
                savedName = f.name
                LinkService.connect(ctx, f.address)
            }
        }

        // ---------------------------------------------------------- options
        SectionTitle("Options")
        var keepalive by remember { mutableStateOf(Prefs.keepalive) }
        ToggleRow(
            "Send phone status every 2 s",
            "Time, phone battery and signal, as the official app does. The bike may drop the link without it.",
            keepalive,
        ) { keepalive = it; Prefs.keepalive = it }

        var autoReconnect by remember { mutableStateOf(Prefs.autoReconnect) }
        ToggleRow("Reconnect automatically", "Retry with increasing delays (2 s up to 30 s) after a drop.", autoReconnect) {
            autoReconnect = it; Prefs.autoReconnect = it
        }

        var inScheme by remember { mutableStateOf(Prefs.inboundScheme) }
        SchemeChooser("Decode bike data as", inScheme) { inScheme = it; Prefs.inboundScheme = it }
        var outScheme by remember { mutableStateOf(Prefs.outboundScheme) }
        SchemeChooser("Scramble data sent to bike as", outScheme) { outScheme = it; Prefs.outboundScheme = it }
        Text(
            "Leave both on Auto. Auto detects the scramble from each frame's checksum.",
            style = MaterialTheme.typography.bodySmall,
        )

        SectionTitle("Rider name on the cluster")
        var name by remember { mutableStateOf(Prefs.riderName) }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(Outbound.TEXT_MAX) },
            label = { Text("Name (letters, digits, space; max 17)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = name.isNotBlank(),
            onClick = {
                Prefs.riderName = name
                if (link.phase == LinkPhase.LIVE) LinkService.sendRiderName(ctx)
            },
        ) { Text(if (link.phase == LinkPhase.LIVE) "Save and send" else "Save (sent on connect)") }

        SectionTitle("What this app sends")
        Text(
            "Only the phone-status frame and the rider name. It never sends calibration, ride-mode or " +
                "vehicle-setting commands, and it has no internet access.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DeviceRow(f: Found, onClick: () -> Unit) {
    val colors = if (f.looksLikeCluster) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.name ?: "(no name)", fontWeight = if (f.looksLikeCluster) FontWeight.Bold else FontWeight.Normal)
                Text(f.address, style = MaterialTheme.typography.bodySmall)
                if (f.looksLikeCluster) Text("Looks like a TVS cluster", style = MaterialTheme.typography.labelSmall)
            }
            Text("${f.rssi} dBm", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SchemeChooser(title: String, selected: String, onSelect: (String) -> Unit) {
    Text(title, modifier = Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val options = listOf(Prefs.AUTO to "Auto") + Scramble.entries.map { it.name to shortLabel(it) }
        options.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

private fun shortLabel(s: Scramble): String = when (s) {
    Scramble.XOR_EA -> "EA"
    Scramble.XOR_EB -> "EB"
    Scramble.NONE -> "None"
}

package dev.bikecompanion.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import dev.bikecompanion.backup.Backup
import dev.bikecompanion.data.AppState
import dev.bikecompanion.data.LinkPhase
import dev.bikecompanion.data.Prefs
import dev.bikecompanion.garage.BitRef
import dev.bikecompanion.garage.Switch as BikeSwitch
import dev.bikecompanion.garage.SwitchLearn
import dev.bikecompanion.garage.RideModes
import dev.bikecompanion.monitor.InfoAction
import dev.bikecompanion.monitor.InfoButton
import androidx.compose.material3.RadioButton
import dev.bikecompanion.link.Found
import dev.bikecompanion.link.LinkService
import dev.bikecompanion.link.Scanner
import dev.bikecompanion.protocol.Maneuver
import dev.bikecompanion.protocol.NavInstruction
import dev.bikecompanion.protocol.Outbound
import dev.bikecompanion.protocol.Scramble
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Page { MAIN, CONNECT, DISPLAY_TESTS, LEARN, DIAGNOSTICS, ADVANCED, INFO, APPS, ARROWS }

/** The More tab: settings in sections, and the tools as separate pages. */
@Composable
fun MoreScreen(permissionsGranted: Boolean, onRequestPermissions: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(Page.MAIN) }
    if (page != Page.MAIN) BackHandler { page = Page.MAIN }
    when (page) {
        Page.MAIN -> MoreMain(permissionsGranted, onRequestPermissions) { page = it }
        Page.CONNECT -> SubPage("Connect a bike", { page = Page.MAIN }) { ConnectPage(permissionsGranted, onRequestPermissions) }
        Page.DISPLAY_TESTS -> SubPage("Cluster display tests", { page = Page.MAIN }) { DisplayTestsPage() }
        Page.LEARN -> SubPage("Learn bike switches", { page = Page.MAIN }) { LearnSwitchesPage() }
        Page.ADVANCED -> SubPage("Advanced", { page = Page.MAIN }) { AdvancedPage() }
        Page.APPS -> SubPage("Apps on the cluster", { page = Page.MAIN }) { RelayAppsPage() }
        Page.ARROWS -> SubPage("Navigation arrows", { page = Page.MAIN }) { ArrowsPage() }
        Page.INFO -> SubPage("Info switch & ride modes", { page = Page.MAIN }) { InfoSwitchPage { page = Page.LEARN } }
        Page.DIAGNOSTICS -> Column(Modifier.fillMaxSize()) {
            PageHeader("Diagnostics & log") { page = Page.MAIN }
            DiagnosticsScreen()
        }
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(title, onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter),
            content = {
                content()
                Spacer(Modifier.height(24.dp))
            },
        )
    }
}

/** A tappable row that opens a sub-page. */
@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Tone.Muted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Tone.Muted)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Tone.Muted)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = Tone.Line, modifier = Modifier.padding(vertical = 2.dp))

// ---------------------------------------------------------------- main

@Composable
private fun MoreMain(permissionsGranted: Boolean, onRequestPermissions: () -> Unit, open: (Page) -> Unit) {
    val ctx = LocalContext.current
    val link by AppState.link.collectAsState()
    val scope = rememberCoroutineScope()
    val notificationAccess = AppState.navAccess.collectAsState().value ||
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore backup?") },
            text = { Text("Rides, fuel, service and battery records from the backup are added to this phone. Files with the same name are replaced.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    scope.launch {
                        val n = withContext(Dispatchers.IO) { runCatching { Backup.restore(ctx, uri) }.getOrDefault(-1) }
                        Toast.makeText(ctx, if (n >= 0) "Restored $n files" else "That file is not a backup", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } },
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter),
    ) {
        ScreenTitle("Settings")

        // ------------------------------------------------------- appearance
        SectionTitle("Appearance")
        Panel(Modifier.fillMaxWidth()) {
            val modes = listOf("system", "light", "dark")
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Text("Light reads best in sunlight; dark is easier at night.", style = MaterialTheme.typography.bodySmall, color = Tone.Muted)
            Spacer(Modifier.height(12.dp))
            Segmented(listOf("Phone", "Light", "Dark"), modes.indexOf(Appearance.mode).coerceAtLeast(0)) { Appearance.set(modes[it]) }
        }

        // ------------------------------------------------------- bike
        SectionTitle("Bike")
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(linkColor(link.phase), 10)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(Prefs.lastName ?: "No bike saved", fontWeight = FontWeight.Bold)
                    Text(
                        link.phase.label + if (link.detail.isNotBlank()) " · ${link.detail}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Tone.Muted,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val address = Prefs.lastAddress
                if (address != null && (link.phase == LinkPhase.IDLE || link.phase == LinkPhase.FAILED)) {
                    Button(enabled = permissionsGranted, onClick = { LinkService.connect(ctx, address) }) { Text("Connect") }
                }
                if (link.phase != LinkPhase.IDLE) {
                    OutlinedButton(onClick = { LinkService.disconnect(ctx) }) { Text("Disconnect") }
                }
                OutlinedButton(onClick = { open(Page.CONNECT) }) { Text(if (address == null) "Find my bike" else "Change bike") }
            }
            Spacer(Modifier.height(4.dp))
            var auto by remember { mutableStateOf(Prefs.autoConnectOnOpen) }
            ToggleRow("Connect automatically", "Whenever the app is open or running, and after the phone restarts. It links up as soon as you switch the ignition on.", auto) {
                auto = it
                Prefs.autoConnectOnOpen = it
            }
            if (!permissionsGranted) {
                Spacer(Modifier.height(8.dp))
                Text("Bluetooth permission is needed.", color = Tone.Amber)
                TextButton(onClick = onRequestPermissions) { Text("Grant permissions") }
            }
        }

        // ------------------------------------------------------- cluster
        SectionTitle("On the cluster")
        Panel(Modifier.fillMaxWidth()) {
            if (!notificationAccess) {
                Text("Directions, calls and messages need notification access.", color = Tone.Amber)
                TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                    Text("Allow notification access")
                }
                Divider()
            }
            var nav by remember { mutableStateOf(Prefs.navEnabled) }
            ToggleRow("Google Maps directions", "Arrow, distance and instruction while navigating.", nav) { nav = it; Prefs.navEnabled = it }
            Divider()
            var calls by remember { mutableStateOf(Prefs.callAlerts) }
            ToggleRow("Incoming calls", "Caller's name on the cluster, and the missed-call count.", calls) { calls = it; Prefs.callAlerts = it }
            Divider()
            val relayCount = remember { Prefs.relayApps.size }
            NavRow(
                "Other apps",
                if (relayCount == 0) "None. Only calls and Maps reach the cluster." else "$relayCount app(s) show the sender's name",
            ) { open(Page.APPS) }
            Divider()
            NavRow("Navigation arrows", "Check and correct the turn arrows the app has seen") { open(Page.ARROWS) }
            Divider()
            var music by remember { mutableStateOf(Prefs.musicOnCluster) }
            ToggleRow("Song title", "Show the track name when the song changes.", music) { music = it; Prefs.musicOnCluster = it }
            Divider()
            var over by remember { mutableStateOf(Prefs.overspeedOn) }
            var limit by remember { mutableIntStateOf(Prefs.overspeedKmh) }
            ToggleRow("Overspeed warning", "The cluster warns above $limit km/h.", over) { over = it; Prefs.overspeedOn = it }
            if (over) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(60, 80, 100, 120).forEach { v ->
                        FilterChip(selected = limit == v, onClick = { limit = v; Prefs.overspeedKmh = v }, label = { Text("$v") })
                    }
                }
            }
            Divider()
            var name by remember { mutableStateOf(Prefs.riderName) }
            Text("Rider name", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Outbound.TEXT_MAX) },
                    singleLine = true,
                    placeholder = { Text("Shown at start-up") },
                    modifier = Modifier.weight(1f),
                )
                TextButton(enabled = name.isNotBlank(), onClick = {
                    Prefs.riderName = name
                    if (link.phase == LinkPhase.LIVE) LinkService.sendRiderName(ctx)
                    Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
                }) { Text("Save") }
            }
            Divider()
            NavRow("Test the cluster display", "Sample directions, arrow codes and text, without riding") { open(Page.DISPLAY_TESTS) }
        }

        // ------------------------------------------------------- alerts & rides
        SectionTitle("Alerts & rides")
        Panel(Modifier.fillMaxWidth()) {
            var alerts by remember { mutableStateOf(Prefs.alertsOn) }
            ToggleRow(
                "Bike alerts",
                "Phone notification for overheating, charging problems, fault codes, side stand and reserve fuel.",
                alerts,
            ) { alerts = it; Prefs.alertsOn = it }
            Divider()
            Text("Save a ride after key-off", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            var minutes by remember { mutableIntStateOf(Prefs.autoEndMinutes) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2, 3, 5, 10).forEach { m ->
                    FilterChip(selected = minutes == m, onClick = { minutes = m; Prefs.autoEndMinutes = m }, label = { Text("$m min") })
                }
            }
            if (!Perms.locationGranted(ctx)) {
                Spacer(Modifier.height(6.dp))
                Text("Location is off, so rides are recorded without a route.", color = Tone.Amber, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRequestPermissions) { Text("Allow location") }
            }
        }

        // ------------------------------------------------------- data
        SectionTitle("Your data")
        Panel(Modifier.fillMaxWidth()) {
            NavRow("Back up", "Save rides, fuel, service and battery history to a file") {
                scope.launch {
                    val file = withContext(Dispatchers.IO) { Backup.export(ctx) }
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
                    val send = Intent(Intent.ACTION_SEND).setType("application/zip")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(send, "Save backup"))
                }
            }
            Divider()
            NavRow("Restore", "Bring back a backup file") { restorePicker.launch(arrayOf("application/zip", "application/octet-stream")) }
        }

        // ------------------------------------------------------- tools
        SectionTitle("Tools")
        Panel(Modifier.fillMaxWidth()) {
            NavRow("Info switch & ride modes", "Choose what short, double and long presses do") { open(Page.INFO) }
            Divider()
            NavRow("Learn bike switches", "Teach the app your indicators, high beam and Info switch") { open(Page.LEARN) }
            Divider()
            NavRow("Diagnostics & log", "Every frame from the bike, connection health, session log") { open(Page.DIAGNOSTICS) }
            Divider()
            NavRow("Advanced", "Keepalive, reconnect and decoding options") { open(Page.ADVANCED) }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Bike Companion talks to the bike directly over Bluetooth. Internet is used only for map tiles; " +
                "nothing about you or your bike is uploaded. It never sends calibration or vehicle-setting commands.",
            style = MaterialTheme.typography.bodySmall,
            color = Tone.Muted,
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- info switch

@Composable
private fun InfoSwitchPage(openLearn: () -> Unit) {
    val learned = remember { SwitchLearn.decode(Prefs.switchMap).containsKey(BikeSwitch.CALL_BUTTON) }
    if (!learned) {
        Panel(Modifier.fillMaxWidth()) {
            Text("Teach the app your Info switch first", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "The bike reports the switch as one bit in its data. Run the switch learner once with the bike on, " +
                    "and hold the Info switch when it asks.",
                style = MaterialTheme.typography.bodySmall, color = Tone.Muted,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = openLearn) { Text("Learn switches") }
        }
        Spacer(Modifier.height(12.dp))
    }

    SectionTitle("What each press does")
    Panel(Modifier.fillMaxWidth()) {
        var short by remember { mutableStateOf(Prefs.infoShort) }
        var double by remember { mutableStateOf(Prefs.infoDouble) }
        var long by remember { mutableStateOf(Prefs.infoLong) }
        ActionRow("Short press", short) { short = it; Prefs.infoShort = it }
        Divider()
        ActionRow("Double press", double) { double = it; Prefs.infoDouble = it }
        Divider()
        ActionRow("Long press (1.5 s)", long) { long = it; Prefs.infoLong = it }
        Spacer(Modifier.height(6.dp))
        Text(
            "The cluster still does its own job too (it cancels calls and clears messages). " +
                "Answers like range or fuel appear on the cluster's text line.",
            style = MaterialTheme.typography.bodySmall, color = Tone.Muted,
        )
    }

    SectionTitle("Smart overrides")
    Panel(Modifier.fillMaxWidth()) {
        var decline by remember { mutableStateOf(Prefs.infoDeclinesCalls) }
        ToggleRow("Short press declines a ringing call", "Works with any dialer that shows a Decline button.", decline) {
            decline = it; Prefs.infoDeclinesCalls = it
        }
        Divider()
        var pump by remember { mutableStateOf(Prefs.infoPumpOnReserve) }
        ToggleRow("Long press finds a petrol pump on reserve", "Sends a notification that opens Maps near you.", pump) {
            pump = it; Prefs.infoPumpOnReserve = it
        }
        Divider()
        var onCluster by remember { mutableStateOf(Prefs.alertsOnCluster) }
        ToggleRow("Show alerts on the cluster", "FUEL LOW, SIDE STAND DOWN, HIGH ENGINE TEMP and others.", onCluster) {
            onCluster = it; Prefs.alertsOnCluster = it
        }
    }

    SectionTitle("Ride modes")
    Panel(Modifier.fillMaxWidth()) {
        val readings by AppState.readings.collectAsState()
        val current = readings["mode"]?.raw?.let { RideModes.mode(it) }
        var names by remember { mutableStateOf(RideModes.decodeNames(Prefs.modeNames)) }
        Text(
            "Urban and Rain are limited to 103 km/h, Sport to 114 km/h (owner's manual). " +
                "Switch modes with the Mode button while stopped.",
            style = MaterialTheme.typography.bodySmall, color = Tone.Muted,
        )
        Spacer(Modifier.height(8.dp))
        if (current == null) {
            Text("Connect to the bike to see the current mode.", color = Tone.Muted)
        } else {
            Text("The bike reports mode $current. Which mode does the cluster show?", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Urban", "Sport", "Rain").forEach { n ->
                    FilterChip(
                        selected = RideModes.name(current, names) == n && names.containsKey(current),
                        onClick = {
                            names = names + (current to n)
                            Prefs.modeNames = RideModes.encodeNames(names)
                        },
                        label = { Text(n) },
                    )
                }
            }
        }
        if (names.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Confirmed: " + names.entries.sortedBy { it.key }.joinToString { "${it.key} = ${it.value}" },
                style = MaterialTheme.typography.bodySmall, color = Tone.Muted,
            )
            TextButton(onClick = { names = emptyMap(); Prefs.modeNames = "" }) { Text("Reset") }
        }
    }
}

@Composable
private fun ActionRow(title: String, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val action = InfoButton.action(selected)
    NavRow(title, action.label + " · " + action.detail) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    InfoAction.entries.filter { it != InfoAction.DECLINE_CALL }.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(a.name); open = false }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = a == action, onClick = { onSelect(a.name); open = false })
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(a.label, fontWeight = FontWeight.SemiBold)
                                Text(a.detail, style = MaterialTheme.typography.bodySmall, color = Tone.Muted)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

// ---------------------------------------------------------------- connect

@Composable
private fun ConnectPage(permissionsGranted: Boolean, onRequestPermissions: () -> Unit) {
    val ctx = LocalContext.current
    val scanner = remember { Scanner(ctx.applicationContext) }
    val results by scanner.results.collectAsState()
    val scanning by scanner.scanning.collectAsState()
    DisposableEffect(Unit) { onDispose { scanner.stop() } }
    val btOn = ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    Panel(Modifier.fillMaxWidth()) {
        Text("Before scanning", fontWeight = FontWeight.SemiBold)
        Text(
            "1. Switch the ignition on.\n2. Force-stop the TVS Connect app.\n3. Turn off Bluetooth on any other phone paired with the bike.\n" +
                "The cluster accepts only one connection at a time.",
            style = MaterialTheme.typography.bodySmall,
            color = Tone.Muted,
        )
    }
    Spacer(Modifier.height(12.dp))
    when {
        !permissionsGranted -> Button(onClick = onRequestPermissions) { Text("Grant Bluetooth permission") }
        !btOn -> Button(onClick = {
            try {
                ctx.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (ignored: SecurityException) {
            }
        }) { Text("Turn Bluetooth on") }
        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !scanning, onClick = { scanner.start() }) { Text(if (scanning) "Scanning…" else "Scan for my bike") }
            if (scanning) OutlinedButton(onClick = { scanner.stop() }) { Text("Stop") }
        }
    }
    if (!permissionsGranted) {
        TextButton(onClick = {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
        }) { Text("Open app settings") }
    }
    Spacer(Modifier.height(8.dp))
    results.take(25).forEach { f ->
        DeviceRow(f) {
            scanner.stop()
            Prefs.lastAddress = f.address
            Prefs.lastName = f.name
            LinkService.connect(ctx, f.address)
            Toast.makeText(ctx, "Connecting to ${f.name ?: f.address}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun DeviceRow(f: Found, onClick: () -> Unit) {
    Panel(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        color = if (f.looksLikeCluster) Tone.RedDeep else Tone.Panel,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.name ?: "(no name)", fontWeight = if (f.looksLikeCluster) FontWeight.Bold else FontWeight.Normal)
                Text(f.address, style = MaterialTheme.typography.bodySmall, color = Tone.Muted)
                if (f.looksLikeCluster) Text("Looks like your bike — tap to connect", style = MaterialTheme.typography.labelSmall, color = Tone.Amber)
            }
            Text("${f.rssi} dBm", style = MaterialTheme.typography.bodySmall, color = Tone.Muted)
        }
    }
}

// ---------------------------------------------------------------- tests

private fun testStep(m: Maneuver, dist: Int, text: String, remaining: Int = 5400, icon: Int? = null) =
    NavInstruction(m, dist, remaining, 12, text, source = "Test", iconOverride = icon)

@Composable
private fun DisplayTestsPage() {
    val link by AppState.link.collectAsState()
    if (link.phase != LinkPhase.LIVE) {
        Text("Connect to the bike first; the tests send to the cluster.", color = Tone.Amber, modifier = Modifier.padding(bottom = 8.dp))
    }
    Panel(Modifier.fillMaxWidth()) {
        Text("Directions", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { AppState.nav.value = testStep(Maneuver.LEFT, 300, "Turn left") }) { Text("Left") }
            OutlinedButton(onClick = { AppState.nav.value = testStep(Maneuver.RIGHT, 40, "Turn right") }) { Text("Right") }
            OutlinedButton(onClick = { AppState.nav.value = testStep(Maneuver.UTURN_RIGHT, 150, "Make a U turn") }) { Text("U-turn") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { AppState.nav.value = testStep(Maneuver.STRAIGHT, 2500, "Continue straight") }) { Text("Straight") }
            OutlinedButton(onClick = { AppState.nav.value = testStep(Maneuver.ARRIVE, 0, "Arrived", remaining = 20) }) { Text("Arrive") }
            OutlinedButton(onClick = { AppState.nav.value = null }) { Text("Stop") }
        }
    }
    Spacer(Modifier.height(12.dp))
    Panel(Modifier.fillMaxWidth()) {
        Text("Arrow codes", fontWeight = FontWeight.SemiBold)
        Text("Step through codes and note which picture the cluster draws.", style = MaterialTheme.typography.bodySmall, color = Tone.Muted)
        var code by remember { mutableStateOf("0") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { v -> code = v.filter { it.isDigit() }.take(3) },
                label = { Text("Code") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                val n = (code.toIntOrNull() ?: 0).coerceIn(0, 255)
                AppState.nav.value = testStep(Maneuver.UNKNOWN, 200, "Code $n", icon = n)
            }) { Text("Send") }
            OutlinedButton(onClick = {
                val n = ((code.toIntOrNull() ?: 0) + 1).coerceIn(0, 255)
                code = n.toString()
                AppState.nav.value = testStep(Maneuver.UNKNOWN, 200, "Code $n", icon = n)
            }) { Text("Next") }
        }
    }
    Spacer(Modifier.height(12.dp))
    Panel(Modifier.fillMaxWidth()) {
        Text("Message line", fontWeight = FontWeight.SemiBold)
        Text(
            "Sends \"BIKE TEST 123\". If the cluster shows it backwards, switch the setting below.",
            style = MaterialTheme.typography.bodySmall,
            color = Tone.Muted,
        )
        Spacer(Modifier.height(6.dp))
        Button(onClick = { AppState.clusterTexts.tryEmit("BIKE TEST 123") }) { Text("Send test text") }
        var reversed by remember { mutableStateOf(Prefs.clusterTextReversed) }
        ToggleRow("Send text reversed", "The official app does this for this cluster family.", reversed) {
            reversed = it; Prefs.clusterTextReversed = it
        }
    }
}

// ---------------------------------------------------------------- learn switches

@Composable
private fun LearnSwitchesPage() {
    val link by AppState.link.collectAsState()
    // step 0 = baseline, 1..n = switches, n+1 = done
    var step by remember { mutableIntStateOf(-1) }
    var capturing by remember { mutableStateOf(false) }
    var baseline by remember { mutableStateOf<Map<BitRef, Double>>(emptyMap()) }
    var found by remember { mutableStateOf(SwitchLearn.decode(Prefs.switchMap)) }
    var lastResult by remember { mutableStateOf("") }
    val switches = BikeSwitch.entries.filter { it.learnable }

    LaunchedEffect(capturing) {
        if (!capturing) return@LaunchedEffect
        val samples = ArrayList<Map<Int, ByteArray>>()
        var lastCounts = -1L
        repeat(40) {
            val frames = AppState.frames.value
            val counts = frames.values.sumOf { it.count }
            if (counts != lastCounts) {
                lastCounts = counts
                samples += frames.mapValues { it.value.lastClear }
            }
            delay(200)
        }
        val fr = SwitchLearn.fractions(samples)
        if (step == 0) {
            baseline = fr
            lastResult = "Baseline captured."
        } else {
            val sw = switches[step - 1]
            val exclude = found.filterKeys { it != sw }.values.map { it.first }.toSet()
            val best = SwitchLearn.best(baseline, fr, exclude)
            lastResult = if (best != null) {
                found = found + (sw to best)
                "${sw.label}: found (byte ${best.first.position}, bit ${best.first.bit})."
            } else {
                "${sw.label}: no change seen. You can repeat or skip."
            }
        }
        capturing = false
    }

    Panel(Modifier.fillMaxWidth()) {
        Text(
            "The bike reports its lamps and side stand as bits the app does not know yet. " +
                "This teaches it in about a minute. Bike standing still, ignition on, engine off, app connected.",
            style = MaterialTheme.typography.bodySmall,
            color = Tone.Muted,
        )
    }
    Spacer(Modifier.height(12.dp))
    if (link.phase != LinkPhase.LIVE) {
        Text("Connect to the bike first.", color = Tone.Amber)
        return
    }
    Panel(Modifier.fillMaxWidth()) {
        when {
            step == -1 -> {
                Text("Ready?", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { step = 0 }) { Text("Start") }
            }
            step == 0 -> {
                Text("Step 1: everything off", fontWeight = FontWeight.SemiBold)
                Text("Indicators off, low beam, side stand up. Then tap Capture and wait 8 seconds.")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !capturing, onClick = { capturing = true }) { Text(if (capturing) "Capturing…" else "Capture") }
                    if (baseline.isNotEmpty() && !capturing) OutlinedButton(onClick = { step = 1; lastResult = "" }) { Text("Next") }
                }
            }
            step in 1..switches.size -> {
                val sw = switches[step - 1]
                Text("Step ${step + 1}: ${sw.label}", fontWeight = FontWeight.SemiBold)
                Text(sw.instruction + " Then tap Capture and wait 8 seconds. Switch it off afterwards.")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !capturing, onClick = { capturing = true }) { Text(if (capturing) "Capturing…" else "Capture") }
                    if (!capturing) OutlinedButton(onClick = { step++; lastResult = "" }) { Text(if (found.containsKey(sw)) "Next" else "Skip") }
                }
            }
            else -> {
                Text("Done", fontWeight = FontWeight.SemiBold)
                Text("Learned: " + found.keys.joinToString { it.label }.ifBlank { "nothing" })
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { Prefs.switchMap = SwitchLearn.encode(found); step = -1 }) { Text("Save") }
                    OutlinedButton(onClick = { step = 0; found = emptyMap(); lastResult = "" }) { Text("Start over") }
                }
            }
        }
        if (lastResult.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(lastResult, color = if (lastResult.contains("found") || lastResult.contains("captured")) Tone.Green else Tone.Amber)
        }
    }
    if (Prefs.switchMap.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { Prefs.switchMap = ""; found = emptyMap() }) { Text("Forget learned switches") }
    }
}

// ---------------------------------------------------------------- advanced

@Composable
private fun AdvancedPage() {
    Panel(Modifier.fillMaxWidth()) {
        var keepalive by remember { mutableStateOf(Prefs.keepalive) }
        ToggleRow("Phone status every 2 s", "Time, battery, signal, missed calls and overspeed limit, as the official app sends.", keepalive) {
            keepalive = it; Prefs.keepalive = it
        }
        Divider()
        var reconnect by remember { mutableStateOf(Prefs.autoReconnect) }
        ToggleRow("Reconnect automatically", "Retry after a drop, 2 s up to 30 s apart.", reconnect) {
            reconnect = it; Prefs.autoReconnect = it
        }
    }
    Spacer(Modifier.height(12.dp))
    Panel(Modifier.fillMaxWidth()) {
        var inScheme by remember { mutableStateOf(Prefs.inboundScheme) }
        SchemeChooser("Decode bike data as", inScheme) { inScheme = it; Prefs.inboundScheme = it }
        var outScheme by remember { mutableStateOf(Prefs.outboundScheme) }
        SchemeChooser("Scramble data sent to bike as", outScheme) { outScheme = it; Prefs.outboundScheme = it }
        Text("Leave both on Auto; it is detected from each frame's checksum.", style = MaterialTheme.typography.bodySmall, color = Tone.Muted)
    }
}

@Composable
private fun SchemeChooser(title: String, selected: String, onSelect: (String) -> Unit) {
    Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
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

package dev.bikecompanion.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.bikecompanion.log.EventLog

@Composable
fun LogScreen() {
    val ctx = LocalContext.current
    val lines by EventLog.lines.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                val f = EventLog.currentFile()
                if (f == null || !f.exists()) {
                    Toast.makeText(ctx, "No log file yet", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(Intent.createChooser(send, "Share ride log"))
            }) { Text("Share log file") }
            OutlinedButton(onClick = { EventLog.clearView() }) { Text("Clear view") }
        }
        Text(
            "${lines.size} lines on screen. The shared file keeps the whole session and includes your bike's Bluetooth address.",
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(lines.asReversed()) { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(vertical = 1.dp))
            }
        }
    }
}

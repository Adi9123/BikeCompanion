package dev.rtrcompanion.link

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.rtrcompanion.R
import dev.rtrcompanion.data.AppState
import dev.rtrcompanion.data.LinkPhase
import dev.rtrcompanion.data.Prefs
import dev.rtrcompanion.log.EventLog
import dev.rtrcompanion.ride.RecPhase
import dev.rtrcompanion.ride.RecState
import dev.rtrcompanion.ride.RideRecorder
import dev.rtrcompanion.ride.RideStore
import dev.rtrcompanion.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Foreground service that owns the Bluetooth link and records rides, so both
 * keep running with the screen off or the app in the background.
 */
class LinkService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var link: BleLink
    private lateinit var store: RideStore
    private var notifJob: Job? = null
    private var tickJob: Job? = null

    @Volatile
    private var locationListener: LocationListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        EventLog.init(this)
        ensureChannel()
        link = BleLink(applicationContext, scope)
        store = RideStore(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!goForeground(withLocation = intent != null)) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                RideRecorder.finish(store, "disconnect")
                link.disconnect("user")
                stopLocation()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                val addr = intent.getStringExtra(EXTRA_ADDRESS)
                if (addr != null) link.connect(addr)
            }
            ACTION_SEND_NAME -> link.queueRiderName()
            ACTION_SAVE_RIDE -> scope.launch { RideRecorder.finish(store, "notification") }
            ACTION_DISCARD_RIDE -> RideRecorder.discard()
            null -> {
                // Restarted by the system after being killed: resume the last bike.
                val addr = Prefs.lastAddress
                if (addr == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                EventLog.i(TAG, "Service restarted by Android; reconnecting")
                link.connect(addr)
            }
        }
        if (notifJob == null) {
            notifJob = scope.launch {
                combine(AppState.link, RideRecorder.state) { l, r -> notificationText(l.phase, l.detail, l.deviceName, r) }
                    .distinctUntilChanged()
                    .collect { (title, text, paused) -> updateNotification(title, text, paused) }
            }
        }
        if (tickJob == null) {
            tickJob = scope.launch {
                while (isActive) {
                    val live = AppState.link.value.phase == LinkPhase.LIVE
                    RideRecorder.tick(System.currentTimeMillis(), live, store)
                    val wantGps = live || RideRecorder.state.value.phase != RecPhase.IDLE
                    if (wantGps) startLocation() else stopLocation()
                    delay(1_000)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        RideRecorder.finish(store, "service stopped")
        stopLocation()
        link.disconnect("service stopped")
        scope.cancel()
        super.onDestroy()
    }

    // ----------------------------------------------------------- location

    private fun locationGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun startLocation() {
        if (locationListener != null || !locationGranted()) return
        val lm = getSystemService(LocationManager::class.java) ?: return
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                RideRecorder.onLocation(location.latitude, location.longitude, System.currentTimeMillis())
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
            locationListener = listener
            EventLog.i(TAG, "GPS on for ride recording")
        } catch (e: Exception) {
            EventLog.w(TAG, "GPS unavailable: $e")
        }
    }

    private fun stopLocation() {
        val l = locationListener ?: return
        try {
            getSystemService(LocationManager::class.java)?.removeUpdates(l)
        } catch (ignored: Exception) {
        }
        locationListener = null
    }

    // ------------------------------------------------------- notification

    /**
     * Starts as a foreground service. Location is added to the service type
     * only when started from the app (Android refuses it for a background
     * restart), and only if location permission is granted.
     */
    private fun goForeground(withLocation: Boolean): Boolean {
        val notification = buildNotification("RTR Companion", "Starting", false)
        if (Build.VERSION.SDK_INT >= 29 && withLocation && locationGranted()) {
            try {
                ServiceCompat.startForeground(
                    this, NOTIF_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
                return true
            } catch (e: Exception) {
                EventLog.w(TAG, "Foreground with location refused, continuing without: $e")
            }
        }
        return try {
            val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
            ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
            true
        } catch (e: Exception) {
            EventLog.w(TAG, "Could not start the foreground service: $e")
            false
        }
    }

    private fun notificationText(phase: LinkPhase, detail: String, name: String?, r: RecState): Triple<String, String, Boolean> {
        val title = name ?: "RTR Companion"
        return when (r.phase) {
            RecPhase.RECORDING -> Triple(title, "Recording ride · ${String.format(Locale.US, "%.1f", r.distanceKm)} km", false)
            RecPhase.PAUSED -> Triple(
                "Ride paused: ${r.reason.lowercase(Locale.US)}",
                "${String.format(Locale.US, "%.1f", r.distanceKm)} km. Saves automatically in ${Prefs.autoEndMinutes} min.",
                true,
            )
            RecPhase.IDLE -> Triple(title, if (detail.isBlank()) phase.label else "${phase.label} · $detail", false)
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Bike connection and rides", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun servicePending(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, LinkService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(title: String, text: String, paused: Boolean): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
        if (paused) {
            b.addAction(0, "Save ride", servicePending(ACTION_SAVE_RIDE, 2))
            b.addAction(0, "Discard", servicePending(ACTION_DISCARD_RIDE, 3))
        } else {
            b.addAction(0, "Disconnect", servicePending(ACTION_DISCONNECT, 1))
        }
        return b.build()
    }

    private fun updateNotification(title: String, text: String, paused: Boolean) {
        try {
            getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification(title, text, paused))
        } catch (ignored: Exception) {
        }
    }

    companion object {
        private const val TAG = "Service"
        private const val CHANNEL = "link"
        private const val NOTIF_ID = 1
        const val ACTION_CONNECT = "dev.rtrcompanion.CONNECT"
        const val ACTION_DISCONNECT = "dev.rtrcompanion.DISCONNECT"
        const val ACTION_SEND_NAME = "dev.rtrcompanion.SEND_NAME"
        const val ACTION_SAVE_RIDE = "dev.rtrcompanion.SAVE_RIDE"
        const val ACTION_DISCARD_RIDE = "dev.rtrcompanion.DISCARD_RIDE"
        const val EXTRA_ADDRESS = "address"

        fun connect(ctx: Context, address: String) {
            val i = Intent(ctx, LinkService::class.java).setAction(ACTION_CONNECT).putExtra(EXTRA_ADDRESS, address)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun disconnect(ctx: Context) {
            ctx.startService(Intent(ctx, LinkService::class.java).setAction(ACTION_DISCONNECT))
        }

        fun sendRiderName(ctx: Context) {
            ctx.startService(Intent(ctx, LinkService::class.java).setAction(ACTION_SEND_NAME))
        }
    }
}

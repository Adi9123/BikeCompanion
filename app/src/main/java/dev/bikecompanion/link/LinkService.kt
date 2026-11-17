package dev.bikecompanion.link

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
import dev.bikecompanion.R
import dev.bikecompanion.data.AppState
import dev.bikecompanion.data.LinkPhase
import dev.bikecompanion.data.Prefs
import dev.bikecompanion.fuel.FuelMath
import dev.bikecompanion.fuel.FuelMeter
import dev.bikecompanion.fuel.FuelStore
import dev.bikecompanion.garage.GarageStore
import dev.bikecompanion.monitor.Alert
import dev.bikecompanion.monitor.BikeMonitor
import dev.bikecompanion.monitor.InfoAction
import dev.bikecompanion.monitor.InfoButton
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import dev.bikecompanion.ui.BikeWidget
import dev.bikecompanion.log.EventLog
import dev.bikecompanion.ride.RecPhase
import dev.bikecompanion.ride.RecState
import dev.bikecompanion.ride.RideRecorder
import dev.bikecompanion.ride.RideStore
import dev.bikecompanion.ui.MainActivity
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
        garage = GarageStore(applicationContext)
        fuelStore = FuelStore(applicationContext)
        BikeMonitor.notifier = { postAlert(it) }
        InfoButton.isRinging = { AppState.ringingCall.value != null }
        InfoButton.onReserve = { BikeMonitor.fuelStatus?.onReserve == true }
        InfoButton.perform = { action, _ -> performInfo(action) }
    }

    /** What the handlebar Info switch was set to do. */
    private fun performInfo(a: InfoAction) {
        fun say(text: String) {
            AppState.clusterTexts.tryEmit(text)
        }
        when (a) {
            InfoAction.NONE -> Unit
            InfoAction.SHOW_RANGE -> {
                refreshFuelStatus()
                say(BikeMonitor.fuelStatus?.rangeKm?.let { "RANGE ${it.toInt()} KM" } ?: "RANGE UNKNOWN")
            }
            InfoAction.SHOW_FUEL -> {
                refreshFuelStatus()
                say(BikeMonitor.fuelStatus?.litresLeft?.let { "FUEL ABOUT ${Math.round(it)} L" } ?: "FUEL UNKNOWN")
            }
            InfoAction.SHOW_RIDE -> {
                val r = RideRecorder.state.value
                val start = r.startedAt
                if (r.phase == RecPhase.IDLE || start == null) {
                    say("NO RIDE YET")
                } else {
                    say("RIDE ${r.distanceKm.toInt()} KM ${(System.currentTimeMillis() - start) / 60_000} MIN")
                }
            }
            InfoAction.SHOW_TIME -> say("TIME " + java.text.SimpleDateFormat("h mm a", Locale.US).format(java.util.Date()))
            InfoAction.SAVE_SPOT -> {
                val fix = RideRecorder.lastFix()
                if (fix != null) {
                    garage.addSpot(System.currentTimeMillis(), fix.first, fix.second)
                    say("SPOT SAVED")
                } else {
                    say("NO GPS FIX")
                }
            }
            InfoAction.NEXT_TRACK -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            InfoAction.PREVIOUS_TRACK -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            InfoAction.PLAY_PAUSE -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            InfoAction.PETROL_PUMP -> {
                postPumpNotification()
                say("PUMP ON PHONE")
            }
            InfoAction.TOGGLE_DIRECTIONS -> {
                Prefs.navEnabled = !Prefs.navEnabled
                say(if (Prefs.navEnabled) "DIRECTIONS ON" else "DIRECTIONS OFF")
            }
            InfoAction.DECLINE_CALL -> AppState.declineCall.tryEmit(Unit)
        }
    }

    private fun mediaKey(code: Int) {
        val am = getSystemService(AudioManager::class.java) ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    /** Android does not let a background app open Maps by itself, so offer it as a notification. */
    private fun postPumpNotification() {
        try {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(ALERT_CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Bike alerts", NotificationManager.IMPORTANCE_HIGH))
            }
            val maps = PendingIntent.getActivity(
                this, 7, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=petrol+pump")),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(this, ALERT_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Nearest petrol pump")
                .setContentText("Tap to see petrol pumps near you in Maps.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(maps)
                .build()
            nm.notify(ALERT_BASE + 250, n)
        } catch (ignored: Exception) {
        }
    }

    private lateinit var garage: GarageStore
    private lateinit var fuelStore: FuelStore
    private var ticks = 0L

    private fun refreshFuelStatus() {
        val r = AppState.readings.value
        BikeMonitor.fuelStatus = FuelMath.status(
            refuels = fuelStore.list(),
            tankLitres = Prefs.tankLitres,
            reserveLitres = Prefs.reserveLitres,
            totalBars = Prefs.fuelBars,
            bars = r["fuel"]?.raw?.toInt()?.takeIf { it in 0..Prefs.fuelBars },
            injNow = Prefs.injTotal,
            clusterMileage = r["avgMileageA"]?.raw?.toInt(),
        )
    }

    private fun postAlert(a: Alert) {
        try {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(ALERT_CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Bike alerts", NotificationManager.IMPORTANCE_HIGH))
            }
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(this, ALERT_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(a.title)
                .setContentText(a.detail)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            nm.notify(ALERT_BASE + (a.id.hashCode() and 0xFF), n)
        } catch (ignored: Exception) {
        }
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
                    val now = System.currentTimeMillis()
                    FuelMeter.tick(now, live)
                    RideRecorder.tick(now, live, store)
                    if (ticks % 30 == 0L) refreshFuelStatus()
                    BikeMonitor.tick(now, live) { garage.addBattery(it) }
                    if (ticks % 60 == 5L) BikeWidget.updateAll(applicationContext)
                    ticks++
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
        val notification = buildNotification("Bike Companion", "Starting", false)
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
        val title = name ?: "Bike Companion"
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
        private const val ALERT_CHANNEL = "alerts"
        private const val ALERT_BASE = 100
        const val ACTION_CONNECT = "dev.bikecompanion.CONNECT"
        const val ACTION_DISCONNECT = "dev.bikecompanion.DISCONNECT"
        const val ACTION_SEND_NAME = "dev.bikecompanion.SEND_NAME"
        const val ACTION_SAVE_RIDE = "dev.bikecompanion.SAVE_RIDE"
        const val ACTION_DISCARD_RIDE = "dev.bikecompanion.DISCARD_RIDE"
        const val EXTRA_ADDRESS = "address"

        fun connect(ctx: Context, address: String) {
            val i = Intent(ctx, LinkService::class.java).setAction(ACTION_CONNECT).putExtra(EXTRA_ADDRESS, address)
            ContextCompat.startForegroundService(ctx, i)
        }

        /**
         * Connects to the remembered bike if the rider wants that and nothing is
         * connected or waiting already. Safe to call often.
         */
        fun autoConnect(ctx: Context, why: String) {
            Prefs.init(ctx)
            val addr = Prefs.lastAddress ?: return
            if (!Prefs.autoConnectOnOpen) return
            if (!dev.bikecompanion.ui.Perms.bluetoothGranted(ctx)) return
            val phase = AppState.link.value.phase
            if (phase != LinkPhase.IDLE && phase != LinkPhase.FAILED) return
            EventLog.i(TAG, "Auto-connect ($why)")
            try {
                connect(ctx, addr)
            } catch (e: Exception) {
                EventLog.w(TAG, "Auto-connect refused: $e")
            }
        }

        fun disconnect(ctx: Context) {
            ctx.startService(Intent(ctx, LinkService::class.java).setAction(ACTION_DISCONNECT))
        }

        fun sendRiderName(ctx: Context) {
            ctx.startService(Intent(ctx, LinkService::class.java).setAction(ACTION_SEND_NAME))
        }
    }
}

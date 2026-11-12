package dev.rtrcompanion.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.rtrcompanion.R
import dev.rtrcompanion.data.AppState
import dev.rtrcompanion.data.Prefs
import dev.rtrcompanion.fuel.FuelMath
import dev.rtrcompanion.fuel.FuelStore
import dev.rtrcompanion.ride.RideStore
import java.util.Locale

/** Home-screen widget: fuel left, range, connection and the last ride. */
class BikeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Prefs.init(context)
        render(context, manager, ids)
    }

    companion object {
        fun updateAll(ctx: Context) {
            try {
                val manager = AppWidgetManager.getInstance(ctx)
                val ids = manager.getAppWidgetIds(ComponentName(ctx, BikeWidget::class.java))
                if (ids.isNotEmpty()) render(ctx, manager, ids)
            } catch (ignored: Exception) {
            }
        }

        private fun render(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val r = AppState.readings.value
            val bars = r["fuel"]?.raw?.toInt()?.takeIf { it in 0..Prefs.fuelBars }
                ?: Prefs.lastStableBars.takeIf { it >= 0 }
            val status = FuelMath.status(
                refuels = FuelStore(ctx).list(),
                tankLitres = Prefs.tankLitres,
                reserveLitres = Prefs.reserveLitres,
                totalBars = Prefs.fuelBars,
                bars = bars,
                injNow = Prefs.injTotal,
                clusterMileage = r["avgMileageA"]?.raw?.toInt(),
            )
            val ride = RideStore(ctx).list().firstOrNull()
            val fuel = status.litresLeft?.let { (if (status.measured) "" else "≈ ") + String.format(Locale.US, "%.1f l", it) } ?: "—"
            val range = status.rangeKm?.let { "≈ ${it.toInt()} km" } ?: "—"
            val last = ride?.let { String.format(Locale.US, "Last ride %.1f km · top %d km/h", it.distanceKm, it.maxSpeed) } ?: "No rides yet"
            val odo = Prefs.lastOdoKm.takeIf { it > 0 }?.let { String.format(Locale.US, "%.0f km", it) } ?: ""
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            for (id in ids) {
                val v = RemoteViews(ctx.packageName, R.layout.widget_bike)
                v.setTextViewText(R.id.w_status, AppState.link.value.phase.label)
                v.setTextViewText(R.id.w_fuel, fuel)
                v.setTextViewText(R.id.w_range, range)
                v.setTextViewText(R.id.w_last, last)
                v.setTextViewText(R.id.w_odo, odo)
                v.setOnClickPendingIntent(R.id.w_root, open)
                manager.updateAppWidget(id, v)
            }
        }
    }
}

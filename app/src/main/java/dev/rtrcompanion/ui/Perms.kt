package dev.rtrcompanion.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Perms {
    /** Everything the app asks for at first launch. */
    fun all(): Array<String> {
        val list = mutableListOf<String>()
        list += bluetooth()
        list += location()
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        return list.distinct().toTypedArray()
    }

    /** The permissions without which the app cannot scan or connect. */
    fun bluetooth(): List<String> =
        if (Build.VERSION.SDK_INT >= 31) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** GPS, for the ride route. */
    fun location(): List<String> =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun bluetoothGranted(ctx: Context): Boolean = bluetooth().all { granted(ctx, it) }

    fun locationGranted(ctx: Context): Boolean = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)

    fun allGranted(ctx: Context): Boolean = all().all { granted(ctx, it) }
}

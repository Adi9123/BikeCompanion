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
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        return list.toTypedArray()
    }

    /** The permissions without which the app cannot scan or connect. */
    fun bluetooth(): List<String> =
        if (Build.VERSION.SDK_INT >= 31) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun bluetoothGranted(ctx: Context): Boolean = bluetooth().all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }
}

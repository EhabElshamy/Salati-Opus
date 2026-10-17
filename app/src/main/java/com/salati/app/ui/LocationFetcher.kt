package com.salati.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper

/** Framework LocationManager only (no Google Play Services). Location is used solely for prayer times. */
object LocationFetcher {
    @SuppressLint("MissingPermission")
    fun fetch(context: Context, onResult: (Location?) -> Unit) {
        val lm = context.getSystemService(LocationManager::class.java)
        if (lm == null) {
            onResult(null)
            return
        }
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            onResult(null)
            return
        }
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(providers.first(), null, context.mainExecutor) { loc -> onResult(loc ?: last) }
            } else if (last != null) {
                onResult(last)
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(providers.first(), object : LocationListener {
                    override fun onLocationChanged(location: Location) = onResult(location)
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) = onResult(null)
                }, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            onResult(null)
        }
    }
}

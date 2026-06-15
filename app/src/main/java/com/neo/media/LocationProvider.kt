package com.neo.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Captures the device's current location and resolves it to a short human-readable
 * place name, using only platform APIs (LocationManager + Geocoder) — no Google
 * Play Services dependency. The ACCESS_FINE_LOCATION permission is already part of
 * the app (used for BLE scanning); callers must ensure it's granted before use.
 */
object LocationProvider {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    sealed class Result {
        data class Success(val name: String) : Result()
        object PermissionDenied : Result()
        object Unavailable : Result()
    }

    /**
     * Returns the current location as a place label (e.g. "Lansing, Illinois"), or a
     * failure reason. Uses last-known fix when available, else requests a single
     * update. Geocoding failures fall back to coordinates so the user still gets a
     * usable location string.
     */
    suspend fun currentPlace(context: Context): Result {
        if (!hasPermission(context)) return Result.PermissionDenied

        val location = try {
            getLocation(context)
        } catch (_: SecurityException) {
            return Result.PermissionDenied
        } ?: return Result.Unavailable

        return Result.Success(reverseGeocode(context, location))
    }

    @Suppress("MissingPermission")
    private suspend fun getLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return null

        // Prefer a recent last-known fix (instant, no wait).
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { return it }

        // Otherwise request a single fresh update with a timeout.
        val provider = providers.first()
        return suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            } else {
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        runCatching { lm.removeUpdates(this) }
                        if (cont.isActive) cont.resume(loc)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {}
                }
                runCatching {
                    lm.requestSingleUpdate(provider, listener, context.mainLooper)
                }.onFailure { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
            }
        }
    }

    private fun reverseGeocode(context: Context, location: Location): String {
        val fallback = String.format(Locale.US, "%.3f, %.3f", location.latitude, location.longitude)
        return try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
            val a = addresses?.firstOrNull() ?: return fallback
            // Build a concise "City, Region" (or the best available subset).
            val city = a.locality ?: a.subAdminArea ?: a.adminArea
            val region = a.adminArea?.takeIf { it != city }
            listOfNotNull(city, region).joinToString(", ").ifBlank { fallback }
        } catch (_: Exception) {
            fallback
        }
    }
}

package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.example.data.FirestoreProvider.awaitResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class MechanicCoords(
  val lat: Double,
  val lng: Double,
  val address: String? = null
)

object MechanicLocationHelper {

  @SuppressLint("MissingPermission")
  suspend fun fetchCurrentLocation(context: Context): MechanicCoords? {
    val fineGranted = ContextCompat.checkSelfPermission(
      context,
      android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
      context,
      android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!fineGranted && !coarseGranted) return null

    val fusedLoc: Location? = runCatching {
      val client = LocationServices.getFusedLocationProviderClient(context)
      client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).awaitResult()
        ?: client.lastLocation.awaitResult()
    }.getOrNull()

    val loc = fusedLoc ?: runCatching {
      val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
      lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    }.getOrNull() ?: return null

    val address = reverseGeocode(context, loc.latitude, loc.longitude)
    return MechanicCoords(lat = loc.latitude, lng = loc.longitude, address = address)
  }

  private suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String? {
    return withContext(Dispatchers.IO) {
      runCatching {
        @Suppress("DEPRECATION")
        val list = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
        list?.firstOrNull()?.let { addr ->
          listOfNotNull(addr.subLocality, addr.locality, addr.adminArea)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { null }
        }
      }.getOrNull()
    }
  }
}

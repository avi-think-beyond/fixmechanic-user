package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

data class MetroPreset(
  val name: String,
  val locality: String,
  val pincode: String,
  val lat: Double,
  val lng: Double
)

val INDIAN_METRO_PRESETS = listOf(
  MetroPreset("Bengaluru (Indiranagar)", "Indiranagar 100ft Rd, Bengaluru", "560038", 12.9784, 77.6408),
  MetroPreset("Bengaluru (Koramangala)", "Koramangala 80ft Rd, Bengaluru", "560034", 12.9352, 77.6245),
  MetroPreset("Bengaluru (Whitefield)", "ITPB Road, Whitefield, Bengaluru", "560066", 12.9698, 77.7499),
  MetroPreset("Mumbai (Andheri West)", "Link Road, Andheri West, Mumbai", "400053", 19.1363, 72.8277),
  MetroPreset("Delhi NCR (Connaught Place)", "Barakhamba Rd, New Delhi", "110001", 28.6315, 77.2167),
  MetroPreset("Hyderabad (Hitec City)", "Madhapur, Hitec City, Hyderabad", "500081", 17.4435, 78.3772)
)

data class ResolvedAddressResult(
  val formattedAddress: String,
  val locality: String,
  val pincode: String
)

private val nominatimHttpClient by lazy {
  OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(6, TimeUnit.SECONDS)
    .build()
}

private fun ensureOsmConfigured(context: Context) {
  val config = Configuration.getInstance()
  config.userAgentValue = "FixMechanicMechanic/1.0 (${context.packageName})"
  val baseDir = File(context.cacheDir, "osmdroid").apply { mkdirs() }
  val tileDir = File(baseDir, "tiles").apply { mkdirs() }
  config.osmdroidBasePath = baseDir
  config.osmdroidTileCache = tileDir
}

internal fun hasDeviceLocationPermission(context: Context): Boolean {
  val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
  val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
  return fine || coarse
}

@SuppressLint("MissingPermission")
internal fun fetchDeviceGpsLocation(
  context: Context,
  onSuccess: (Double, Double) -> Unit,
  onFailure: (String) -> Unit
) {
  if (!hasDeviceLocationPermission(context)) {
    onFailure("Location permission is required to detect your current mobile location.")
    return
  }

  val completed = java.util.concurrent.atomic.AtomicBoolean(false)
  val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
  val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
  val cts = CancellationTokenSource()
  var activeLmListener: android.location.LocationListener? = null

  fun deliverLocation(lat: Double, lng: Double) {
    if (lat == 0.0 && lng == 0.0) return
    if (completed.compareAndSet(false, true)) {
      mainHandler.post {
        try {
          activeLmListener?.let { lm?.removeUpdates(it) }
          cts.cancel()
        } catch (_: Exception) {}
        onSuccess(
          (lat * 100000.0).roundToInt() / 100000.0,
          (lng * 100000.0).roundToInt() / 100000.0
        )
      }
    }
  }

  fun readBestLastKnown(): android.location.Location? {
    if (lm == null) return null
    val providers = listOf(
      LocationManager.GPS_PROVIDER,
      LocationManager.NETWORK_PROVIDER,
      LocationManager.PASSIVE_PROVIDER
    )
    return providers.mapNotNull { provider ->
      try {
        lm.getLastKnownLocation(provider)
      } catch (_: Exception) {
        null
      }
    }.filter { it.latitude != 0.0 || it.longitude != 0.0 }
      .maxByOrNull { it.time }
  }

  // If we have a very fresh fix (< 45s old), return it immediately while also keeping UI responsive
  val recentLastKnown = readBestLastKnown()
  if (recentLastKnown != null && (System.currentTimeMillis() - recentLastKnown.time) < 45_000L) {
    deliverLocation(recentLastKnown.latitude, recentLastKnown.longitude)
    return
  }

  // Fallback timeout after 4 seconds so UI never hangs in "Locating..."
  mainHandler.postDelayed({
    if (!completed.get()) {
      val fallback = readBestLastKnown()
      if (fallback != null) {
        deliverLocation(fallback.latitude, fallback.longitude)
      } else if (completed.compareAndSet(false, true)) {
        try {
          activeLmListener?.let { lm?.removeUpdates(it) }
          cts.cancel()
        } catch (_: Exception) {}
        onFailure("GPS signal unavailable right now. Turn on device Location or pan the map manually.")
      }
    }
  }, 4000L)

  // 1. Try Google Play Services FusedLocationProviderClient
  try {
    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
      .addOnSuccessListener { currentLoc ->
        if (currentLoc != null && (currentLoc.latitude != 0.0 || currentLoc.longitude != 0.0)) {
          deliverLocation(currentLoc.latitude, currentLoc.longitude)
        } else {
          fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
            if (lastLoc != null && (lastLoc.latitude != 0.0 || lastLoc.longitude != 0.0)) {
              deliverLocation(lastLoc.latitude, lastLoc.longitude)
            }
          }
        }
      }
      .addOnFailureListener {
        val fallback = readBestLastKnown()
        if (fallback != null) {
          deliverLocation(fallback.latitude, fallback.longitude)
        }
      }
  } catch (_: Exception) {
    val fallback = readBestLastKnown()
    if (fallback != null) {
      deliverLocation(fallback.latitude, fallback.longitude)
      return
    }
  }

  // 2. Also listen briefly via native LocationManager (GPS + Network) for emulators & AOSP devices
  if (lm != null && !completed.get()) {
    val listener = android.location.LocationListener { loc ->
      deliverLocation(loc.latitude, loc.longitude)
    }
    activeLmListener = listener
    listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
      try {
        if (lm.isProviderEnabled(provider)) {
          lm.requestLocationUpdates(provider, 500L, 0f, listener, android.os.Looper.getMainLooper())
        }
      } catch (_: Exception) {}
    }
  }
}

internal suspend fun resolveCurrentDeviceLocationWithAddress(
  context: Context
): Triple<Double, Double, ResolvedAddressResult?>? {
  if (!hasDeviceLocationPermission(context)) return null
  val coords = suspendCancellableCoroutine<Pair<Double, Double>?> { cont ->
    fetchDeviceGpsLocation(
      context = context,
      onSuccess = { lat, lng -> if (cont.isActive) cont.resume(lat to lng) },
      onFailure = { if (cont.isActive) cont.resume(null) }
    )
  } ?: return null
  val resolved = reverseGeocodeCoordinates(context, coords.first, coords.second)
  return Triple(coords.first, coords.second, resolved)
}

private suspend fun reverseGeocodeCoordinates(
  context: Context,
  lat: Double,
  lng: Double
): ResolvedAddressResult? = withContext(Dispatchers.IO) {
  // 1. Try Android native Geocoder first
  try {
    if (Geocoder.isPresent()) {
      val geocoder = Geocoder(context, Locale.Builder().setLanguage("en").setRegion("IN").build())
      val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { cont ->
          geocoder.getFromLocation(lat, lng, 1) { list ->
            if (cont.isActive) cont.resume(list)
          }
        }
      } else {
        @Suppress("DEPRECATION")
        geocoder.getFromLocation(lat, lng, 1) ?: emptyList()
      }

      val addr = addresses.firstOrNull()
      if (addr != null) {
        val line0 = (0..addr.maxAddressLineIndex).mapNotNull { addr.getAddressLine(it) }.joinToString(", ")
        val parts = listOfNotNull(
          addr.featureName?.takeIf { it.isNotBlank() && it != addr.postalCode },
          addr.thoroughfare?.takeIf { it.isNotBlank() },
          addr.subLocality?.takeIf { it.isNotBlank() },
          addr.locality?.takeIf { it.isNotBlank() }
        ).distinct()
        val localityText = parts.take(3).joinToString(", ").ifBlank { line0 }
        val pin = addr.postalCode?.filter { it.isDigit() }?.take(6).orEmpty()
        if (localityText.isNotBlank() || line0.isNotBlank()) {
          return@withContext ResolvedAddressResult(
            formattedAddress = line0.ifBlank { localityText },
            locality = localityText.ifBlank { line0 },
            pincode = pin
          )
        }
      }
    }
  } catch (_: Exception) {
    // Fallback to OpenStreetMap Nominatim
  }

  // 2. Fallback to OpenStreetMap Nominatim Reverse Geocoding
  try {
    val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lng&addressdetails=1"
    val request = Request.Builder()
      .url(url)
      .header("User-Agent", "FixMechanicMechanic/1.0 (${context.packageName})")
      .header("Accept-Language", "en-IN,en;q=0.9")
      .build()
    nominatimHttpClient.newCall(request).execute().use { resp ->
      if (resp.isSuccessful) {
        val body = resp.body?.string().orEmpty()
        if (body.isNotBlank()) {
          val json = JSONObject(body)
          val display = json.optString("display_name", "")
          val addrObj = json.optJSONObject("address")
          val road = addrObj?.optString("road", "")?.takeIf { it.isNotBlank() }
          val house = addrObj?.optString("house_number", "")?.takeIf { it.isNotBlank() }
          val building = addrObj?.optString("building", "")?.takeIf { it.isNotBlank() }
          val suburb = (addrObj?.optString("suburb", "")?.takeIf { it.isNotBlank() }
            ?: addrObj?.optString("neighbourhood", "")?.takeIf { it.isNotBlank() })
          val city = (addrObj?.optString("city", "")?.takeIf { it.isNotBlank() }
            ?: addrObj?.optString("town", "")?.takeIf { it.isNotBlank() }
            ?: addrObj?.optString("state_district", "")?.takeIf { it.isNotBlank() })
          val postcode = addrObj?.optString("postcode", "")?.filter { it.isDigit() }?.take(6).orEmpty()

          val streetPart = listOfNotNull(house, building, road).joinToString(" ").takeIf { it.isNotBlank() }
          val shortLocality = listOfNotNull(streetPart, suburb, city).distinct().joinToString(", ")
          return@withContext ResolvedAddressResult(
            formattedAddress = display.ifBlank { shortLocality },
            locality = shortLocality.ifBlank { display },
            pincode = postcode
          )
        }
      }
    }
  } catch (_: Exception) {
    // Offline or network failure
  }

  null
}

private suspend fun searchLocationCoordinates(
  context: Context,
  query: String
): Pair<GeoPoint, ResolvedAddressResult>? = withContext(Dispatchers.IO) {
  val trimmed = query.trim()
  if (trimmed.isBlank()) return@withContext null

  // 1. Try Android native Geocoder first
  try {
    if (Geocoder.isPresent()) {
      val geocoder = Geocoder(context, Locale.Builder().setLanguage("en").setRegion("IN").build())
      val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { cont ->
          geocoder.getFromLocationName(trimmed, 1) { list ->
            if (cont.isActive) cont.resume(list)
          }
        }
      } else {
        @Suppress("DEPRECATION")
        geocoder.getFromLocationName(trimmed, 1) ?: emptyList()
      }
      val addr = addresses.firstOrNull()
      if (addr != null) {
        val parts = listOfNotNull(
          addr.thoroughfare?.takeIf { it.isNotBlank() },
          addr.subLocality?.takeIf { it.isNotBlank() },
          addr.locality?.takeIf { it.isNotBlank() }
        ).distinct().joinToString(", ")
        val full = (0..addr.maxAddressLineIndex).mapNotNull { addr.getAddressLine(it) }.joinToString(", ")
        val pin = addr.postalCode?.filter { it.isDigit() }?.take(6).orEmpty()
        return@withContext GeoPoint(addr.latitude, addr.longitude) to ResolvedAddressResult(
          formattedAddress = full.ifBlank { trimmed },
          locality = parts.ifBlank { full.ifBlank { trimmed } },
          pincode = pin
        )
      }
    }
  } catch (_: Exception) {
    // Fallback to Nominatim
  }

  // 2. Fallback to OpenStreetMap Nominatim Search API
  try {
    val encoded = URLEncoder.encode(trimmed, "UTF-8")
    val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&q=$encoded&addressdetails=1&limit=1"
    val request = Request.Builder()
      .url(url)
      .header("User-Agent", "FixMechanicMechanic/1.0 (${context.packageName})")
      .header("Accept-Language", "en-IN,en;q=0.9")
      .build()
    nominatimHttpClient.newCall(request).execute().use { resp ->
      if (resp.isSuccessful) {
        val body = resp.body?.string().orEmpty()
        if (body.isNotBlank()) {
          val arr = JSONArray(body)
          if (arr.length() > 0) {
            val first = arr.getJSONObject(0)
            val lat = first.optString("lat").toDoubleOrNull()
            val lon = first.optString("lon").toDoubleOrNull()
            if (lat != null && lon != null) {
              val display = first.optString("display_name", trimmed)
              val addrObj = first.optJSONObject("address")
              val road = addrObj?.optString("road", "")?.takeIf { it.isNotBlank() }
              val suburb = (addrObj?.optString("suburb", "")?.takeIf { it.isNotBlank() }
                ?: addrObj?.optString("neighbourhood", "")?.takeIf { it.isNotBlank() })
              val city = (addrObj?.optString("city", "")?.takeIf { it.isNotBlank() }
                ?: addrObj?.optString("town", "")?.takeIf { it.isNotBlank() }
                ?: addrObj?.optString("state_district", "")?.takeIf { it.isNotBlank() })
              val postcode = addrObj?.optString("postcode", "")?.filter { it.isDigit() }?.take(6).orEmpty()
              val shortLocality = listOfNotNull(road, suburb, city).distinct().joinToString(", ")
              return@withContext GeoPoint(lat, lon) to ResolvedAddressResult(
                formattedAddress = display,
                locality = shortLocality.ifBlank { display },
                pincode = postcode
              )
            }
          }
        }
      }
    }
  } catch (_: Exception) {
    // Network error
  }

  null
}

@Composable
private fun OsmInteractiveMapView(
  latitude: Double,
  longitude: Double,
  radiusKm: Int,
  showMyLocationDot: Boolean,
  allowScrollInterceptDisable: Boolean,
  onCenterChanged: (lat: Double, lng: Double) -> Unit,
  onMapViewReady: (MapView) -> Unit,
  modifier: Modifier = Modifier,
  onMapTouchChanged: (Boolean) -> Unit = {}
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  remember(context) {
    ensureOsmConfigured(context)
    true
  }

  val mapViewHolder = remember { mutableStateOf<MapView?>(null) }
  val radiusOverlayHolder = remember { mutableStateOf<Polygon?>(null) }
  val myLocationOverlayHolder = remember { mutableStateOf<MyLocationNewOverlay?>(null) }
  val lastUserEmittedLat = remember { mutableDoubleStateOf(latitude) }
  val lastUserEmittedLng = remember { mutableDoubleStateOf(longitude) }
  val lastAnimatedLat = remember { mutableDoubleStateOf(latitude) }
  val lastAnimatedLng = remember { mutableDoubleStateOf(longitude) }
  val suppressScrollUntilMs = remember { mutableLongStateOf(System.currentTimeMillis() + 700L) }

  // Keep latest callbacks & props in State so MapListener always reads fresh values
  val currentOnCenterChanged by rememberUpdatedState(onCenterChanged)
  val currentOnMapTouchChanged by rememberUpdatedState(onMapTouchChanged)
  val latestLatitude by rememberUpdatedState(latitude)
  val latestLongitude by rememberUpdatedState(longitude)

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> mapViewHolder.value?.onResume()
        Lifecycle.Event.ON_PAUSE -> mapViewHolder.value?.onPause()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      currentOnMapTouchChanged(false)
      lifecycleOwner.lifecycle.removeObserver(observer)
      myLocationOverlayHolder.value?.disableMyLocation()
      mapViewHolder.value?.onDetach()
    }
  }

  // Update radius circle whenever coordinates or radiusKm change
  LaunchedEffect(latitude, longitude, radiusKm, mapViewHolder.value) {
    val map = mapViewHolder.value ?: return@LaunchedEffect
    val polygon = radiusOverlayHolder.value ?: return@LaunchedEffect
    polygon.points = Polygon.pointsAsCircle(GeoPoint(latitude, longitude), radiusKm * 1000.0)
    map.invalidate()
  }

  // Enable/disable live blue GPS dot overlay when permission is granted
  LaunchedEffect(showMyLocationDot, mapViewHolder.value) {
    val map = mapViewHolder.value ?: return@LaunchedEffect
    if (showMyLocationDot && hasDeviceLocationPermission(context)) {
      if (myLocationOverlayHolder.value == null) {
        val provider = GpsMyLocationProvider(context).apply {
          addLocationSource(LocationManager.NETWORK_PROVIDER)
          addLocationSource(LocationManager.PASSIVE_PROVIDER)
        }
        val overlay = MyLocationNewOverlay(provider, map).apply {
          enableMyLocation()
          isDrawAccuracyEnabled = true
        }
        myLocationOverlayHolder.value = overlay
        map.overlays.add(overlay)
        map.invalidate()
      } else {
        myLocationOverlayHolder.value?.enableMyLocation()
      }
    }
  }

  AndroidView(
    modifier = modifier,
    factory = { ctx ->
      ensureOsmConfigured(ctx)
      suppressScrollUntilMs.longValue = System.currentTimeMillis() + 700L
      MapView(ctx).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        isHorizontalMapRepetitionEnabled = false
        isVerticalMapRepetitionEnabled = false
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        minZoomLevel = 4.0
        maxZoomLevel = 20.0

        controller.setZoom(16.5)
        controller.setCenter(GeoPoint(latitude, longitude))

        // Prevent parent Compose verticalScroll from stealing map pan/pinch gestures
        setOnTouchListener { v, event ->
          when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN -> {
              suppressScrollUntilMs.longValue = 0L
              currentOnMapTouchChanged(true)
              if (allowScrollInterceptDisable) {
                v.parent?.requestDisallowInterceptTouchEvent(true)
              }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
              currentOnMapTouchChanged(false)
              if (allowScrollInterceptDisable) {
                v.parent?.requestDisallowInterceptTouchEvent(false)
              }
            }
          }
          false
        }

        // Dispatch coverage radius ring overlay
        val circlePolygon = Polygon(this).apply {
          fillPaint.color = android.graphics.Color.argb(32, 220, 38, 38)
          outlinePaint.color = android.graphics.Color.argb(170, 220, 38, 38)
          outlinePaint.strokeWidth = 4f
          points = Polygon.pointsAsCircle(GeoPoint(latitude, longitude), radiusKm * 1000.0)
        }
        radiusOverlayHolder.value = circlePolygon
        overlays.add(circlePolygon)

        // Tap anywhere on a road or building to center pin right there
        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
          override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
            if (p != null) {
              val latRounded = (p.latitude * 100000.0).roundToInt() / 100000.0
              val lngRounded = (p.longitude * 100000.0).roundToInt() / 100000.0
              suppressScrollUntilMs.longValue = System.currentTimeMillis() + 500L
              lastUserEmittedLat.doubleValue = latRounded
              lastUserEmittedLng.doubleValue = lngRounded
              lastAnimatedLat.doubleValue = latRounded
              lastAnimatedLng.doubleValue = lngRounded
              controller.animateTo(p)
              currentOnCenterChanged(latRounded, lngRounded)
            }
            return true
          }

          override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
        overlays.add(0, eventsOverlay)

        if (showMyLocationDot && hasDeviceLocationPermission(ctx)) {
          val provider = GpsMyLocationProvider(ctx).apply {
            addLocationSource(LocationManager.NETWORK_PROVIDER)
            addLocationSource(LocationManager.PASSIVE_PROVIDER)
          }
          val locOverlay = MyLocationNewOverlay(provider, this).apply {
            enableMyLocation()
            isDrawAccuracyEnabled = true
          }
          myLocationOverlayHolder.value = locOverlay
          overlays.add(locOverlay)
        }

        // Track pan/drag cleanly without fighting AnimateTo or external GPS jumps
        addMapListener(object : MapListener {
          override fun onScroll(event: ScrollEvent?): Boolean {
            if (System.currentTimeMillis() < suppressScrollUntilMs.longValue) {
              return false
            }
            val center = mapCenter
            val latRounded = (center.latitude * 100000.0).roundToInt() / 100000.0
            val lngRounded = (center.longitude * 100000.0).roundToInt() / 100000.0
            if (abs(latRounded - latestLatitude) < 0.00004 && abs(lngRounded - latestLongitude) < 0.00004) {
              return false
            }
            lastUserEmittedLat.doubleValue = latRounded
            lastUserEmittedLng.doubleValue = lngRounded
            lastAnimatedLat.doubleValue = latRounded
            lastAnimatedLng.doubleValue = lngRounded
            currentOnCenterChanged(latRounded, lngRounded)
            return false
          }

          override fun onZoom(event: ZoomEvent?): Boolean = false
        })

        mapViewHolder.value = this
        onMapViewReady(this)
      }
    },
    update = { map ->
      val fromUser = abs(latitude - lastUserEmittedLat.doubleValue) <= 0.0002 &&
        abs(longitude - lastUserEmittedLng.doubleValue) <= 0.0002
      val alreadyAtTarget = abs(latitude - lastAnimatedLat.doubleValue) <= 0.0002 &&
        abs(longitude - lastAnimatedLng.doubleValue) <= 0.0002
      if (!fromUser && !alreadyAtTarget) {
        val prevLat = lastAnimatedLat.doubleValue
        val prevLng = lastAnimatedLng.doubleValue
        lastAnimatedLat.doubleValue = latitude
        lastAnimatedLng.doubleValue = longitude
        lastUserEmittedLat.doubleValue = latitude
        lastUserEmittedLng.doubleValue = longitude
        suppressScrollUntilMs.longValue = System.currentTimeMillis() + 600L
        val target = GeoPoint(latitude, longitude)
        // Jump immediately when crossing cities/large distances so tiles load fast
        if (abs(latitude - prevLat) > 0.05 || abs(longitude - prevLng) > 0.05) {
          map.controller.setCenter(target)
        } else {
          map.controller.animateTo(target)
        }
      }
    }
  )
}

@Composable
fun InteractiveMapPicker(
  latitude: Double,
  longitude: Double,
  radiusKm: Int,
  localityLabel: String,
  onLocationSelected: (lat: Double, lng: Double) -> Unit,
  onAddressDetected: ((locality: String, pincode: String) -> Unit)? = null,
  modifier: Modifier = Modifier,
  autoFetchCurrentLocationOnStart: Boolean = false,
  onMapTouchChanged: (Boolean) -> Unit = {}
) {
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val coroutineScope = rememberCoroutineScope()

  var currentLat by remember { mutableDoubleStateOf(latitude) }
  var currentLng by remember { mutableDoubleStateOf(longitude) }
  var searchQuery by remember { mutableStateOf("") }
  var isSearching by remember { mutableStateOf(false) }
  var isFetchingGps by remember { mutableStateOf(false) }
  var gpsStatusMessage by remember { mutableStateOf<String?>(null) }
  var hasPermission by remember { mutableStateOf(hasDeviceLocationPermission(context)) }
  var pendingGpsRequest by remember { mutableStateOf(false) }
  var detectedAddress by remember { mutableStateOf<ResolvedAddressResult?>(null) }
  var isResolvingAddress by remember { mutableStateOf(false) }
  var shouldAutoApplyAddress by remember { mutableStateOf(false) }
  var hasAutoCenteredOnce by remember { mutableStateOf(false) }
  var isFullScreenOpen by remember { mutableStateOf(false) }

  var embeddedMapView by remember { mutableStateOf<MapView?>(null) }
  var fullScreenMapView by remember { mutableStateOf<MapView?>(null) }

  // Keep synced when parent resets external coordinates (e.g., async Firestore hydration)
  LaunchedEffect(latitude, longitude) {
    if (abs(currentLat - latitude) > 0.0002 || abs(currentLng - longitude) > 0.0002) {
      shouldAutoApplyAddress = false
      currentLat = latitude
      currentLng = longitude
    }
  }

  // Debounced reverse geocoding when pin settles on a road or building
  LaunchedEffect(currentLat, currentLng) {
    isResolvingAddress = true
    delay(450)
    val resolved = reverseGeocodeCoordinates(context, currentLat, currentLng)
    detectedAddress = resolved
    isResolvingAddress = false
    if (shouldAutoApplyAddress && resolved != null && (resolved.locality.isNotBlank() || resolved.pincode.isNotBlank())) {
      shouldAutoApplyAddress = false
      onAddressDetected?.invoke(resolved.locality, resolved.pincode)
    }
  }

  fun movePinTo(lat: Double, lng: Double, zoomToStreet: Boolean = false, autoApplyAddress: Boolean = false) {
    shouldAutoApplyAddress = autoApplyAddress
    val isLargeJump = abs(lat - currentLat) > 0.05 || abs(lng - currentLng) > 0.05
    currentLat = lat
    currentLng = lng
    onLocationSelected(lat, lng)
    val target = GeoPoint(lat, lng)
    embeddedMapView?.let { map ->
      if (zoomToStreet) map.controller.setZoom(17.2)
      if (isLargeJump) map.controller.setCenter(target) else map.controller.animateTo(target)
    }
    fullScreenMapView?.let { map ->
      if (zoomToStreet) map.controller.setZoom(17.2)
      if (isLargeJump) map.controller.setCenter(target) else map.controller.animateTo(target)
    }
  }

  fun triggerGpsLookup() {
    isFetchingGps = true
    gpsStatusMessage = "Detecting current mobile GPS location..."
    fetchDeviceGpsLocation(
      context = context,
      onSuccess = { lat, lng ->
        isFetchingGps = false
        hasPermission = true
        gpsStatusMessage = "GPS locked on your mobile's current location"
        movePinTo(lat, lng, zoomToStreet = true, autoApplyAddress = true)
      },
      onFailure = { errorMsg ->
        isFetchingGps = false
        gpsStatusMessage = errorMsg
      }
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { result ->
    val granted = (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
      (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
    hasPermission = granted
    if (granted && pendingGpsRequest) {
      pendingGpsRequest = false
      triggerGpsLookup()
    } else if (!granted) {
      pendingGpsRequest = false
      gpsStatusMessage = "Location permission denied. Pan the map or search an address to pin your workshop."
    }
  }

  // Default to logged-in mobile's current GPS location when opening with unset/default coordinates
  LaunchedEffect(autoFetchCurrentLocationOnStart) {
    if (autoFetchCurrentLocationOnStart && !hasAutoCenteredOnce) {
      hasAutoCenteredOnce = true
      if (hasDeviceLocationPermission(context)) {
        triggerGpsLookup()
      } else {
        pendingGpsRequest = true
        permissionLauncher.launch(
          arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
          )
        )
      }
    }
  }

  fun performSearch() {
    val q = searchQuery.trim()
    if (q.isBlank()) return
    focusManager.clearFocus()
    coroutineScope.launch {
      isSearching = true
      gpsStatusMessage = null
      val match = searchLocationCoordinates(context, q)
      isSearching = false
      if (match != null) {
        val (point, resolved) = match
        detectedAddress = resolved
        movePinTo(
          (point.latitude * 100000.0).roundToInt() / 100000.0,
          (point.longitude * 100000.0).roundToInt() / 100000.0,
          zoomToStreet = true
        )
        if (resolved.locality.isNotBlank() || resolved.pincode.isNotBlank()) {
          onAddressDetected?.invoke(resolved.locality, resolved.pincode)
        }
      } else {
        gpsStatusMessage = "Location not found for \"$q\". Try adding city name or PIN code."
      }
    }
  }

  Card(
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, BorderLight),
    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Map, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Workshop Map Location",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Navy900)
          )
        }
        Surface(
          color = EmergencyRedSoft,
          shape = RoundedCornerShape(6.dp)
        ) {
          Text(
            text = "Pan, Pinch Zoom or Tap",
            style = MaterialTheme.typography.labelSmall.copy(color = EmergencyRedDark, fontWeight = FontWeight.Bold, fontSize = 10.sp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Address / Road / Building / PIN Search Field
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search street, building, area or PIN code", fontSize = 13.sp) },
        singleLine = true,
        leadingIcon = {
          Icon(Icons.Filled.Search, contentDescription = null, tint = Navy700, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
          if (isSearching) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = EmergencyRed)
          } else if (searchQuery.isNotBlank()) {
            Row {
              IconButton(onClick = { performSearch() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Search location", tint = EmergencyRed)
              }
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Filled.Clear, contentDescription = "Clear search", tint = TextMuted)
              }
            }
          }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { performSearch() }),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("map_address_search_input")
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Embedded Real OpenStreetMap Viewport (Roads, Buildings, Streets)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(270.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(SurfaceVariantSlate)
      ) {
        OsmInteractiveMapView(
          latitude = currentLat,
          longitude = currentLng,
          radiusKm = radiusKm,
          showMyLocationDot = hasPermission,
          allowScrollInterceptDisable = true,
          onCenterChanged = { lat, lng ->
            shouldAutoApplyAddress = true
            currentLat = lat
            currentLng = lng
            onLocationSelected(lat, lng)
          },
          onMapViewReady = { mv -> embeddedMapView = mv },
          onMapTouchChanged = onMapTouchChanged,
          modifier = Modifier.fillMaxSize()
        )

        // Fixed Center Workshop Pin (tip points directly at center GeoPoint)
        Box(
          modifier = Modifier.align(Alignment.Center),
          contentAlignment = Alignment.Center
        ) {
          // Ground crosshair dot
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(Navy900.copy(alpha = 0.6f))
          )
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = (-24).dp)
          ) {
            Surface(
              color = Navy900,
              shape = RoundedCornerShape(6.dp),
              shadowElevation = 4.dp
            ) {
              Text(
                text = "Workshop Pin",
                style = MaterialTheme.typography.labelSmall.copy(color = SurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 10.sp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
              )
            }
            Icon(
              imageVector = Icons.Filled.LocationOn,
              contentDescription = "Selected Workshop Pin",
              tint = EmergencyRed,
              modifier = Modifier.size(38.dp)
            )
          }
        }

        // Top-right Fullscreen + Zoom Controls
        Column(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Surface(
            color = SurfaceWhite.copy(alpha = 0.94f),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 3.dp,
            modifier = Modifier
              .size(36.dp)
              .clickable { isFullScreenOpen = true }
              .testTag("expand_fullscreen_map_btn")
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(Icons.Filled.Fullscreen, contentDescription = "Expand Map Fullscreen", tint = Navy900, modifier = Modifier.size(20.dp))
            }
          }

          Surface(
            color = SurfaceWhite.copy(alpha = 0.94f),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 3.dp
          ) {
            Column {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .size(36.dp)
                  .clickable { embeddedMapView?.controller?.zoomIn() }
              ) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom In", tint = Navy900, modifier = Modifier.size(18.dp))
              }
              HorizontalDivider(color = BorderLight, modifier = Modifier.width(36.dp))
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .size(36.dp)
                  .clickable { embeddedMapView?.controller?.zoomOut() }
              ) {
                Icon(Icons.Filled.Remove, contentDescription = "Zoom Out", tint = Navy900, modifier = Modifier.size(18.dp))
              }
            }
          }
        }

        // Bottom-left Live GPS Coordinates Chip
        Surface(
          color = Navy900.copy(alpha = 0.88f),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(8.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Icon(Icons.Filled.MyLocation, contentDescription = null, tint = OnlineGreen, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = String.format(Locale.US, "%.5f, %.5f", currentLat, currentLng),
              style = MaterialTheme.typography.labelSmall.copy(color = SurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            )
          }
        }

        // Bottom-right Current GPS Locator Floating Button
        Surface(
          color = SurfaceWhite,
          shape = CircleShape,
          shadowElevation = 4.dp,
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
            .size(42.dp)
            .clickable {
              if (hasDeviceLocationPermission(context)) {
                triggerGpsLookup()
              } else {
                pendingGpsRequest = true
                permissionLauncher.launch(
                  arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                  )
                )
              }
            }
        ) {
          Box(contentAlignment = Alignment.Center) {
            if (isFetchingGps) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = EmergencyRed)
            } else {
              Icon(Icons.Filled.GpsFixed, contentDescription = "Center on My GPS", tint = EmergencyRed, modifier = Modifier.size(22.dp))
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Detected Street / Building Readout Card
      Surface(
        color = SurfaceVariantSlate,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(Icons.Filled.Place, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = when {
                  isResolvingAddress -> "Detecting road & building..."
                  detectedAddress != null -> detectedAddress!!.formattedAddress
                  else -> localityLabel.ifBlank { "Pin at ${String.format(Locale.US, "%.5f, %.5f", currentLat, currentLng)}" }
                },
                style = MaterialTheme.typography.bodySmall.copy(color = Navy900, fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          if (detectedAddress != null && onAddressDetected != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = if (detectedAddress!!.pincode.isNotBlank()) {
                  "Detected PIN: ${detectedAddress!!.pincode}"
                } else {
                  "Locality ready to apply"
                },
                style = MaterialTheme.typography.labelSmall.copy(color = OnlineGreenDark, fontWeight = FontWeight.Bold)
              )
              FilledTonalButton(
                onClick = {
                  onAddressDetected(detectedAddress!!.locality, detectedAddress!!.pincode)
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
              ) {
                Text("Use Address & PIN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      if (gpsStatusMessage != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = gpsStatusMessage!!,
          style = MaterialTheme.typography.bodySmall.copy(
            color = if (gpsStatusMessage!!.startsWith("GPS locked")) OnlineGreenDark else EmergencyRedDark,
            fontSize = 11.sp
          )
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Coordinates + Action Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          color = SurfaceVariantSlate,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.weight(1f)
        ) {
          Column(modifier = Modifier.padding(8.dp)) {
            Text("Latitude", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
            Text(String.format(Locale.US, "%.5f", currentLat), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Navy900))
          }
        }
        Surface(
          color = SurfaceVariantSlate,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.weight(1f)
        ) {
          Column(modifier = Modifier.padding(8.dp)) {
            Text("Longitude", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
            Text(String.format(Locale.US, "%.5f", currentLng), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Navy900))
          }
        }

        OutlinedButton(
          onClick = {
            if (hasDeviceLocationPermission(context)) {
              triggerGpsLookup()
            } else {
              pendingGpsRequest = true
              permissionLauncher.launch(
                arrayOf(
                  Manifest.permission.ACCESS_FINE_LOCATION,
                  Manifest.permission.ACCESS_COARSE_LOCATION
                )
              )
            }
          },
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.testTag("use_current_gps_btn")
        ) {
          Icon(Icons.Filled.GpsFixed, contentDescription = null, tint = Navy900, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (isFetchingGps) "Locating..." else "Current GPS",
            fontSize = 11.sp,
            color = Navy900,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = "Quick Metro Workshop Hubs:",
        style = MaterialTheme.typography.labelSmall.copy(color = Navy700, fontWeight = FontWeight.Bold)
      )
      Spacer(modifier = Modifier.height(6.dp))
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        items(INDIAN_METRO_PRESETS.size) { idx ->
          val preset = INDIAN_METRO_PRESETS[idx]
          val isPresetSelected = abs(currentLat - preset.lat) < 0.01 && abs(currentLng - preset.lng) < 0.01
          Surface(
            color = if (isPresetSelected) Navy900 else SurfaceVariantSlate,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.clickable {
              movePinTo(preset.lat, preset.lng, zoomToStreet = true)
              onAddressDetected?.invoke(preset.locality, preset.pincode)
            }
          ) {
            Text(
              text = preset.name,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              color = if (isPresetSelected) SurfaceWhite else Navy900,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }
      }
    }
  }

  // Full-Screen Map Picker Dialog for ultra-precise road & building pinning
  if (isFullScreenOpen) {
    Dialog(
      onDismissRequest = { isFullScreenOpen = false },
      properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
      Scaffold(
        topBar = {
          Surface(color = SurfaceWhite, shadowElevation = 4.dp) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(onClick = { isFullScreenOpen = false }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Full Map", tint = Navy900)
                  }
                  Column {
                    Text(
                      text = "Pin Exact Workshop Location",
                      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Navy900)
                    )
                    Text(
                      text = "Zoom to see roads & buildings, pan or tap to pin",
                      style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(8.dp))

              OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search road, building, area or PIN code", fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = {
                  Icon(Icons.Filled.Search, contentDescription = null, tint = Navy700)
                },
                trailingIcon = {
                  if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = EmergencyRed)
                  } else if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { performSearch() }) {
                      Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Search", tint = EmergencyRed)
                    }
                  }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
              )
            }
          }
        },
        bottomBar = {
          Surface(color = SurfaceWhite, shadowElevation = 8.dp) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
            ) {
              Text(
                text = when {
                  isResolvingAddress -> "Resolving street & building..."
                  detectedAddress != null -> detectedAddress!!.formattedAddress
                  else -> String.format(Locale.US, "Coordinates: %.5f, %.5f", currentLat, currentLng)
                },
                style = MaterialTheme.typography.bodySmall.copy(color = Navy900, fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = String.format(Locale.US, "Exact Lat/Lng: %.5f, %.5f", currentLat, currentLng),
                style = MaterialTheme.typography.labelSmall.copy(color = OnlineGreenDark, fontWeight = FontWeight.Bold)
              )
              Spacer(modifier = Modifier.height(12.dp))
              Button(
                onClick = {
                  onLocationSelected(currentLat, currentLng)
                  detectedAddress?.let { addr ->
                    if (addr.locality.isNotBlank() || addr.pincode.isNotBlank()) {
                      onAddressDetected?.invoke(addr.locality, addr.pincode)
                    }
                  }
                  isFullScreenOpen = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(50.dp)
                  .testTag("confirm_fullscreen_map_btn")
              ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Confirm Exact Workshop Location", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      ) { innerPadding ->
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
          OsmInteractiveMapView(
            latitude = currentLat,
            longitude = currentLng,
            radiusKm = radiusKm,
            showMyLocationDot = hasPermission,
            allowScrollInterceptDisable = false,
            onCenterChanged = { lat, lng ->
              shouldAutoApplyAddress = true
              currentLat = lat
              currentLng = lng
              onLocationSelected(lat, lng)
            },
            onMapViewReady = { mv -> fullScreenMapView = mv },
            modifier = Modifier.fillMaxSize()
          )

          // Fixed center pin in full-screen view
          Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center
          ) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Navy900.copy(alpha = 0.7f))
            )
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.offset(y = (-26).dp)
            ) {
              Surface(
                color = Navy900,
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 4.dp
              ) {
                Text(
                  text = "My Workshop",
                  style = MaterialTheme.typography.labelSmall.copy(color = SurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp),
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
              }
              Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = "Workshop Location Pin",
                tint = EmergencyRed,
                modifier = Modifier.size(44.dp)
              )
            }
          }

          // Zoom & GPS Controls in full-screen view
          Column(
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
          ) {
            Surface(
              color = SurfaceWhite,
              shape = RoundedCornerShape(10.dp),
              shadowElevation = 4.dp
            ) {
              Column {
                IconButton(onClick = { fullScreenMapView?.controller?.zoomIn() }) {
                  Icon(Icons.Filled.Add, contentDescription = "Zoom In", tint = Navy900)
                }
                HorizontalDivider(color = BorderLight, modifier = Modifier.width(48.dp))
                IconButton(onClick = { fullScreenMapView?.controller?.zoomOut() }) {
                  Icon(Icons.Filled.Remove, contentDescription = "Zoom Out", tint = Navy900)
                }
              }
            }

            FloatingActionButton(
              onClick = {
                if (hasDeviceLocationPermission(context)) {
                  triggerGpsLookup()
                } else {
                  pendingGpsRequest = true
                  permissionLauncher.launch(
                    arrayOf(
                      Manifest.permission.ACCESS_FINE_LOCATION,
                      Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                  )
                }
              },
              containerColor = SurfaceWhite,
              contentColor = EmergencyRed,
              modifier = Modifier.shadow(4.dp, CircleShape)
            ) {
              if (isFetchingGps) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = EmergencyRed)
              } else {
                Icon(Icons.Filled.GpsFixed, contentDescription = "Use My GPS")
              }
            }
          }
        }
      }
    }
  }
}

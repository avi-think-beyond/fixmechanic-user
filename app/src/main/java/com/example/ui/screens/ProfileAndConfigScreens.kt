package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.INDIAN_METRO_PRESETS
import com.example.ui.components.InteractiveMapPicker
import com.example.ui.components.StatusBadge
import com.example.ui.components.hasDeviceLocationPermission
import com.example.ui.theme.*

// ----------------------------------------------------
// VERIFICATION STATUS SCREEN
// ----------------------------------------------------
@Composable
fun VerificationStatusScreen(
  profile: MechanicProfile,
  onSubmitDocument: (idNumber: String, docType: String) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var idInput by remember { mutableStateOf(profile.verificationIdNumber) }
  val isApproved = profile.registrationStatus == RegistrationStatus.APPROVED
  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceWhite)
      .verticalScroll(scrollState)
      .padding(20.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onNavigateBack) {
        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Navy900)
      }
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "KYC & Verification Status",
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          color = Navy900
        )
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Main status card
    val (statusColor, statusBg, statusIcon) = when (profile.registrationStatus) {
      RegistrationStatus.APPROVED -> Triple(OnlineGreenDark, OnlineGreenSoft, Icons.Filled.CheckCircle)
      RegistrationStatus.PENDING -> Triple(WarningAmberDark, WarningAmberLight, Icons.Filled.Pending)
      RegistrationStatus.REJECTED -> Triple(EmergencyRedDark, EmergencyRedLight, Icons.Filled.Cancel)
      RegistrationStatus.SUSPENDED -> Triple(EmergencyRedDark, EmergencyRedLight, Icons.Filled.Block)
    }

    Surface(
      color = statusBg,
      shape = RoundedCornerShape(12.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(statusColor),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = statusIcon,
              contentDescription = null,
              tint = SurfaceWhite,
              modifier = Modifier.size(24.dp)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = profile.registrationStatus.label,
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = statusColor
              )
            )
            Text(
              text = "Registered ID: ${profile.verificationIdNumber}",
              style = MaterialTheme.typography.bodySmall.copy(color = Navy700)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = BorderLight)
        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = profile.statusReason,
          style = MaterialTheme.typography.bodySmall.copy(
            color = Navy900,
            lineHeight = 18.sp
          )
        )
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text(
      text = "Eligibility Rule Enforcement",
      style = MaterialTheme.typography.titleSmall.copy(
        fontWeight = FontWeight.Bold,
        color = Navy900
      )
    )
    Spacer(modifier = Modifier.height(6.dp))
    Surface(
      color = SurfaceVariantSlate,
      shape = RoundedCornerShape(8.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(modifier = Modifier.padding(12.dp)) {
        Icon(Icons.Filled.Security, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = "FixMechanic backend strictly prevents unapproved mechanics from receiving, viewing, or accepting emergency roadside job offers. Toggling Online is locked until status is APPROVED.",
          style = MaterialTheme.typography.bodySmall.copy(color = Navy700, lineHeight = 16.sp)
        )
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text(
      text = "Verification Requirements Checklist",
      style = MaterialTheme.typography.titleSmall.copy(
        fontWeight = FontWeight.Bold,
        color = Navy900
      )
    )

    Spacer(modifier = Modifier.height(10.dp))

    val checklist = listOf(
      "Aadhaar Card / Government Photo Identity" to true,
      "Commercial Driving License / Two-Wheeler / LMV License" to isApproved,
      "Workshop Trade Certificate or Garage Proof" to isApproved,
      "Police Verification & Roadside Safety Clearance" to isApproved,
      "Mobile Roadside Safety Kit (Cones, Jack, Battery Booster, Multimeter)" to true
    )

    checklist.forEach { (item, verified) ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = if (verified) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
          contentDescription = null,
          tint = if (verified) OnlineGreen else TextMuted,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = item,
          style = MaterialTheme.typography.bodySmall.copy(
            color = if (verified) Navy900 else Navy700,
            fontWeight = if (verified) FontWeight.Medium else FontWeight.Normal
          )
        )
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = "Re-submit / Update KYC Document ID",
      style = MaterialTheme.typography.titleSmall.copy(
        fontWeight = FontWeight.Bold,
        color = Navy900
      )
    )

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
      value = idInput,
      onValueChange = { idInput = it },
      label = { Text("DL / Trade Certificate Number") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(8.dp)
    )

    Spacer(modifier = Modifier.height(12.dp))

    Button(
      onClick = { onSubmitDocument(idInput, "Updated Trade Certificate") },
      colors = ButtonDefaults.buttonColors(containerColor = Navy900),
      shape = RoundedCornerShape(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("submit_document_audit_btn")
    ) {
      Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text("Submit for Verification Review")
    }
  }
}

// ----------------------------------------------------
// VEHICLE CAPABILITY & SERVICES SCREEN
// ----------------------------------------------------
@Composable
fun VehicleAndServicesScreen(
  currentCapability: VehicleCapability,
  supportedServices: Set<ServiceCategory>,
  onUpdateCapability: (VehicleCapability) -> Unit,
  onToggleService: (ServiceCategory) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceWhite)
      .verticalScroll(scrollState)
      .padding(20.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onNavigateBack) {
        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Navy900)
      }
      Spacer(modifier = Modifier.width(8.dp))
      Column {
        Text(
          text = "Vehicles & Emergency Services",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = Navy900
          )
        )
        Text(
          text = "Choose what breakdowns you are equipped to service",
          style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
        )
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text(
      text = "SERVICE CAPABILITY (BIKE / CAR / BOTH)",
      style = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Bold,
        color = Navy700,
        letterSpacing = 1.sp
      )
    )

    Spacer(modifier = Modifier.height(10.dp))

    VehicleCapability.entries.forEach { cap ->
      val isSelected = currentCapability == cap
      Surface(
        color = if (isSelected) Navy50 else SurfaceWhite,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
          1.dp,
          if (isSelected) Navy900 else BorderLight
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp)
          .clip(RoundedCornerShape(10.dp))
          .clickable { onUpdateCapability(cap) }
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          RadioButton(
            selected = isSelected,
            onClick = { onUpdateCapability(cap) },
            colors = RadioButtonDefaults.colors(selectedColor = Navy900)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = cap.label,
              style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Navy900
              )
            )
            Text(
              text = cap.description,
              style = MaterialTheme.typography.bodySmall.copy(
                color = Navy700,
                fontSize = 12.sp
              )
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = "EMERGENCY SERVICE SPECIALTIES (INDIA)",
      style = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Bold,
        color = Navy700,
        letterSpacing = 1.sp
      )
    )

    Spacer(modifier = Modifier.height(10.dp))

    ServiceCategory.entries.forEach { category ->
      val isSelected = supportedServices.contains(category)
      Surface(
        color = if (isSelected) EmergencyRedSoft else SurfaceWhite,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
          1.dp,
          if (isSelected) EmergencyRed else BorderLight
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp)
          .clip(RoundedCornerShape(10.dp))
          .clickable { onToggleService(category) }
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleService(category) },
            colors = CheckboxDefaults.colors(checkedColor = EmergencyRed)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = category.label,
            style = MaterialTheme.typography.bodyMedium.copy(
              fontWeight = FontWeight.Bold,
              color = if (isSelected) EmergencyRedDark else Navy900
            )
          )
        }
      }
    }
  }
}

// ----------------------------------------------------
// SERVICE RADIUS & PINCODE SCREEN
// ----------------------------------------------------
@Composable
fun ServiceAreaRadiusScreen(
  currentRadiusKm: Int,
  baseLocality: String,
  currentPincode: String = "560038",
  servicePincodes: List<String> = listOf("560038", "560008", "560075"),
  initialLatitude: Double = 12.9716,
  initialLongitude: Double = 77.5946,
  hasCustomLocation: Boolean = false,
  onUpdateServiceArea: (locality: String, pincode: String, radiusKm: Int, servicePincodes: List<String>, latitude: Double, longitude: Double) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
  profileId: String = "",
  isAuthenticated: Boolean = true,
  isSaving: Boolean = false
) {
  var hasLocalEdits by remember(profileId) { mutableStateOf(false) }
  var localityInput by remember(profileId) { mutableStateOf(baseLocality) }
  var pincodeInput by remember(profileId) { mutableStateOf(currentPincode) }
  var additionalPinsInput by remember(profileId) {
    mutableStateOf(servicePincodes.filter { it != currentPincode }.joinToString(", "))
  }
  var selectedLat by remember(profileId) { mutableDoubleStateOf(initialLatitude) }
  var selectedLng by remember(profileId) { mutableDoubleStateOf(initialLongitude) }
  var sliderValue by remember(profileId) { mutableFloatStateOf(currentRadiusKm.toFloat()) }
  var isInteractingWithMap by remember { mutableStateOf(false) }

  LaunchedEffect(profileId, baseLocality, currentPincode, servicePincodes, initialLatitude, initialLongitude, currentRadiusKm) {
    if (!hasLocalEdits) {
      localityInput = baseLocality
      pincodeInput = currentPincode
      additionalPinsInput = servicePincodes.filter { it != currentPincode }.joinToString(", ")
      selectedLat = initialLatitude
      selectedLng = initialLongitude
      sliderValue = currentRadiusKm.toFloat()
    }
  }

  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceWhite)
      .verticalScroll(scrollState, enabled = !isInteractingWithMap)
      .padding(20.dp),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onNavigateBack) {
          Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Navy900)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(
            text = "Service Area & PIN Code",
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              color = Navy900
            )
          )
          Text(
            text = "Set your dispatch hub, postal PIN, and map coordinates",
            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
          )
        }
      }

      if (!isAuthenticated) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
          color = WarningAmberLight,
          shape = RoundedCornerShape(10.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, tint = WarningAmberDark, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Not signed in with Google. Changes cannot sync to Firebase or the Customer App until you Sign in with Google.",
              style = MaterialTheme.typography.bodySmall.copy(color = Navy900, fontWeight = FontWeight.Medium)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Current Coverage Summary Card
      Surface(
        color = Navy900,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "PRIMARY WORKSHOP HUB",
                style = MaterialTheme.typography.labelSmall.copy(
                  color = OnlineGreenLight,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 1.sp
                )
              )
              Text(
                text = localityInput.ifBlank { "Unset Locality" },
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = SurfaceWhite
                )
              )
              Text(
                text = "Postal PIN: ${pincodeInput.ifBlank { "------" }}",
                style = MaterialTheme.typography.bodyMedium.copy(color = Navy300)
              )
              Text(
                text = "GPS: ${String.format(java.util.Locale.US, "%.4f, %.4f", selectedLat, selectedLng)}",
                style = MaterialTheme.typography.bodySmall.copy(color = OnlineGreenLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
              )
            }
            Surface(
              color = EmergencyRed,
              shape = RoundedCornerShape(8.dp)
            ) {
              Text(
                text = "${sliderValue.toInt()} km",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.titleSmall.copy(
                  fontWeight = FontWeight.Black,
                  color = SurfaceWhite
                )
              )
            }
          }

          Spacer(modifier = Modifier.height(14.dp))
          HorizontalDivider(color = Navy800)
          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = "Emergency roadside calls matching this postal PIN, within ${sliderValue.toInt()} km, or closest to (${String.format(java.util.Locale.US, "%.4f, %.4f", selectedLat, selectedLng)}) will trigger immediate alerts.",
            style = MaterialTheme.typography.bodySmall.copy(color = Navy300, fontSize = 11.sp)
          )
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Interactive Map Location Picker
      Text(
        text = "SELECT WORKSHOP LOCATION ON MAP",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Navy700)
      )
      Spacer(modifier = Modifier.height(8.dp))
      InteractiveMapPicker(
        latitude = selectedLat,
        longitude = selectedLng,
        radiusKm = sliderValue.toInt(),
        localityLabel = localityInput,
        autoFetchCurrentLocationOnStart = !hasCustomLocation,
        onMapTouchChanged = { isInteractingWithMap = it },
        onLocationSelected = { lat, lng ->
          hasLocalEdits = true
          selectedLat = lat
          selectedLng = lng
          // Automatically update matching preset locality if close and input is blank
          val match = INDIAN_METRO_PRESETS.firstOrNull { Math.abs(it.lat - lat) < 0.03 && Math.abs(it.lng - lng) < 0.03 }
          if (match != null && localityInput.isBlank()) {
            localityInput = match.locality
            pincodeInput = match.pincode
          }
        },
        onAddressDetected = { detectedLocality, detectedPin ->
          hasLocalEdits = true
          if (detectedLocality.isNotBlank()) localityInput = detectedLocality
          if (detectedPin.length == 6) pincodeInput = detectedPin
        }
      )

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = "BASE LOCATION & POSTAL DETAILS",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Navy700)
      )

      Spacer(modifier = Modifier.height(8.dp))

      OutlinedTextField(
        value = localityInput,
        onValueChange = {
          hasLocalEdits = true
          localityInput = it
        },
        label = { Text("Base City & Locality / Landmark") },
        placeholder = { Text("e.g. Indiranagar, Bengaluru") },
        leadingIcon = {
          Icon(Icons.Filled.LocationOn, contentDescription = null, tint = EmergencyRed)
        },
        modifier = Modifier.fillMaxWidth().testTag("service_locality_input")
      )

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
        value = pincodeInput,
        onValueChange = { input ->
          // Indian PIN codes are 6 digits
          if (input.length <= 6 && input.all { it.isDigit() }) {
            hasLocalEdits = true
            pincodeInput = input
          }
        },
        label = { Text("Primary Workshop PIN Code (6 Digits)") },
        placeholder = { Text("e.g. 560038") },
        leadingIcon = {
          Icon(Icons.Filled.PinDrop, contentDescription = null, tint = Navy700)
        },
        supportingText = {
          Text(
            text = if (pincodeInput.length == 6) "Valid 6-digit Indian PIN code" else "Enter 6-digit postal PIN code",
            color = if (pincodeInput.length == 6) OnlineGreenDark else TextMuted
          )
        },
        modifier = Modifier.fillMaxWidth().testTag("service_pincode_input")
      )

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
        value = additionalPinsInput,
        onValueChange = {
          hasLocalEdits = true
          additionalPinsInput = it
        },
        label = { Text("Additional Serviced PIN Codes (Optional)") },
        placeholder = { Text("e.g. 560008, 560075, 560103") },
        supportingText = {
          Text("Separate multiple postal PIN codes with commas")
        },
        modifier = Modifier.fillMaxWidth().testTag("additional_pincodes_input")
      )

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = "DISPATCH PERIMETER (RADIUS)",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Navy700)
      )
      Text(
        text = "Maximum travel distance you are willing to fulfill for roadside emergencies.",
        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
      )

      Spacer(modifier = Modifier.height(14.dp))

      Slider(
        value = sliderValue,
        onValueChange = {
          hasLocalEdits = true
          sliderValue = it
        },
        valueRange = 5f..40f,
        steps = 6,
        colors = SliderDefaults.colors(
          thumbColor = EmergencyRed,
          activeTrackColor = EmergencyRed,
          inactiveTrackColor = SurfaceVariantSlate
        ),
        modifier = Modifier.fillMaxWidth().testTag("service_radius_slider")
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text("5 km", fontSize = 12.sp, color = Navy700, fontWeight = FontWeight.Bold)
        Text("15 km", fontSize = 12.sp, color = Navy700, fontWeight = FontWeight.Bold)
        Text("25 km", fontSize = 12.sp, color = Navy700, fontWeight = FontWeight.Bold)
        Text("40 km", fontSize = 12.sp, color = Navy700, fontWeight = FontWeight.Bold)
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Preset quick chips
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        listOf(5 to "5 km Hub", 15 to "15 km City", 25 to "25 km Metro", 40 to "40 km Highway").forEach { (rad, label) ->
          val isSelected = sliderValue.toInt() == rad
          Surface(
            color = if (isSelected) Navy900 else SurfaceVariantSlate,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .weight(1f)
              .clickable {
                hasLocalEdits = true
                sliderValue = rad.toFloat()
              }
          ) {
            Text(
              text = label,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = if (isSelected) SurfaceWhite else Navy900,
              modifier = Modifier.padding(vertical = 8.dp),
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Button(
      onClick = {
        val extraPins = additionalPinsInput
          .split(",")
          .map { it.trim() }
          .filter { it.length == 6 && it.all { ch -> ch.isDigit() } }
        val allPins = buildList {
          if (pincodeInput.isNotBlank()) add(pincodeInput.trim())
          addAll(extraPins)
        }.distinct()

        onUpdateServiceArea(
          localityInput.trim(),
          pincodeInput.trim(),
          sliderValue.toInt(),
          allPins,
          selectedLat,
          selectedLng
        )
      },
      enabled = !isSaving,
      colors = ButtonDefaults.buttonColors(containerColor = Navy900),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .testTag("save_service_area_btn")
    ) {
      if (isSaving) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SurfaceWhite, strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Saving to Firebase...", fontWeight = FontWeight.Bold)
      } else {
        Text(
          text = "Save Service Area & Coordinates (${sliderValue.toInt()} km)",
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

// ----------------------------------------------------
// EDIT PERMITTED PROFILE FIELDS SCREEN
// ----------------------------------------------------
@Composable
fun EditProfileScreen(
  profile: MechanicProfile,
  onSaveProfile: (name: String, workshop: String, capability: VehicleCapability, city: String, radius: Int, pincode: String, latitude: Double, longitude: Double) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
  isAuthenticated: Boolean = true,
  isSaving: Boolean = false,
  isNewUserSetup: Boolean = false
) {
  var hasLocalEdits by remember(profile.id) { mutableStateOf(false) }
  var fullName by remember(profile.id) { mutableStateOf(profile.fullName) }
  var workshopName by remember(profile.id) { mutableStateOf(profile.workshopName) }
  var capability by remember(profile.id) { mutableStateOf(profile.vehicleCapability) }
  var cityLocality by remember(profile.id) { mutableStateOf(profile.baseCityLocality) }
  var pincode by remember(profile.id) { mutableStateOf(profile.pincode) }
  var radiusKm by remember(profile.id) { mutableIntStateOf(profile.serviceRadiusKm) }
  var selectedLat by remember(profile.id) { mutableDoubleStateOf(profile.latitude) }
  var selectedLng by remember(profile.id) { mutableDoubleStateOf(profile.longitude) }
  var isInteractingWithMap by remember { mutableStateOf(false) }

  LaunchedEffect(
    profile.id, profile.fullName, profile.workshopName, profile.vehicleCapability,
    profile.baseCityLocality, profile.pincode, profile.serviceRadiusKm, profile.latitude, profile.longitude
  ) {
    if (!hasLocalEdits) {
      fullName = profile.fullName
      workshopName = profile.workshopName
      capability = profile.vehicleCapability
      cityLocality = profile.baseCityLocality
      pincode = profile.pincode
      radiusKm = profile.serviceRadiusKm
      selectedLat = profile.latitude
      selectedLng = profile.longitude
    }
  }

  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceWhite)
      .verticalScroll(scrollState, enabled = !isInteractingWithMap)
      .padding(20.dp),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onNavigateBack) {
          Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Navy900)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (isNewUserSetup) "Complete Your Mechanic Profile" else "Edit Profile & Capabilities",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Navy900)
        )
      }

      if (isNewUserSetup && isAuthenticated) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
          color = OnlineGreenSoft,
          shape = RoundedCornerShape(10.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, OnlineGreen),
          modifier = Modifier.fillMaxWidth().testTag("new_user_profile_setup_banner")
        ) {
          Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = OnlineGreenDark, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Welcome! You are signed in. Update your workshop details, PIN code, and map location below to continue to Home.",
              style = MaterialTheme.typography.bodySmall.copy(color = Navy900, fontWeight = FontWeight.Medium)
            )
          }
        }
      }

      if (!isAuthenticated) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
          color = WarningAmberLight,
          shape = RoundedCornerShape(10.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, tint = WarningAmberDark, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Not signed in with Google. Changes cannot sync to Firebase or the Customer App until you Sign in with Google.",
              style = MaterialTheme.typography.bodySmall.copy(color = Navy900, fontWeight = FontWeight.Medium)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      OutlinedTextField(
        value = fullName,
        onValueChange = {
          hasLocalEdits = true
          fullName = it
        },
        label = { Text("Mechanic Name") },
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
        value = workshopName,
        onValueChange = {
          hasLocalEdits = true
          workshopName = it
        },
        label = { Text("Workshop / Garage Name") },
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(16.dp))

      Text("VEHICLE CAPABILITY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Navy700))
      Spacer(modifier = Modifier.height(6.dp))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VehicleCapability.entries.forEach { cap ->
          val isSelected = capability == cap
          Surface(
            color = if (isSelected) Navy900 else SurfaceVariantSlate,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f).clickable {
              hasLocalEdits = true
              capability = cap
            }
          ) {
            Text(
              text = when (cap) {
                VehicleCapability.BIKE -> "Bike"
                VehicleCapability.CAR -> "Car"
                VehicleCapability.BOTH -> "Both"
              },
              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
              modifier = Modifier.padding(vertical = 10.dp),
              color = if (isSelected) SurfaceWhite else Navy900,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      OutlinedTextField(
        value = cityLocality,
        onValueChange = {
          hasLocalEdits = true
          cityLocality = it
        },
        label = { Text("Operating City & Locality") },
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
        value = pincode,
        onValueChange = { input ->
          if (input.length <= 6 && input.all { it.isDigit() }) {
            hasLocalEdits = true
            pincode = input
          }
        },
        label = { Text("Postal PIN Code (6 Digits)") },
        placeholder = { Text("e.g. 560038") },
        supportingText = { Text("PIN code for local roadside dispatch priority") },
        modifier = Modifier.fillMaxWidth().testTag("edit_profile_pincode")
      )

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "EXACT WORKSHOP GPS PIN",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Navy700)
      )
      Spacer(modifier = Modifier.height(8.dp))

      InteractiveMapPicker(
        latitude = selectedLat,
        longitude = selectedLng,
        radiusKm = radiusKm,
        localityLabel = cityLocality,
        autoFetchCurrentLocationOnStart = !profile.hasCustomLocation,
        onMapTouchChanged = { isInteractingWithMap = it },
        onLocationSelected = { lat, lng ->
          hasLocalEdits = true
          selectedLat = lat
          selectedLng = lng
        },
        onAddressDetected = { detectedLocality, detectedPin ->
          hasLocalEdits = true
          if (detectedLocality.isNotBlank()) cityLocality = detectedLocality
          if (detectedPin.length == 6) pincode = detectedPin
        }
      )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Button(
      onClick = {
        onSaveProfile(fullName, workshopName, capability, cityLocality, radiusKm, pincode, selectedLat, selectedLng)
      },
      enabled = !isSaving,
      colors = ButtonDefaults.buttonColors(containerColor = Navy900),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .testTag("save_profile_changes_btn")
    ) {
      if (isSaving) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SurfaceWhite, strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Saving to Firebase...", fontWeight = FontWeight.Bold)
      } else {
        Text(if (isNewUserSetup) "Save Profile & Continue to Home" else "Save Changes", fontWeight = FontWeight.Bold)
      }
    }
  }
}

// ----------------------------------------------------
// PROFILE AND SUPPORT SCREEN
// ----------------------------------------------------
@Composable
fun ProfileAndSupportScreen(
  profile: MechanicProfile,
  onNavigateToEditProfile: () -> Unit,
  onNavigateToVerification: () -> Unit,
  onNavigateToVehicles: () -> Unit,
  onNavigateToRadius: () -> Unit,
  onSignOut: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
  isAuthenticated: Boolean = true,
  isSyncingLocation: Boolean = false,
  onRefreshCurrentGps: () -> Unit = {}
) {
  val scrollState = rememberScrollState()
  val context = LocalContext.current
  val locationPermLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { result ->
    val granted = (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
      (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
    if (granted) {
      onRefreshCurrentGps()
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceWhite)
      .verticalScroll(scrollState)
      .padding(20.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onNavigateBack) {
        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Navy900)
      }
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "Mechanic Profile & Account",
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          color = Navy900
        )
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Mechanic ID Card
    Surface(
      color = Navy900,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(20.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = profile.fullName,
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = SurfaceWhite
              )
            )
            Text(
              text = profile.workshopName,
              style = MaterialTheme.typography.bodyMedium.copy(color = Navy300)
            )
            Text(
              text = "${profile.mobileNumber} • ${profile.vehicleCapability.label}",
              style = MaterialTheme.typography.bodySmall.copy(color = Navy300, fontSize = 11.sp)
            )
          }

          Surface(
            color = when (profile.registrationStatus) {
              RegistrationStatus.APPROVED -> OnlineGreen
              RegistrationStatus.PENDING -> WarningAmber
              RegistrationStatus.REJECTED -> EmergencyRed
              RegistrationStatus.SUSPENDED -> EmergencyRed
            },
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = profile.registrationStatus.label,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = NavyDark,
                fontSize = 10.sp
              )
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Surface(
          color = if (isAuthenticated) OnlineGreenDark.copy(alpha = 0.28f) else EmergencyRedDark.copy(alpha = 0.28f),
          shape = RoundedCornerShape(6.dp)
        ) {
          Text(
            text = if (isAuthenticated) {
              "Firebase Live Sync • ${profile.baseCityLocality} (${profile.pincode})"
            } else {
              "Local Demo Session • Sign in with Google to sync changes"
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(
              color = if (isAuthenticated) OnlineGreenLight else WarningAmberLight,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp
            )
          )
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = Navy700)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column {
            Text("Rating", style = MaterialTheme.typography.labelSmall.copy(color = Navy300))
            Text("★ ${profile.rating}", style = MaterialTheme.typography.titleMedium.copy(color = SurfaceWhite, fontWeight = FontWeight.Bold))
          }
          Column {
            Text("Fulfilled Jobs", style = MaterialTheme.typography.labelSmall.copy(color = Navy300))
            Text("${profile.totalCompletedJobs}", style = MaterialTheme.typography.titleMedium.copy(color = SurfaceWhite, fontWeight = FontWeight.Bold))
          }
          Column {
            Text("Experience", style = MaterialTheme.typography.labelSmall.copy(color = Navy300))
            Text("${profile.experienceYears} yrs", style = MaterialTheme.typography.titleMedium.copy(color = SurfaceWhite, fontWeight = FontWeight.Bold))
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Active Mobile / Workshop Location Status Card
    Surface(
      color = Navy50,
      shape = RoundedCornerShape(12.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
              imageVector = if (profile.hasCustomLocation) Icons.Filled.Storefront else Icons.Filled.MyLocation,
              contentDescription = null,
              tint = EmergencyRed,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (profile.hasCustomLocation) "SAVED WORKSHOP PIN" else "MOBILE CURRENT LOCATION",
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Navy900,
                letterSpacing = 0.6.sp
              )
            )
          }
          Surface(
            color = if (profile.hasCustomLocation) OnlineGreenSoft else SurfaceWhite,
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = "${profile.serviceRadiusKm} km",
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = if (profile.hasCustomLocation) OnlineGreenDark else Navy700
              ),
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = profile.baseCityLocality.ifBlank { "Location not set yet" },
          style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.SemiBold,
            color = Navy900
          )
        )
        Text(
          text = "PIN: ${profile.pincode.ifBlank { "------" }} • GPS: ${String.format(java.util.Locale.US, "%.4f, %.4f", profile.latitude, profile.longitude)}",
          style = MaterialTheme.typography.bodySmall.copy(color = Navy700, fontSize = 11.sp)
        )

        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = {
              if (hasDeviceLocationPermission(context)) {
                onRefreshCurrentGps()
              } else {
                locationPermLauncher.launch(
                  arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                  )
                )
              }
            },
            enabled = !isSyncingLocation,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("profile_use_current_gps_btn")
          ) {
            if (isSyncingLocation) {
              CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Navy900)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Locating...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            } else {
              Icon(Icons.Filled.GpsFixed, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(5.dp))
              Text("Use Mobile GPS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }

          Button(
            onClick = onNavigateToRadius,
            colors = ButtonDefaults.buttonColors(containerColor = Navy900),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("profile_pin_on_map_btn")
          ) {
            Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text("Pin on Map", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text(
      text = "PROFILE SETTINGS",
      style = MaterialTheme.typography.labelSmall.copy(color = Navy700, fontWeight = FontWeight.Bold)
    )

    Spacer(modifier = Modifier.height(8.dp))

    ProfileNavCard(
      title = "Edit Profile Details",
      subtitle = "${profile.baseCityLocality} • PIN ${profile.pincode}",
      icon = Icons.Filled.Edit,
      onClick = onNavigateToEditProfile
    )

    ProfileNavCard(
      title = "KYC & Verification Audit",
      subtitle = "Current status: ${profile.registrationStatus.label}",
      icon = Icons.Filled.VerifiedUser,
      onClick = onNavigateToVerification
    )

    ProfileNavCard(
      title = "Bike & Car Emergency Services",
      subtitle = "${profile.vehicleCapability.label} • ${profile.supportedServices.size} categories",
      icon = Icons.Filled.TwoWheeler,
      onClick = onNavigateToVehicles
    )

    ProfileNavCard(
      title = "Service Area, PIN Code & Radius",
      subtitle = "${profile.serviceRadiusKm} km • ${profile.baseCityLocality} (PIN: ${profile.pincode})",
      icon = Icons.Filled.MyLocation,
      onClick = onNavigateToRadius
    )

    Spacer(modifier = Modifier.height(20.dp))

    Text(
      text = "SUPPORT & EMERGENCY HELPLINE",
      style = MaterialTheme.typography.labelSmall.copy(color = Navy700, fontWeight = FontWeight.Bold)
    )

    Spacer(modifier = Modifier.height(8.dp))

    Surface(
      color = SurfaceVariantSlate,
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Call, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "FixMechanic 24/7 Helpline: 1800-FIX-MECH (India)",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Navy900)
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "For highway life safety incidents, call Indian National Emergency (112) or Highway Patrol (1033).",
          style = MaterialTheme.typography.bodySmall.copy(color = Navy700, fontSize = 11.sp)
        )
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    OutlinedButton(
      onClick = onSignOut,
      colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("mechanic_sign_out_btn")
    ) {
      Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text("Log Out of Console", fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun ProfileNavCard(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit
) {
  Surface(
    color = SurfaceWhite,
    shape = RoundedCornerShape(10.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .clip(RoundedCornerShape(10.dp))
      .clickable { onClick() }
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(Navy50),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = null, tint = Navy900, modifier = Modifier.size(20.dp))
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Navy900))
        Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 12.sp))
      }
      Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextMuted)
    }
  }
}

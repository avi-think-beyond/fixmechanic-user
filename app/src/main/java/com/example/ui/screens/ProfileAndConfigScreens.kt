package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.StatusBadge
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
// SERVICE RADIUS SCREEN
// ----------------------------------------------------
@Composable
fun ServiceAreaRadiusScreen(
  currentRadiusKm: Int,
  baseLocality: String,
  onUpdateRadius: (Int) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var sliderValue by remember { mutableFloatStateOf(currentRadiusKm.toFloat()) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceWhite)
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
          text = "Service Area & Operating Radius",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = Navy900
          )
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      Surface(
        color = Navy900,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(20.dp)) {
          Text(
            text = "Base Workshop / Hub Locality",
            style = MaterialTheme.typography.labelSmall.copy(color = Navy300)
          )
          Text(
            text = baseLocality,
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              color = SurfaceWhite
            )
          )
          Spacer(modifier = Modifier.height(16.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Dispatch Radius:",
              style = MaterialTheme.typography.bodyMedium.copy(color = Navy300)
            )
            Text(
              text = "${sliderValue.toInt()} km",
              style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Black,
                color = OnlineGreenLight
              )
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = "Adjust Response Perimeter",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Navy900)
      )
      Text(
        text = "Emergency calls outside this radius will not be sent to your device.",
        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
      )

      Spacer(modifier = Modifier.height(16.dp))

      Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        valueRange = 5f..40f,
        steps = 7,
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
        Text("5 km (Local Hub)", fontSize = 12.sp, color = Navy700)
        Text("20 km (City Perimeter)", fontSize = 12.sp, color = Navy700)
        Text("40 km (Highway Corridor)", fontSize = 12.sp, color = Navy700)
      }
    }

    Button(
      onClick = {
        onUpdateRadius(sliderValue.toInt())
        onNavigateBack()
      },
      colors = ButtonDefaults.buttonColors(containerColor = Navy900),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .testTag("save_service_radius_btn")
    ) {
      Text("Save Dispatch Radius (${sliderValue.toInt()} km)", fontWeight = FontWeight.Bold)
    }
  }
}

// ----------------------------------------------------
// EDIT PERMITTED PROFILE FIELDS SCREEN
// ----------------------------------------------------
@Composable
fun EditProfileScreen(
  profile: MechanicProfile,
  onSaveProfile: (name: String, workshop: String, capability: VehicleCapability, city: String, radius: Int) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var fullName by remember { mutableStateOf(profile.fullName) }
  var workshopName by remember { mutableStateOf(profile.workshopName) }
  var capability by remember { mutableStateOf(profile.vehicleCapability) }
  var cityLocality by remember { mutableStateOf(profile.baseCityLocality) }
  var radiusKm by remember { mutableIntStateOf(profile.serviceRadiusKm) }

  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceWhite)
      .verticalScroll(scrollState)
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
          text = "Edit Profile & Capabilities",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Navy900)
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      OutlinedTextField(
        value = fullName,
        onValueChange = { fullName = it },
        label = { Text("Mechanic Name") },
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
        value = workshopName,
        onValueChange = { workshopName = it },
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
            modifier = Modifier.weight(1f).clickable { capability = cap }
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
        onValueChange = { cityLocality = it },
        label = { Text("Operating City & Locality") },
        modifier = Modifier.fillMaxWidth()
      )
    }

    Button(
      onClick = {
        onSaveProfile(fullName, workshopName, capability, cityLocality, radiusKm)
        onNavigateBack()
      },
      colors = ButtonDefaults.buttonColors(containerColor = Navy900),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .testTag("save_profile_changes_btn")
    ) {
      Text("Save Changes", fontWeight = FontWeight.Bold)
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
          Column {
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

        Spacer(modifier = Modifier.height(16.dp))
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

    Spacer(modifier = Modifier.height(20.dp))

    Text(
      text = "PROFILE SETTINGS",
      style = MaterialTheme.typography.labelSmall.copy(color = Navy700, fontWeight = FontWeight.Bold)
    )

    Spacer(modifier = Modifier.height(8.dp))

    ProfileNavCard(
      title = "Edit Profile Details",
      subtitle = "Name, Workshop, City, Vehicle Capability",
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
      title = "Service Radius & Base Hub",
      subtitle = "${profile.serviceRadiusKm} km around ${profile.baseCityLocality}",
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

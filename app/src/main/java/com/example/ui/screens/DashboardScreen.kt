package com.example.ui.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ui.components.DemoModeTopBanner
import com.example.ui.components.MechanicTopBar
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

@Composable
fun DashboardScreen(
  profile: MechanicProfile,
  activeJob: ActiveJob?,
  completedHistory: List<CompletedJobRecord>,
  todayEarningsInr: Double,
  isCompetitorModeOn: Boolean,
  statusBanner: String?,
  isLocationPermissionGranted: Boolean,
  isGpsEnabled: Boolean,
  onCheckPermissions: (Context) -> Unit,
  onSetLocationPermission: (Boolean) -> Unit,
  onToggleOnline: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenActiveJob: () -> Unit,
  onOpenHistory: () -> Unit,
  onOpenVerification: () -> Unit,
  onOpenServices: () -> Unit,
  onSwitchProfile: (String) -> Unit,
  onToggleCompetitorMode: (Boolean) -> Unit,
  onSimulateJobOffer: (Int) -> Unit,
  onDismissBanner: () -> Unit,
  onSyncLocation: (Context) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val isApproved = profile.registrationStatus == RegistrationStatus.APPROVED

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
    val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
    onSetLocationPermission(fineLocation || coarseLocation)
    onCheckPermissions(context)
  }

  LaunchedEffect(Unit) {
    onCheckPermissions(context)
  }
  LaunchedEffect(profile.isOnline, isLocationPermissionGranted) {
    if (profile.isOnline && isLocationPermissionGranted) {
      onSyncLocation(context)
    }
  }

  Scaffold(
    topBar = {
      Column {
        MechanicTopBar(
          isOnline = profile.isOnline,
          registrationStatus = profile.registrationStatus,
          onToggleOnline = onToggleOnline,
          onOpenSettings = onOpenSettings
        )
        if (profile.isDemoMode) {
          DemoModeTopBanner(
            isCompetitorModeOn = isCompetitorModeOn,
            onToggleCompetitorMode = onToggleCompetitorMode,
            onSwitchProfile = onSwitchProfile,
            onSimulateJobOffer = { onSimulateJobOffer(0) }
          )
        }
      }
    },
    modifier = modifier.fillMaxSize(),
    containerColor = SurfaceWhite
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        Spacer(modifier = Modifier.height(4.dp))
      }

      // Notification banner if any
      if (statusBanner != null) {
        item {
          Surface(
            color = Navy900,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = statusBanner,
                style = MaterialTheme.typography.bodySmall.copy(color = SurfaceWhite),
                modifier = Modifier.weight(1f)
              )
              IconButton(onClick = onDismissBanner, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = SurfaceWhite, modifier = Modifier.size(16.dp))
              }
            }
          }
        }
      }

      // B. CLEAR NOTIFICATION AND LOCATION PERMISSION STATES
      item {
        if (!isLocationPermissionGranted || !isGpsEnabled) {
          Surface(
            color = WarningAmberLight,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Filled.LocationOff,
                contentDescription = "Permission Alert",
                tint = WarningAmberDark,
                modifier = Modifier.size(24.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Location & GPS Required",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Navy900)
                )
                Text(
                  text = if (!isLocationPermissionGranted) "Grant location permission to receive nearby breakdown dispatches." else "Turn ON device GPS provider for dispatch routing.",
                  style = MaterialTheme.typography.bodySmall.copy(color = Navy700, fontSize = 11.sp)
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Button(
                onClick = {
                  permissionLauncher.launch(
                    arrayOf(
                      Manifest.permission.ACCESS_FINE_LOCATION,
                      Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                  )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(32.dp).testTag("grant_permissions_btn")
              ) {
                Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        } else {
          Surface(
            color = SurfaceVariantSlate,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = OnlineGreen, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "GPS verified • Live dispatch location active",
                style = MaterialTheme.typography.bodySmall.copy(color = Navy700, fontSize = 11.sp)
              )
            }
          }
        }
      }

      // ACTIVE JOB ALERT BANNER
      if (activeJob != null) {
        item {
          Surface(
            color = EmergencyRedSoft,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, EmergencyRed),
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { onOpenActiveJob() }
              .testTag("dashboard_active_job_card")
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier
                      .size(10.dp)
                      .clip(CircleShape)
                      .background(EmergencyRed)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "ACTIVE EMERGENCY IN PROGRESS",
                    style = MaterialTheme.typography.labelSmall.copy(
                      fontWeight = FontWeight.Black,
                      color = EmergencyRedDark,
                      letterSpacing = 1.sp
                    )
                  )
                }

                StatusBadge(
                  text = activeJob.status.label,
                  color = SurfaceWhite,
                  backgroundColor = EmergencyRedDark
                )
              }

              Spacer(modifier = Modifier.height(10.dp))

              Text(
                text = "${activeJob.vehicleModel} • ${activeJob.problemCategory.label}",
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = Navy900
                )
              )

              Text(
                text = "Breakdown Site: ${activeJob.exactAddress}",
                style = MaterialTheme.typography.bodySmall.copy(
                  color = Navy700,
                  fontSize = 12.sp
                )
              )

              Spacer(modifier = Modifier.height(12.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Customer: ${activeJob.customerDisplayName}",
                  style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Navy900
                  )
                )

                Button(
                  onClick = onOpenActiveJob,
                  colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Text("Resume Job Console", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  Spacer(modifier = Modifier.width(4.dp))
                  Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                }
              }
            }
          }
        }
      }

      // METRIC STAT CARDS (IN ₹ INR)
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          MetricCard(
            title = "Today's Payout",
            value = "₹${"%.0f".format(todayEarningsInr)}",
            subtitle = "${completedHistory.count { !it.isCancelled }} roadside assists",
            icon = Icons.Filled.AccountBalanceWallet,
            accentColor = OnlineGreenDark,
            modifier = Modifier.weight(1f)
          )

          MetricCard(
            title = "Mechanic Score",
            value = "★ ${profile.rating}",
            subtitle = "${profile.activeResponseRatePercent}% fast response",
            icon = Icons.Filled.Star,
            accentColor = WarningAmberDark,
            modifier = Modifier.weight(1f)
          )
        }
      }

      // B. RELEVANT SERVICE CATEGORIES CHIPS
      item {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "YOUR ACTIVE SERVICE SPECIALTIES",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Navy700, letterSpacing = 1.sp)
            )
            TextButton(onClick = onOpenServices) {
              Text("Manage →", color = EmergencyRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(profile.supportedServices.toList()) { s ->
              Surface(
                color = SurfaceVariantSlate,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
              ) {
                Text(
                  text = s.label,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  style = MaterialTheme.typography.bodySmall.copy(color = Navy900, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                )
              }
            }
          }
        }
      }

      // DISPATCH RADAR STATUS
      item {
        if (!isApproved) {
          Surface(
            color = when (profile.registrationStatus) {
              RegistrationStatus.PENDING -> WarningAmberLight
              else -> EmergencyRedLight
            },
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (profile.registrationStatus == RegistrationStatus.PENDING) WarningAmber.copy(alpha = 0.5f) else EmergencyRed.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = if (profile.registrationStatus == RegistrationStatus.PENDING) Icons.Filled.HourglassTop else Icons.Filled.Block,
                contentDescription = null,
                tint = if (profile.registrationStatus == RegistrationStatus.PENDING) WarningAmberDark else EmergencyRedDark,
                modifier = Modifier.size(32.dp)
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Status: ${profile.registrationStatus.label}",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Navy900)
                )
                Text(
                  text = profile.statusReason,
                  style = MaterialTheme.typography.bodySmall.copy(color = Navy700)
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                  onClick = onOpenVerification,
                  contentPadding = PaddingValues(0.dp)
                ) {
                  Text("View Verification Details →", color = Navy900, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        } else if (profile.isOnline && activeJob == null) {
          RadarScanningCard(
            radiusKm = profile.serviceRadiusKm,
            baseCity = profile.baseCityLocality,
            capability = profile.vehicleCapability,
            onSimulate0 = { onSimulateJobOffer(0) },
            onSimulate1 = { onSimulateJobOffer(1) },
            onSimulate2 = { onSimulateJobOffer(2) }
          )
        } else if (!profile.isOnline && activeJob == null) {
          Surface(
            color = SurfaceVariantSlate,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "You Are Currently Offline",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Navy900)
              )
              Text(
                text = "Toggle the status pill to 'ONLINE' to start receiving emergency dispatches in your radius.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
              )
              Spacer(modifier = Modifier.height(12.dp))
              Button(
                onClick = onToggleOnline,
                colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("dashboard_go_online_btn")
              ) {
                Text("Go Online Now", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      // SECTION HEADER: Today's Jobs & History
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "TODAY'S ROAD ASSISTANCES",
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              color = Navy700,
              letterSpacing = 1.sp
            )
          )

          TextButton(onClick = onOpenHistory) {
            Text("Full History →", color = EmergencyRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }
        }
      }

      if (completedHistory.isEmpty()) {
        item {
          Surface(
            color = SurfaceVariantSlate,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "No roadside assistances completed yet today.",
              modifier = Modifier.padding(20.dp),
              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
              style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
            )
          }
        }
      } else {
        items(completedHistory) { item ->
          CompletedJobItemCard(item = item)
        }
      }

      item {
        Spacer(modifier = Modifier.height(20.dp))
      }
    }
  }
}

@Composable
private fun MetricCard(
  title: String,
  value: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accentColor: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    color = SurfaceWhite,
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
    shadowElevation = 1.dp,
    modifier = modifier
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelSmall.copy(
            color = TextMuted,
            fontWeight = FontWeight.Bold
          )
        )
        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge.copy(
          fontWeight = FontWeight.Black,
          color = Navy900
        )
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall.copy(
          color = TextMuted,
          fontSize = 11.sp
        )
      )
    }
  }
}

@Composable
private fun RadarScanningCard(
  radiusKm: Int,
  baseCity: String,
  capability: VehicleCapability,
  onSimulate0: () -> Unit,
  onSimulate1: () -> Unit,
  onSimulate2: () -> Unit
) {
  val infiniteTransition = rememberInfiniteTransition(label = "radar")
  val pulseSize by infiniteTransition.animateFloat(
    initialValue = 0.8f,
    targetValue = 1.2f,
    animationSpec = infiniteRepeatable(
      animation = tween(1400, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseSize"
  )

  Surface(
    color = Navy900,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(OnlineGreenSoft.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .size((28 * pulseSize).dp)
              .clip(CircleShape)
              .background(OnlineGreen.copy(alpha = 0.6f))
          )
          Icon(Icons.Filled.Sensors, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "ONLINE EMERGENCY RADAR ACTIVE",
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Black,
              color = OnlineGreenLight,
              letterSpacing = 1.sp
            )
          )
          Text(
            text = "Monitoring ${capability.label} dispatches within $radiusKm km of $baseCity",
            style = MaterialTheme.typography.bodySmall.copy(
              color = Navy300,
              fontSize = 11.sp
            )
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(color = Navy700)
      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "Test Dispatch Simulator Incidents:",
        style = MaterialTheme.typography.labelSmall.copy(color = Navy300)
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = onSimulate0,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = SurfaceWhite),
          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
          modifier = Modifier.weight(1f).height(34.dp)
        ) {
          Text("Car Battery", fontSize = 10.sp)
        }
        OutlinedButton(
          onClick = onSimulate1,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = SurfaceWhite),
          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
          modifier = Modifier.weight(1f).height(34.dp)
        ) {
          Text("Bike Puncture", fontSize = 10.sp)
        }
        OutlinedButton(
          onClick = onSimulate2,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = SurfaceWhite),
          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
          modifier = Modifier.weight(1f).height(34.dp)
        ) {
          Text("Swift Fuel", fontSize = 10.sp)
        }
      }
    }
  }
}

@Composable
fun CompletedJobItemCard(
  item: CompletedJobRecord,
  modifier: Modifier = Modifier
) {
  Surface(
    color = SurfaceWhite,
    shape = RoundedCornerShape(10.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "#${item.requestCode}",
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              color = EmergencyRedDark
            )
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = item.completedAtDate,
            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
          )
        }

        if (item.isCancelled) {
          StatusBadge(text = "CANCELLED", color = EmergencyRedDark, backgroundColor = EmergencyRedSoft)
        } else {
          Text(
            text = "+₹${"%.0f".format(item.payoutInr)}",
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.ExtraBold,
              color = OnlineGreenDark
            )
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "${item.vehicleInfo} • ${item.serviceType}",
        style = MaterialTheme.typography.bodyMedium.copy(
          fontWeight = FontWeight.Bold,
          color = Navy900
        )
      )

      Text(
        text = item.locality,
        style = MaterialTheme.typography.bodySmall.copy(
          color = Navy700,
          fontSize = 12.sp
        )
      )

      if (!item.isCancelled) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "★ ${item.rating}",
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              color = WarningAmberDark
            )
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "\"${item.review}\"",
            style = MaterialTheme.typography.bodySmall.copy(
              color = TextMuted,
              fontSize = 11.sp
            ),
            maxLines = 1
          )
        }
      }
    }
  }
}

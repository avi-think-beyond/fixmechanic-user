package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RegistrationStatus
import com.example.ui.theme.*

@Composable
fun MechanicTopBar(
  isOnline: Boolean,
  registrationStatus: RegistrationStatus,
  onToggleOnline: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    color = SurfaceWhite,
    tonalElevation = 2.dp,
    shadowElevation = 2.dp,
    modifier = modifier.fillMaxWidth()
  ) {
    Column {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        // Brand & Title
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(Navy900),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Filled.Build,
              contentDescription = "FixMechanic Logo",
              tint = SurfaceWhite,
              modifier = Modifier.size(22.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "FixMechanic",
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = Navy900,
                  fontSize = 17.sp
                )
              )
              Spacer(modifier = Modifier.width(6.dp))
              Surface(
                color = EmergencyRedLight,
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  text = "INDIA",
                  modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    color = EmergencyRedDark,
                    fontSize = 9.sp
                  )
                )
              }
            }
            Text(
              text = "Roadside Dispatch Partner Console",
              style = MaterialTheme.typography.bodySmall.copy(
                color = TextMuted,
                fontSize = 11.sp
              )
            )
          }
        }

        // Online/Offline Button & Status
        Row(
          verticalAlignment = Alignment.CenterVertically
        ) {
          OnlineStatusPill(
            isOnline = isOnline,
            registrationStatus = registrationStatus,
            onClick = onToggleOnline
          )

          Spacer(modifier = Modifier.width(8.dp))

          IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
              .size(40.dp)
              .testTag("top_bar_settings_button")
          ) {
            Icon(
              imageVector = Icons.Outlined.Settings,
              contentDescription = "Settings and Profile",
              tint = Navy800
            )
          }
        }
      }
      HorizontalDivider(color = BorderLight, thickness = 1.dp)
    }
  }
}

@Composable
fun OnlineStatusPill(
  isOnline: Boolean,
  registrationStatus: RegistrationStatus,
  onClick: () -> Unit
) {
  val isApproved = registrationStatus == RegistrationStatus.APPROVED

  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(900, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseAlpha"
  )

  Surface(
    shape = RoundedCornerShape(20.dp),
    color = when {
      !isApproved -> SurfaceVariantSlate
      isOnline -> OnlineGreenSoft
      else -> SurfaceVariantSlate
    },
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      when {
        !isApproved -> BorderLight
        isOnline -> OnlineGreen.copy(alpha = 0.5f)
        else -> BorderLight
      }
    ),
    modifier = Modifier
      .clip(RoundedCornerShape(20.dp))
      .clickable { onClick() }
      .testTag("online_offline_toggle")
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(10.dp)
          .clip(CircleShape)
          .background(
            when {
              registrationStatus == RegistrationStatus.PENDING -> WarningAmberDark
              registrationStatus == RegistrationStatus.SUSPENDED -> EmergencyRed
              registrationStatus == RegistrationStatus.REJECTED -> EmergencyRed
              isOnline -> OnlineGreen.copy(alpha = pulseAlpha)
              else -> TextMuted
            }
          )
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = when {
          registrationStatus == RegistrationStatus.PENDING -> "PENDING"
          registrationStatus == RegistrationStatus.SUSPENDED -> "SUSPENDED"
          registrationStatus == RegistrationStatus.REJECTED -> "REJECTED"
          isOnline -> "ONLINE"
          else -> "OFFLINE"
        },
        style = MaterialTheme.typography.labelMedium.copy(
          fontWeight = FontWeight.Bold,
          color = when {
            registrationStatus == RegistrationStatus.PENDING -> WarningAmberDark
            registrationStatus == RegistrationStatus.SUSPENDED -> EmergencyRedDark
            registrationStatus == RegistrationStatus.REJECTED -> EmergencyRedDark
            isOnline -> OnlineGreenDark
            else -> Navy700
          },
          fontSize = 11.sp
        )
      )
    }
  }
}

@Composable
fun DemoModeTopBanner(
  isCompetitorModeOn: Boolean,
  onToggleCompetitorMode: (Boolean) -> Unit,
  onSwitchProfile: (String) -> Unit,
  onSimulateJobOffer: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    color = Navy900,
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            color = WarningAmber,
            shape = RoundedCornerShape(4.dp)
          ) {
            Text(
              text = "DEMO SIMULATION MODE",
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Black,
                color = Navy900,
                fontSize = 9.sp
              )
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Testing India Dispatch & Statuses",
            style = MaterialTheme.typography.bodySmall.copy(
              color = SurfaceWhite.copy(alpha = 0.85f),
              fontSize = 11.sp
            )
          )
        }

        Button(
          onClick = onSimulateJobOffer,
          colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier
            .height(28.dp)
            .testTag("simulate_emergency_offer_btn")
        ) {
          Icon(
            imageVector = Icons.Filled.NotificationsActive,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("Simulate Offer", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Personas
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "Persona: ",
            style = MaterialTheme.typography.labelSmall.copy(color = SurfaceWhite.copy(alpha = 0.7f))
          )
          TextButton(
            onClick = { onSwitchProfile("rajesh") },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            modifier = Modifier.height(24.dp)
          ) {
            Text("Rajesh (Approved)", fontSize = 10.sp, color = OnlineGreenLight)
          }
          Text("|", color = SurfaceWhite.copy(alpha = 0.4f), fontSize = 10.sp)
          TextButton(
            onClick = { onSwitchProfile("amit") },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            modifier = Modifier.height(24.dp)
          ) {
            Text("Amit (Pending)", fontSize = 10.sp, color = WarningAmberLight)
          }
          Text("|", color = SurfaceWhite.copy(alpha = 0.4f), fontSize = 10.sp)
          TextButton(
            onClick = { onSwitchProfile("suresh") },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            modifier = Modifier.height(24.dp)
          ) {
            Text("Suresh (Suspended)", fontSize = 10.sp, color = EmergencyRedLight)
          }
        }

        // Competitor Race Condition Toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "Competitor Win:",
            style = MaterialTheme.typography.labelSmall.copy(
              color = SurfaceWhite.copy(alpha = 0.8f),
              fontSize = 10.sp
            )
          )
          Spacer(modifier = Modifier.width(4.dp))
          Switch(
            checked = isCompetitorModeOn,
            onCheckedChange = onToggleCompetitorMode,
            modifier = Modifier
              .height(20.dp)
              .testTag("competitor_steal_switch"),
            colors = SwitchDefaults.colors(
              checkedThumbColor = SurfaceWhite,
              checkedTrackColor = EmergencyRed
            )
          )
        }
      }
    }
  }
}

@Composable
fun StatusBadge(
  text: String,
  color: Color,
  backgroundColor: Color,
  icon: ImageVector? = null,
  modifier: Modifier = Modifier
) {
  Surface(
    color = backgroundColor,
    shape = RoundedCornerShape(6.dp),
    modifier = modifier
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = color,
          modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
      }
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
          color = color,
          fontWeight = FontWeight.Bold,
          fontSize = 11.sp
        )
      )
    }
  }
}

@Composable
fun CountdownTimerBar(
  remainingSeconds: Int,
  totalSeconds: Int = 30,
  modifier: Modifier = Modifier
) {
  val progress = (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
  val barColor = when {
    remainingSeconds <= 10 -> EmergencyRed
    remainingSeconds <= 20 -> WarningAmber
    else -> OnlineGreen
  }

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Filled.Timer,
          contentDescription = null,
          tint = barColor,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "Server Offer Expiry Window",
          style = MaterialTheme.typography.labelSmall.copy(
            color = Navy700,
            fontWeight = FontWeight.Medium
          )
        )
      }
      Text(
        text = "${remainingSeconds}s remaining",
        style = MaterialTheme.typography.labelMedium.copy(
          color = barColor,
          fontWeight = FontWeight.ExtraBold,
          fontSize = 13.sp
        )
      )
    }
    Spacer(modifier = Modifier.height(6.dp))
    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier
        .fillMaxWidth()
        .height(8.dp)
        .clip(RoundedCornerShape(4.dp)),
      color = barColor,
      trackColor = SurfaceVariantSlate
    )
  }
}

@Composable
fun SafetyAlertBanner(
  notes: String,
  modifier: Modifier = Modifier
) {
  Surface(
    color = WarningAmberLight,
    shape = RoundedCornerShape(8.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f)),
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.Top
    ) {
      Icon(
        imageVector = Icons.Filled.WarningAmber,
        contentDescription = "Safety Alert",
        tint = WarningAmberDark,
        modifier = Modifier.size(20.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Column {
        Text(
          text = "ROAD SAFETY PROTOCOL (INDIAN HIGHWAYS)",
          style = MaterialTheme.typography.labelSmall.copy(
            color = WarningAmberDark,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
          )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = notes,
          style = MaterialTheme.typography.bodySmall.copy(
            color = Navy900,
            lineHeight = 16.sp
          )
        )
      }
    }
  }
}

@Composable
fun MaskedPrivacyCard(modifier: Modifier = Modifier) {
  Surface(
    color = SurfaceVariantSlate,
    shape = RoundedCornerShape(8.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = Icons.Filled.Lock,
        contentDescription = "Privacy Shield",
        tint = Navy700,
        modifier = Modifier.size(16.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "Customer phone number & WhatsApp identifier are completely hidden. Minimum required assessment info provided before server-confirmed assignment.",
        style = MaterialTheme.typography.bodySmall.copy(
          color = Navy700,
          fontSize = 11.sp,
          lineHeight = 15.sp
        )
      )
    }
  }
}

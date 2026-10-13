package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.*
import com.example.ui.components.CountdownTimerBar
import com.example.ui.components.MaskedPrivacyCard
import com.example.ui.components.SafetyAlertBanner
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

// ----------------------------------------------------
// INCOMING EMERGENCY JOB OFFER DIALOG
// ----------------------------------------------------
@Composable
fun IncomingJobOfferDialog(
  offer: JobOffer,
  isAccepting: Boolean,
  onAccept: (String) -> Unit,
  onReject: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Dialog(
    onDismissRequest = { /* Never dismiss accidentally */ },
    properties = DialogProperties(
      dismissOnBackPress = false,
      dismissOnClickOutside = false,
      usePlatformDefaultWidth = false
    )
  ) {
    Surface(
      color = SurfaceWhite,
      shape = RoundedCornerShape(16.dp),
      shadowElevation = 8.dp,
      modifier = modifier
        .fillMaxWidth(0.94f)
        .padding(vertical = 16.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        // Red Emergency Header Banner
        Surface(
          color = EmergencyRed,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(34.dp)
                  .clip(CircleShape)
                  .background(SurfaceWhite),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.Warning,
                  contentDescription = "Urgent Alert",
                  tint = EmergencyRed,
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "EMERGENCY BREAKDOWN OFFER",
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    color = SurfaceWhite,
                    letterSpacing = 1.sp
                  )
                )
                Text(
                  text = "Request ID: #${offer.requestCode}",
                  style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = SurfaceWhite.copy(alpha = 0.9f)
                  )
                )
              }
            }

            Surface(
              color = SurfaceWhite.copy(alpha = 0.2f),
              shape = RoundedCornerShape(4.dp)
            ) {
              Text(
                text = "${offer.approxDistanceKm} km away",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = SurfaceWhite
                )
              )
            }
          }
        }

        Column(modifier = Modifier.padding(16.dp)) {
          // Server countdown progress bar
          CountdownTimerBar(
            remainingSeconds = offer.remainingCountdownSeconds,
            totalSeconds = offer.totalCountdownSeconds
          )

          Spacer(modifier = Modifier.height(14.dp))

          // 1. Vehicle Type & Problem Category
          Surface(
            color = SurfaceVariantSlate,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = offer.vehicleModel,
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Navy900
                  )
                )
                StatusBadge(
                  text = offer.vehicleType.label,
                  color = Navy900,
                  backgroundColor = BorderLight
                )
              }

              Spacer(modifier = Modifier.height(8.dp))

              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Filled.Handyman,
                  contentDescription = null,
                  tint = EmergencyRed,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = offer.problemCategory.label,
                  style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = EmergencyRedDark
                  )
                )
              }

              Spacer(modifier = Modifier.height(4.dp))

              Text(
                text = offer.problemDescription,
                style = MaterialTheme.typography.bodySmall.copy(
                  color = Navy700,
                  lineHeight = 18.sp
                )
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // 2. Customer-Provided Notes
          if (offer.customerNotes.isNotBlank()) {
            Surface(
              color = Navy50,
              shape = RoundedCornerShape(8.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text(
                  text = "CUSTOMER NOTES:",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextMuted)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "\"${offer.customerNotes}\"",
                  style = MaterialTheme.typography.bodySmall.copy(color = Navy900, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                )
              }
            }
            Spacer(modifier = Modifier.height(12.dp))
          }

          // 3. Approximate Locality & Distance
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Filled.Place,
              contentDescription = null,
              tint = Navy800,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
              Text(
                text = "Approximate Location Area",
                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
              )
              Text(
                text = offer.locality,
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = Navy900
                )
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // 4. Estimated Compensation in ₹ (INR)
          Surface(
            color = OnlineGreenSoft,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, OnlineGreen.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "ESTIMATED COMPENSATION",
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = OnlineGreenDark,
                    fontSize = 10.sp
                  )
                )
                Text(
                  text = "Base dispatch + emergency repair fee",
                  style = MaterialTheme.typography.bodySmall.copy(
                    color = Navy700,
                    fontSize = 11.sp
                  )
                )
              }

              Text(
                text = "₹${offer.estimatedPayoutMinInr.toInt()} - ₹${offer.estimatedPayoutMaxInr.toInt()}",
                style = MaterialTheme.typography.titleLarge.copy(
                  fontWeight = FontWeight.Black,
                  color = OnlineGreenDark
                )
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Safety Notes
          SafetyAlertBanner(notes = offer.safetyNotes)

          Spacer(modifier = Modifier.height(12.dp))

          // Masked Privacy Notice
          MaskedPrivacyCard()

          Spacer(modifier = Modifier.height(20.dp))

          // Accept / Reject Actions with Loading State
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedButton(
              onClick = { onReject(offer.id) },
              modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .testTag("reject_offer_btn"),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Navy700)
            ) {
              Text("Decline", fontWeight = FontWeight.Bold)
            }

            Button(
              onClick = { onAccept(offer.id) },
              enabled = !isAccepting,
              modifier = Modifier
                .weight(1.5f)
                .height(50.dp)
                .testTag("accept_offer_btn"),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Navy900)
            ) {
              if (isAccepting) {
                CircularProgressIndicator(
                  color = SurfaceWhite,
                  modifier = Modifier.size(20.dp),
                  strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Locking on Server...", fontSize = 12.sp)
              } else {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Accept Offer", fontWeight = FontWeight.Bold, fontSize = 15.sp)
              }
            }
          }
        }
      }
    }
  }
}

// ----------------------------------------------------
// ASSIGNMENT CONFIRMATION / RACE-CONDITION RESULT
// ----------------------------------------------------
@Composable
fun AssignmentResultDialog(
  result: JobAcceptanceResult,
  onDismiss: () -> Unit,
  onViewJob: () -> Unit,
  modifier: Modifier = Modifier
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(dismissOnClickOutside = false)
  ) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = SurfaceWhite,
      shadowElevation = 8.dp,
      modifier = modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        when (result) {
          is JobAcceptanceResult.Assigned -> {
            Box(
              modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(OnlineGreenSoft),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Assigned",
                tint = OnlineGreen,
                modifier = Modifier.size(40.dp)
              )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
              text = "Request Assigned to You!",
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Navy900
              )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "The server has confirmed you as the first valid acceptance. Breakdown site address & turn-by-turn navigation unlocked.",
              textAlign = TextAlign.Center,
              style = MaterialTheme.typography.bodySmall.copy(
                color = Navy700,
                lineHeight = 18.sp
              )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
              onClick = onViewJob,
              colors = ButtonDefaults.buttonColors(containerColor = Navy900),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("view_assigned_job_btn")
            ) {
              Text("Open Active Job & Navigation", fontWeight = FontWeight.Bold)
            }
          }

          is JobAcceptanceResult.TakenByOther -> {
            // CRITICAL ASSIGNMENT RULE: Must show "This request has already been assigned"
            Box(
              modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(EmergencyRedLight),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.Cancel,
                contentDescription = "Assigned to another",
                tint = EmergencyRed,
                modifier = Modifier.size(40.dp)
              )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
              text = "Assignment Conflict",
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = EmergencyRedDark
              )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
              color = EmergencyRedSoft,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = result.message, // "This request has already been assigned"
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = EmergencyRedDark
                )
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "Another nearby verified mechanic won the atomic server lock. You remain online in the dispatch queue for the next emergency request.",
              textAlign = TextAlign.Center,
              style = MaterialTheme.typography.bodySmall.copy(
                color = TextMuted,
                lineHeight = 16.sp
              )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
              onClick = onDismiss,
              colors = ButtonDefaults.buttonColors(containerColor = Navy900),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("dismiss_taken_dialog_btn")
            ) {
              Text("Return to Dispatch Radar", fontWeight = FontWeight.Bold)
            }
          }

          is JobAcceptanceResult.Expired -> {
            Text(
              text = "Offer Expired",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Navy900)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = result.message,
              textAlign = TextAlign.Center,
              style = MaterialTheme.typography.bodySmall.copy(color = Navy700)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
              onClick = onDismiss,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("Close")
            }
          }

          is JobAcceptanceResult.NotEligible -> {
            Text(
              text = "Ineligible to Accept",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EmergencyRedDark)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = result.reason,
              textAlign = TextAlign.Center,
              style = MaterialTheme.typography.bodySmall.copy(color = Navy700)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
              onClick = onDismiss,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("Check Account Status")
            }
          }

          is JobAcceptanceResult.Error -> {
            Text(
              text = "Server Error",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EmergencyRed)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = result.errorMessage,
              textAlign = TextAlign.Center,
              style = MaterialTheme.typography.bodySmall.copy(color = Navy700)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
              onClick = onDismiss,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("Dismiss")
            }
          }
        }
      }
    }
  }
}

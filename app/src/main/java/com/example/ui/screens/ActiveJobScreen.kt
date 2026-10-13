package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.example.ui.components.SafetyAlertBanner
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

@Composable
fun ActiveJobScreen(
  activeJob: ActiveJob,
  onAdvanceStatus: (JobStatus) -> Unit,
  onCancelJob: (String) -> Unit,
  onOpenChat: () -> Unit,
  onOpenQuote: () -> Unit,
  onOpenCompletion: () -> Unit,
  onLaunchNavigation: (Context) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scrollState = rememberScrollState()
  var showCancelDialog by remember { mutableStateOf(false) }
  var cancelReason by remember { mutableStateOf("Customer vehicle already towed / unreachable") }

  Scaffold(
    topBar = {
      Surface(
        color = SurfaceWhite,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = onNavigateBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Navy900)
              }
              Spacer(modifier = Modifier.width(4.dp))
              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = "Request #${activeJob.requestCode}",
                    style = MaterialTheme.typography.titleMedium.copy(
                      fontWeight = FontWeight.Bold,
                      color = Navy900
                    )
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  StatusBadge(
                    text = activeJob.status.label,
                    color = SurfaceWhite,
                    backgroundColor = EmergencyRed
                  )
                }
                Text(
                  text = "Assigned Roadside Console",
                  style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
              }
            }

            IconButton(
              onClick = onOpenChat,
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Navy50)
                .testTag("active_job_chat_shortcut_btn")
            ) {
              Icon(
                imageVector = Icons.Filled.Chat,
                contentDescription = "In-App Chat",
                tint = Navy900
              )
            }
          }
          HorizontalDivider(color = BorderLight)
        }
      }
    },
    bottomBar = {
      // Primary Action Bar strictly controlled by backend status
      Surface(
        color = SurfaceWhite,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          when (activeJob.status) {
            JobStatus.ACCEPTED -> {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                  onClick = { showCancelDialog = true },
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRedDark),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.height(52.dp)
                ) {
                  Text("Cancel", fontWeight = FontWeight.Bold)
                }

                Button(
                  onClick = { onAdvanceStatus(JobStatus.EN_ROUTE) },
                  colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("action_en_route_btn")
                ) {
                  Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Start Navigation (En Route)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
              }
            }

            JobStatus.EN_ROUTE -> {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                  onClick = { showCancelDialog = true },
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRedDark),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.height(52.dp)
                ) {
                  Text("Cancel", fontWeight = FontWeight.Bold)
                }

                Button(
                  onClick = { onAdvanceStatus(JobStatus.ARRIVED) },
                  colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("action_arrived_btn")
                ) {
                  Icon(Icons.Filled.PinDrop, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Confirm Arrived on Site", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
              }
            }

            JobStatus.ARRIVED -> {
              Button(
                onClick = { onAdvanceStatus(JobStatus.IN_PROGRESS) },
                colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(52.dp)
                  .testTag("action_in_progress_btn")
              ) {
                Icon(Icons.Filled.Handyman, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Begin Diagnostics & Repair", fontWeight = FontWeight.Bold, fontSize = 15.sp)
              }
            }

            JobStatus.IN_PROGRESS -> {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                OutlinedButton(
                  onClick = onOpenQuote,
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("action_submit_quote_btn")
                ) {
                  Text("Add Quote (₹)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                  onClick = onOpenCompletion,
                  colors = ButtonDefaults.buttonColors(containerColor = OnlineGreenDark),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .weight(1.3f)
                    .height(52.dp)
                    .testTag("action_ready_completion_btn")
                ) {
                  Text("Work Done → Sign-Off", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
              }
            }

            JobStatus.COMPLETED -> {
              OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth().height(48.dp)
              ) {
                Text("Job Closed • Return to Dashboard", fontWeight = FontWeight.Bold)
              }
            }

            JobStatus.CANCELLED -> {
              OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth().height(48.dp)
              ) {
                Text("Job Cancelled • Return", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    },
    modifier = modifier.fillMaxSize(),
    containerColor = SurfaceWhite
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(scrollState)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. STATE MACHINE STEPPER
      JobStatusStepper(currentStatus = activeJob.status)

      // 2. CUSTOMER & REVEALED CONTACT (MASKED)
      Surface(
        color = SurfaceVariantSlate,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "CUSTOMER DETAILS (MASKED)",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = TextMuted,
                  letterSpacing = 1.sp
                )
              )
              Text(
                text = activeJob.customerDisplayName,
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = Navy900
                )
              )
            }

            Button(
              onClick = onOpenChat,
              colors = ButtonDefaults.buttonColors(containerColor = Navy900),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              shape = RoundedCornerShape(8.dp)
            ) {
              Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Private Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          HorizontalDivider(color = BorderLight)
          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = "Customer Standing Location:",
            style = MaterialTheme.typography.labelSmall.copy(color = Navy700, fontWeight = FontWeight.Bold)
          )
          Text(
            text = activeJob.customerSafetyStatus,
            style = MaterialTheme.typography.bodySmall.copy(color = Navy900, lineHeight = 16.sp)
          )

          if (activeJob.customerNotes.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Notes from Customer: \"${activeJob.customerNotes}\"",
              style = MaterialTheme.typography.bodySmall.copy(color = Navy700, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            )
          }
        }
      }

      // 3. NAVIGATION & EXACT ADDRESS (Revealed only for assigned job)
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
                text = "DESTINATION & GPS NAVIGATION",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = OnlineGreenLight,
                  letterSpacing = 1.sp
                )
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = activeJob.exactAddress,
                style = MaterialTheme.typography.titleSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = SurfaceWhite,
                  lineHeight = 20.sp
                )
              )
            }

            Surface(
              color = EmergencyRed,
              shape = RoundedCornerShape(8.dp)
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Text(
                  text = "ETA",
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = SurfaceWhite,
                    fontSize = 9.sp
                  )
                )
                Text(
                  text = "${activeJob.currentEtaMinutes}m",
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = SurfaceWhite
                  )
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          Button(
            onClick = { onLaunchNavigation(context) },
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite, contentColor = Navy900),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("launch_external_navigation_btn")
          ) {
            Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Google Maps Turn-by-Turn", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          }

          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Direct destination navigation via external Google Maps intent. (Live background mechanic GPS tracking telemetry will be hooked when production location socket is connected).",
            style = MaterialTheme.typography.bodySmall.copy(color = Navy300, fontSize = 10.sp)
          )
        }
      }

      // 4. VEHICLE & BREAKDOWN ISSUE
      Surface(
        color = SurfaceWhite,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "VEHICLE & INCIDENT DIAGNOSTIC",
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              color = TextMuted,
              letterSpacing = 1.sp
            )
          )

          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = activeJob.vehicleModel,
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Navy900
              )
            )
            StatusBadge(
              text = activeJob.vehicleType.label,
              color = Navy900,
              backgroundColor = SurfaceVariantSlate
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Handyman, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = activeJob.problemCategory.label,
              style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = EmergencyRedDark
              )
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = activeJob.problemDescription,
            style = MaterialTheme.typography.bodySmall.copy(
              color = Navy700,
              lineHeight = 18.sp
            )
          )
        }
      }

      // 5. SAFETY ALERT BANNER
      SafetyAlertBanner(
        notes = "Follow Indian Motor Vehicles Road Safety: Wear your high-visibility vest. Set up collapsible hazard cones 15-20 metres behind the vehicle. Be alert for fast-moving traffic on the right shoulder."
      )

      // 6. QUOTE PREVIEW IF SUBMITTED
      if (activeJob.quote != null) {
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
                text = "APPROVED IN-APP QUOTE",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = OnlineGreenDark
                )
              )
              Text(
                text = "${activeJob.quote.items.size} itemized parts & extra labor authorized",
                style = MaterialTheme.typography.bodySmall.copy(color = Navy700)
              )
            }
            Text(
              text = "₹${"%.0f".format(activeJob.quote.totalCostInr)}",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Black,
                color = OnlineGreenDark
              )
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))
    }
  }

  // CANCEL CONFIRMATION DIALOG
  if (showCancelDialog) {
    AlertDialog(
      onDismissRequest = { showCancelDialog = false },
      title = { Text("Cancel Emergency Dispatch?", fontWeight = FontWeight.Bold) },
      text = {
        Column {
          Text("Cancelling an accepted emergency request is audited by FixMechanic safety operations. Please select reason:")
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = cancelReason,
            onValueChange = { cancelReason = it },
            label = { Text("Cancellation Reason") },
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showCancelDialog = false
            onCancelJob(cancelReason)
          },
          colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
        ) {
          Text("Confirm Cancel")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCancelDialog = false }) {
          Text("Keep Job")
        }
      }
    )
  }
}

@Composable
fun JobStatusStepper(currentStatus: JobStatus) {
  val steps = listOf(
    JobStatus.ACCEPTED to "Accepted",
    JobStatus.EN_ROUTE to "En Route",
    JobStatus.ARRIVED to "Arrived",
    JobStatus.IN_PROGRESS to "In Progress",
    JobStatus.COMPLETED to "Completed"
  )

  val currentIndex = steps.indexOfFirst { it.first == currentStatus }.let { if (it < 0) 4 else it }

  Surface(
    color = SurfaceWhite,
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(
        text = "DISPATCH STATUS (BACKEND VALIDATED)",
        style = MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.Bold,
          color = TextMuted,
          letterSpacing = 1.sp
        )
      )

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        steps.forEachIndexed { index, (status, label) ->
          val isPassed = index <= currentIndex
          val isCurrent = index == currentIndex

          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
          ) {
            Box(
              modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                  when {
                    isCurrent -> EmergencyRed
                    isPassed -> Navy900
                    else -> BorderLight
                  }
                ),
              contentAlignment = Alignment.Center
            ) {
              if (isPassed && !isCurrent) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(14.dp))
              } else {
                Text(
                  text = "${index + 1}",
                  style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isPassed) SurfaceWhite else TextMuted,
                    fontWeight = FontWeight.Bold
                  )
                )
              }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
              text = label,
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) EmergencyRedDark else Navy700
              )
            )
          }
        }
      }
    }
  }
}

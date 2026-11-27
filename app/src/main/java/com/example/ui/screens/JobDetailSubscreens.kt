package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
// 14. QUOTE SUBMISSION SCREEN (INR ₹)
// ----------------------------------------------------
@Composable
fun QuoteSubmissionScreen(
  activeJob: ActiveJob,
  onSubmitQuote: (JobQuote) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var item1Desc by remember { mutableStateOf("New Heavy-Duty Inner Tube / Patch Kit") }
  var item1Amount by remember { mutableStateOf("350") }
  var item2Desc by remember { mutableStateOf("Valve Stem Replacement & Nitrogen Air") }
  var item2Amount by remember { mutableStateOf("150") }

  var laborCost by remember { mutableStateOf("200") }
  var notes by remember { mutableStateOf("Customer agreed to OEM equivalent tube and puncture patch on site.") }

  val partsTotal = (item1Amount.toDoubleOrNull() ?: 0.0) + (item2Amount.toDoubleOrNull() ?: 0.0)
  val laborTotal = laborCost.toDoubleOrNull() ?: 0.0
  val grandTotal = partsTotal + laborTotal

  val scrollState = rememberScrollState()

  Scaffold(
    topBar = {
      Surface(color = SurfaceWhite, shadowElevation = 2.dp) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Navy900)
          }
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = "Submit In-App Parts & Labor Quote",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Navy900)
            )
            Text(
              text = "Request #${activeJob.requestCode} • ${activeJob.vehicleModel}",
              style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
            )
          }
        }
      }
    },
    bottomBar = {
      Surface(color = SurfaceWhite, shadowElevation = 4.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Total Quote Amount:",
              style = MaterialTheme.typography.titleSmall.copy(color = Navy700)
            )
            Text(
              text = "₹${"%.0f".format(grandTotal)}",
              style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, color = OnlineGreenDark)
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          Button(
            onClick = {
              val items = listOfNotNull(
                QuoteItem("qi_1", item1Desc, item1Amount.toDoubleOrNull() ?: 0.0),
                if (item2Desc.isNotBlank()) QuoteItem("qi_2", item2Desc, item2Amount.toDoubleOrNull() ?: 0.0) else null
              )
              val quote = JobQuote(
                id = "quote_" + System.currentTimeMillis(),
                jobId = activeJob.id,
                items = items,
                laborCostInr = laborTotal,
                partsCostInr = partsTotal,
                notes = notes,
                status = QuoteStatus.SUBMITTED_TO_CUSTOMER
              )
              onSubmitQuote(quote)
              onNavigateBack()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Navy900),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("send_quote_to_customer_btn")
          ) {
            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Send Quote for Customer In-App Approval", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          }
        }
      }
    },
    containerColor = SurfaceWhite,
    modifier = modifier.fillMaxSize()
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(scrollState)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Surface(
        color = SurfaceVariantSlate,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = OnlineGreenDark, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "Customer will authorize quote directly in their FixMechanic app before you perform chargeable replacement work.",
            style = MaterialTheme.typography.bodySmall.copy(color = Navy700, fontSize = 12.sp)
          )
        }
      }

      Text(
        text = "ITEMIZED PARTS & HARDWARE",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Navy700, letterSpacing = 1.sp)
      )

      OutlinedTextField(
        value = item1Desc,
        onValueChange = { item1Desc = it },
        label = { Text("Part Item 1 Description") },
        modifier = Modifier.fillMaxWidth()
      )

      OutlinedTextField(
        value = item1Amount,
        onValueChange = { item1Amount = it },
        label = { Text("Part 1 Price (₹)") },
        modifier = Modifier.fillMaxWidth()
      )

      OutlinedTextField(
        value = item2Desc,
        onValueChange = { item2Desc = it },
        label = { Text("Part Item 2 Description (Optional)") },
        modifier = Modifier.fillMaxWidth()
      )

      OutlinedTextField(
        value = item2Amount,
        onValueChange = { item2Amount = it },
        label = { Text("Part 2 Price (₹)") },
        modifier = Modifier.fillMaxWidth()
      )

      Text(
        text = "SPECIALIZED ROADSIDE LABOR",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Navy700, letterSpacing = 1.sp)
      )

      OutlinedTextField(
        value = laborCost,
        onValueChange = { laborCost = it },
        label = { Text("Additional Labor / Diagnostic Fee (₹)") },
        modifier = Modifier.fillMaxWidth()
      )

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("Diagnostic Notes for Customer") },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}

// ----------------------------------------------------
// 15. PRIVATE IN-APP CHAT SCREEN
// ----------------------------------------------------
@Composable
fun PrivateChatScreen(
  activeJob: ActiveJob,
  messages: List<ChatMessage>,
  cannedResponses: List<String>,
  isChatAllowed: Boolean,
  onSendMessage: (String) -> Unit,
  onRetryMessage: (String) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var messageInput by remember { mutableStateOf("") }

  Scaffold(
    topBar = {
      Surface(color = SurfaceWhite, shadowElevation = 2.dp) {
        Column {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(onClick = onNavigateBack) {
              Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Navy900)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = activeJob.customerDisplayName,
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Navy900)
                )
                Spacer(modifier = Modifier.width(6.dp))
                StatusBadge(
                  text = "MASKED",
                  color = Navy900,
                  backgroundColor = BorderLight
                )
              }
              Text(
                text = "Private Secure Relay • Customer personal phone number is hidden",
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
              )
            }
          }
          HorizontalDivider(color = BorderLight)
        }
      }
    },
    bottomBar = {
      Surface(color = SurfaceWhite, shadowElevation = 6.dp) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
          if (!isChatAllowed) {
            Surface(
              color = SurfaceVariantSlate,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
              Text(
                text = "Direct messaging session closed. Job is in ${activeJob.status.label} state.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
              )
            }
          } else {
            // Quick Canned Responses
            LazyColumn(modifier = Modifier.heightIn(max = 70.dp).fillMaxWidth()) {
              item {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  cannedResponses.take(3).forEach { canned ->
                    Surface(
                      color = Navy50,
                      shape = RoundedCornerShape(16.dp),
                      border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                      modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSendMessage(canned) }
                    ) {
                      Text(
                        text = canned,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.bodySmall.copy(color = Navy900, fontSize = 11.sp)
                      )
                    }
                  }
                }
              }
            }

            // Input Row
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = messageInput,
                onValueChange = { messageInput = it },
                placeholder = { Text("Message ${activeJob.customerDisplayName}...") },
                modifier = Modifier
                  .weight(1f)
                  .testTag("chat_message_input"),
                shape = RoundedCornerShape(20.dp),
                maxLines = 3
              )

              Spacer(modifier = Modifier.width(8.dp))

              IconButton(
                onClick = {
                  if (messageInput.isNotBlank()) {
                    onSendMessage(messageInput.trim())
                    messageInput = ""
                  }
                },
                enabled = messageInput.isNotBlank(),
                modifier = Modifier
                  .size(48.dp)
                  .clip(CircleShape)
                  .background(if (messageInput.isNotBlank()) Navy900 else BorderLight)
                  .testTag("chat_send_button")
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Send,
                  contentDescription = "Send",
                  tint = if (messageInput.isNotBlank()) SurfaceWhite else TextMuted
                )
              }
            }
          }
        }
      }
    },
    containerColor = SurfaceWhite,
    modifier = modifier.fillMaxSize()
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      item {
        Surface(
          color = SurfaceVariantSlate,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Navy700, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Personal phone numbers are strictly hidden. Do not share external links or QR codes.",
              style = MaterialTheme.typography.bodySmall.copy(color = Navy700, fontSize = 11.sp)
            )
          }
        }
      }

      items(messages) { msg ->
        val isMechanic = msg.senderRole == SenderRole.MECHANIC
        val isSystem = msg.senderRole == SenderRole.SYSTEM_DISPATCH

        if (isSystem) {
          Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
              color = WarningAmberLight,
              shape = RoundedCornerShape(6.dp)
            ) {
              Text(
                text = msg.text,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                  color = WarningAmberDark,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              )
            }
          }
        } else {
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isMechanic) Alignment.End else Alignment.Start
          ) {
            Text(
              text = msg.senderName,
              style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Surface(
              color = if (isMechanic) Navy900 else SurfaceVariantSlate,
              shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isMechanic) 12.dp else 2.dp,
                bottomEnd = if (isMechanic) 2.dp else 12.dp
              ),
              modifier = Modifier.widthIn(max = 280.dp)
            ) {
              Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(
                  text = msg.text,
                  style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isMechanic) SurfaceWhite else Navy900,
                    fontSize = 14.sp
                  )
                )

                if (isMechanic) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    when (msg.status) {
                      MessageDeliveryStatus.DELIVERED -> {
                        Icon(Icons.Filled.DoneAll, contentDescription = "Delivered", tint = OnlineGreenLight, modifier = Modifier.size(12.dp))
                      }
                      MessageDeliveryStatus.SENT -> {
                        Icon(Icons.Filled.Done, contentDescription = "Sent", tint = SurfaceWhite.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                      }
                      MessageDeliveryStatus.FAILED -> {
                        Row(modifier = Modifier.clickable { onRetryMessage(msg.id) }) {
                          Icon(Icons.Filled.ErrorOutline, contentDescription = "Failed", tint = EmergencyRedLight, modifier = Modifier.size(12.dp))
                          Spacer(modifier = Modifier.width(2.dp))
                          Text("Retry", color = EmergencyRedLight, fontSize = 10.sp)
                        }
                      }
                      MessageDeliveryStatus.SENDING -> {
                        CircularProgressIndicator(color = SurfaceWhite, modifier = Modifier.size(10.dp), strokeWidth = 1.dp)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

// ----------------------------------------------------
// 16. JOB COMPLETION & SIGN-OFF SCREEN
// ----------------------------------------------------
@Composable
fun JobCompletionScreen(
  activeJob: ActiveJob,
  onFinalizeCompletion: (CompletionReport) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var diagnosticFindings by remember { mutableStateOf("Inspected vehicle issue. 12V Battery / tyre pressure restored to operating specifications.") }
  var workPerformed by remember { mutableStateOf("Emergency roadside repair completed. Vehicle safely restarted and confirmed roadworthy.") }
  var partsReplaced by remember { mutableStateOf("Patch repair, terminal dielectric coat.") }
  var laborHours by remember { mutableStateOf("0.5") }
  var signoffToken by remember { mutableStateOf("CUST-ACK-9102-OK") }

  val totalPayout = activeJob.estimatedPayoutInr + (activeJob.quote?.totalCostInr ?: 0.0)
  val scrollState = rememberScrollState()

  Scaffold(
    topBar = {
      Surface(color = SurfaceWhite, shadowElevation = 2.dp) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Navy900)
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Finalize Roadside Emergency Service",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Navy900)
          )
        }
      }
    },
    bottomBar = {
      Surface(color = SurfaceWhite, shadowElevation = 6.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Total Payout to Mechanic:", style = MaterialTheme.typography.titleSmall.copy(color = Navy700))
            Text(
              text = "₹${"%.0f".format(totalPayout)}",
              style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, color = OnlineGreenDark)
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          Button(
            onClick = {
              val report = CompletionReport(
                workPerformedSummary = buildString {
                  append(workPerformed.trim())
                  if (diagnosticFindings.isNotBlank()) append(" | Findings: ${diagnosticFindings.trim()}")
                  if (partsReplaced.isNotBlank()) append(" | Parts: ${partsReplaced.trim()}")
                },
                safetyChecklistPassed = true,
                customerSignOffToken = signoffToken,
                totalLaborHours = laborHours.toDoubleOrNull() ?: 0.5,
                finalPayoutAmountInr = totalPayout,
                customerRating = 5.0f,
                customerReview = "Prompt response. Mechanic resolved issue on roadside safely!"
              )
              onFinalizeCompletion(report)
            },
            colors = ButtonDefaults.buttonColors(containerColor = OnlineGreenDark),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("submit_job_completion_btn")
          ) {
            Icon(Icons.Filled.TaskAlt, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Complete Job & Credit Payout", fontWeight = FontWeight.Bold, fontSize = 15.sp)
          }
        }
      }
    },
    containerColor = SurfaceWhite,
    modifier = modifier.fillMaxSize()
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(scrollState)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Text(
        text = "SERVICE LOG & DIAGNOSTIC FINDINGS",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Navy700, letterSpacing = 1.sp)
      )

      OutlinedTextField(
        value = diagnosticFindings,
        onValueChange = { diagnosticFindings = it },
        label = { Text("Diagnostic Findings") },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth()
      )

      OutlinedTextField(
        value = workPerformed,
        onValueChange = { workPerformed = it },
        label = { Text("Roadside Work Performed") },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth()
      )

      OutlinedTextField(
        value = partsReplaced,
        onValueChange = { partsReplaced = it },
        label = { Text("Parts / Materials Consumed") },
        modifier = Modifier.fillMaxWidth()
      )

      Text(
        text = "ON-SITE PHOTO CHECKLIST (MANDATORY)",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Navy700, letterSpacing = 1.sp)
      )

      Surface(
        color = SurfaceVariantSlate,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = OnlineGreen, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Photo 1: Vehicle number plate & breakdown location", fontSize = 12.sp, color = Navy900)
          }
          Spacer(modifier = Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = OnlineGreen, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Photo 2: Component inspected / repaired", fontSize = 12.sp, color = Navy900)
          }
          Spacer(modifier = Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = OnlineGreen, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Photo 3: Safe completion & clear roadside check", fontSize = 12.sp, color = Navy900)
          }
        }
      }

      Text(
        text = "CUSTOMER DIGITAL SIGN-OFF TOKEN",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Navy700, letterSpacing = 1.sp)
      )

      OutlinedTextField(
        value = signoffToken,
        onValueChange = { signoffToken = it },
        label = { Text("Customer Sign-off Acknowledgment Code") },
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}

// ----------------------------------------------------
// 17. JOB HISTORY & EARNINGS BREAKDOWN SCREEN
// ----------------------------------------------------
@Composable
fun JobHistoryAndEarningsScreen(
  completedHistory: List<CompletedJobRecord>,
  todayEarningsInr: Double,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var selectedTab by remember { mutableIntStateOf(0) } // 0: All, 1: Completed, 2: Cancelled

  val filteredHistory = when (selectedTab) {
    1 -> completedHistory.filter { !it.isCancelled }
    2 -> completedHistory.filter { it.isCancelled }
    else -> completedHistory
  }

  Scaffold(
    topBar = {
      Surface(color = SurfaceWhite, shadowElevation = 2.dp) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Navy900)
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Job History & Earnings (₹)",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Navy900)
          )
        }
      }
    },
    containerColor = SurfaceWhite,
    modifier = modifier.fillMaxSize()
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item {
        Spacer(modifier = Modifier.height(4.dp))
      }

      // Earnings summary card
      item {
        Surface(
          color = Navy900,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(20.dp)) {
            Text(
              text = "NET DISPATCH PAYOUT (TODAY)",
              style = MaterialTheme.typography.labelSmall.copy(color = OnlineGreenLight, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "₹${"%.0f".format(todayEarningsInr)}",
              style = MaterialTheme.typography.headlineMedium.copy(color = SurfaceWhite, fontWeight = FontWeight.Black)
            )
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Navy700)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Column {
                Text("Platform Share", style = MaterialTheme.typography.labelSmall.copy(color = Navy300))
                Text("15% standard", style = MaterialTheme.typography.bodySmall.copy(color = SurfaceWhite, fontWeight = FontWeight.Bold))
              }
              Column {
                Text("Settlement Method", style = MaterialTheme.typography.labelSmall.copy(color = Navy300))
                Text("Daily UPI / IMPS Bank Transfer", style = MaterialTheme.typography.bodySmall.copy(color = SurfaceWhite, fontWeight = FontWeight.Bold))
              }
            }
          }
        }
      }

      // Filter Tabs
      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            label = { Text("All (${completedHistory.size})") }
          )
          FilterChip(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            label = { Text("Completed (${completedHistory.count { !it.isCancelled }})") }
          )
          FilterChip(
            selected = selectedTab == 2,
            onClick = { selectedTab = 2 },
            label = { Text("Cancelled (${completedHistory.count { it.isCancelled }})") }
          )
        }
      }

      items(filteredHistory) { item ->
        CompletedJobItemCard(item = item)
      }

      item {
        Spacer(modifier = Modifier.height(20.dp))
      }
    }
  }
}

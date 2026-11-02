package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ServiceCategory
import com.example.model.VehicleCapability
import com.example.ui.theme.*

@Composable
fun SplashScreen(
  onNavigateNext: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(NavyDark)
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .size(90.dp)
          .clip(RoundedCornerShape(22.dp))
          .background(EmergencyRed),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Filled.Build,
          contentDescription = "FixMechanic Icon",
          tint = SurfaceWhite,
          modifier = Modifier.size(50.dp)
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = "FixMechanic",
        style = MaterialTheme.typography.headlineMedium.copy(
          fontWeight = FontWeight.Black,
          color = SurfaceWhite,
          letterSpacing = 1.sp
        )
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
      ) {
        Surface(
          color = OnlineGreen,
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = "MECHANIC INDIA",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.Bold,
              color = NavyDark
            )
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "Emergency Roadside Assistance Partner Console\nBike & Car Mechanics Across India",
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium.copy(
          color = Navy300,
          lineHeight = 20.sp
        )
      )

      Spacer(modifier = Modifier.height(48.dp))

      Button(
        onClick = onNavigateNext,
        colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite, contentColor = NavyDark),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("splash_continue_btn")
      ) {
        Text("Enter Partner Console", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = null,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

@Composable
fun OnboardingScreen(
  onCompleteOnboarding: () -> Unit,
  onGoToLogin: () -> Unit,
  modifier: Modifier = Modifier
) {
  var currentStep by remember { mutableIntStateOf(0) }

  val steps = listOf(
    Triple(
      Icons.Filled.FlashOn,
      "Instant Emergency Roadside Dispatches",
      "Receive real-time 2-wheeler and 4-wheeler breakdown requests in your city. View vehicle model, issue, and distance before accepting."
    ),
    Triple(
      Icons.Filled.AccountBalanceWallet,
      "Guaranteed Payouts in ₹ (INR)",
      "Standardised breakdown pricing with upfront rate estimates. Add itemised spare parts and extra labor quotes for instant customer in-app approval."
    ),
    Triple(
      Icons.Filled.Security,
      "Approved & Verified Mechanics Only",
      "Only approved mechanics with verified KYC/trade certificates receive live requests. Private masked communication protects phone privacy."
    )
  )

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceWhite)
      .padding(24.dp),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    // Top Bar Skip
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "FixMechanic Partner Onboarding",
        style = MaterialTheme.typography.titleSmall.copy(
          fontWeight = FontWeight.Bold,
          color = Navy700
        )
      )
      TextButton(onClick = onGoToLogin) {
        Text("Sign In", color = EmergencyRed, fontWeight = FontWeight.Bold)
      }
    }

    // Carousel content
    val (icon, title, desc) = steps[currentStep]
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxWidth()
    ) {
      Box(
        modifier = Modifier
          .size(110.dp)
          .clip(CircleShape)
          .background(EmergencyRedLight),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = EmergencyRed,
          modifier = Modifier.size(56.dp)
        )
      }

      Spacer(modifier = Modifier.height(32.dp))

      Text(
        text = title,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.headlineSmall.copy(
          fontWeight = FontWeight.Bold,
          color = Navy900
        )
      )

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = desc,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium.copy(
          color = Navy700,
          lineHeight = 22.sp
        )
      )

      Spacer(modifier = Modifier.height(32.dp))

      // Dots
      Row(horizontalArrangement = Arrangement.Center) {
        steps.indices.forEach { index ->
          Box(
            modifier = Modifier
              .padding(horizontal = 4.dp)
              .height(8.dp)
              .width(if (index == currentStep) 24.dp else 8.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(if (index == currentStep) Navy900 else BorderLight)
          )
        }
      }
    }

    // Bottom Navigation Buttons
    Column(modifier = Modifier.fillMaxWidth()) {
      Button(
        onClick = {
          if (currentStep < steps.size - 1) {
            currentStep++
          } else {
            onCompleteOnboarding()
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = Navy900),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("onboarding_next_btn")
      ) {
        Text(
          text = if (currentStep < steps.size - 1) "Next" else "Register as Mechanic",
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp
        )
      }

      if (currentStep > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
          onClick = { currentStep-- },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Back", color = Navy700)
        }
      }
    }
  }
}

// ----------------------------------------------------
// MECHANIC MOBILE NUMBER LOGIN SCREEN
// ----------------------------------------------------
@Composable
fun MechanicLoginScreen(
  onSendOtp: (mobile: String) -> Unit,
  onFastDemoLogin: (persona: String) -> Unit,
  onNavigateToRegister: () -> Unit,
  modifier: Modifier = Modifier
) {
  var mobileNumber by remember { mutableStateOf("9876543210") }
  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceWhite)
      .verticalScroll(scrollState)
      .padding(24.dp)
  ) {
    Spacer(modifier = Modifier.height(16.dp))

    // Header
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(46.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Navy900),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Filled.Handyman,
          contentDescription = null,
          tint = SurfaceWhite,
          modifier = Modifier.size(24.dp)
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text(
          text = "FixMechanic Partner",
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            color = Navy900
          )
        )
        Text(
          text = "Mobile OTP Authentication (India)",
          style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
        )
      }
    }

    Spacer(modifier = Modifier.height(28.dp))

    Text(
      text = "Mechanic Sign In",
      style = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.ExtraBold,
        color = Navy900
      )
    )
    Text(
      text = "Enter your 10-digit registered Indian mobile number",
      style = MaterialTheme.typography.bodyMedium.copy(color = Navy700)
    )

    Spacer(modifier = Modifier.height(24.dp))

    OutlinedTextField(
      value = mobileNumber,
      onValueChange = { if (it.length <= 10 && it.all { char -> char.isDigit() }) mobileNumber = it },
      label = { Text("Mobile Number") },
      prefix = { Text("+91  ", fontWeight = FontWeight.Bold, color = Navy900) },
      leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null, tint = Navy700) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
      singleLine = true,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("login_mobile_input"),
      shape = RoundedCornerShape(10.dp)
    )

    com.example.ui.auth.GoogleSignInButton(
      onAuthSuccess = {
        onFastDemoLogin("rajesh")
      },
      onAuthError = { }
    )

    Spacer(modifier = Modifier.height(16.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      HorizontalDivider(modifier = Modifier.weight(1f), color = BorderLight)
      Text(
        text = "  OR INDIAN MOBILE OTP  ",
        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold)
      )
      HorizontalDivider(modifier = Modifier.weight(1f), color = BorderLight)
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
      onClick = {
        val fullNumber = "+91$mobileNumber"
        onSendOtp(fullNumber)
      },
      enabled = mobileNumber.length == 10,
      colors = ButtonDefaults.buttonColors(containerColor = Navy900),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .testTag("login_send_otp_btn")
    ) {
      Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text("Send Verification OTP", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Fast Demo Personas
    Surface(
      color = SurfaceVariantSlate,
      shape = RoundedCornerShape(12.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Science, contentDescription = null, tint = WarningAmberDark, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "DEMO FAST PERSONA LOGINS",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Navy900)
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { onFastDemoLogin("rajesh") },
            modifier = Modifier.weight(1f).testTag("demo_login_rajesh"),
            shape = RoundedCornerShape(8.dp)
          ) {
            Text("Rajesh (Approved)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OnlineGreenDark)
          }
          OutlinedButton(
            onClick = { onFastDemoLogin("amit") },
            modifier = Modifier.weight(1f).testTag("demo_login_amit"),
            shape = RoundedCornerShape(8.dp)
          ) {
            Text("Amit (Pending)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WarningAmberDark)
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("New to FixMechanic?", color = Navy700, fontSize = 14.sp)
      Spacer(modifier = Modifier.width(4.dp))
      TextButton(onClick = onNavigateToRegister) {
        Text("Register Your Workshop", color = EmergencyRed, fontWeight = FontWeight.Bold)
      }
    }
  }
}

// ----------------------------------------------------
// OTP VERIFICATION SCREEN
// ----------------------------------------------------
@Composable
fun OtpVerificationScreen(
  mobileNumber: String,
  onVerifyOtp: (otpCode: String) -> Unit,
  onResendOtp: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var otpCode by remember { mutableStateOf("123456") }
  var countdown by remember { mutableIntStateOf(30) }

  LaunchedEffect(Unit) {
    while (countdown > 0) {
      kotlinx.coroutines.delay(1000)
      countdown--
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceWhite)
      .padding(24.dp),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onNavigateBack) {
          Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Navy900)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Verify OTP",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Navy900)
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = "Enter 6-Digit Code",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = Navy900)
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "Sent to $mobileNumber via SMS",
        style = MaterialTheme.typography.bodyMedium.copy(color = Navy700)
      )

      Spacer(modifier = Modifier.height(20.dp))

      Surface(
        color = WarningAmberLight,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Info, contentDescription = null, tint = WarningAmberDark, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Demo Verification Code is: 123456",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Navy900)
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      OutlinedTextField(
        value = otpCode,
        onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) otpCode = it },
        label = { Text("6-Digit OTP") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("otp_code_input"),
        shape = RoundedCornerShape(10.dp)
      )

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (countdown > 0) {
          Text("Resend code in ${countdown}s", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
        } else {
          TextButton(onClick = {
            countdown = 30
            onResendOtp()
          }) {
            Text("Resend OTP SMS", color = EmergencyRed, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    Button(
      onClick = { onVerifyOtp(otpCode) },
      enabled = otpCode.length == 6,
      colors = ButtonDefaults.buttonColors(containerColor = Navy900),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .testTag("verify_otp_submit_btn")
    ) {
      Text("Verify & Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
  }
}

// ----------------------------------------------------
// MECHANIC REGISTRATION & PROFILE ONBOARDING
// ----------------------------------------------------
@Composable
fun MechanicRegistrationScreen(
  onRegisterSubmit: (
    name: String,
    mobile: String,
    email: String,
    workshop: String,
    capability: VehicleCapability,
    services: Set<ServiceCategory>,
    radiusKm: Int,
    city: String,
    experience: Int,
    verificationId: String
  ) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var fullName by remember { mutableStateOf("Rajesh Kumar") }
  var mobile by remember { mutableStateOf("9876543210") }
  var email by remember { mutableStateOf("rajesh.kumar@gmail.com") }
  var workshopName by remember { mutableStateOf("Sri Balaji Mobile Auto Works") }
  var capability by remember { mutableStateOf(VehicleCapability.BOTH) }
  var cityLocality by remember { mutableStateOf("Indiranagar, Bengaluru") }
  var experienceYears by remember { mutableStateOf("8") }
  var verificationId by remember { mutableStateOf("DL-KA03-2016-99214") }

  var selectedServices by remember {
    mutableStateOf(
      setOf(
        ServiceCategory.PUNCTURE_TYRE,
        ServiceCategory.BATTERY_JUMPSTART,
        ServiceCategory.FUEL_DELIVERY,
        ServiceCategory.CLUTCH_BRAKE_CABLE
      )
    )
  }

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
        text = "Mechanic Partner Application",
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          color = Navy900
        )
      )
    }

    Spacer(modifier = Modifier.height(14.dp))

    Surface(
      color = EmergencyRedSoft,
      shape = RoundedCornerShape(8.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, EmergencyRedLight),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Security, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = "FixMechanic Trust Policy: All registrations start in PENDING status. Only APPROVED mechanics can go online or accept emergency requests.",
          style = MaterialTheme.typography.bodySmall.copy(color = EmergencyRedDark, fontSize = 12.sp)
        )
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text(
      text = "PERSONAL & WORKSHOP DETAILS",
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Navy700, letterSpacing = 1.sp)
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
      value = fullName,
      onValueChange = { fullName = it },
      label = { Text("Mechanic Full Name") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(10.dp)
    )

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
      value = mobile,
      onValueChange = { if (it.length <= 10 && it.all { ch -> ch.isDigit() }) mobile = it },
      label = { Text("Mobile Number (10 digits)") },
      prefix = { Text("+91  ", fontWeight = FontWeight.Bold) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(10.dp)
    )

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
      value = email,
      onValueChange = { email = it },
      label = { Text("Contact Email") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(10.dp)
    )

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
      value = workshopName,
      onValueChange = { workshopName = it },
      label = { Text("Workshop / Garage Name (or Independent)") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(10.dp)
    )

    Spacer(modifier = Modifier.height(18.dp))

    // Vehicle Capability
    Text(
      text = "SERVICE CAPABILITY (BIKE / CAR / BOTH)",
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Navy700, letterSpacing = 1.sp)
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      VehicleCapability.entries.forEach { cap ->
        val isSelected = capability == cap
        Surface(
          color = if (isSelected) Navy900 else SurfaceVariantSlate,
          shape = RoundedCornerShape(8.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Navy900 else BorderLight),
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable { capability = cap }
        ) {
          Text(
            text = when (cap) {
              VehicleCapability.BIKE -> "Bike"
              VehicleCapability.CAR -> "Car"
              VehicleCapability.BOTH -> "Both (Bike & Car)"
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall.copy(
              fontWeight = FontWeight.Bold,
              color = if (isSelected) SurfaceWhite else Navy900,
              fontSize = 11.sp
            )
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(18.dp))

    Text(
      text = "BASE LOCATION & EXPERIENCE",
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Navy700, letterSpacing = 1.sp)
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
      value = cityLocality,
      onValueChange = { cityLocality = it },
      label = { Text("Service Area & City (e.g. Indiranagar, Bengaluru)") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(10.dp)
    )

    Spacer(modifier = Modifier.height(10.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      OutlinedTextField(
        value = experienceYears,
        onValueChange = { experienceYears = it },
        label = { Text("Experience (Yrs)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(10.dp)
      )
      OutlinedTextField(
        value = verificationId,
        onValueChange = { verificationId = it },
        label = { Text("Driving License / Aadhaar ID") },
        singleLine = true,
        modifier = Modifier.weight(1.8f),
        shape = RoundedCornerShape(10.dp)
      )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Button(
      onClick = {
        onRegisterSubmit(
          fullName,
          "+91$mobile",
          email,
          workshopName,
          capability,
          selectedServices,
          15,
          cityLocality,
          experienceYears.toIntOrNull() ?: 5,
          verificationId
        )
      },
      colors = ButtonDefaults.buttonColors(containerColor = Navy900),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .testTag("submit_registration_btn")
    ) {
      Text("Submit Application for KYC Review", fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }

    Spacer(modifier = Modifier.height(16.dp))
  }
}

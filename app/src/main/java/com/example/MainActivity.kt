package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.data.FixMechanicRepositoryImpl
import com.example.model.JobAcceptanceResult
import com.example.model.JobStatus
import com.example.ui.MechanicViewModel
import com.example.ui.navigation.MechanicScreen
import com.example.ui.screens.*
import com.example.ui.theme.FixMechanicTheme

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val repository = FixMechanicRepositoryImpl(this, lifecycleScope)
    val viewModel = MechanicViewModel(repository)

    val credentialManager = androidx.credentials.CredentialManager.create(this)
    com.example.ui.auth.attemptAutoSignIn(
      context = this,
      credentialManager = credentialManager,
      onAuthSuccess = { },
      onUnauthenticated = { },
      scope = lifecycleScope
    )

    setContent {
      FixMechanicTheme {
        MechanicAppRoot(viewModel = viewModel)
      }
    }
  }
}

@Composable
fun MechanicAppRoot(viewModel: MechanicViewModel) {
  var screenStack by remember { mutableStateOf(listOf(MechanicScreen.SPLASH)) }
  var pendingMobileForOtp by remember { mutableStateOf("+91 98765 43210") }
  val currentScreen = screenStack.lastOrNull() ?: MechanicScreen.DASHBOARD

  fun navigateTo(screen: MechanicScreen) {
    screenStack = screenStack + screen
  }

  fun navigateBack() {
    if (screenStack.size > 1) {
      screenStack = screenStack.dropLast(1)
    }
  }

  fun popTo(screen: MechanicScreen) {
    val idx = screenStack.indexOf(screen)
    if (idx >= 0) {
      screenStack = screenStack.take(idx + 1)
    } else {
      screenStack = listOf(screen)
    }
  }

  // BackHandler to handle backstack navigation
  BackHandler(enabled = screenStack.size > 1) {
    navigateBack()
  }

  val profile by viewModel.profile.collectAsStateWithLifecycle()
  val activeJob by viewModel.activeJob.collectAsStateWithLifecycle()
  val currentOffer by viewModel.currentOffer.collectAsStateWithLifecycle()
  val completedHistory by viewModel.completedHistory.collectAsStateWithLifecycle()
  val todayEarningsInr by viewModel.todayEarningsInr.collectAsStateWithLifecycle()
  val isCompetitorModeOn by viewModel.isSimulateCompetitorSteal.collectAsStateWithLifecycle()
  val isAcceptingOffer by viewModel.isAcceptingOffer.collectAsStateWithLifecycle()
  val lastAcceptanceResult by viewModel.lastAcceptanceResult.collectAsStateWithLifecycle()
  val activeChatMessages by viewModel.activeChatMessages.collectAsStateWithLifecycle()
  val cannedResponses by viewModel.cannedResponses.collectAsStateWithLifecycle()
  val isChatAllowed by viewModel.isChatAllowed.collectAsStateWithLifecycle()
  val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
  val isLocationPermissionGranted by viewModel.isLocationPermissionGranted.collectAsStateWithLifecycle()
  val isGpsEnabled by viewModel.isGpsEnabled.collectAsStateWithLifecycle()

  Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (currentScreen) {
        MechanicScreen.SPLASH -> {
          SplashScreen(
            onNavigateNext = {
              screenStack = listOf(MechanicScreen.ONBOARDING)
            }
          )
        }

        MechanicScreen.ONBOARDING -> {
          OnboardingScreen(
            onCompleteOnboarding = {
              screenStack = listOf(MechanicScreen.DASHBOARD)
            },
            onGoToLogin = {
              navigateTo(MechanicScreen.LOGIN)
            }
          )
        }

        MechanicScreen.LOGIN -> {
          MechanicLoginScreen(
            onSendOtp = { mobile ->
              pendingMobileForOtp = mobile
              viewModel.sendOtp(mobile) { success, _ ->
                if (success) {
                  navigateTo(MechanicScreen.OTP_VERIFY)
                }
              }
            },
            onFastDemoLogin = { persona ->
              viewModel.switchDemoProfile(persona)
              popTo(MechanicScreen.DASHBOARD)
            },
            onNavigateToRegister = {
              navigateTo(MechanicScreen.REGISTRATION)
            }
          )
        }

        MechanicScreen.OTP_VERIFY -> {
          OtpVerificationScreen(
            mobileNumber = pendingMobileForOtp,
            onVerifyOtp = { otpCode ->
              viewModel.verifyOtp(pendingMobileForOtp, otpCode) { success, _ ->
                if (success) {
                  popTo(MechanicScreen.DASHBOARD)
                }
              }
            },
            onResendOtp = {
              viewModel.sendOtp(pendingMobileForOtp) { _, _ -> }
            },
            onNavigateBack = { navigateBack() }
          )
        }

        MechanicScreen.REGISTRATION -> {
          MechanicRegistrationScreen(
            onRegisterSubmit = { name, mobile, email, workshop, cap, srv, rad, city, exp, idNo ->
              viewModel.register(name, mobile, email, workshop, cap, srv, rad, city, exp, idNo)
              popTo(MechanicScreen.DASHBOARD)
              navigateTo(MechanicScreen.VERIFICATION)
            },
            onNavigateBack = { navigateBack() }
          )
        }

        MechanicScreen.DASHBOARD -> {
          DashboardScreen(
            profile = profile,
            activeJob = activeJob,
            completedHistory = completedHistory,
            todayEarningsInr = todayEarningsInr,
            isCompetitorModeOn = isCompetitorModeOn,
            statusBanner = statusBanner,
            isLocationPermissionGranted = isLocationPermissionGranted,
            isGpsEnabled = isGpsEnabled,
            onCheckPermissions = { ctx -> viewModel.checkDevicePermissions(ctx) },
            onSetLocationPermission = { granted -> viewModel.setLocationPermissionGranted(granted) },
            onToggleOnline = { viewModel.toggleOnlineStatus(!profile.isOnline) },
            onOpenSettings = { navigateTo(MechanicScreen.PROFILE_SUPPORT) },
            onOpenActiveJob = { navigateTo(MechanicScreen.ACTIVE_JOB) },
            onOpenHistory = { navigateTo(MechanicScreen.JOB_HISTORY) },
            onOpenVerification = { navigateTo(MechanicScreen.VERIFICATION) },
            onOpenServices = { navigateTo(MechanicScreen.VEHICLES_SERVICES) },
            onSwitchProfile = { persona -> viewModel.switchDemoProfile(persona) },
            onToggleCompetitorMode = { enabled -> viewModel.toggleCompetitorSteal(enabled) },
            onSimulateJobOffer = { scenarioIdx -> viewModel.triggerSimulatedOffer(scenarioIdx) },
            onDismissBanner = { viewModel.dismissBanner() }
          )
        }

        MechanicScreen.VERIFICATION -> {
          VerificationStatusScreen(
            profile = profile,
            onSubmitDocument = { idNumber, docType ->
              viewModel.submitKycProof(idNumber, docType)
            },
            onNavigateBack = { navigateBack() }
          )
        }

        MechanicScreen.VEHICLES_SERVICES -> {
          VehicleAndServicesScreen(
            currentCapability = profile.vehicleCapability,
            supportedServices = profile.supportedServices,
            onUpdateCapability = { viewModel.setVehicleCapability(it) },
            onToggleService = { viewModel.toggleServiceCategory(it) },
            onNavigateBack = { navigateBack() }
          )
        }

        MechanicScreen.SERVICE_RADIUS -> {
          ServiceAreaRadiusScreen(
            currentRadiusKm = profile.serviceRadiusKm,
            baseLocality = profile.baseCityLocality,
            onUpdateRadius = { viewModel.updateServiceRadius(it) },
            onNavigateBack = { navigateBack() }
          )
        }

        MechanicScreen.EDIT_PROFILE -> {
          EditProfileScreen(
            profile = profile,
            onSaveProfile = { name, workshop, cap, city, rad ->
              viewModel.updateProfileDetails(name, workshop, cap, city, rad)
            },
            onNavigateBack = { navigateBack() }
          )
        }

        MechanicScreen.PROFILE_SUPPORT -> {
          ProfileAndSupportScreen(
            profile = profile,
            onNavigateToEditProfile = { navigateTo(MechanicScreen.EDIT_PROFILE) },
            onNavigateToVerification = { navigateTo(MechanicScreen.VERIFICATION) },
            onNavigateToVehicles = { navigateTo(MechanicScreen.VEHICLES_SERVICES) },
            onNavigateToRadius = { navigateTo(MechanicScreen.SERVICE_RADIUS) },
            onSignOut = {
              viewModel.signOut()
              popTo(MechanicScreen.LOGIN)
            },
            onNavigateBack = { navigateBack() }
          )
        }

        MechanicScreen.ACTIVE_JOB -> {
          val job = activeJob
          if (job != null) {
            ActiveJobScreen(
              activeJob = job,
              onAdvanceStatus = { nextStatus ->
                viewModel.advanceJobStatus(job.id, nextStatus)
              },
              onCancelJob = { reason ->
                viewModel.cancelJob(job.id, reason)
                popTo(MechanicScreen.DASHBOARD)
              },
              onOpenChat = { navigateTo(MechanicScreen.PRIVATE_CHAT) },
              onOpenQuote = { navigateTo(MechanicScreen.QUOTE_SUBMISSION) },
              onOpenCompletion = { navigateTo(MechanicScreen.JOB_COMPLETION) },
              onLaunchNavigation = { ctx -> viewModel.launchNavigation(ctx) },
              onNavigateBack = { navigateBack() }
            )
          } else {
            popTo(MechanicScreen.DASHBOARD)
          }
        }

        MechanicScreen.QUOTE_SUBMISSION -> {
          val job = activeJob
          if (job != null) {
            QuoteSubmissionScreen(
              activeJob = job,
              onSubmitQuote = { quote -> viewModel.submitQuote(job.id, quote) },
              onNavigateBack = { navigateBack() }
            )
          } else {
            navigateBack()
          }
        }

        MechanicScreen.PRIVATE_CHAT -> {
          val job = activeJob
          if (job != null) {
            PrivateChatScreen(
              activeJob = job,
              messages = activeChatMessages,
              cannedResponses = cannedResponses,
              isChatAllowed = isChatAllowed,
              onSendMessage = { text -> viewModel.sendChatMessage(text) },
              onRetryMessage = { msgId -> viewModel.retryMessage(msgId) },
              onNavigateBack = { navigateBack() }
            )
          } else {
            navigateBack()
          }
        }

        MechanicScreen.JOB_COMPLETION -> {
          val job = activeJob
          if (job != null) {
            JobCompletionScreen(
              activeJob = job,
              onFinalizeCompletion = { report ->
                viewModel.finalizeJobCompletion(job.id, report)
                popTo(MechanicScreen.DASHBOARD)
              },
              onNavigateBack = { navigateBack() }
            )
          } else {
            navigateBack()
          }
        }

        MechanicScreen.JOB_HISTORY -> {
          JobHistoryAndEarningsScreen(
            completedHistory = completedHistory,
            todayEarningsInr = todayEarningsInr,
            onNavigateBack = { navigateBack() }
          )
        }
      }

      // GLOBAL INCOMING EMERGENCY JOB OFFER MODAL
      val offer = currentOffer
      if (offer != null && activeJob == null) {
        IncomingJobOfferDialog(
          offer = offer,
          isAccepting = isAcceptingOffer,
          onAccept = { offerId -> viewModel.acceptJobOffer(offerId) },
          onReject = { offerId -> viewModel.rejectJobOffer(offerId) }
        )
      }

      // ASSIGNMENT CONFIRMATION / REJECTION RESULT DIALOG
      val acceptanceResult = lastAcceptanceResult
      if (acceptanceResult != null) {
        AssignmentResultDialog(
          result = acceptanceResult,
          onDismiss = { viewModel.clearAcceptanceResult() },
          onViewJob = {
            viewModel.clearAcceptanceResult()
            navigateTo(MechanicScreen.ACTIVE_JOB)
          }
        )
      }
    }
  }
}

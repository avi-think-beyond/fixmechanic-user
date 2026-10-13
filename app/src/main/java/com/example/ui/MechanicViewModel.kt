package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FixMechanicRepositoryImpl
import com.example.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MechanicViewModel(
  private val repository: FixMechanicRepositoryImpl
) : ViewModel() {

  val profile: StateFlow<MechanicProfile> = repository.profile
  val currentOffer: StateFlow<JobOffer?> = repository.currentOffer
  val activeJob: StateFlow<ActiveJob?> = repository.activeJob
  val completedHistory: StateFlow<List<CompletedJobRecord>> = repository.completedHistory
  val todayEarningsInr: StateFlow<Double> = repository.todayEarningsInr
  val isAuthenticated: StateFlow<Boolean> = repository.isAuthenticated
  val isSimulateCompetitorSteal: StateFlow<Boolean> = repository.isSimulateCompetitorSteal

  private val _lastAcceptanceResult = MutableStateFlow<JobAcceptanceResult?>(null)
  val lastAcceptanceResult: StateFlow<JobAcceptanceResult?> = _lastAcceptanceResult.asStateFlow()

  private val _isAcceptingOffer = MutableStateFlow(false)
  val isAcceptingOffer: StateFlow<Boolean> = _isAcceptingOffer.asStateFlow()

  private val _statusBannerMessage = MutableStateFlow<String?>(null)
  val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

  private val _otpVerificationId = MutableStateFlow<String?>(null)
  val otpVerificationId: StateFlow<String?> = _otpVerificationId.asStateFlow()

  private val _isOtpSent = MutableStateFlow(false)
  val isOtpSent: StateFlow<Boolean> = _isOtpSent.asStateFlow()

  private val _isLocationPermissionGranted = MutableStateFlow(true)
  val isLocationPermissionGranted: StateFlow<Boolean> = _isLocationPermissionGranted.asStateFlow()

  private val _isGpsEnabled = MutableStateFlow(true)
  val isGpsEnabled: StateFlow<Boolean> = _isGpsEnabled.asStateFlow()

  // Chat message stream connected to current active job
  val activeChatMessages: StateFlow<List<ChatMessage>> = activeJob
    .flatMapLatest { job ->
      if (job != null) repository.getMessages(job.id) else flowOf(emptyList())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val cannedResponses: StateFlow<List<String>> = activeJob
    .map { job ->
      if (job != null) repository.getQuickCannedResponses(job.status) else emptyList()
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val isChatAllowed: StateFlow<Boolean> = activeJob
    .map { job ->
      if (job != null) repository.isChatAllowedForStatus(job.status) else false
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

  // Auth Operations
  fun sendOtp(mobileNumber: String, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch {
      val res = repository.sendOtp(mobileNumber)
      res.onSuccess { msg ->
        _isOtpSent.value = true
        _otpVerificationId.value = "mock_verification_id_${System.currentTimeMillis()}"
        _statusBannerMessage.value = msg
        onResult(true, msg)
      }.onFailure { err ->
        onResult(false, err.localizedMessage ?: "Failed to send OTP")
      }
    }
  }

  fun verifyOtp(mobileNumber: String, otpCode: String, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch {
      val res = repository.verifyOtp(mobileNumber, otpCode)
      res.onSuccess {
        _isOtpSent.value = false
        _statusBannerMessage.value = "Welcome back, ${it.fullName}"
        onResult(true, "Authentication successful")
      }.onFailure { err ->
        onResult(false, err.localizedMessage ?: "Invalid OTP")
      }
    }
  }

  fun register(
    fullName: String,
    mobileNumber: String,
    email: String,
    workshopName: String,
    capability: VehicleCapability,
    services: Set<ServiceCategory>,
    radiusKm: Int,
    cityLocality: String,
    experienceYears: Int,
    verificationId: String
  ) {
    viewModelScope.launch {
      val res = repository.registerMechanic(
        fullName, mobileNumber, email, workshopName,
        capability, services, radiusKm, cityLocality,
        experienceYears, verificationId
      )
      res.onSuccess {
        _statusBannerMessage.value = "Registration submitted! KYC verification pending."
      }.onFailure {
        _statusBannerMessage.value = "Registration error: ${it.localizedMessage}"
      }
    }
  }

  fun signOut() {
    viewModelScope.launch {
      repository.signOut()
    }
  }

  fun switchDemoProfile(profileId: String) {
    viewModelScope.launch {
      repository.switchDemoProfile(profileId)
      _statusBannerMessage.value = when (profileId) {
        "rajesh", "approved" -> "Switched to Rajesh Kumar (Approved 24/7 Mechanic)"
        "amit", "pending" -> "Switched to Amit Sharma (Pending KYC Verification)"
        "suresh", "suspended" -> "Switched to Suresh Patil (Account Suspended)"
        else -> "Switched profile"
      }
    }
  }

  // Profile and Capabilities
  fun toggleOnlineStatus(online: Boolean) {
    viewModelScope.launch {
      val res = repository.toggleOnlineStatus(online)
      res.onSuccess {
        _statusBannerMessage.value = if (online) "You are now ONLINE for emergency dispatches" else "You are now OFFLINE"
      }.onFailure {
        _statusBannerMessage.value = it.localizedMessage
      }
    }
  }

  fun updateProfileDetails(
    name: String,
    workshop: String,
    capability: VehicleCapability,
    city: String,
    radius: Int
  ) {
    viewModelScope.launch {
      val res = repository.updateProfileDetails(name, workshop, capability, city, radius)
      res.onSuccess {
        _statusBannerMessage.value = "Profile updated successfully."
      }
    }
  }

  fun setVehicleCapability(capability: VehicleCapability) {
    viewModelScope.launch {
      repository.updateVehicleCapability(capability)
    }
  }

  fun toggleServiceCategory(category: ServiceCategory) {
    viewModelScope.launch {
      val current = profile.value.supportedServices.toMutableSet()
      if (current.contains(category)) {
        if (current.size > 1) current.remove(category)
      } else {
        current.add(category)
      }
      repository.updateServiceCategories(current)
    }
  }

  fun updateServiceRadius(radiusKm: Int) {
    viewModelScope.launch {
      repository.updateServiceRadius(radiusKm)
    }
  }

  fun submitKycProof(idNumber: String, docType: String) {
    viewModelScope.launch {
      repository.submitKycDocuments(idNumber, docType)
      _statusBannerMessage.value = "KYC documents submitted for review."
    }
  }

  fun toggleCompetitorSteal(simulateSteal: Boolean) {
    viewModelScope.launch {
      repository.setSimulateCompetitorSteal(simulateSteal)
    }
  }

  // Dispatch & Jobs
  fun acceptJobOffer(offerId: String) {
    viewModelScope.launch {
      _isAcceptingOffer.value = true
      val result = repository.acceptJobOffer(offerId)
      _lastAcceptanceResult.value = result
      _isAcceptingOffer.value = false
    }
  }

  fun rejectJobOffer(offerId: String) {
    viewModelScope.launch {
      repository.rejectJobOffer(offerId)
    }
  }

  fun clearAcceptanceResult() {
    _lastAcceptanceResult.value = null
  }

  fun advanceJobStatus(jobId: String, nextStatus: JobStatus) {
    viewModelScope.launch {
      val res = repository.advanceJobStatus(jobId, nextStatus)
      res.onSuccess {
        _statusBannerMessage.value = "Status updated to: ${it.label}"
      }.onFailure {
        _statusBannerMessage.value = it.localizedMessage
      }
    }
  }

  fun cancelJob(jobId: String, reason: String) {
    viewModelScope.launch {
      val res = repository.cancelJob(jobId, reason)
      res.onSuccess {
        _statusBannerMessage.value = "Job cancelled: $reason"
      }
    }
  }

  fun submitQuote(jobId: String, quote: JobQuote) {
    viewModelScope.launch {
      val res = repository.submitJobQuote(jobId, quote)
      res.onSuccess {
        _statusBannerMessage.value = "Quote submitted & authorized by customer"
      }.onFailure {
        _statusBannerMessage.value = "Quote error: ${it.localizedMessage}"
      }
    }
  }

  fun finalizeJobCompletion(jobId: String, report: CompletionReport) {
    viewModelScope.launch {
      val res = repository.finalizeJobCompletion(jobId, report)
      res.onSuccess {
        _statusBannerMessage.value = "Job completed! Payout ₹${"%.0f".format(report.finalPayoutAmountInr)} credited."
      }.onFailure {
        _statusBannerMessage.value = "Completion error: ${it.localizedMessage}"
      }
    }
  }

  fun triggerSimulatedOffer(scenarioIndex: Int) {
    repository.triggerSimulatedOffer(scenarioIndex)
  }

  fun dismissCurrentOffer() {
    repository.dismissCurrentOffer()
  }

  // Chat
  fun sendChatMessage(text: String) {
    val job = activeJob.value ?: return
    viewModelScope.launch {
      repository.sendMessage(job.id, text, profile.value.fullName)
    }
  }

  fun retryMessage(messageId: String) {
    viewModelScope.launch {
      repository.retryFailedMessage(messageId)
    }
  }

  // Location & Navigation
  fun launchNavigation(context: Context) {
    val job = activeJob.value ?: return
    repository.openExternalTurnByTurn(context, job.destinationLat, job.destinationLng, job.exactAddress)
  }

  fun checkDevicePermissions(context: Context) {
    _isLocationPermissionGranted.value = repository.isLocationPermissionGranted(context)
    _isGpsEnabled.value = repository.isGpsProviderEnabled(context)
  }

  fun setLocationPermissionGranted(granted: Boolean) {
    _isLocationPermissionGranted.value = granted
  }

  fun dismissBanner() {
    _statusBannerMessage.value = null
  }
}

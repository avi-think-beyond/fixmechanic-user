package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FixMechanicRepositoryImpl
import com.example.data.MechanicLocationHelper
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

  fun signInWithEmail(email: String, password: String, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch {
      val res = repository.signInWithEmail(email, password)
      res.onSuccess {
        _statusBannerMessage.value = "Signed in as ${it.email}"
        onResult(true, "Signed in successfully")
      }.onFailure { err ->
        val msg = err.localizedMessage ?: "Email sign-in failed"
        _statusBannerMessage.value = msg
        onResult(false, msg)
      }
    }
  }

  fun registerWithEmail(email: String, password: String, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch {
      val res = repository.registerWithEmail(email, password)
      res.onSuccess {
        _statusBannerMessage.value = "Account created! Complete partner workshop registration."
        onResult(true, "Account created")
      }.onFailure { err ->
        val msg = err.localizedMessage ?: "Account creation failed"
        _statusBannerMessage.value = msg
        onResult(false, msg)
      }
    }
  }

  fun sendOtp(mobileNumber: String, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch {
      val res = repository.sendOtp(mobileNumber)
      res.onSuccess { msg ->
        _isOtpSent.value = true
        _statusBannerMessage.value = msg
        onResult(true, msg)
      }.onFailure { err ->
        val msg = err.localizedMessage ?: "Phone OTP requires Blaze billing."
        _statusBannerMessage.value = msg
        onResult(false, msg)
      }
    }
  }

  fun verifyOtp(mobileNumber: String, otpCode: String, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch {
      val res = repository.verifyOtp(mobileNumber, otpCode)
      res.onSuccess {
        _isOtpSent.value = false
        onResult(true, "Authentication successful")
      }.onFailure { err ->
        val msg = err.localizedMessage ?: "OTP unavailable on Spark"
        _statusBannerMessage.value = msg
        onResult(false, msg)
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
        _statusBannerMessage.value = "Registration submitted! Pending Operations Admin verification."
      }.onFailure {
        _statusBannerMessage.value = "Registration error: ${it.localizedMessage}"
      }
    }
  }

  fun signOut() {
    viewModelScope.launch { repository.signOut() }
  }

  fun switchDemoProfile(profileId: String) {
    viewModelScope.launch { repository.switchDemoProfile(profileId) }
  }

  fun toggleOnlineStatus(online: Boolean, context: Context? = null) {
    viewModelScope.launch {
      val res = repository.toggleOnlineStatus(online)
      res.onSuccess {
        _statusBannerMessage.value = if (online) {
          "You are now ONLINE for emergency dispatches"
        } else {
          "You are now OFFLINE"
        }
        if (online && context != null) {
          syncGpsLocation(context)
        }
      }.onFailure {
        _statusBannerMessage.value = it.localizedMessage
      }
    }
  }

  fun syncGpsLocation(context: Context) {
    viewModelScope.launch {
      val coords = MechanicLocationHelper.fetchCurrentLocation(context)
      if (coords != null) {
        repository.updateGpsLocation(coords.lat, coords.lng, coords.address)
          .onSuccess {
            _statusBannerMessage.value = "Live GPS location synced (${coords.address ?: "GPS active"})"
          }
      }
    }
  }

  fun registerFcmToken() {
    viewModelScope.launch {
      repository.getOrRegisterFcmToken()
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
      repository.updateProfileDetails(name, workshop, capability, city, radius)
        .onSuccess { _statusBannerMessage.value = "Profile updated successfully." }
        .onFailure { _statusBannerMessage.value = "Update failed: ${it.localizedMessage}" }
    }
  }

  fun setVehicleCapability(capability: VehicleCapability) {
    viewModelScope.launch { repository.updateVehicleCapability(capability) }
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
    viewModelScope.launch { repository.updateServiceRadius(radiusKm) }
  }

  fun submitKycProof(idNumber: String, docType: String) {
    viewModelScope.launch {
      repository.submitKycDocuments(idNumber, docType)
        .onSuccess { _statusBannerMessage.value = "KYC documents submitted for review." }
        .onFailure { _statusBannerMessage.value = "KYC error: ${it.localizedMessage}" }
    }
  }

  fun toggleCompetitorSteal(simulateSteal: Boolean) {
    viewModelScope.launch { repository.setSimulateCompetitorSteal(simulateSteal) }
  }

  fun acceptJobOffer(offerId: String) {
    viewModelScope.launch {
      _isAcceptingOffer.value = true
      val result = repository.acceptJobOffer(offerId)
      _lastAcceptanceResult.value = result
      _isAcceptingOffer.value = false
    }
  }

  fun rejectJobOffer(offerId: String) {
    viewModelScope.launch { repository.rejectJobOffer(offerId) }
  }

  fun clearAcceptanceResult() {
    _lastAcceptanceResult.value = null
  }

  fun advanceJobStatus(jobId: String, nextStatus: JobStatus) {
    viewModelScope.launch {
      repository.advanceJobStatus(jobId, nextStatus)
        .onSuccess { _statusBannerMessage.value = "Status updated to: ${it.label}" }
        .onFailure { _statusBannerMessage.value = it.localizedMessage }
    }
  }

  fun cancelJob(jobId: String, reason: String) {
    viewModelScope.launch {
      repository.cancelJob(jobId, reason)
        .onSuccess { _statusBannerMessage.value = "Job cancelled & returned to dispatch queue." }
        .onFailure { _statusBannerMessage.value = it.localizedMessage }
    }
  }

  fun submitQuote(jobId: String, quote: JobQuote) {
    viewModelScope.launch {
      repository.submitJobQuote(jobId, quote)
        .onSuccess { _statusBannerMessage.value = "Quote submitted to customer" }
        .onFailure { _statusBannerMessage.value = "Quote error: ${it.localizedMessage}" }
    }
  }

  fun finalizeJobCompletion(jobId: String, report: CompletionReport) {
    viewModelScope.launch {
      repository.finalizeJobCompletion(jobId, report)
        .onSuccess { _statusBannerMessage.value = "Job completed! Payout ₹${"%.0f".format(report.finalPayoutAmountInr)} recorded." }
        .onFailure { _statusBannerMessage.value = "Completion error: ${it.localizedMessage}" }
    }
  }

  fun triggerSimulatedOffer(scenarioIndex: Int) {
    repository.triggerSimulatedOffer(scenarioIndex)
  }

  fun dismissCurrentOffer() {
    repository.dismissCurrentOffer()
  }

  fun sendChatMessage(text: String) {
    val job = activeJob.value ?: return
    viewModelScope.launch {
      repository.sendMessage(job.id, text, profile.value.fullName)
        .onFailure { _statusBannerMessage.value = it.localizedMessage }
    }
  }

  fun retryMessage(messageId: String) {
    viewModelScope.launch { repository.retryFailedMessage(messageId) }
  }

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

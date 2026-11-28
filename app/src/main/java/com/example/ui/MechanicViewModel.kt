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
  val availableOffers: StateFlow<List<JobOffer>> = repository.availableOffers
  val activeJob: StateFlow<ActiveJob?> = repository.activeJob
  val completedHistory: StateFlow<List<CompletedJobRecord>> = repository.completedHistory
  val todayEarningsInr: StateFlow<Double> = repository.todayEarningsInr
  val isAuthenticated: StateFlow<Boolean> = repository.isAuthenticated
  val isSimulateCompetitorSteal: StateFlow<Boolean> = repository.isSimulateCompetitorSteal

  private val _isSavingProfile = MutableStateFlow(false)
  val isSavingProfile: StateFlow<Boolean> = _isSavingProfile.asStateFlow()

  private val _isSyncingDeviceLocation = MutableStateFlow(false)
  val isSyncingDeviceLocation: StateFlow<Boolean> = _isSyncingDeviceLocation.asStateFlow()

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

  private val _isEmailAuthLoading = MutableStateFlow(false)
  val isEmailAuthLoading: StateFlow<Boolean> = _isEmailAuthLoading.asStateFlow()

  fun handlePostSignIn(
    context: Context,
    isFirebaseNewUser: Boolean = false,
    onResolved: (isNewMechanic: Boolean) -> Unit
  ) {
    viewModelScope.launch {
      val isNewMechanic = repository.completeSignInAndCheckIsNewMechanic(isFirebaseNewUser)
      registerFcmToken()
      syncGpsLocation(context)
      if (isNewMechanic) {
        _statusBannerMessage.value = "Welcome! Please complete your mechanic profile details."
      } else {
        _statusBannerMessage.value = "Welcome back, ${profile.value.fullName.ifBlank { "Partner" }}!"
      }
      onResolved(isNewMechanic)
    }
  }

  fun signInWithEmail(
    context: Context,
    email: String,
    password: String,
    onResult: (Boolean, String) -> Unit
  ) {
    if (_isEmailAuthLoading.value) return
    viewModelScope.launch {
      _isEmailAuthLoading.value = true
      try {
        val res = repository.signInWithEmail(email, password)
        res.onSuccess {
          _statusBannerMessage.value = "Signed in as ${it.email}"
          onResult(true, "Signed in successfully")
        }.onFailure { err ->
          val msg = com.example.ui.auth.formatAuthErrorMessage(context, err)
          _statusBannerMessage.value = msg
          onResult(false, msg)
        }
      } finally {
        _isEmailAuthLoading.value = false
      }
    }
  }

  fun registerWithEmail(
    context: Context,
    email: String,
    password: String,
    onResult: (Boolean, String) -> Unit
  ) {
    if (_isEmailAuthLoading.value) return
    viewModelScope.launch {
      _isEmailAuthLoading.value = true
      try {
        val res = repository.registerWithEmail(email, password)
        res.onSuccess {
          _statusBannerMessage.value = "Account created! Complete your profile details below."
          onResult(true, "Account created")
        }.onFailure { err ->
          val msg = com.example.ui.auth.formatAuthErrorMessage(context, err)
          _statusBannerMessage.value = msg
          onResult(false, msg)
        }
      } finally {
        _isEmailAuthLoading.value = false
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

  fun syncInitialDeviceLocation(context: Context, forceRefresh: Boolean = false) {
    if (!forceRefresh && profile.value.hasCustomLocation) return
    viewModelScope.launch {
      _isSyncingDeviceLocation.value = true
      try {
        val detected = com.example.ui.components.resolveCurrentDeviceLocationWithAddress(context)
        if (detected != null) {
          val (lat, lng, addr) = detected
          repository.syncInitialDeviceLocation(
            latitude = lat,
            longitude = lng,
            locality = addr?.locality,
            pincode = addr?.pincode,
            forceRefresh = forceRefresh
          )
          if (forceRefresh) {
            _statusBannerMessage.value = "Updated to mobile GPS location${addr?.locality?.let { ": $it" } ?: ""}"
          }
        } else if (forceRefresh) {
          _statusBannerMessage.value = "Could not detect GPS fix. Check Location Services and permissions."
        }
      } finally {
        _isSyncingDeviceLocation.value = false
      }
    }
  }

  fun updateProfileDetails(
    name: String,
    workshop: String,
    capability: VehicleCapability,
    city: String,
    radius: Int,
    pincode: String = profile.value.pincode,
    latitude: Double = profile.value.latitude,
    longitude: Double = profile.value.longitude,
    onResult: ((Boolean) -> Unit)? = null
  ) {
    viewModelScope.launch {
      _isSavingProfile.value = true
      val res = repository.updateProfileDetails(name, workshop, capability, city, radius, pincode, latitude, longitude)
      _isSavingProfile.value = false
      res.onSuccess {
        _statusBannerMessage.value = "Profile and workshop location saved to Firebase."
        onResult?.invoke(true)
      }.onFailure { err ->
        _statusBannerMessage.value = "Update failed: ${err.localizedMessage}"
        onResult?.invoke(false)
      }
    }
  }

  fun updateServiceAreaAndPincode(
    cityLocality: String,
    pincode: String,
    radiusKm: Int,
    servicePincodes: List<String>,
    latitude: Double = profile.value.latitude,
    longitude: Double = profile.value.longitude,
    onResult: ((Boolean) -> Unit)? = null
  ) {
    viewModelScope.launch {
      _isSavingProfile.value = true
      val res = repository.updateServiceAreaAndPincode(cityLocality, pincode, radiusKm, servicePincodes, latitude, longitude)
      _isSavingProfile.value = false
      res.onSuccess {
        _statusBannerMessage.value = "Service area saved to Firebase: $cityLocality ($pincode), $radiusKm km"
        onResult?.invoke(true)
      }.onFailure { err ->
        _statusBannerMessage.value = "Save failed: ${err.localizedMessage}"
        onResult?.invoke(false)
      }
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

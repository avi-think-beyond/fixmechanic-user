package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FixMechanicRepositoryImpl(
  private val scope: CoroutineScope
) : AuthRepository, MechanicProfileRepository, JobDispatchRepository, ChatRepository, LocationNavigationService, FcmNotificationService {

  // --- Auth State ---
  private val _currentUserMobile = MutableStateFlow<String?>("+91 98765 43210")
  override val currentUserMobile: StateFlow<String?> = _currentUserMobile.asStateFlow()

  private val _isAuthenticated = MutableStateFlow(true)
  override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

  // --- Synthetic Profiles ---
  private val approvedRajeshProfile = MechanicProfile(
    id = "mech_rajesh_kumar",
    fullName = "Rajesh Kumar",
    mobileNumber = "+91 98765 43210",
    email = "rajesh.auto@fixmechanic.in",
    workshopName = "Sri Balaji 24/7 Mobile Auto Tech",
    vehicleCapability = VehicleCapability.BOTH,
    supportedServices = setOf(
      ServiceCategory.PUNCTURE_TYRE,
      ServiceCategory.BATTERY_JUMPSTART,
      ServiceCategory.FUEL_DELIVERY,
      ServiceCategory.CLUTCH_BRAKE_CABLE,
      ServiceCategory.STARTING_TROUBLE,
      ServiceCategory.LOCKOUT_KEY,
      ServiceCategory.OVERHEATING_COOLANT
    ),
    serviceRadiusKm = 15,
    baseCityLocality = "Indiranagar & Outer Ring Road, Bengaluru",
    experienceYears = 11,
    verificationIdNumber = "DL-KA03-2015-89123",
    registrationStatus = RegistrationStatus.APPROVED,
    statusReason = "Aadhaar & Commercial Trade Certificate verified. Active roadside dispatch clearance.",
    isOnline = true,
    isDemoMode = true,
    rating = 4.94f,
    totalCompletedJobs = 184,
    todayEarningsInr = 2450.0,
    activeResponseRatePercent = 99
  )

  private val pendingAmitProfile = MechanicProfile(
    id = "mech_amit_sharma",
    fullName = "Amit Sharma",
    mobileNumber = "+91 98450 11223",
    email = "amit.sharma@speedway.in",
    workshopName = "Speedway Roadside Services",
    vehicleCapability = VehicleCapability.BIKE,
    supportedServices = setOf(
      ServiceCategory.PUNCTURE_TYRE,
      ServiceCategory.CLUTCH_BRAKE_CABLE,
      ServiceCategory.FUEL_DELIVERY
    ),
    serviceRadiusKm = 10,
    baseCityLocality = "Koramangala 4th Block, Bengaluru",
    experienceYears = 4,
    verificationIdNumber = "DL-KA01-2021-44019",
    registrationStatus = RegistrationStatus.PENDING,
    statusReason = "Aadhaar & Workshop Trade Proof submitted. Verification in progress by FixMechanic operations team.",
    isOnline = false,
    isDemoMode = true,
    rating = 4.80f,
    totalCompletedJobs = 12,
    todayEarningsInr = 0.0,
    activeResponseRatePercent = 94
  )

  private val suspendedSureshProfile = MechanicProfile(
    id = "mech_suresh_patil",
    fullName = "Suresh Patil",
    mobileNumber = "+91 91234 56789",
    email = "suresh.patil@garage.in",
    workshopName = "Patil Auto Garage",
    vehicleCapability = VehicleCapability.CAR,
    supportedServices = setOf(
      ServiceCategory.BATTERY_JUMPSTART,
      ServiceCategory.OVERHEATING_COOLANT
    ),
    serviceRadiusKm = 12,
    baseCityLocality = "Whitefield Main Road, Bengaluru",
    experienceYears = 7,
    verificationIdNumber = "DL-KA53-2017-77182",
    registrationStatus = RegistrationStatus.SUSPENDED,
    statusReason = "Account temporarily suspended due to repeated customer cancellation policy audit.",
    isOnline = false,
    isDemoMode = true,
    rating = 4.20f,
    totalCompletedJobs = 64,
    todayEarningsInr = 0.0,
    activeResponseRatePercent = 75
  )

  // --- Profile State ---
  private val _profile = MutableStateFlow(approvedRajeshProfile)
  override val profile: StateFlow<MechanicProfile> = _profile.asStateFlow()

  private val _isSimulateCompetitorSteal = MutableStateFlow(false)
  override val isSimulateCompetitorSteal: StateFlow<Boolean> = _isSimulateCompetitorSteal.asStateFlow()

  // --- Offers & Job State ---
  private val _currentOffer = MutableStateFlow<JobOffer?>(null)
  override val currentOffer: StateFlow<JobOffer?> = _currentOffer.asStateFlow()

  private val _activeJob = MutableStateFlow<ActiveJob?>(null)
  override val activeJob: StateFlow<ActiveJob?> = _activeJob.asStateFlow()

  private val _todayEarningsInr = MutableStateFlow(2450.0)
  override val todayEarningsInr: StateFlow<Double> = _todayEarningsInr.asStateFlow()

  private val _completedHistory = MutableStateFlow(
    listOf(
      CompletedJobRecord(
        id = "job_hist_in_1",
        requestCode = "FIX-8291",
        completedAtDate = "Today, 11:20 AM",
        vehicleInfo = "Royal Enfield Classic 350",
        serviceType = "Tyre Puncture & Tube Replacement",
        locality = "Indiranagar 100ft Road, near Metro Pillar 42",
        payoutInr = 450.00,
        rating = 5.0f,
        review = "Reached within 12 minutes! Fixed puncture right on road shoulder with safety cones."
      ),
      CompletedJobRecord(
        id = "job_hist_in_2",
        requestCode = "FIX-8240",
        completedAtDate = "Today, 09:15 AM",
        vehicleInfo = "Maruti Suzuki Baleno (2020)",
        serviceType = "12V Battery Jumpstart & Terminal Boost",
        locality = "Outer Ring Road near Embassy TechVillage",
        payoutInr = 650.00,
        rating = 5.0f,
        review = "Car started in 2 minutes. Alternator charging was checked with multimeter. Very polite."
      ),
      CompletedJobRecord(
        id = "job_hist_in_3",
        requestCode = "FIX-8115",
        completedAtDate = "Yesterday, 06:40 PM",
        vehicleInfo = "Honda Activa 6G",
        serviceType = "Clutch & Accelerator Cable Snapped",
        locality = "Koramangala Sony World Junction",
        payoutInr = 400.00,
        rating = 4.9f,
        review = "Replaced broken accelerator wire on the spot. Great emergency support!"
      )
    )
  )
  override val completedHistory: StateFlow<List<CompletedJobRecord>> = _completedHistory.asStateFlow()

  // --- Chat State ---
  private val _messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())

  private var countdownJob: Job? = null

  init {
    // Automatically trigger initial simulated incoming offer for the approved mechanic
    scope.launch {
      delay(1500)
      if (_profile.value.isOnline && _profile.value.registrationStatus == RegistrationStatus.APPROVED && _activeJob.value == null) {
        triggerSimulatedOffer(0)
      }
    }
  }

  // --- Auth Repository Implementation ---
  override suspend fun sendOtp(mobileNumber: String): Result<String> {
    delay(400)
    if (!mobileNumber.startsWith("+91") && mobileNumber.length < 10) {
      return Result.failure(IllegalArgumentException("Please enter a valid 10-digit Indian mobile number."))
    }
    return Result.success("OTP sent to $mobileNumber (Demo OTP: 123456)")
  }

  override suspend fun verifyOtp(mobileNumber: String, otpCode: String): Result<MechanicProfile> {
    delay(500)
    if (otpCode != "123456" && otpCode.length != 6) {
      return Result.failure(IllegalArgumentException("Invalid verification OTP. Enter 123456 for demo."))
    }
    _currentUserMobile.value = mobileNumber
    _isAuthenticated.value = true
    _profile.update { it.copy(mobileNumber = mobileNumber) }
    return Result.success(_profile.value)
  }

  override suspend fun registerMechanic(
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
  ): Result<MechanicProfile> {
    delay(600)
    val newProfile = MechanicProfile(
      id = "mech_" + System.currentTimeMillis(),
      fullName = fullName,
      mobileNumber = mobileNumber,
      email = email,
      workshopName = workshopName,
      vehicleCapability = capability,
      supportedServices = services,
      serviceRadiusKm = radiusKm,
      baseCityLocality = cityLocality,
      experienceYears = experienceYears,
      verificationIdNumber = verificationId,
      registrationStatus = RegistrationStatus.PENDING,
      statusReason = "Registration submitted. KYC documents queued for verification by FixMechanic safety audit team.",
      isOnline = false,
      isDemoMode = true,
      todayEarningsInr = 0.0,
      activeResponseRatePercent = 100
    )
    _profile.value = newProfile
    _currentUserMobile.value = mobileNumber
    _isAuthenticated.value = true
    return Result.success(newProfile)
  }

  override suspend fun switchDemoProfile(profileId: String) {
    when (profileId) {
      "rajesh", "approved" -> {
        _profile.value = approvedRajeshProfile
        _currentUserMobile.value = approvedRajeshProfile.mobileNumber
      }
      "amit", "pending" -> {
        _profile.value = pendingAmitProfile
        _currentUserMobile.value = pendingAmitProfile.mobileNumber
        _currentOffer.value = null
        _activeJob.value = null
        countdownJob?.cancel()
      }
      "suresh", "suspended" -> {
        _profile.value = suspendedSureshProfile
        _currentUserMobile.value = suspendedSureshProfile.mobileNumber
        _currentOffer.value = null
        _activeJob.value = null
        countdownJob?.cancel()
      }
    }
  }

  override suspend fun signOut() {
    _isAuthenticated.value = false
    _currentUserMobile.value = null
    _currentOffer.value = null
    _activeJob.value = null
    countdownJob?.cancel()
  }

  // --- Profile Repository Implementation ---
  override suspend fun updateProfileDetails(
    fullName: String,
    workshopName: String,
    capability: VehicleCapability,
    cityLocality: String,
    radiusKm: Int
  ): Result<Unit> {
    _profile.update {
      it.copy(
        fullName = fullName,
        workshopName = workshopName,
        vehicleCapability = capability,
        baseCityLocality = cityLocality,
        serviceRadiusKm = radiusKm
      )
    }
    return Result.success(Unit)
  }

  override suspend fun updateVehicleCapability(capability: VehicleCapability): Result<Unit> {
    _profile.update { it.copy(vehicleCapability = capability) }
    return Result.success(Unit)
  }

  override suspend fun updateServiceCategories(services: Set<ServiceCategory>): Result<Unit> {
    _profile.update { it.copy(supportedServices = services) }
    return Result.success(Unit)
  }

  override suspend fun updateServiceRadius(radiusKm: Int): Result<Unit> {
    _profile.update { it.copy(serviceRadiusKm = radiusKm) }
    return Result.success(Unit)
  }

  override suspend fun toggleOnlineStatus(online: Boolean): Result<Boolean> {
    val prof = _profile.value
    if (prof.registrationStatus != RegistrationStatus.APPROVED && online) {
      return Result.failure(IllegalStateException("Only APPROVED mechanics can switch Online. Current status: ${prof.registrationStatus.label}"))
    }
    _profile.update { it.copy(isOnline = online) }
    if (!online) {
      _currentOffer.value = null
      countdownJob?.cancel()
    }
    return Result.success(online)
  }

  override suspend fun submitKycDocuments(idNumber: String, docType: String): Result<Unit> {
    delay(400)
    _profile.update {
      it.copy(
        verificationIdNumber = idNumber,
        registrationStatus = RegistrationStatus.PENDING,
        statusReason = "Uploaded $docType. KYC verification under audit review by FixMechanic team."
      )
    }
    return Result.success(Unit)
  }

  override suspend fun toggleDemoMode(enabled: Boolean) {
    _profile.update { it.copy(isDemoMode = enabled) }
  }

  override suspend fun setSimulateCompetitorSteal(enabled: Boolean) {
    _isSimulateCompetitorSteal.value = enabled
  }

  // --- Job Dispatch Repository Implementation ---
  override suspend fun acceptJobOffer(offerId: String): JobAcceptanceResult {
    val offer = _currentOffer.value
    if (offer == null || offer.id != offerId) {
      return JobAcceptanceResult.Expired("This offer has expired or is no longer available.")
    }

    // Mechanic Eligibility check: ONLY APPROVED mechanics who are ONLINE
    val prof = _profile.value
    if (prof.registrationStatus != RegistrationStatus.APPROVED) {
      return JobAcceptanceResult.NotEligible("Your registration status is ${prof.registrationStatus.label}. Only APPROVED mechanics may accept emergency requests.")
    }
    if (!prof.isOnline) {
      return JobAcceptanceResult.NotEligible("You are currently OFFLINE. Toggle Online to accept dispatches.")
    }

    // Server-side assignment validation delay
    delay(600)

    // CRITICAL ASSIGNMENT RULE: If another mechanic won first:
    if (_isSimulateCompetitorSteal.value || offer.simulateTakenByOther) {
      _currentOffer.value = null
      countdownJob?.cancel()
      return JobAcceptanceResult.TakenByOther("This request has already been assigned")
    }

    // Won and confirmed by the authoritative backend
    countdownJob?.cancel()
    _currentOffer.value = null

    val newJob = ActiveJob(
      id = "job_" + offer.id,
      requestCode = offer.requestCode,
      status = JobStatus.ACCEPTED,
      vehicleType = offer.vehicleType,
      vehicleModel = offer.vehicleModel,
      problemCategory = offer.problemCategory,
      problemDescription = offer.problemDescription,
      customerNotes = offer.customerNotes,
      approxDistanceKm = offer.approxDistanceKm,
      locality = offer.locality,
      exactAddress = "Near Pillar #142, Outer Ring Road, Marathahalli flyover service lane, Bengaluru",
      customerDisplayName = "Rohan S.",
      customerSafetyStatus = "Driver in safe standing area beside road divider. Vehicle hazard blinkers on.",
      destinationLat = 12.9562,
      destinationLng = 77.7019,
      currentEtaMinutes = 14,
      estimatedPayoutInr = offer.estimatedPayoutMinInr + ((offer.estimatedPayoutMaxInr - offer.estimatedPayoutMinInr) / 2),
      createdAtEpochMs = System.currentTimeMillis() - 60000L,
      assignedAtEpochMs = System.currentTimeMillis(),
      statusHistory = listOf(
        StatusTimestamp(JobStatus.ACCEPTED, System.currentTimeMillis(), "Offer accepted & server assignment confirmed")
      )
    )

    _activeJob.value = newJob

    // Seed secure private chat
    val initialChat = listOf(
      ChatMessage(
        id = "msg_sys_1",
        jobId = newJob.id,
        senderRole = SenderRole.SYSTEM_DISPATCH,
        senderName = "FixMechanic Relay",
        text = "Emergency request assigned to ${prof.fullName}. Customer contact details remain masked for privacy.",
        timestampEpoch = System.currentTimeMillis()
      ),
      ChatMessage(
        id = "msg_cust_1",
        jobId = newJob.id,
        senderRole = SenderRole.CUSTOMER,
        senderName = "Rohan S.",
        text = "Namaste. I am standing near the flyover pillar with the vehicle. Please let me know your approximate ETA.",
        timestampEpoch = System.currentTimeMillis() + 1000
      )
    )
    _messages.update { it + (newJob.id to initialChat) }

    return JobAcceptanceResult.Assigned(newJob)
  }

  override suspend fun rejectJobOffer(offerId: String): Result<Unit> {
    countdownJob?.cancel()
    _currentOffer.value = null
    return Result.success(Unit)
  }

  override suspend fun advanceJobStatus(jobId: String, nextStatus: JobStatus): Result<JobStatus> {
    val current = _activeJob.value ?: return Result.failure(IllegalStateException("No active job session"))
    if (current.id != jobId) return Result.failure(IllegalArgumentException("Job ID mismatch"))

    // Strict state machine validation: NO ARBITRARY SKIPPING
    val validTransitions = mapOf(
      JobStatus.ACCEPTED to setOf(JobStatus.EN_ROUTE, JobStatus.CANCELLED),
      JobStatus.EN_ROUTE to setOf(JobStatus.ARRIVED, JobStatus.CANCELLED),
      JobStatus.ARRIVED to setOf(JobStatus.IN_PROGRESS, JobStatus.CANCELLED),
      JobStatus.IN_PROGRESS to setOf(JobStatus.COMPLETED)
    )

    val allowedNext = validTransitions[current.status] ?: emptySet()
    if (!allowedNext.contains(nextStatus)) {
      return Result.failure(IllegalStateException("Invalid transition from ${current.status.label} to ${nextStatus.label}. Sequential progression required."))
    }

    delay(300)
    val updatedHistory = current.statusHistory + StatusTimestamp(
      nextStatus,
      System.currentTimeMillis(),
      "Status updated to ${nextStatus.label}"
    )

    val updatedEta = when (nextStatus) {
      JobStatus.ACCEPTED -> 14
      JobStatus.EN_ROUTE -> 8
      JobStatus.ARRIVED -> 0
      JobStatus.IN_PROGRESS -> 0
      JobStatus.COMPLETED -> 0
      JobStatus.CANCELLED -> 0
    }

    val updatedJob = current.copy(
      status = nextStatus,
      currentEtaMinutes = updatedEta,
      statusHistory = updatedHistory
    )
    _activeJob.value = updatedJob

    // System log into private chat
    val systemMsg = ChatMessage(
      id = "msg_sys_" + System.currentTimeMillis(),
      jobId = jobId,
      senderRole = SenderRole.SYSTEM_DISPATCH,
      senderName = "FixMechanic Relay",
      text = "Mechanic updated dispatch status: ${nextStatus.label}",
      timestampEpoch = System.currentTimeMillis()
    )
    _messages.update { currentMap ->
      val list = currentMap[jobId].orEmpty() + systemMsg
      currentMap + (jobId to list)
    }

    return Result.success(nextStatus)
  }

  override suspend fun cancelJob(jobId: String, reason: String): Result<Unit> {
    val current = _activeJob.value ?: return Result.failure(IllegalStateException("No active job"))
    if (current.status == JobStatus.COMPLETED) {
      return Result.failure(IllegalStateException("Cannot cancel a completed job."))
    }
    delay(300)
    val cancelledJob = current.copy(
      status = JobStatus.CANCELLED,
      statusHistory = current.statusHistory + StatusTimestamp(JobStatus.CANCELLED, System.currentTimeMillis(), reason)
    )
    _activeJob.value = null

    val historyItem = CompletedJobRecord(
      id = current.id,
      requestCode = current.requestCode,
      completedAtDate = "Today, Just now",
      vehicleInfo = current.vehicleModel,
      serviceType = current.problemCategory.label,
      locality = current.locality,
      payoutInr = 0.0,
      rating = 0f,
      review = "Cancelled: $reason",
      isCancelled = true
    )
    _completedHistory.update { listOf(historyItem) + it }
    return Result.success(Unit)
  }

  override suspend fun submitJobQuote(jobId: String, quote: JobQuote): Result<JobQuote> {
    val current = _activeJob.value ?: return Result.failure(IllegalStateException("No active job"))
    delay(400)
    val approvedQuote = quote.copy(status = QuoteStatus.APPROVED_BY_CUSTOMER)
    _activeJob.value = current.copy(quote = approvedQuote)

    val quoteMsg = ChatMessage(
      id = "msg_quote_" + System.currentTimeMillis(),
      jobId = jobId,
      senderRole = SenderRole.SYSTEM_DISPATCH,
      senderName = "FixMechanic Billing",
      text = "In-App quote of ₹${"%.0f".format(approvedQuote.totalCostInr)} submitted and authorized by customer.",
      timestampEpoch = System.currentTimeMillis()
    )
    _messages.update { map ->
      map + (jobId to (map[jobId].orEmpty() + quoteMsg))
    }

    return Result.success(approvedQuote)
  }

  override suspend fun finalizeJobCompletion(jobId: String, report: CompletionReport): Result<Unit> {
    val current = _activeJob.value ?: return Result.failure(IllegalStateException("No active job"))
    delay(500)

    val payout = report.finalPayoutAmountInr
    _todayEarningsInr.update { it + payout }

    val newRecord = CompletedJobRecord(
      id = current.id,
      requestCode = current.requestCode,
      completedAtDate = "Today, Just now",
      vehicleInfo = current.vehicleModel,
      serviceType = current.problemCategory.label,
      locality = current.locality,
      payoutInr = payout,
      rating = report.customerRating ?: 5.0f,
      review = report.customerReview ?: "Quick roadside help. Mechanic was very knowledgeable and courteous.",
      isCancelled = false
    )

    _completedHistory.update { listOf(newRecord) + it }
    _activeJob.value = null

    val closedMsg = ChatMessage(
      id = "msg_close_" + System.currentTimeMillis(),
      jobId = jobId,
      senderRole = SenderRole.SYSTEM_DISPATCH,
      senderName = "FixMechanic Security",
      text = "Roadside assistance completed. Private chat session is now closed.",
      timestampEpoch = System.currentTimeMillis()
    )
    _messages.update { map ->
      map + (jobId to (map[jobId].orEmpty() + closedMsg))
    }

    return Result.success(Unit)
  }

  override fun triggerSimulatedOffer(scenarioIndex: Int) {
    countdownJob?.cancel()

    val scenarios = listOf(
      JobOffer(
        id = "off_in_101",
        requestCode = "FIX-9102",
        vehicleType = VehicleType.FOUR_WHEELER,
        vehicleModel = "Hyundai Creta (2021) • Four-Wheeler",
        problemCategory = ServiceCategory.BATTERY_JUMPSTART,
        problemDescription = "Engine starter clicking repeatedly, 12V battery depleted on highway road.",
        customerNotes = "Car is parked in left breakdown lane with hazard blinkers on.",
        approxDistanceKm = 2.8,
        locality = "Outer Ring Road near Marathahalli Bridge, Bengaluru",
        safetyNotes = "Heavy corridor traffic. Use hazard safety cones 20m behind vehicle.",
        estimatedPayoutMinInr = 550.0,
        estimatedPayoutMaxInr = 750.0,
        totalCountdownSeconds = 30,
        remainingCountdownSeconds = 30
      ),
      JobOffer(
        id = "off_in_102",
        requestCode = "FIX-9103",
        vehicleType = VehicleType.TWO_WHEELER,
        vehicleModel = "Bajaj Pulsar 150 • Two-Wheeler",
        problemCategory = ServiceCategory.PUNCTURE_TYRE,
        problemDescription = "Rear tubeless tyre puncture. Sharp nail embedded on tread.",
        customerNotes = "Bike stopped on service road next to bus shelter.",
        approxDistanceKm = 1.6,
        locality = "Koramangala 80ft Road, Sony World Junction, Bengaluru",
        safetyNotes = "Paved flat ground suitable for paddock/centre stand.",
        estimatedPayoutMinInr = 350.0,
        estimatedPayoutMaxInr = 500.0,
        totalCountdownSeconds = 30,
        remainingCountdownSeconds = 30
      ),
      JobOffer(
        id = "off_in_103",
        requestCode = "FIX-9104",
        vehicleType = VehicleType.FOUR_WHEELER,
        vehicleModel = "Maruti Suzuki Swift • Four-Wheeler",
        problemCategory = ServiceCategory.FUEL_DELIVERY,
        problemDescription = "Out of petrol. Engine died approaching flyover ramp.",
        customerNotes = "Need 3 litres petrol in approved container to reach nearest fuel pump.",
        approxDistanceKm = 2.4,
        locality = "Old Airport Road near HAL Main Gate, Bengaluru",
        safetyNotes = "Vehicle positioned safely in curb lane.",
        estimatedPayoutMinInr = 450.0,
        estimatedPayoutMaxInr = 650.0,
        totalCountdownSeconds = 30,
        remainingCountdownSeconds = 30
      )
    )

    val selected = scenarios[scenarioIndex % scenarios.size]
    _currentOffer.value = selected

    countdownJob = scope.launch {
      for (rem in selected.totalCountdownSeconds downTo 0) {
        val current = _currentOffer.value
        if (current == null || current.id != selected.id) break
        _currentOffer.value = current.copy(remainingCountdownSeconds = rem)
        if (rem == 0) {
          _currentOffer.value = null
          break
        }
        delay(1000)
      }
    }
  }

  override fun dismissCurrentOffer() {
    countdownJob?.cancel()
    _currentOffer.value = null
  }

  // --- Chat Repository Implementation ---
  override fun getMessages(jobId: String): Flow<List<ChatMessage>> {
    return _messages.map { it[jobId].orEmpty() }
  }

  override suspend fun sendMessage(jobId: String, text: String, senderName: String): Result<ChatMessage> {
    val job = _activeJob.value ?: return Result.failure(IllegalStateException("No active job session"))
    if (!isChatAllowedForStatus(job.status)) {
      return Result.failure(IllegalStateException("Chat session is closed for status: ${job.status.label}"))
    }

    val msg = ChatMessage(
      id = "msg_" + System.currentTimeMillis(),
      jobId = jobId,
      senderRole = SenderRole.MECHANIC,
      senderName = senderName,
      text = text,
      timestampEpoch = System.currentTimeMillis(),
      status = MessageDeliveryStatus.DELIVERED
    )

    _messages.update { currentMap ->
      val list = currentMap[jobId].orEmpty() + msg
      currentMap + (jobId to list)
    }

    // Auto customer response simulation
    if (text.contains("ETA", ignoreCase = true) || text.contains("way", ignoreCase = true) || text.contains("arrive", ignoreCase = true) || text.contains("reached", ignoreCase = true)) {
      scope.launch {
        delay(1200)
        val reply = ChatMessage(
          id = "msg_reply_" + System.currentTimeMillis(),
          jobId = jobId,
          senderRole = SenderRole.CUSTOMER,
          senderName = job.customerDisplayName,
          text = "Thank you! I see you approaching. Staying safe next to the vehicle.",
          timestampEpoch = System.currentTimeMillis(),
          status = MessageDeliveryStatus.DELIVERED
        )
        _messages.update { map ->
          map + (jobId to (map[jobId].orEmpty() + reply))
        }
      }
    }

    return Result.success(msg)
  }

  override suspend fun retryFailedMessage(messageId: String): Result<Unit> {
    delay(300)
    val jobId = _activeJob.value?.id ?: return Result.failure(IllegalStateException("No active job"))
    _messages.update { map ->
      val currentList = map[jobId].orEmpty()
      val updated = currentList.map { msg ->
        if (msg.id == messageId) msg.copy(status = MessageDeliveryStatus.DELIVERED) else msg
      }
      map + (jobId to updated)
    }
    return Result.success(Unit)
  }

  override fun getQuickCannedResponses(status: JobStatus): List<String> {
    return when (status) {
      JobStatus.ACCEPTED, JobStatus.EN_ROUTE -> listOf(
        "I am en route with emergency equipment, ETA ~10-15 mins.",
        "Please remain safely behind the road divider/barrier.",
        "Keep your vehicle hazard blinkers turned on.",
        "Navigating Bangalore traffic, approaching your location."
      )
      JobStatus.ARRIVED -> listOf(
        "I have arrived and parked safely behind your vehicle.",
        "Setting up emergency safety cones now.",
        "Please remain inside or behind the barrier until I greet you."
      )
      JobStatus.IN_PROGRESS -> listOf(
        "Beginning diagnosis of starter motor and 12V battery.",
        "Inspecting tyre puncture and checking rim bead.",
        "Connecting OBD scanner for electronic diagnostics.",
        "Completing roadside repair and torque checks."
      )
      else -> listOf("Thank you! Stay safe on the road.")
    }
  }

  override fun isChatAllowedForStatus(status: JobStatus): Boolean {
    return status != JobStatus.COMPLETED && status != JobStatus.CANCELLED
  }

  // --- Location Navigation Implementation ---
  override fun openExternalTurnByTurn(context: Context, lat: Double, lng: Double, address: String) {
    try {
      val uri = Uri.parse("google.navigation:q=$lat,$lng")
      val intent = Intent(Intent.ACTION_VIEW, uri)
      intent.setPackage("com.google.android.apps.maps")
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
    } catch (_: Exception) {
      val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(address)})")
      val intent = Intent(Intent.ACTION_VIEW, geoUri)
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
    }
  }

  override fun isLocationPermissionGranted(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
  }

  override fun isGpsProviderEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
  }

  // --- FCM Notification Implementation ---
  override suspend fun getOrRegisterFcmToken(): String {
    return "fcm_token_india_mechanic_${System.currentTimeMillis()}"
  }

  override fun simulatePushAlert(title: String, message: String) {
    // Background push notification trigger logger
  }
}

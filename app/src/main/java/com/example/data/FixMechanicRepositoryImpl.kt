package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FixMechanicRepositoryImpl(
  private val db: FirebaseFirestore?,
  private val auth: FirebaseAuth?,
  private val scope: CoroutineScope
) : AuthRepository, MechanicProfileRepository, JobDispatchRepository, ChatRepository, LocationNavigationService, FcmNotificationService {

  // Pure unit test constructor without Firebase
  constructor(scope: CoroutineScope) : this(null, null, scope)

  // Primary production constructor passing firestore_database_id
  constructor(context: Context, scope: CoroutineScope) : this(
    FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id)),
    FirebaseAuth.getInstance(),
    scope
  )

  // Direct fail-fast accessor for authenticated user's UID
  private fun requireUserId(): String {
    return auth?.currentUser?.uid
      ?: throw IllegalStateException("Mechanic must be authenticated before performing this operation.")
  }

  // --- Auth State ---
  private val _currentUserMobile = MutableStateFlow<String?>(auth?.currentUser?.phoneNumber ?: auth?.currentUser?.email ?: "+91 98765 43210")
  override val currentUserMobile: StateFlow<String?> = _currentUserMobile.asStateFlow()

  private val _isAuthenticated = MutableStateFlow(auth?.currentUser != null)
  override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

  // Default initial profile
  private val defaultProfile = MechanicProfile(
    id = auth?.currentUser?.uid ?: "mech_rajesh_kumar",
    fullName = auth?.currentUser?.displayName ?: "Rajesh Kumar",
    mobileNumber = auth?.currentUser?.phoneNumber ?: "+91 98765 43210",
    email = auth?.currentUser?.email ?: "rajesh.auto@fixmechanic.in",
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
    isDemoMode = false,
    rating = 4.94f,
    totalCompletedJobs = 184,
    todayEarningsInr = 2450.0,
    activeResponseRatePercent = 99
  )

  // --- Profile State ---
  private val _profile = MutableStateFlow(defaultProfile)
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
      )
    )
  )
  override val completedHistory: StateFlow<List<CompletedJobRecord>> = _completedHistory.asStateFlow()

  // In-memory fallback chat map
  private val _messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())

  private var countdownJob: Job? = null
  private var offersListenerRegistration: ListenerRegistration? = null
  private var activeJobListenerRegistration: ListenerRegistration? = null
  private var profileListenerRegistration: ListenerRegistration? = null

  init {
    // Monitor Auth state changes
    auth?.addAuthStateListener { firebaseAuth ->
      val user = firebaseAuth.currentUser
      _isAuthenticated.value = user != null
      if (user != null) {
        _currentUserMobile.value = user.phoneNumber ?: user.email
        attachProfileListener(user.uid)
      } else {
        detachAllListeners()
      }
    }

    if (auth?.currentUser != null) {
      attachProfileListener(auth.currentUser!!.uid)
    } else {
      // In demo mode without login, trigger synthetic sample offer
      scope.launch {
        delay(1500)
        if (_profile.value.isOnline && _profile.value.registrationStatus == RegistrationStatus.APPROVED && _activeJob.value == null) {
          triggerSimulatedOffer(0)
        }
      }
    }
  }

  private fun detachAllListeners() {
    profileListenerRegistration?.remove()
    profileListenerRegistration = null
    offersListenerRegistration?.remove()
    offersListenerRegistration = null
    activeJobListenerRegistration?.remove()
    activeJobListenerRegistration = null
  }

  private fun attachProfileListener(uid: String) {
    val firestore = db ?: return
    profileListenerRegistration?.remove()
    val docRef = firestore.collection("mechanics").document(uid)
    profileListenerRegistration = docRef.addSnapshotListener { snapshot, error ->
      if (error != null) {
        handleFirestoreError(error, OperationType.GET, docRef.path)
        return@addSnapshotListener
      }
      if (snapshot != null && snapshot.exists()) {
        val fullName = snapshot.getString("fullName") ?: _profile.value.fullName
        val mobile = snapshot.getString("mobileNumber") ?: _profile.value.mobileNumber
        val isOnline = snapshot.getBoolean("isOnline") ?: _profile.value.isOnline
        val statusStr = snapshot.getString("registrationStatus") ?: "APPROVED"
        val regStatus = try { RegistrationStatus.valueOf(statusStr) } catch (_: Exception) { RegistrationStatus.APPROVED }
        val activeJobId = snapshot.getString("activeJobId")

        _profile.update {
          it.copy(
            id = uid,
            fullName = fullName,
            mobileNumber = mobile,
            isOnline = isOnline,
            registrationStatus = regStatus,
            activeJobId = activeJobId,
            isDemoMode = false
          )
        }

        // Attach or sync active job
        if (!activeJobId.isNullOrBlank()) {
          attachActiveJobListener(activeJobId)
        } else {
          _activeJob.value = null
          activeJobListenerRegistration?.remove()
          activeJobListenerRegistration = null
          if (isOnline && regStatus == RegistrationStatus.APPROVED) {
            attachOffersListener()
          }
        }
      } else {
        // Document doesn't exist yet: initialize state as pending registration
        _profile.value = defaultProfile.copy(
          fullName = auth?.currentUser?.displayName ?: "FixMechanic Partner",
          email = auth?.currentUser?.email ?: "",
          mobileNumber = auth?.currentUser?.phoneNumber ?: "",
          registrationStatus = RegistrationStatus.PENDING,
          isOnline = false
        )
      }
    }
  }

  private fun attachOffersListener() {
    val firestore = db ?: return
    if (offersListenerRegistration != null) return
    val query = firestore.collection("emergency_requests").whereEqualTo("status", "PENDING_DISPATCH")
    offersListenerRegistration = query.addSnapshotListener { snapshot, error ->
      if (error != null) {
        handleFirestoreError(error, OperationType.LIST, "emergency_requests")
        return@addSnapshotListener
      }
      if (snapshot != null && !snapshot.isEmpty && _activeJob.value == null) {
        val doc = snapshot.documents.first()
        val offer = mapDocToJobOffer(doc)
        _currentOffer.value = offer
      } else if (snapshot != null && snapshot.isEmpty) {
        // If current offer was from live Firestore and is no longer pending, clear it
        if (_currentOffer.value != null && !_currentOffer.value!!.id.startsWith("off_in_")) {
          _currentOffer.value = null
        }
      }
    }
  }

  private fun attachActiveJobListener(jobId: String) {
    val firestore = db ?: return
    activeJobListenerRegistration?.remove()
    val docRef = firestore.collection("emergency_requests").document(jobId)
    activeJobListenerRegistration = docRef.addSnapshotListener { snapshot, error ->
      if (error != null) {
        handleFirestoreError(error, OperationType.GET, docRef.path)
        return@addSnapshotListener
      }
      if (snapshot != null && snapshot.exists()) {
        val job = mapDocToActiveJob(snapshot)
        _activeJob.value = job
        if (job.status == JobStatus.COMPLETED || job.status == JobStatus.CANCELLED) {
          _activeJob.value = null
        }
      }
    }
  }

  private fun mapDocToJobOffer(doc: DocumentSnapshot): JobOffer {
    val id = doc.id
    val reqCode = "FIX-${id.takeLast(4).uppercase()}"
    val vTypeStr = doc.getString("vehicleType") ?: "FOUR_WHEELER"
    val vType = try { VehicleType.valueOf(vTypeStr) } catch (_: Exception) { VehicleType.FOUR_WHEELER }
    val vModel = doc.getString("vehicleModel") ?: "Passenger Vehicle"
    val probCatStr = doc.getString("problemCategory") ?: "PUNCTURE_TYRE"
    val probCat = try { ServiceCategory.valueOf(probCatStr) } catch (_: Exception) { ServiceCategory.PUNCTURE_TYRE }
    val desc = doc.getString("problemDescription") ?: "Roadside emergency breakdown reported"
    val notes = doc.getString("customerNotes") ?: "Parked safely on road shoulder"
    val dist = (doc.get("approxDistanceKm") as? Number)?.toDouble() ?: 2.5
    val loc = doc.getString("locality") ?: "Nearby Roadway"
    val payout = (doc.get("estimatedPayoutInr") as? Number)?.toDouble() ?: 550.0

    return JobOffer(
      id = id,
      requestCode = reqCode,
      vehicleType = vType,
      vehicleModel = vModel,
      problemCategory = probCat,
      problemDescription = desc,
      customerNotes = notes,
      approxDistanceKm = dist,
      locality = loc,
      safetyNotes = "Traffic corridor active. Maintain safe perimeter.",
      estimatedPayoutMinInr = payout * 0.9,
      estimatedPayoutMaxInr = payout * 1.2,
      totalCountdownSeconds = 30,
      remainingCountdownSeconds = 30
    )
  }

  private fun mapDocToActiveJob(doc: DocumentSnapshot): ActiveJob {
    val id = doc.id
    val reqCode = "FIX-${id.takeLast(4).uppercase()}"
    val statusStr = doc.getString("status") ?: "ACCEPTED"
    val status = try { JobStatus.valueOf(statusStr) } catch (_: Exception) { JobStatus.ACCEPTED }
    val vTypeStr = doc.getString("vehicleType") ?: "FOUR_WHEELER"
    val vType = try { VehicleType.valueOf(vTypeStr) } catch (_: Exception) { VehicleType.FOUR_WHEELER }
    val vModel = doc.getString("vehicleModel") ?: "Passenger Vehicle"
    val probCatStr = doc.getString("problemCategory") ?: "PUNCTURE_TYRE"
    val probCat = try { ServiceCategory.valueOf(probCatStr) } catch (_: Exception) { ServiceCategory.PUNCTURE_TYRE }
    val desc = doc.getString("problemDescription") ?: "Roadside breakdown assistance"
    val notes = doc.getString("customerNotes") ?: "Standing by vehicle"
    val dist = (doc.get("approxDistanceKm") as? Number)?.toDouble() ?: 2.5
    val loc = doc.getString("locality") ?: "Bengaluru City Road"
    val exactAddr = doc.getString("exactAddress") ?: "Main corridor, Roadside site"
    val custName = doc.getString("customerDisplayName") ?: "Customer"
    val safety = doc.getString("customerSafetyStatus") ?: "Safe area"
    val lat = (doc.get("destinationLat") as? Number)?.toDouble() ?: 12.9562
    val lng = (doc.get("destinationLng") as? Number)?.toDouble() ?: 77.7019
    val payout = (doc.get("estimatedPayoutInr") as? Number)?.toDouble() ?: 550.0

    val eta = when (status) {
      JobStatus.ACCEPTED -> 14
      JobStatus.EN_ROUTE -> 8
      else -> 0
    }

    return ActiveJob(
      id = id,
      requestCode = reqCode,
      status = status,
      vehicleType = vType,
      vehicleModel = vModel,
      problemCategory = probCat,
      problemDescription = desc,
      customerNotes = notes,
      approxDistanceKm = dist,
      locality = loc,
      exactAddress = exactAddr,
      customerDisplayName = custName,
      customerSafetyStatus = safety,
      destinationLat = lat,
      destinationLng = lng,
      currentEtaMinutes = eta,
      estimatedPayoutInr = payout,
      createdAtEpochMs = System.currentTimeMillis() - 60000L,
      assignedAtEpochMs = System.currentTimeMillis(),
      statusHistory = listOf(StatusTimestamp(status, System.currentTimeMillis(), "Status: ${status.label}"))
    )
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
    val uid = auth?.currentUser?.uid ?: ("mech_" + System.currentTimeMillis())
    val newProfile = MechanicProfile(
      id = uid,
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
      isDemoMode = false,
      todayEarningsInr = 0.0,
      activeResponseRatePercent = 100
    )
    _profile.value = newProfile
    _currentUserMobile.value = mobileNumber
    _isAuthenticated.value = true

    val firestore = db
    if (auth?.currentUser != null && firestore != null) {
      try {
        val docRef = firestore.collection("mechanics").document(uid)
        val existingSnap = docRef.get().await()
        val createdAtVal = if (existingSnap.exists() && existingSnap.getTimestamp("createdAt") != null) {
          existingSnap.getTimestamp("createdAt")
        } else {
          FieldValue.serverTimestamp()
        }
        docRef.set(
          mapOf(
            "userId" to uid,
            "fullName" to fullName,
            "mobileNumber" to mobileNumber,
            "email" to email,
            "workshopName" to workshopName,
            "vehicleCapability" to capability.name,
            "supportedServices" to services.map { it.name },
            "serviceRadiusKm" to radiusKm,
            "baseCityLocality" to cityLocality,
            "experienceYears" to experienceYears,
            "verificationIdNumber" to verificationId,
            "registrationStatus" to "PENDING",
            "statusReason" to "Registration submitted. KYC documents in verification queue.",
            "isOnline" to false,
            "createdAt" to createdAtVal,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).await()
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "mechanics/$uid")
      }
    }

    return Result.success(newProfile)
  }

  override suspend fun switchDemoProfile(profileId: String) {
    when (profileId) {
      "rajesh", "approved" -> {
        _profile.value = defaultProfile.copy(registrationStatus = RegistrationStatus.APPROVED, isOnline = true)
        _currentUserMobile.value = defaultProfile.mobileNumber
      }
      "amit", "pending" -> {
        _profile.value = defaultProfile.copy(registrationStatus = RegistrationStatus.PENDING, isOnline = false)
        _currentOffer.value = null
        _activeJob.value = null
        countdownJob?.cancel()
      }
      "suresh", "suspended" -> {
        _profile.value = defaultProfile.copy(registrationStatus = RegistrationStatus.SUSPENDED, isOnline = false)
        _currentOffer.value = null
        _activeJob.value = null
        countdownJob?.cancel()
      }
    }
  }

  override suspend fun signOut() {
    detachAllListeners()
    auth?.signOut()
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
    val uid = auth?.currentUser?.uid
    val firestore = db
    if (uid != null && firestore != null) {
      try {
        firestore.collection("mechanics").document(uid).update(
          mapOf(
            "fullName" to fullName,
            "workshopName" to workshopName,
            "vehicleCapability" to capability.name,
            "baseCityLocality" to cityLocality,
            "serviceRadiusKm" to radiusKm,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).await()
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "mechanics/$uid")
      }
    }
    return Result.success(Unit)
  }

  override suspend fun updateVehicleCapability(capability: VehicleCapability): Result<Unit> {
    _profile.update { it.copy(vehicleCapability = capability) }
    val uid = auth?.currentUser?.uid
    val firestore = db
    if (uid != null && firestore != null) {
      try {
        firestore.collection("mechanics").document(uid).update("vehicleCapability", capability.name, "updatedAt", FieldValue.serverTimestamp()).await()
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "mechanics/$uid")
      }
    }
    return Result.success(Unit)
  }

  override suspend fun updateServiceCategories(services: Set<ServiceCategory>): Result<Unit> {
    _profile.update { it.copy(supportedServices = services) }
    val uid = auth?.currentUser?.uid
    val firestore = db
    if (uid != null && firestore != null) {
      try {
        firestore.collection("mechanics").document(uid).update(
          "supportedServices", services.map { it.name },
          "updatedAt", FieldValue.serverTimestamp()
        ).await()
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "mechanics/$uid")
      }
    }
    return Result.success(Unit)
  }

  override suspend fun updateServiceRadius(radiusKm: Int): Result<Unit> {
    _profile.update { it.copy(serviceRadiusKm = radiusKm) }
    val uid = auth?.currentUser?.uid
    val firestore = db
    if (uid != null && firestore != null) {
      try {
        firestore.collection("mechanics").document(uid).update("serviceRadiusKm", radiusKm, "updatedAt", FieldValue.serverTimestamp()).await()
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "mechanics/$uid")
      }
    }
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
    } else {
      attachOffersListener()
    }
    val uid = auth?.currentUser?.uid
    val firestore = db
    if (uid != null && firestore != null) {
      try {
        firestore.collection("mechanics").document(uid).update("isOnline", online, "updatedAt", FieldValue.serverTimestamp()).await()
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "mechanics/$uid")
      }
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
    val uid = auth?.currentUser?.uid
    val firestore = db
    if (uid != null && firestore != null) {
      try {
        firestore.collection("mechanics").document(uid).update(
          "verificationIdNumber", idNumber,
          "registrationStatus", "PENDING",
          "statusReason", "Uploaded $docType. KYC verification under audit review.",
          "updatedAt", FieldValue.serverTimestamp()
        ).await()
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "mechanics/$uid")
      }
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
    if (db == null && (offer == null || offer.id != offerId)) {
      return JobAcceptanceResult.Expired("This offer has expired or is no longer available.")
    }

    val prof = _profile.value
    if (prof.registrationStatus != RegistrationStatus.APPROVED) {
      return JobAcceptanceResult.NotEligible("Your registration status is ${prof.registrationStatus.label}. Only APPROVED mechanics may accept emergency requests.")
    }
    if (!prof.isOnline) {
      return JobAcceptanceResult.NotEligible("You are currently OFFLINE. Toggle Online to accept dispatches.")
    }

    // Check if live Firestore backend is connected
    val uid = auth?.currentUser?.uid
    val firestore = db
    if (uid != null && firestore != null && !_isSimulateCompetitorSteal.value && (offer == null || !offer.simulateTakenByOther)) {
      try {
        val docRef = firestore.collection("emergency_requests").document(offerId)
        firestore.runTransaction { tx ->
          val snapshot = tx.get(docRef)
          val status = snapshot.getString("status")
          val assignedMechanicId = snapshot.getString("assignedMechanicId")
          if (status != "PENDING_DISPATCH" || assignedMechanicId != null) {
            throw IllegalStateException("This request has already been assigned")
          }
          tx.update(docRef, mapOf(
            "status" to "ACCEPTED",
            "assignedMechanicId" to uid,
            "assignedMechanicName" to prof.fullName,
            "assignedMechanicMobile" to prof.mobileNumber,
            "updatedAt" to FieldValue.serverTimestamp()
          ))
          val mechRef = firestore.collection("mechanics").document(uid)
          tx.update(mechRef, mapOf(
            "activeJobId" to offerId,
            "updatedAt" to FieldValue.serverTimestamp()
          ))
        }.await()

        countdownJob?.cancel()
        _currentOffer.value = null

        val docSnap = docRef.get().await()
        val acceptedJob = mapDocToActiveJob(docSnap)
        _activeJob.value = acceptedJob
        attachActiveJobListener(offerId)

        return JobAcceptanceResult.Assigned(acceptedJob)
      } catch (e: Exception) {
        var isTaken = false
        var curr: Throwable? = e
        while (curr != null) {
          if (curr.message?.contains("already been assigned") == true ||
            curr.message?.contains("PERMISSION_DENIED") == true ||
            (curr is FirebaseFirestoreException && curr.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
          ) {
            isTaken = true
            break
          }
          curr = curr.cause
        }
        if (isTaken) {
          _currentOffer.value = null
          countdownJob?.cancel()
          return JobAcceptanceResult.TakenByOther("This request has already been assigned")
        }
        handleFirestoreError(e, OperationType.UPDATE, "emergency_requests/$offerId")
        return JobAcceptanceResult.Error(e.localizedMessage ?: "Failed to accept job offer.")
      }
    }

    // Offline / Demo fallback
    val fallbackOffer = offer ?: return JobAcceptanceResult.Expired("This offer has expired or is no longer available.")
    delay(600)
    if (_isSimulateCompetitorSteal.value || fallbackOffer.simulateTakenByOther) {
      _currentOffer.value = null
      countdownJob?.cancel()
      return JobAcceptanceResult.TakenByOther("This request has already been assigned")
    }

    countdownJob?.cancel()
    _currentOffer.value = null

    val newJob = ActiveJob(
      id = "job_" + fallbackOffer.id,
      requestCode = fallbackOffer.requestCode,
      status = JobStatus.ACCEPTED,
      vehicleType = fallbackOffer.vehicleType,
      vehicleModel = fallbackOffer.vehicleModel,
      problemCategory = fallbackOffer.problemCategory,
      problemDescription = fallbackOffer.problemDescription,
      customerNotes = fallbackOffer.customerNotes,
      approxDistanceKm = fallbackOffer.approxDistanceKm,
      locality = fallbackOffer.locality,
      exactAddress = "Near Pillar #142, Outer Ring Road, Marathahalli flyover service lane, Bengaluru",
      customerDisplayName = "Rohan S.",
      customerSafetyStatus = "Driver in safe standing area beside road divider. Vehicle hazard blinkers on.",
      destinationLat = 12.9562,
      destinationLng = 77.7019,
      currentEtaMinutes = 14,
      estimatedPayoutInr = fallbackOffer.estimatedPayoutMinInr + ((fallbackOffer.estimatedPayoutMaxInr - fallbackOffer.estimatedPayoutMinInr) / 2),
      createdAtEpochMs = System.currentTimeMillis() - 60000L,
      assignedAtEpochMs = System.currentTimeMillis(),
      statusHistory = listOf(
        StatusTimestamp(JobStatus.ACCEPTED, System.currentTimeMillis(), "Offer accepted & server assignment confirmed")
      )
    )

    _activeJob.value = newJob
    return JobAcceptanceResult.Assigned(newJob)
  }

  override suspend fun rejectJobOffer(offerId: String): Result<Unit> {
    countdownJob?.cancel()
    _currentOffer.value = null
    return Result.success(Unit)
  }

  override suspend fun advanceJobStatus(jobId: String, nextStatus: JobStatus): Result<JobStatus> {
    val current = _activeJob.value ?: return Result.failure(IllegalStateException("No active job session"))
    if (current.id != jobId && !jobId.contains(current.id)) return Result.failure(IllegalArgumentException("Job ID mismatch"))

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

    val uid = auth?.currentUser?.uid
    val firestore = db
    if (uid != null && firestore != null && !jobId.startsWith("job_off_")) {
      try {
        val docRef = firestore.collection("emergency_requests").document(jobId)
        docRef.update(
          mapOf(
            "status" to nextStatus.name,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).await()

        if (nextStatus == JobStatus.COMPLETED) {
          firestore.collection("mechanics").document(uid).update(
            mapOf(
              "activeJobId" to null,
              "totalCompletedJobs" to FieldValue.increment(1),
              "todayEarningsInr" to FieldValue.increment(current.estimatedPayoutInr),
              "updatedAt" to FieldValue.serverTimestamp()
            )
          ).await()
        }
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "emergency_requests/$jobId")
      }
    }

    val updatedHistory = current.statusHistory + StatusTimestamp(nextStatus, System.currentTimeMillis(), "Status updated to ${nextStatus.label}")
    val updatedJob = current.copy(
      status = nextStatus,
      statusHistory = updatedHistory
    )
    _activeJob.value = updatedJob

    return Result.success(nextStatus)
  }

  override suspend fun cancelJob(jobId: String, reason: String): Result<Unit> {
    val current = _activeJob.value ?: return Result.failure(IllegalStateException("No active job"))
    if (current.status == JobStatus.COMPLETED) {
      return Result.failure(IllegalStateException("Cannot cancel a completed job."))
    }

    val uid = auth?.currentUser?.uid
    val firestore = db
    if (uid != null && firestore != null && !jobId.startsWith("job_off_")) {
      try {
        firestore.collection("emergency_requests").document(jobId).update(
          mapOf(
            "status" to "CANCELLED",
            "cancellationReason" to reason,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).await()
        firestore.collection("mechanics").document(uid).update("activeJobId", null, "updatedAt", FieldValue.serverTimestamp()).await()
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "emergency_requests/$jobId")
      }
    }

    _activeJob.value = null
    return Result.success(Unit)
  }

  override suspend fun submitJobQuote(jobId: String, quote: JobQuote): Result<JobQuote> {
    val current = _activeJob.value ?: return Result.failure(IllegalStateException("No active job"))
    val approvedQuote = quote.copy(status = QuoteStatus.APPROVED_BY_CUSTOMER)
    _activeJob.value = current.copy(quote = approvedQuote)
    return Result.success(approvedQuote)
  }

  override suspend fun finalizeJobCompletion(jobId: String, report: CompletionReport): Result<Unit> {
    val current = _activeJob.value ?: return Result.failure(IllegalStateException("No active job"))
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
  override fun getMessages(jobId: String): Flow<List<ChatMessage>> = callbackFlow {
    val uid = auth?.currentUser?.uid
    val firestore = db
    if (uid != null && firestore != null && !jobId.startsWith("job_off_")) {
      val messagesRef = firestore.collection("emergency_requests").document(jobId).collection("messages")
      val registration = messagesRef.addSnapshotListener { snapshot, error ->
        if (error != null) {
          handleFirestoreError(error, OperationType.LIST, messagesRef.path)
          close(error)
          return@addSnapshotListener
        }
        if (snapshot != null) {
          val list = snapshot.documents.map { doc ->
            val roleStr = doc.getString("senderRole") ?: "CUSTOMER"
            val role = try { SenderRole.valueOf(roleStr) } catch (_: Exception) { SenderRole.CUSTOMER }
            ChatMessage(
              id = doc.id,
              jobId = jobId,
              senderRole = role,
              senderName = doc.getString("senderName") ?: "User",
              text = doc.getString("text") ?: "",
              timestampEpoch = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
              status = MessageDeliveryStatus.DELIVERED
            )
          }.sortedBy { it.timestampEpoch }
          trySend(list)
        }
      }
      awaitClose { registration.remove() }
    } else {
      // In-memory fallback
      val initial = _messages.value[jobId].orEmpty()
      trySend(initial)
      awaitClose {}
    }
  }

  override suspend fun sendMessage(jobId: String, text: String, senderName: String): Result<ChatMessage> {
    val job = _activeJob.value ?: return Result.failure(IllegalStateException("No active job session"))
    if (!isChatAllowedForStatus(job.status)) {
      return Result.failure(IllegalStateException("Chat session is closed for status: ${job.status.label}"))
    }

    val uid = auth?.currentUser?.uid
    val firestore = db
    if (uid != null && firestore != null && !jobId.startsWith("job_off_")) {
      val messagesRef = firestore.collection("emergency_requests").document(jobId).collection("messages")
      try {
        val payload = mapOf(
          "jobId" to jobId,
          "senderId" to uid,
          "senderRole" to SenderRole.MECHANIC.name,
          "senderName" to senderName,
          "text" to text,
          "createdAt" to FieldValue.serverTimestamp()
        )
        val docRef = messagesRef.add(payload).await()
        val sentMsg = ChatMessage(
          id = docRef.id,
          jobId = jobId,
          senderRole = SenderRole.MECHANIC,
          senderName = senderName,
          text = text,
          timestampEpoch = System.currentTimeMillis(),
          status = MessageDeliveryStatus.DELIVERED
        )
        return Result.success(sentMsg)
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, messagesRef.path)
        return Result.failure(e)
      }
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
    return Result.success(msg)
  }

  override suspend fun retryFailedMessage(messageId: String): Result<Unit> {
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
  }
}

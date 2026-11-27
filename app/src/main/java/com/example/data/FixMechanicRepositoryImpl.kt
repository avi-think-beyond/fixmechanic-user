package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.FirestoreProvider.awaitResult
import com.example.model.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FixMechanicRepositoryImpl(
  private val db: FirebaseFirestore?,
  private val auth: FirebaseAuth?,
  private val backendApiClient: BackendApiClient?,
  private val scope: CoroutineScope
) : AuthRepository, MechanicProfileRepository, JobDispatchRepository, ChatRepository, LocationNavigationService, FcmNotificationService {

  // Pure unit-test constructor (no Firebase or Spark backend)
  constructor(scope: CoroutineScope) : this(null, null, null, scope)

  // Emulator / Rule test constructor
  constructor(
    db: FirebaseFirestore?,
    auth: FirebaseAuth?,
    scope: CoroutineScope,
    backendBaseUrl: String = com.example.BuildConfig.BACKEND_BASE_URL
  ) : this(db, auth, auth?.let { BackendApiClient(it, backendBaseUrl) }, scope)

  // Production constructor using unified `(default)` database
  constructor(context: Context, scope: CoroutineScope) : this(
    db = createFirestore(context),
    auth = createAuth(),
    backendApiClient = createBackendApiClient(createAuth()),
    scope = scope
  )

  private fun requireUserId(): String {
    return auth?.currentUser?.uid
      ?: throw IllegalStateException("Mechanic must be authenticated before performing this operation.")
  }

  private val _currentUserMobile = MutableStateFlow<String?>(auth?.currentUser?.phoneNumber ?: auth?.currentUser?.email)
  override val currentUserMobile: StateFlow<String?> = _currentUserMobile.asStateFlow()

  private val _isAuthenticated = MutableStateFlow(auth?.currentUser != null)
  override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

  private val defaultProfile = MechanicProfile(
    id = auth?.currentUser?.uid ?: "",
    fullName = auth?.currentUser?.displayName ?: "FixMechanic Partner",
    mobileNumber = auth?.currentUser?.phoneNumber ?: "",
    email = auth?.currentUser?.email ?: "",
    workshopName = "FixMechanic Partner Workshop",
    vehicleCapability = VehicleCapability.BOTH,
    supportedServices = setOf(ServiceCategory.PUNCTURE_TYRE, ServiceCategory.BATTERY_JUMPSTART),
    serviceRadiusKm = 15,
    baseCityLocality = "Gurugram Sector 29",
    experienceYears = 1,
    verificationIdNumber = "",
    registrationStatus = RegistrationStatus.PENDING,
    statusReason = "Complete workshop onboarding to start verification.",
    isOnline = false,
    isDemoMode = false,
    rating = 5.0f,
    totalCompletedJobs = 0,
    todayEarningsInr = 0.0,
    activeResponseRatePercent = 100
  )

  private val _profile = MutableStateFlow(defaultProfile)
  override val profile: StateFlow<MechanicProfile> = _profile.asStateFlow()

  private val _isSimulateCompetitorSteal = MutableStateFlow(false)
  override val isSimulateCompetitorSteal: StateFlow<Boolean> = _isSimulateCompetitorSteal.asStateFlow()

  private val _currentOffer = MutableStateFlow<JobOffer?>(null)
  override val currentOffer: StateFlow<JobOffer?> = _currentOffer.asStateFlow()

  private val _activeJob = MutableStateFlow<ActiveJob?>(null)
  override val activeJob: StateFlow<ActiveJob?> = _activeJob.asStateFlow()

  private val _todayEarningsInr = MutableStateFlow(0.0)
  override val todayEarningsInr: StateFlow<Double> = _todayEarningsInr.asStateFlow()

  private val _completedHistory = MutableStateFlow<List<CompletedJobRecord>>(emptyList())
  override val completedHistory: StateFlow<List<CompletedJobRecord>> = _completedHistory.asStateFlow()

  private val _messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
  private var countdownJob: Job? = null
  private var offersListenerRegistration: ListenerRegistration? = null
  private var activeJobListenerRegistration: ListenerRegistration? = null
  private var profileListenerRegistration: ListenerRegistration? = null
  private var historyListenerRegistration: ListenerRegistration? = null

  private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
    val user = firebaseAuth.currentUser
    _isAuthenticated.value = user != null
    if (user != null) {
      _currentUserMobile.value = user.phoneNumber ?: user.email
      attachProfileListener(user.uid)
      attachHistoryListener(user.uid)
      scope.launch { ensureUserDocument(user.uid, user.email, user.displayName, user.phoneNumber) }
    } else {
      detachAllListeners()
      _currentOffer.value = null
      _activeJob.value = null
      _completedHistory.value = emptyList()
      _todayEarningsInr.value = 0.0
      _profile.value = defaultProfile
    }
  }

  init {
    if (auth != null) {
      auth.addAuthStateListener(authStateListener)
      auth.currentUser?.let { user ->
        attachProfileListener(user.uid)
        attachHistoryListener(user.uid)
      }
    }
  }

  private suspend fun ensureUserDocument(
    uid: String,
    email: String?,
    displayName: String?,
    phone: String?
  ) {
    val firestore = db ?: return
    runCatching {
      val userRef = firestore.collection("users").document(uid)
      val snap = userRef.get().awaitResult()
      if (!snap.exists()) {
        val now = System.currentTimeMillis()
        userRef.set(
          mapOf(
            "uid" to uid,
            "role" to "MECHANIC",
            "email" to (email ?: ""),
            "displayName" to (displayName ?: "FixMechanic Partner"),
            "phone" to (phone ?: ""),
            "disabled" to false,
            "createdAt" to now,
            "updatedAt" to now
          )
        ).awaitResult()
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
    historyListenerRegistration?.remove()
    historyListenerRegistration = null
  }

  private fun attachProfileListener(uid: String) {
    val firestore = db ?: return
    profileListenerRegistration?.remove()
    val docRef = firestore.collection("mechanicProfiles").document(uid)
    profileListenerRegistration = docRef.addSnapshotListener { snapshot, error ->
      if (error != null) {
        handleFirestoreError(error, OperationType.GET, docRef.path)
        return@addSnapshotListener
      }
      if (snapshot != null && snapshot.exists()) {
        val name = snapshot.getString("name") ?: snapshot.getString("fullName") ?: _profile.value.fullName
        val phone = snapshot.getString("phone") ?: snapshot.getString("mobileNumber") ?: _profile.value.mobileNumber
        val email = snapshot.getString("email") ?: auth?.currentUser?.email ?: _profile.value.email
        val workshop = snapshot.getString("workshopName") ?: _profile.value.workshopName
        val vTypes = (snapshot.get("vehicleTypes") as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        val capability = VehicleCapability.fromBackendVehicleTypes(vTypes)
        val rawCats = (snapshot.get("serviceCategories") as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        val services = rawCats.map { ServiceCategory.fromBackendCode(it) }.toSet()
          .ifEmpty { setOf(ServiceCategory.PUNCTURE_TYRE) }
        val radiusKm = (snapshot.getLong("serviceRadiusKm") ?: 15L).toInt()
        val locMap = snapshot.get("currentLocation") as? Map<*, *>
        val address = locMap?.get("address")?.toString() ?: _profile.value.baseCityLocality
        val isOnline = snapshot.getBoolean("isOnline") == true
        val statusStr = (snapshot.getString("approvalStatus") ?: "PENDING").uppercase()
        val regStatus = runCatching { RegistrationStatus.valueOf(statusStr) }.getOrDefault(RegistrationStatus.PENDING)
        val statusReason = snapshot.getString("statusReason")
          ?: snapshot.getString("rejectionReason")
          ?: when (regStatus) {
            RegistrationStatus.APPROVED -> "Verified & cleared for live roadside dispatch."
            RegistrationStatus.PENDING -> "Verification pending Ops Admin approval."
            RegistrationStatus.REJECTED -> "Verification rejected. Update KYC documents."
            RegistrationStatus.SUSPENDED -> "Account suspended by safety operations."
          }
        val activeJobId = snapshot.getString("activeJobId")
        val rating = (snapshot.get("ratingAverage") as? Number)?.toFloat() ?: 5.0f
        val totalJobs = (snapshot.get("totalCompletedJobs") as? Number)?.toInt() ?: 0
        val todayEarnings = (snapshot.get("todayEarningsInr") as? Number)?.toDouble() ?: 0.0

        _todayEarningsInr.value = todayEarnings
        _profile.update {
          it.copy(
            id = uid,
            fullName = name,
            mobileNumber = phone,
            email = email,
            workshopName = workshop,
            vehicleCapability = capability,
            supportedServices = services,
            serviceRadiusKm = radiusKm,
            baseCityLocality = address,
            registrationStatus = regStatus,
            statusReason = statusReason,
            isOnline = isOnline,
            isDemoMode = false,
            rating = rating,
            totalCompletedJobs = totalJobs,
            todayEarningsInr = todayEarnings,
            activeJobId = activeJobId
          )
        }

        if (!activeJobId.isNullOrBlank()) {
          attachActiveJobListener(activeJobId)
          offersListenerRegistration?.remove()
          offersListenerRegistration = null
          _currentOffer.value = null
        } else {
          _activeJob.value = null
          activeJobListenerRegistration?.remove()
          activeJobListenerRegistration = null
          if (isOnline && regStatus == RegistrationStatus.APPROVED) {
            attachOffersListener(uid)
          } else {
            offersListenerRegistration?.remove()
            offersListenerRegistration = null
            _currentOffer.value = null
          }
        }
      } else {
        _profile.value = defaultProfile.copy(
          id = uid,
          fullName = auth?.currentUser?.displayName ?: "FixMechanic Partner",
          email = auth?.currentUser?.email ?: "",
          mobileNumber = auth?.currentUser?.phoneNumber ?: "",
          registrationStatus = RegistrationStatus.PENDING,
          isOnline = false,
          isDemoMode = false
        )
      }
    }
  }

  private fun attachOffersListener(uid: String) {
    val firestore = db ?: return
    if (offersListenerRegistration != null) return
    val offersQuery = firestore.collection("jobOffers")
      .whereEqualTo("mechanicId", uid)
      .whereEqualTo("status", "PENDING")

    offersListenerRegistration = offersQuery.addSnapshotListener { snapshot, error ->
      if (error != null || snapshot == null) return@addSnapshotListener
      val now = System.currentTimeMillis()
      val validDoc = snapshot.documents
        .filter { doc ->
          val expiresAt = extractEpochMillis(doc.get("expiresAt"))
          expiresAt > now
        }
        .minByOrNull { doc -> (doc.get("distanceKm") as? Number)?.toDouble() ?: Double.MAX_VALUE }

      if (validDoc == null) {
        if (_currentOffer.value?.requestId?.isNotBlank() == true) {
          countdownJob?.cancel()
          _currentOffer.value = null
        }
        return@addSnapshotListener
      }

      val mapped = mapDocToJobOffer(validDoc)
      if (_currentOffer.value?.id != mapped.id) {
        _currentOffer.value = mapped
        startOfferCountdown(mapped)
      }
    }
  }

  private fun startOfferCountdown(offer: JobOffer) {
    countdownJob?.cancel()
    countdownJob = scope.launch {
      while (true) {
        val current = _currentOffer.value
        if (current == null || current.id != offer.id) break
        val remSec = ((offer.expiresAtEpochMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
        _currentOffer.value = current.copy(remainingCountdownSeconds = remSec)
        if (remSec <= 0) {
          _currentOffer.value = null
          break
        }
        delay(1000L)
      }
    }
  }

  private fun attachActiveJobListener(requestId: String) {
    val firestore = db ?: return
    activeJobListenerRegistration?.remove()
    val docRef = firestore.collection("serviceRequests").document(requestId)
    activeJobListenerRegistration = docRef.addSnapshotListener { snapshot, error ->
      if (error != null) {
        handleFirestoreError(error, OperationType.GET, docRef.path)
        return@addSnapshotListener
      }
      if (snapshot != null && snapshot.exists()) {
        val job = mapDocToActiveJob(snapshot)
        if (job.status == JobStatus.COMPLETED || job.status == JobStatus.CANCELLED) {
          _activeJob.value = null
        } else {
          _activeJob.value = job
        }
      }
    }
  }

  private fun attachHistoryListener(uid: String) {
    val firestore = db ?: return
    historyListenerRegistration?.remove()
    historyListenerRegistration = firestore.collection("serviceRequests")
      .whereEqualTo("assignedMechanicId", uid)
      .addSnapshotListener { snap, err ->
        if (err != null || snap == null) return@addSnapshotListener
        val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        val items = snap.documents.mapNotNull { doc ->
          val st = (doc.getString("status") ?: "").uppercase()
          if (st != "COMPLETED" && st != "CANCELLED") return@mapNotNull null
          val loc = doc.get("location") as? Map<*, *>
          val ts = extractEpochMillis(doc.get("completedAt") ?: doc.get("updatedAt") ?: doc.get("createdAt"))
          val id = doc.id
          CompletedJobRecord(
            id = id,
            requestCode = "FIX-${id.takeLast(4).uppercase()}",
            completedAtDate = fmt.format(Date(ts)),
            vehicleInfo = doc.getString("vehicleMakeModel") ?: doc.getString("vehicleType") ?: "Vehicle",
            serviceType = doc.getString("issueTitle") ?: doc.getString("serviceCategory") ?: "Roadside Assist",
            locality = loc?.get("address")?.toString() ?: "Gurugram",
            payoutInr = (doc.get("totalPaid") as? Number)?.toDouble()
              ?: (doc.get("estimatedPriceInr") as? Number)?.toDouble() ?: 349.0,
            rating = (doc.get("ratingGiven") as? Number)?.toFloat() ?: 5.0f,
            review = doc.getString("feedbackComment") ?: "Completed roadside assistance",
            isCancelled = st == "CANCELLED"
          )
        }.sortedByDescending { it.id }
        _completedHistory.value = items
      }
  }

  private fun mapDocToJobOffer(doc: DocumentSnapshot): JobOffer {
    val id = doc.id
    val requestId = doc.getString("requestId") ?: id
    val reqCode = "FIX-${requestId.takeLast(4).uppercase()}"
    val vType = VehicleType.fromBackendCode(doc.getString("vehicleType"))
    val vModel = doc.getString("vehicleMakeModel") ?: vType.label
    val probCat = ServiceCategory.fromBackendCode(doc.getString("serviceCategory") ?: doc.getString("issueTitle"))
    val issueTitle = doc.getString("issueTitle") ?: probCat.label
    val dist = (doc.get("distanceKm") as? Number)?.toDouble() ?: 1.5
    val loc = doc.getString("locality") ?: "Nearby Pilot Zone"
    val payout = (doc.get("estimatedPriceInr") as? Number)?.toDouble() ?: 399.0
    val now = System.currentTimeMillis()
    val expiresAt = extractEpochMillis(doc.get("expiresAt")).let { if (it <= 0L) now + 45000L else it }
    val remSec = ((expiresAt - now) / 1000L).toInt().coerceAtLeast(1)
    return JobOffer(
      id = id,
      requestId = requestId,
      requestCode = reqCode,
      vehicleType = vType,
      vehicleModel = vModel,
      problemCategory = probCat,
      problemDescription = issueTitle,
      customerNotes = "Roadside breakdown request (~${dist} km away)",
      approxDistanceKm = dist,
      locality = loc,
      safetyNotes = "Wear high-visibility safety gear and follow roadside safety rules.",
      estimatedPayoutMinInr = payout,
      estimatedPayoutMaxInr = payout + 150.0,
      totalCountdownSeconds = remSec,
      remainingCountdownSeconds = remSec,
      expiresAtEpochMs = expiresAt
    )
  }

  private fun mapDocToActiveJob(doc: DocumentSnapshot): ActiveJob {
    val id = doc.id
    val reqCode = "FIX-${id.takeLast(4).uppercase()}"
    val statusStr = (doc.getString("status") ?: "ASSIGNED").uppercase()
    val status = runCatching { JobStatus.valueOf(statusStr) }.getOrDefault(JobStatus.ASSIGNED)
    val vType = VehicleType.fromBackendCode(doc.getString("vehicleType"))
    val vModel = doc.getString("vehicleMakeModel") ?: vType.label
    val regNo = doc.getString("vehicleRegNumber")
    val modelLabel = if (!regNo.isNullOrBlank() && regNo != "UNREGISTERED") "$vModel ($regNo)" else vModel
    val probCat = ServiceCategory.fromBackendCode(doc.getString("serviceCategory") ?: doc.getString("issueTitle"))
    val desc = doc.getString("description")?.takeIf { it.isNotBlank() } ?: doc.getString("issueTitle") ?: probCat.label
    val locMap = doc.get("location") as? Map<*, *>
    val exactAddr = locMap?.get("address")?.toString() ?: "GPS Breakdown Location"
    val locality = locMap?.get("city")?.toString() ?: "Gurugram"
    val lat = (locMap?.get("lat") as? Number)?.toDouble() ?: 28.4595
    val lng = (locMap?.get("lng") as? Number)?.toDouble() ?: 77.0266
    val custName = doc.getString("customerName") ?: "Customer"
    val landmark = locMap?.get("landmark")?.toString().orEmpty()
    val payout = (doc.get("estimatedPriceInr") as? Number)?.toDouble() ?: 349.0
    val createdAt = extractEpochMillis(doc.get("createdAt"))
    val eta = when (status) {
      JobStatus.ASSIGNED, JobStatus.ACCEPTED -> 12
      JobStatus.EN_ROUTE -> 7
      else -> 0
    }
    return ActiveJob(
      id = id,
      requestCode = reqCode,
      status = status,
      vehicleType = vType,
      vehicleModel = modelLabel,
      problemCategory = probCat,
      problemDescription = desc,
      customerNotes = if (landmark.isNotBlank()) "Landmark: $landmark" else "Customer awaiting roadside assistance",
      approxDistanceKm = 2.0,
      locality = locality,
      exactAddress = exactAddr,
      customerDisplayName = custName,
      customerSafetyStatus = "Verified FixMechanic Dispatch",
      destinationLat = lat,
      destinationLng = lng,
      currentEtaMinutes = eta,
      estimatedPayoutInr = payout,
      createdAtEpochMs = createdAt,
      assignedAtEpochMs = System.currentTimeMillis(),
      statusHistory = listOf(StatusTimestamp(status, System.currentTimeMillis() / 1000L, "Status: ${status.label}"))
    )
  }

  private fun extractEpochMillis(raw: Any?): Long {
    return when (raw) {
      is Number -> raw.toLong()
      is Timestamp -> raw.toDate().time
      else -> 0L
    }
  }

  // --- Auth Repository Implementation ---

  override suspend fun signInWithEmail(email: String, password: String): Result<MechanicProfile> = runCatching {
    val firebaseAuth = auth ?: error("FirebaseAuth unavailable")
    val cred = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).awaitResult()
    val uid = cred.user?.uid ?: error("Auth returned null UID")
    ensureUserDocument(uid, cred.user?.email, cred.user?.displayName, cred.user?.phoneNumber)
    _isAuthenticated.value = true
    _currentUserMobile.value = email.trim()
    _profile.value.copy(id = uid, email = email.trim())
  }

  override suspend fun registerWithEmail(email: String, password: String): Result<Unit> = runCatching {
    val firebaseAuth = auth ?: error("FirebaseAuth unavailable")
    val cred = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).awaitResult()
    val uid = cred.user?.uid ?: error("Auth returned null UID")
    ensureUserDocument(uid, cred.user?.email, cred.user?.displayName, cred.user?.phoneNumber)
    _isAuthenticated.value = true
  }

  override suspend fun sendOtp(mobileNumber: String): Result<String> {
    return Result.failure(
      UnsupportedOperationException(
        "Phone SMS OTP requires Firebase Blaze billing. Use Google Sign-In or Email & Password on the Spark plan."
      )
    )
  }

  override suspend fun verifyOtp(mobileNumber: String, otpCode: String): Result<MechanicProfile> {
    return Result.failure(
      UnsupportedOperationException(
        "Phone SMS OTP verification requires Firebase Blaze billing. Use Google Sign-In or Email & Password."
      )
    )
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
  ): Result<MechanicProfile> = runCatching {
    val uid = auth?.currentUser?.uid
      ?: throw IllegalStateException("Please sign in with Google or Email before submitting partner registration.")
    val now = System.currentTimeMillis()
    val backendVehicleTypes = capability.toBackendVehicleTypes()
    val backendCategories = services.map { it.toBackendCode() }.ifEmpty { listOf("FLAT_TYRE") }
    val newProfile = MechanicProfile(
      id = uid,
      fullName = fullName.trim(),
      mobileNumber = mobileNumber.trim(),
      email = email.trim(),
      workshopName = workshopName.trim(),
      vehicleCapability = capability,
      supportedServices = services,
      serviceRadiusKm = radiusKm,
      baseCityLocality = cityLocality.trim(),
      experienceYears = experienceYears,
      verificationIdNumber = verificationId.trim(),
      registrationStatus = RegistrationStatus.PENDING,
      statusReason = "Registration submitted. Pending Operations Admin approval.",
      isOnline = false,
      isDemoMode = false,
      todayEarningsInr = 0.0,
      activeResponseRatePercent = 100
    )

    val firestore = db
    if (firestore != null) {
      ensureUserDocument(uid, email.trim(), fullName.trim(), mobileNumber.trim())
      val docRef = firestore.collection("mechanicProfiles").document(uid)
      val existing = docRef.get().awaitResult()
      if (!existing.exists()) {
        docRef.set(
          mapOf(
            "mechanicId" to uid,
            "name" to fullName.trim(),
            "phone" to mobileNumber.trim(),
            "email" to email.trim(),
            "workshopName" to workshopName.trim(),
            "vehicleTypes" to backendVehicleTypes,
            "serviceCategories" to backendCategories,
            "serviceRadiusKm" to radiusKm,
            "experienceYears" to experienceYears,
            "verificationIdNumber" to verificationId.trim(),
            "approvalStatus" to "PENDING",
            "isOnline" to false,
            "isBusy" to false,
            "activeJobId" to null,
            "currentLocation" to mapOf(
              "lat" to 28.4595,
              "lng" to 77.0266,
              "address" to cityLocality.trim(),
              "geohash" to "ttnf4",
              "updatedAt" to now
            ),
            "ratingAverage" to 5.0,
            "ratingCount" to 0,
            "totalCompletedJobs" to 0,
            "todayEarningsInr" to 0.0,
            "createdAt" to now,
            "updatedAt" to now
          )
        ).awaitResult()
      } else {
        // Update ONLY non-privileged fields so security rules pass
        docRef.update(
          mapOf(
            "name" to fullName.trim(),
            "phone" to mobileNumber.trim(),
            "email" to email.trim(),
            "workshopName" to workshopName.trim(),
            "vehicleTypes" to backendVehicleTypes,
            "serviceCategories" to backendCategories,
            "serviceRadiusKm" to radiusKm,
            "experienceYears" to experienceYears,
            "verificationIdNumber" to verificationId.trim(),
            "currentLocation.address" to cityLocality.trim(),
            "updatedAt" to now
          )
        ).awaitResult()
      }
    }
    _profile.value = newProfile
    _currentUserMobile.value = mobileNumber.trim()
    _isAuthenticated.value = true
    newProfile
  }

  override suspend fun switchDemoProfile(profileId: String) {
    // No-op in production/unified backend mode; real auth dictates profile state
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
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val firestore = db
    if (firestore != null) {
      firestore.collection("mechanicProfiles").document(uid).update(
        mapOf(
          "name" to fullName.trim(),
          "workshopName" to workshopName.trim(),
          "vehicleTypes" to capability.toBackendVehicleTypes(),
          "serviceRadiusKm" to radiusKm,
          "currentLocation.address" to cityLocality.trim(),
          "updatedAt" to System.currentTimeMillis()
        )
      ).awaitResult()
    }
    _profile.update {
      it.copy(
        fullName = fullName.trim(),
        workshopName = workshopName.trim(),
        vehicleCapability = capability,
        baseCityLocality = cityLocality.trim(),
        serviceRadiusKm = radiusKm
      )
    }
  }

  override suspend fun updateVehicleCapability(capability: VehicleCapability): Result<Unit> = runCatching {
    val uid = requireUserId()
    db?.collection("mechanicProfiles")?.document(uid)?.update(
      mapOf(
        "vehicleTypes" to capability.toBackendVehicleTypes(),
        "updatedAt" to System.currentTimeMillis()
      )
    )?.awaitResult()
    _profile.update { it.copy(vehicleCapability = capability) }
  }

  override suspend fun updateServiceCategories(categories: Set<ServiceCategory>): Result<Unit> = runCatching {
    val uid = requireUserId()
    val codes = categories.map { it.toBackendCode() }.ifEmpty { listOf("FLAT_TYRE") }
    db?.collection("mechanicProfiles")?.document(uid)?.update(
      mapOf(
        "serviceCategories" to codes,
        "updatedAt" to System.currentTimeMillis()
      )
    )?.awaitResult()
    _profile.update { it.copy(supportedServices = categories) }
  }

  override suspend fun updateServiceRadius(radiusKm: Int): Result<Unit> = runCatching {
    val uid = requireUserId()
    db?.collection("mechanicProfiles")?.document(uid)?.update(
      mapOf(
        "serviceRadiusKm" to radiusKm,
        "updatedAt" to System.currentTimeMillis()
      )
    )?.awaitResult()
    _profile.update { it.copy(serviceRadiusKm = radiusKm) }
  }

  override suspend fun toggleOnlineStatus(isOnline: Boolean): Result<Boolean> {
    val current = _profile.value
    if (isOnline && current.registrationStatus != RegistrationStatus.APPROVED) {
      return Result.failure(
        IllegalStateException(
          "Cannot go ONLINE while registration status is ${current.registrationStatus.label}. Wait for Ops Admin approval."
        )
      )
    }
    return runCatching {
      val uid = requireUserId()
      val firestore = db
      if (firestore != null) {
        firestore.collection("mechanicProfiles").document(uid).update(
          mapOf(
            "isOnline" to isOnline,
            "updatedAt" to System.currentTimeMillis()
          )
        ).awaitResult()
      }
      _profile.update { it.copy(isOnline = isOnline) }
      if (!isOnline) {
        offersListenerRegistration?.remove()
        offersListenerRegistration = null
        _currentOffer.value = null
      } else {
        attachOffersListener(uid)
      }
      isOnline
    }
  }

  override suspend fun submitKycDocuments(idNumber: String, docType: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    db?.collection("mechanicProfiles")?.document(uid)?.update(
      mapOf(
        "verificationIdNumber" to "$docType: $idNumber",
        "updatedAt" to System.currentTimeMillis()
      )
    )?.awaitResult()
    _profile.update { it.copy(verificationIdNumber = "$docType: $idNumber") }
  }

  override suspend fun updateGpsLocation(lat: Double, lng: Double, address: String?): Result<Unit> = runCatching {
    val api = backendApiClient ?: error("Backend client unavailable")
    val payload = JSONObject()
      .put("lat", lat)
      .put("lng", lng)
    if (!address.isNullOrBlank()) {
      payload.put("address", address)
    }
    api.callFunction("updateMechanicLocation", payload)
  }

  override fun setSimulateCompetitorSteal(enabled: Boolean) {
    _isSimulateCompetitorSteal.value = enabled
  }

  // --- Job Dispatch Repository Implementation (Spark Backend) ---

  override suspend fun acceptJobOffer(offerId: String): JobAcceptanceResult {
    val prof = _profile.value
    if (prof.registrationStatus != RegistrationStatus.APPROVED) {
      return JobAcceptanceResult.NotEligible(
        "Only APPROVED mechanics can accept dispatches (current: ${prof.registrationStatus.label})."
      )
    }
    val api = backendApiClient
      ?: return JobAcceptanceResult.Error("Backend client unavailable.")

    val current = _currentOffer.value
    val reqIdHint = if (current?.id == offerId) current.requestId else ""

    return try {
      val payload = JSONObject().put("offerId", offerId)
      if (reqIdHint.isNotBlank()) payload.put("requestId", reqIdHint)
      val res = api.callFunction("acceptJobOffer", payload)
      val assignedReqId = res.optString("requestId").ifBlank { reqIdHint.ifBlank { offerId } }

      countdownJob?.cancel()
      _currentOffer.value = null

      val firestore = db
      val reqDoc = firestore?.collection("serviceRequests")?.document(assignedReqId)?.get()?.awaitResult()
      val acceptedJob = if (reqDoc != null && reqDoc.exists()) {
        mapDocToActiveJob(reqDoc)
      } else {
        ActiveJob(
          id = assignedReqId,
          requestCode = "FIX-${assignedReqId.takeLast(4).uppercase()}",
          status = JobStatus.ASSIGNED,
          vehicleType = current?.vehicleType ?: VehicleType.FOUR_WHEELER,
          vehicleModel = current?.vehicleModel ?: "Vehicle",
          problemCategory = current?.problemCategory ?: ServiceCategory.PUNCTURE_TYRE,
          problemDescription = current?.problemDescription ?: "Roadside breakdown",
          customerNotes = "Assigned via atomic lock",
          approxDistanceKm = current?.approxDistanceKm ?: 1.5,
          locality = current?.locality ?: prof.baseCityLocality,
          exactAddress = current?.locality ?: prof.baseCityLocality,
          customerDisplayName = "Customer",
          customerSafetyStatus = "Verified FixMechanic Dispatch",
          destinationLat = 28.4595,
          destinationLng = 77.0266,
          currentEtaMinutes = 12,
          estimatedPayoutInr = current?.estimatedPayoutMinInr ?: 349.0,
          createdAtEpochMs = System.currentTimeMillis(),
          assignedAtEpochMs = System.currentTimeMillis(),
          statusHistory = listOf(StatusTimestamp(JobStatus.ASSIGNED, System.currentTimeMillis() / 1000L, "Assigned"))
        )
      }
      _activeJob.value = acceptedJob
      attachActiveJobListener(assignedReqId)
      JobAcceptanceResult.Assigned(acceptedJob)
    } catch (e: BackendCallException) {
      countdownJob?.cancel()
      _currentOffer.value = null
      when {
        e.httpStatus == 409 || e.errorCode == "aborted" || e.errorCode == "already-exists" ||
          e.message.contains("already assigned", ignoreCase = true) ||
          e.message.contains("already been assigned", ignoreCase = true) ||
          e.message.contains("Status: LOST", ignoreCase = true) ||
          e.message.contains("no longer available", ignoreCase = true) -> {
          JobAcceptanceResult.TakenByOther("This request has already been assigned")
        }
        e.message.contains("expired", ignoreCase = true) -> {
          JobAcceptanceResult.Expired(e.message)
        }
        e.httpStatus == 403 || e.httpStatus == 412 -> {
          JobAcceptanceResult.NotEligible(e.message)
        }
        else -> JobAcceptanceResult.Error(e.message)
      }
    } catch (e: Exception) {
      JobAcceptanceResult.Error(e.localizedMessage ?: "Failed accepting job offer.")
    }
  }

  override suspend fun rejectJobOffer(offerId: String): Result<Unit> = runCatching {
    countdownJob?.cancel()
    _currentOffer.value = null
    val api = backendApiClient ?: return@runCatching
    api.callFunction(
      "rejectJobOffer",
      JSONObject().put("offerId", offerId).put("reason", "Declined by mechanic")
    )
  }

  override suspend fun advanceJobStatus(jobId: String, nextStatus: JobStatus): Result<JobStatus> = runCatching {
    val api = backendApiClient ?: error("Backend client unavailable")
    api.callFunction(
      "updateJobStatus",
      JSONObject().put("requestId", jobId).put("targetStatus", nextStatus.name)
    )
    _activeJob.update { curr ->
      if (curr == null || curr.id != jobId) curr
      else curr.copy(
        status = nextStatus,
        statusHistory = curr.statusHistory + StatusTimestamp(
          nextStatus,
          System.currentTimeMillis() / 1000L,
          "Status updated to ${nextStatus.label}"
        )
      )
    }
    nextStatus
  }

  override suspend fun cancelJob(jobId: String, reason: String): Result<Unit> = runCatching {
    val api = backendApiClient ?: error("Backend client unavailable")
    api.callFunction(
      "updateJobStatus",
      JSONObject()
        .put("requestId", jobId)
        .put("targetStatus", "CANCELLED")
        .put("cancellationReason", reason)
    )
    _activeJob.value = null
  }

  override suspend fun submitJobQuote(jobId: String, quote: JobQuote): Result<JobQuote> = runCatching {
    val approvedQuote = quote.copy(status = QuoteStatus.SUBMITTED_TO_CUSTOMER)
    _activeJob.update { curr ->
      if (curr?.id == jobId) curr.copy(quote = approvedQuote) else curr
    }
    approvedQuote
  }

  override suspend fun finalizeJobCompletion(jobId: String, report: CompletionReport): Result<Unit> = runCatching {
    val api = backendApiClient ?: error("Backend client unavailable")
    api.callFunction(
      "updateJobStatus",
      JSONObject()
        .put("requestId", jobId)
        .put("targetStatus", "COMPLETED")
        .put("totalPaid", report.finalPayoutAmountInr)
        .put("note", report.workPerformedSummary)
    )
    _activeJob.value = null
  }

  override fun triggerSimulatedOffer(scenarioIndex: Int) {
    // Disabled in live unified backend mode; real offers arrive via Firestore `jobOffers`.
  }

  override fun dismissCurrentOffer() {
    countdownJob?.cancel()
    _currentOffer.value = null
  }

  // --- Chat Repository Implementation ---

  override fun getMessages(jobId: String): Flow<List<ChatMessage>> = callbackFlow {
    val uid = auth?.currentUser?.uid
    val firestore = db
    if (uid != null && firestore != null) {
      val messagesRef = firestore.collection("jobChats")
        .document(jobId)
        .collection("jobMessages")
        .orderBy("timestamp", Query.Direction.ASCENDING)
      val registration = messagesRef.addSnapshotListener { snapshot, error ->
        if (error != null) {
          handleFirestoreError(error, OperationType.LIST, "jobChats/$jobId/jobMessages")
          return@addSnapshotListener
        }
        if (snapshot != null) {
          val list = snapshot.documents.map { doc ->
            val roleStr = (doc.getString("senderRole") ?: "CUSTOMER").uppercase()
            val role = when (roleStr) {
              "MECHANIC" -> SenderRole.MECHANIC
              "SYSTEM" -> SenderRole.SYSTEM_DISPATCH
              else -> SenderRole.CUSTOMER
            }
            ChatMessage(
              id = doc.id,
              jobId = jobId,
              senderRole = role,
              senderName = if (role == SenderRole.MECHANIC) "You" else "Customer",
              text = doc.getString("text") ?: "",
              timestampEpoch = extractEpochMillis(doc.get("timestamp")).let {
                if (it <= 0L) System.currentTimeMillis() else it
              },
              status = MessageDeliveryStatus.DELIVERED
            )
          }
          trySend(list)
        }
      }
      awaitClose { registration.remove() }
    } else {
      trySend(_messages.value[jobId].orEmpty())
      awaitClose {}
    }
  }

  override suspend fun sendMessage(jobId: String, text: String, senderName: String): Result<ChatMessage> = runCatching {
    val job = _activeJob.value ?: error("No active job session")
    if (!isChatAllowedForStatus(job.status)) {
      error("Chat session is closed for status: ${job.status.label}")
    }
    val api = backendApiClient ?: error("Backend client unavailable")
    val res = api.callFunction(
      "sendJobChatMessage",
      JSONObject().put("requestId", jobId).put("text", text.trim())
    )
    ChatMessage(
      id = res.optString("messageId", "msg_${System.currentTimeMillis()}"),
      jobId = jobId,
      senderRole = SenderRole.MECHANIC,
      senderName = senderName,
      text = text.trim(),
      timestampEpoch = res.optLong("timestamp", System.currentTimeMillis()),
      status = MessageDeliveryStatus.DELIVERED
    )
  }

  override suspend fun retryFailedMessage(messageId: String): Result<Unit> = Result.success(Unit)

  override fun getQuickCannedResponses(status: JobStatus): List<String> {
    return when (status) {
      JobStatus.ASSIGNED, JobStatus.ACCEPTED, JobStatus.EN_ROUTE -> listOf(
        "I am en route with emergency tools.",
        "Please keep your hazard lights on and stay safe.",
        "Reaching your breakdown location shortly."
      )
      JobStatus.ARRIVED -> listOf(
        "I have arrived at your location.",
        "Setting up roadside safety cones now."
      )
      JobStatus.IN_PROGRESS -> listOf(
        "Inspecting vehicle issue and beginning repair.",
        "Almost done with final safety checks."
      )
      else -> listOf("Thank you! Drive safely.")
    }
  }

  override fun isChatAllowedForStatus(status: JobStatus): Boolean {
    return status != JobStatus.COMPLETED && status != JobStatus.CANCELLED
  }

  // --- Location Navigation Implementation ---

  override fun openExternalTurnByTurn(context: Context, lat: Double, lng: Double, address: String) {
    try {
      val uri = Uri.parse("google.navigation:q=$lat,$lng")
      val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (_: Exception) {
      val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(address)})")
      val intent = Intent(Intent.ACTION_VIEW, geoUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    }
  }

  override fun isLocationPermissionGranted(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
  }

  override fun isGpsProviderEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    return lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
  }

  // --- FCM Notification Implementation ---

  override suspend fun getOrRegisterFcmToken(): String {
    val uid = auth?.currentUser?.uid ?: return ""
    val token = runCatching { FirebaseMessaging.getInstance().token.awaitResult() }.getOrDefault("")
    val firestore = db
    if (token.isNotBlank() && firestore != null) {
      runCatching {
        firestore.collection("deviceTokens").document("${uid}_android").set(
          mapOf(
            "userId" to uid,
            "role" to "MECHANIC",
            "platform" to "android",
            "token" to token,
            "updatedAt" to System.currentTimeMillis()
          ),
          SetOptions.merge()
        ).awaitResult()
      }
    }
    return token
  }

  override fun simulatePushAlert(title: String, message: String) {
    Log.d("FixMechRepo", "Alert: $title - $message")
  }

  companion object {
    private fun createFirestore(context: Context): FirebaseFirestore? {
      return runCatching { FirestoreProvider.createFirestore(context) }.getOrNull()
    }

    private fun createAuth(): FirebaseAuth? {
      return runCatching { FirebaseAuth.getInstance() }.getOrNull()
    }

    private fun createBackendApiClient(auth: FirebaseAuth?): BackendApiClient? {
      return runCatching { BackendApiClient(auth) }.getOrNull()
    }
  }
}

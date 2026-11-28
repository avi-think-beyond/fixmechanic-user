package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
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
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class FixMechanicRepositoryImpl(
  private val db: FirebaseFirestore?,
  private val auth: FirebaseAuth?,
  private val backendApiClient: BackendApiClient?,
  private val scope: CoroutineScope,
  private val appContext: Context? = null
) : AuthRepository, MechanicProfileRepository, JobDispatchRepository, ChatRepository, LocationNavigationService, FcmNotificationService {

  // Pure unit-test constructor (no Firebase or Spark backend)
  constructor(scope: CoroutineScope) : this(null, null, null, scope, null)

  // Emulator / Rule test constructor
  constructor(
    db: FirebaseFirestore?,
    auth: FirebaseAuth?,
    scope: CoroutineScope,
    backendBaseUrl: String = com.example.BuildConfig.BACKEND_BASE_URL
  ) : this(db, auth, auth?.let { BackendApiClient(it, backendBaseUrl) }, scope, null)

  // Production constructor using configured Firestore database
  constructor(context: Context, scope: CoroutineScope) : this(
    db = createFirestore(context),
    auth = createAuth(),
    backendApiClient = createBackendApiClient(createAuth()),
    scope = scope,
    appContext = context.applicationContext
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

  private val _availableOffers = MutableStateFlow<List<JobOffer>>(emptyList())
  override val availableOffers: StateFlow<List<JobOffer>> = _availableOffers.asStateFlow()

  private val _activeJob = MutableStateFlow<ActiveJob?>(null)
  override val activeJob: StateFlow<ActiveJob?> = _activeJob.asStateFlow()

  private val _todayEarningsInr = MutableStateFlow(0.0)
  override val todayEarningsInr: StateFlow<Double> = _todayEarningsInr.asStateFlow()

  private val _completedHistory = MutableStateFlow<List<CompletedJobRecord>>(emptyList())
  override val completedHistory: StateFlow<List<CompletedJobRecord>> = _completedHistory.asStateFlow()

  private val _messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
  private var countdownJob: Job? = null
  private var offersListenerRegistration: ListenerRegistration? = null
  private var searchingRequestsListenerRegistration: ListenerRegistration? = null
  private var activeJobListenerRegistration: ListenerRegistration? = null
  private var profileListenerRegistration: ListenerRegistration? = null
  private var historyListenerRegistration: ListenerRegistration? = null
  private var latestJobOffers: List<JobOffer> = emptyList()
  private var latestSearchingRequests: List<JobOffer> = emptyList()
  private val declinedRequestIds = mutableSetOf<String>()
  private val alertedRequestIds = mutableSetOf<String>()
  private val directRequestIds = mutableSetOf<String>()

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
      _availableOffers.value = emptyList()
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

  /**
   * Called immediately after Google or Email sign-in succeeds.
   * Ensures `users/{uid}` exists, attaches live Firestore listeners, and checks whether
   * `mechanicProfiles/{uid}` already exists so the app can route new mechanics to Profile Setup
   * and returning mechanics directly to the Home Dashboard.
   *
   * @return true if this mechanic still needs to complete/update their profile details.
   */
  suspend fun completeSignInAndCheckIsNewMechanic(isFirebaseNewUser: Boolean = false): Boolean {
    val user = auth?.currentUser ?: return false
    val uid = user.uid
    _isAuthenticated.value = true
    _currentUserMobile.value = user.phoneNumber ?: user.email

    ensureUserDocument(uid, user.email, user.displayName, user.phoneNumber)
    attachProfileListener(uid)
    attachHistoryListener(uid)

    val firestore = db
    if (firestore == null) {
      _profile.update {
        it.copy(
          id = uid,
          fullName = user.displayName?.takeIf { n -> n.isNotBlank() } ?: it.fullName,
          email = user.email ?: it.email,
          mobileNumber = user.phoneNumber ?: it.mobileNumber
        )
      }
      return isFirebaseNewUser
    }

    val snapshot = runCatching {
      firestore.collection("mechanicProfiles").document(uid).get().awaitResult()
    }.getOrNull()

    if (snapshot != null && snapshot.exists()) {
      applyProfileSnapshot(uid, snapshot)
      val existingName = snapshot.getString("name") ?: snapshot.getString("fullName") ?: ""
      val existingWorkshop = snapshot.getString("workshopName") ?: ""
      return existingName.isBlank() || existingWorkshop.isBlank()
    }

    // New mechanic: pre-populate profile with their Google/Email account info so Profile Edit is ready
    _profile.update {
      it.copy(
        id = uid,
        fullName = user.displayName?.takeIf { n -> n.isNotBlank() } ?: "",
        email = user.email ?: it.email,
        mobileNumber = user.phoneNumber ?: it.mobileNumber,
        workshopName = ""
      )
    }
    return true
  }

  private fun applyProfileSnapshot(uid: String, snapshot: DocumentSnapshot) {
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
    val address = locMap?.get("address")?.toString()?.takeIf { it.isNotBlank() } ?: _profile.value.baseCityLocality
    val lat = (locMap?.get("lat") as? Number)?.toDouble() ?: _profile.value.latitude
    val lng = (locMap?.get("lng") as? Number)?.toDouble() ?: _profile.value.longitude
    val pin = snapshot.getString("pincode")?.takeIf { it.isNotBlank() }
      ?: locMap?.get("pincode")?.toString()?.takeIf { it.isNotBlank() }
      ?: _profile.value.pincode
    val servicePins = (snapshot.get("servicePincodes") as? List<*>)
      ?.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
      ?.takeIf { it.isNotEmpty() }
      ?: if (pin.isNotBlank()) listOf(pin) else _profile.value.servicePincodes
    val hasCustomLoc = snapshot.getBoolean("hasCustomLocation")
      ?: (kotlin.math.abs(lat - defaultProfile.latitude) > 0.0005 ||
        kotlin.math.abs(lng - defaultProfile.longitude) > 0.0005)
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
        pincode = pin,
        servicePincodes = servicePins,
        latitude = lat,
        longitude = lng,
        hasCustomLocation = hasCustomLoc,
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
  }

  private fun detachAllListeners() {
    profileListenerRegistration?.remove()
    profileListenerRegistration = null
    detachOffersListeners()
    activeJobListenerRegistration?.remove()
    activeJobListenerRegistration = null
    historyListenerRegistration?.remove()
    historyListenerRegistration = null
  }

  private fun detachOffersListeners() {
    offersListenerRegistration?.remove()
    offersListenerRegistration = null
    searchingRequestsListenerRegistration?.remove()
    searchingRequestsListenerRegistration = null
    latestJobOffers = emptyList()
    latestSearchingRequests = emptyList()
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
        val address = locMap?.get("address")?.toString()?.takeIf { it.isNotBlank() } ?: _profile.value.baseCityLocality
        val lat = (locMap?.get("lat") as? Number)?.toDouble() ?: _profile.value.latitude
        val lng = (locMap?.get("lng") as? Number)?.toDouble() ?: _profile.value.longitude
        val pin = snapshot.getString("pincode")?.takeIf { it.isNotBlank() }
          ?: locMap?.get("pincode")?.toString()?.takeIf { it.isNotBlank() }
          ?: _profile.value.pincode
        val servicePins = (snapshot.get("servicePincodes") as? List<*>)
          ?.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
          ?.takeIf { it.isNotEmpty() }
          ?: if (pin.isNotBlank()) listOf(pin) else _profile.value.servicePincodes
        val hasCustomLoc = snapshot.getBoolean("hasCustomLocation")
          ?: (kotlin.math.abs(lat - defaultProfile.latitude) > 0.0005 ||
            kotlin.math.abs(lng - defaultProfile.longitude) > 0.0005)
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
            pincode = pin,
            servicePincodes = servicePins,
            latitude = lat,
            longitude = lng,
            hasCustomLocation = hasCustomLoc,
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
          detachOffersListeners()
          _availableOffers.value = emptyList()
          _currentOffer.value = null
        } else {
          _activeJob.value = null
          activeJobListenerRegistration?.remove()
          activeJobListenerRegistration = null
          if (isOnline && regStatus == RegistrationStatus.APPROVED) {
            attachOffersListener(uid)
            publishMergedIncomingOffers()
          } else {
            detachOffersListeners()
            _availableOffers.value = emptyList()
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

    // 1. Listen to targeted jobOffers (when Spark/Functions dispatch backend is running)
    if (offersListenerRegistration == null) {
      val offersQuery = firestore.collection("jobOffers")
        .whereEqualTo("mechanicId", uid)
        .whereEqualTo("status", "PENDING")

      offersListenerRegistration = offersQuery.addSnapshotListener { snapshot, error ->
        if (error != null || snapshot == null) return@addSnapshotListener
        val now = System.currentTimeMillis()
        latestJobOffers = snapshot.documents
          .filter { doc ->
            val expiresAt = extractEpochMillis(doc.get("expiresAt"))
            expiresAt <= 0L || expiresAt > now
          }
          .map { mapDocToJobOffer(it) }
        publishMergedIncomingOffers()
      }
    }

    // 2. Real-time direct listener on customer serviceRequests (Ola/Uber instant dispatch)
    if (searchingRequestsListenerRegistration == null) {
      val searchingQuery = firestore.collection("serviceRequests")
        .whereIn("status", OPEN_DISPATCH_STATUSES)

      searchingRequestsListenerRegistration = searchingQuery.addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.w("FixMechDispatch", "serviceRequests listener error: ${error.localizedMessage}", error)
          return@addSnapshotListener
        }
        if (snapshot == null) return@addSnapshotListener
        latestSearchingRequests = snapshot.documents.mapNotNull { doc ->
          mapSearchingServiceRequestToJobOffer(doc)
        }
        publishMergedIncomingOffers()
      }
    }
  }

  private fun publishMergedIncomingOffers() {
    val prof = _profile.value
    if (!prof.isOnline || prof.registrationStatus != RegistrationStatus.APPROVED || _activeJob.value != null) {
      _availableOffers.value = emptyList()
      if (_currentOffer.value != null) {
        countdownJob?.cancel()
        _currentOffer.value = null
      }
      return
    }

    // Merge jobOffers and direct SEARCHING serviceRequests, preferring targeted jobOffers on duplicate requestId
    val mergedByRequestId = linkedMapOf<String, JobOffer>()
    latestJobOffers.forEach { offer ->
      val key = offer.requestId.ifBlank { offer.id }
      if (key !in declinedRequestIds) {
        mergedByRequestId[key] = offer
      }
    }
    latestSearchingRequests.forEach { offer ->
      val key = offer.requestId.ifBlank { offer.id }
      if (key !in declinedRequestIds && key !in mergedByRequestId) {
        mergedByRequestId[key] = offer
      }
    }

    val mergedList = mergedByRequestId.values.sortedBy { it.approxDistanceKm }
    _availableOffers.value = mergedList

    // Fire Ola/Uber style heads-up notification + sound/vibration for any brand-new incoming request
    mergedList.forEach { offer ->
      val key = offer.requestId.ifBlank { offer.id }
      if (key !in alertedRequestIds) {
        alertedRequestIds.add(key)
        triggerIncomingRequestNotification(offer)
      }
    }

    val current = _currentOffer.value
    if (mergedList.isEmpty()) {
      if (current != null) {
        countdownJob?.cancel()
        _currentOffer.value = null
      }
      return
    }

    val currentStillValid = current != null && mergedList.any {
      it.id == current.id || (it.requestId.isNotBlank() && it.requestId == current.requestId)
    }
    if (!currentStillValid) {
      val topOffer = mergedList.first()
      _currentOffer.value = topOffer
      startOfferCountdown(topOffer)
    }
  }

  private fun mapSearchingServiceRequestToJobOffer(doc: DocumentSnapshot): JobOffer? {
    val status = (doc.getString("status") ?: "").uppercase()
    if (status !in OPEN_DISPATCH_STATUSES) return null

    // Skip if already assigned to any mechanic
    val assignedMech = doc.getString("assignedMechanicId")?.trim().orEmpty()
    val legacyMech = doc.getString("mechanicId")?.trim().orEmpty()
    if (assignedMech.isNotBlank() || legacyMech.isNotBlank()) return null

    val prof = _profile.value
    val requestId = doc.getString("requestId")?.takeIf { it.isNotBlank() } ?: doc.id
    if (requestId in declinedRequestIds) return null

    val locMap = extractLocationMap(doc)
    val reqLat = extractDouble(locMap?.get("lat") ?: locMap?.get("latitude") ?: doc.get("lat") ?: doc.get("latitude"))
    val reqLng = extractDouble(locMap?.get("lng") ?: locMap?.get("longitude") ?: doc.get("lng") ?: doc.get("longitude"))
    val reqPin = (locMap?.get("pincode") ?: doc.get("pincode"))?.toString()?.trim().orEmpty()
    val landmark = (locMap?.get("landmark") ?: doc.get("landmark"))?.toString()?.trim().orEmpty()
    val address = (locMap?.get("address") ?: doc.get("address"))?.toString()?.trim().orEmpty()
    val city = (locMap?.get("city") ?: doc.get("city"))?.toString()?.trim().orEmpty()

    val isMechanicOnFallbackDefault = !prof.hasCustomLocation &&
      kotlin.math.abs(prof.latitude - defaultProfile.latitude) < 0.001 &&
      kotlin.math.abs(prof.longitude - defaultProfile.longitude) < 0.001

    val computedDistKm = if (reqLat != null && reqLng != null && (reqLat != 0.0 || reqLng != 0.0)) {
      haversineKm(prof.latitude, prof.longitude, reqLat, reqLng)
    } else {
      1.5
    }

    val effectiveRadiusKm = prof.serviceRadiusKm.coerceAtLeast(15).toDouble() + 2.0
    val matchesPin = reqPin.isNotBlank() &&
      (reqPin == prof.pincode || prof.servicePincodes.contains(reqPin))
    val withinRadius = reqLat == null || reqLng == null || computedDistKm <= effectiveRadiusKm

    if (!withinRadius && !matchesPin && !isMechanicOnFallbackDefault) {
      Log.d(
        "FixMechDispatch",
        "Skipping request $requestId: distance=${"%.1f".format(computedDistKm)}km > radius=${effectiveRadiusKm}km and PIN '$reqPin' not in ${prof.servicePincodes}"
      )
      return null
    }

    val rawVehicleModel = doc.getString("vehicleMakeModel")
      ?: doc.getString("vehicleModel")
      ?: doc.getString("vehicleName")
      ?: ""
    val rawVehicleType = doc.getString("vehicleType") ?: doc.getString("vehicle")
    val vType = inferVehicleType(rawVehicleType, rawVehicleModel)

    // Filter by mechanic vehicle capability (BOTH matches all)
    if (prof.vehicleCapability == VehicleCapability.BIKE && vType == VehicleType.FOUR_WHEELER) return null
    if (prof.vehicleCapability == VehicleCapability.CAR && vType == VehicleType.TWO_WHEELER) return null

    val vModel = rawVehicleModel.ifBlank { vType.label }
    val probCat = ServiceCategory.fromBackendCode(
      doc.getString("serviceCategory")
        ?: doc.getString("problemCategory")
        ?: doc.getString("serviceType")
        ?: doc.getString("issueTitle")
    )
    val issueDesc = doc.getString("description")?.takeIf { it.isNotBlank() }
      ?: doc.getString("issueDescription")?.takeIf { it.isNotBlank() }
      ?: doc.getString("issueTitle")?.takeIf { it.isNotBlank() }
      ?: probCat.label

    val localityDisplay = listOf(landmark, address, city, reqPin)
      .filter { it.isNotBlank() }
      .distinct()
      .joinToString(", ")
      .ifBlank { "Nearby Customer Location" }

    val payout = extractDouble(doc.get("estimatedPriceInr") ?: doc.get("estimatedPrice")) ?: 399.0
    val displayDist = ((computedDistKm * 10.0).toInt() / 10.0).coerceAtLeast(0.1)
    val now = System.currentTimeMillis()

    return JobOffer(
      id = requestId,
      requestId = requestId,
      requestCode = "FIX-${requestId.takeLast(4).uppercase()}",
      vehicleType = vType,
      vehicleModel = vModel,
      problemCategory = probCat,
      problemDescription = issueDesc,
      customerNotes = if (landmark.isNotBlank()) "Landmark: $landmark (~$displayDist km away)" else "Customer waiting (~$displayDist km away)",
      approxDistanceKm = displayDist,
      locality = localityDisplay,
      safetyNotes = "Confirm exact customer spot via in-app chat or turn-by-turn navigation.",
      estimatedPayoutMinInr = payout,
      estimatedPayoutMaxInr = payout + 150.0,
      totalCountdownSeconds = 60,
      remainingCountdownSeconds = 60,
      expiresAtEpochMs = now + 60_000L,
      isDirectServiceRequest = true
    )
  }

  private fun triggerIncomingRequestNotification(offer: JobOffer) {
    val ctx = appContext ?: return
    runCatching {
      val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      val channelId = "fixmechanic_live_dispatch"
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
          channelId,
          "Live Emergency Requests",
          NotificationManager.IMPORTANCE_HIGH
        ).apply {
          description = "Instant alerts when a customer requests roadside help in your area"
          enableVibration(true)
          vibrationPattern = longArrayOf(0L, 350L, 200L, 350L)
        }
        manager.createNotificationChannel(channel)
      }

      val launchIntent = Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val pendingIntent = PendingIntent.getActivity(
        ctx,
        offer.requestId.hashCode(),
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      val notification = NotificationCompat.Builder(ctx, channelId)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("New Request: ${offer.problemCategory.label} (${offer.approxDistanceKm} km)")
        .setContentText("${offer.vehicleModel} • ${offer.locality}")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setSound(soundUri)
        .setVibrate(longArrayOf(0L, 350L, 200L, 350L))
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

      manager.notify(offer.requestId.hashCode(), notification)

      // Vibrate device for immediate tactile Ola/Uber driver feedback
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 300L, 150L, 300L), -1))
      } else {
        @Suppress("DEPRECATION")
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 300L, 150L, 300L), -1))
        } else {
          @Suppress("DEPRECATION")
          v?.vibrate(longArrayOf(0L, 300L, 150L, 300L), -1)
        }
      }
    }.onFailure {
      Log.w("FixMechDispatch", "Could not post local dispatch alert notification", it)
    }
  }

  private fun extractLocationMap(doc: DocumentSnapshot): Map<*, *>? {
    return (doc.get("location") as? Map<*, *>)
      ?: (doc.get("pickupLocation") as? Map<*, *>)
      ?: (doc.get("customerLocation") as? Map<*, *>)
      ?: (doc.get("currentLocation") as? Map<*, *>)
  }

  private fun extractDouble(raw: Any?): Double? = when (raw) {
    is Number -> raw.toDouble()
    is String -> raw.toDoubleOrNull()
    else -> null
  }

  private fun inferVehicleType(rawType: String?, rawModel: String?): VehicleType {
    if (!rawType.isNullOrBlank()) {
      return VehicleType.fromBackendCode(rawType)
    }
    val model = (rawModel ?: "").lowercase()
    val twoWheelerHints = listOf(
      "royal enfield", "classic 350", "bullet", "meteor", "hunter", "himalayan",
      "activa", "jupiter", "access", "dio", "ntorq", "ola s1", "ather", "chetak",
      "pulsar", "apache", "splendor", "passion", "shine", "unicorn", "fz", "r15",
      "mt 15", "ktm", "duke", "yamaha", "bajaj", "hero", "tvs", "bike", "scooter", "scooty", "2w"
    )
    return if (twoWheelerHints.any { model.contains(it) }) {
      VehicleType.TWO_WHEELER
    } else {
      VehicleType.FOUR_WHEELER
    }
  }

  private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) +
      cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusKm * c
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
    val rawModel = doc.getString("vehicleMakeModel") ?: doc.getString("vehicleModel") ?: ""
    val vType = inferVehicleType(doc.getString("vehicleType") ?: doc.getString("vehicle"), rawModel)
    val vModel = rawModel.ifBlank { vType.label }
    val regNo = doc.getString("vehicleRegNumber") ?: doc.getString("registrationNumber")
    val modelLabel = if (!regNo.isNullOrBlank() && regNo != "UNREGISTERED") "$vModel ($regNo)" else vModel
    val probCat = ServiceCategory.fromBackendCode(
      doc.getString("serviceCategory") ?: doc.getString("problemCategory") ?: doc.getString("issueTitle")
    )
    val desc = doc.getString("description")?.takeIf { it.isNotBlank() }
      ?: doc.getString("issueDescription")?.takeIf { it.isNotBlank() }
      ?: doc.getString("issueTitle")
      ?: probCat.label
    val locMap = extractLocationMap(doc)
    val landmark = (locMap?.get("landmark") ?: doc.get("landmark"))?.toString()?.trim().orEmpty()
    val address = (locMap?.get("address") ?: doc.get("address"))?.toString()?.trim().orEmpty()
    val city = (locMap?.get("city") ?: doc.get("city"))?.toString()?.trim().orEmpty()
    val exactAddr = listOf(landmark, address, city).filter { it.isNotBlank() }.distinct().joinToString(", ")
      .ifBlank { "GPS Breakdown Location" }
    val locality = landmark.ifBlank { address.ifBlank { city.ifBlank { "Nearby Location" } } }
    val lat = extractDouble(locMap?.get("lat") ?: locMap?.get("latitude") ?: doc.get("lat") ?: doc.get("latitude"))
      ?: _profile.value.latitude
    val lng = extractDouble(locMap?.get("lng") ?: locMap?.get("longitude") ?: doc.get("lng") ?: doc.get("longitude"))
      ?: _profile.value.longitude
    val custName = doc.getString("customerName") ?: "Customer"
    val payout = extractDouble(doc.get("estimatedPriceInr") ?: doc.get("estimatedPrice")) ?: 349.0
    val createdAt = extractEpochMillis(doc.get("createdAt") ?: doc.get("updatedAt"))
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
    val trimmedEmail = email.trim()
    val cred = firebaseAuth.signInWithEmailAndPassword(trimmedEmail, password).awaitResult()
    val uid = cred.user?.uid ?: error("Auth returned null UID")
    ensureUserDocument(uid, cred.user?.email ?: trimmedEmail, cred.user?.displayName, cred.user?.phoneNumber)
    _isAuthenticated.value = true
    _currentUserMobile.value = trimmedEmail
    val updated = _profile.value.copy(
      id = uid,
      email = cred.user?.email ?: trimmedEmail,
      fullName = cred.user?.displayName?.takeIf { it.isNotBlank() } ?: _profile.value.fullName
    )
    _profile.value = updated
    updated
  }

  override suspend fun registerWithEmail(email: String, password: String): Result<Unit> = runCatching {
    val firebaseAuth = auth ?: error("FirebaseAuth unavailable")
    val trimmedEmail = email.trim()
    val cred = firebaseAuth.createUserWithEmailAndPassword(trimmedEmail, password).awaitResult()
    val uid = cred.user?.uid ?: error("Auth returned null UID")
    ensureUserDocument(uid, cred.user?.email ?: trimmedEmail, cred.user?.displayName, cred.user?.phoneNumber)
    _isAuthenticated.value = true
    _currentUserMobile.value = trimmedEmail
    _profile.update { it.copy(id = uid, email = trimmedEmail) }
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
    declinedRequestIds.clear()
    alertedRequestIds.clear()
    directRequestIds.clear()
    auth?.signOut()
    _isAuthenticated.value = false
    _currentUserMobile.value = null
    _currentOffer.value = null
    _availableOffers.value = emptyList()
    _activeJob.value = null
    countdownJob?.cancel()
  }

  // --- Profile Repository Implementation ---

  /**
   * Rule-compliant upsert for mechanicProfiles/{uid}:
   * - If doc does not exist yet: creates initial PENDING/offline profile (passes allow create).
   * - If doc exists: updates ONLY non-privileged fields (passes allow update).
   */
  private suspend fun upsertNonPrivilegedMechanicProfileFields(
    uid: String,
    updates: Map<String, Any>
  ) {
    val firestore = db ?: return
    val prof = _profile.value
    val now = System.currentTimeMillis()
    ensureUserDocument(uid, prof.email.ifBlank { auth?.currentUser?.email }, prof.fullName, prof.mobileNumber)
    val docRef = firestore.collection("mechanicProfiles").document(uid)
    val existing = docRef.get().awaitResult()
    if (!existing.exists()) {
      val createPayload = mutableMapOf<String, Any>(
        "mechanicId" to uid,
        "name" to prof.fullName.ifBlank { auth?.currentUser?.displayName ?: "FixMechanic Partner" },
        "phone" to prof.mobileNumber,
        "email" to prof.email.ifBlank { auth?.currentUser?.email ?: "" },
        "workshopName" to prof.workshopName,
        "vehicleTypes" to prof.vehicleCapability.toBackendVehicleTypes(),
        "serviceCategories" to prof.supportedServices.map { it.toBackendCode() }.ifEmpty { listOf("FLAT_TYRE") },
        "serviceRadiusKm" to prof.serviceRadiusKm,
        "pincode" to prof.pincode,
        "servicePincodes" to prof.servicePincodes,
        "hasCustomLocation" to prof.hasCustomLocation,
        "experienceYears" to prof.experienceYears,
        "verificationIdNumber" to prof.verificationIdNumber,
        "approvalStatus" to "PENDING",
        "isOnline" to false,
        "isBusy" to false,
        "currentLocation" to mapOf(
          "lat" to prof.latitude,
          "lng" to prof.longitude,
          "address" to prof.baseCityLocality,
          "pincode" to prof.pincode,
          "updatedAt" to now
        ),
        "ratingAverage" to 5.0,
        "ratingCount" to 0,
        "totalCompletedJobs" to 0,
        "todayEarningsInr" to 0.0,
        "createdAt" to now,
        "updatedAt" to now
      )
      // Merge only top-level non-dot fields into creation payload
      updates.forEach { (k, v) ->
        if (!k.contains(".")) {
          createPayload[k] = v
        }
      }
      docRef.set(createPayload).awaitResult()
    } else {
      val safeUpdates = updates.toMutableMap()
      safeUpdates["updatedAt"] = now
      docRef.update(safeUpdates).awaitResult()
    }
  }

  override suspend fun updateProfileDetails(
    fullName: String,
    workshopName: String,
    capability: VehicleCapability,
    cityLocality: String,
    radiusKm: Int,
    pincode: String,
    latitude: Double,
    longitude: Double
  ): Result<Unit> = runCatching {
    val cleanName = fullName.trim().ifBlank { _profile.value.fullName }
    val cleanWorkshop = workshopName.trim().ifBlank { _profile.value.workshopName }
    val cleanCity = cityLocality.trim()
    val cleanPin = pincode.trim()
    val updatedPins = buildList {
      if (cleanPin.isNotBlank()) add(cleanPin)
      addAll(_profile.value.servicePincodes.filter { it.isNotBlank() && it != cleanPin })
    }.distinct()

    _profile.update {
      it.copy(
        fullName = cleanName,
        workshopName = cleanWorkshop,
        vehicleCapability = capability,
        baseCityLocality = cleanCity,
        pincode = cleanPin,
        servicePincodes = updatedPins.ifEmpty { it.servicePincodes },
        serviceRadiusKm = radiusKm,
        latitude = latitude,
        longitude = longitude,
        hasCustomLocation = true
      )
    }

    if (db != null) {
      val uid = requireUserId()
      val now = System.currentTimeMillis()
      upsertNonPrivilegedMechanicProfileFields(
        uid,
        mapOf(
          "name" to cleanName,
          "workshopName" to cleanWorkshop,
          "vehicleTypes" to capability.toBackendVehicleTypes(),
          "serviceRadiusKm" to radiusKm,
          "pincode" to cleanPin,
          "servicePincodes" to _profile.value.servicePincodes,
          "hasCustomLocation" to true,
          "currentLocation" to mapOf(
            "lat" to latitude,
            "lng" to longitude,
            "address" to cleanCity,
            "pincode" to cleanPin,
            "updatedAt" to now
          ),
          "updatedAt" to now
        )
      )
    }
  }

  override suspend fun updateServiceAreaAndPincode(
    cityLocality: String,
    pincode: String,
    radiusKm: Int,
    servicePincodes: List<String>,
    latitude: Double,
    longitude: Double
  ): Result<Unit> = runCatching {
    val cleanCity = cityLocality.trim()
    val cleanPin = pincode.trim()
    val cleanPins = servicePincodes.map { it.trim() }.filter { it.isNotBlank() }.distinct()
      .ifEmpty { if (cleanPin.isNotBlank()) listOf(cleanPin) else _profile.value.servicePincodes }

    _profile.update {
      it.copy(
        baseCityLocality = cleanCity,
        pincode = cleanPin,
        serviceRadiusKm = radiusKm,
        servicePincodes = cleanPins,
        latitude = latitude,
        longitude = longitude,
        hasCustomLocation = true
      )
    }

    if (db != null) {
      val uid = requireUserId()
      val now = System.currentTimeMillis()
      upsertNonPrivilegedMechanicProfileFields(
        uid,
        mapOf(
          "serviceRadiusKm" to radiusKm,
          "pincode" to cleanPin,
          "servicePincodes" to cleanPins,
          "hasCustomLocation" to true,
          "currentLocation" to mapOf(
            "lat" to latitude,
            "lng" to longitude,
            "address" to cleanCity,
            "pincode" to cleanPin,
            "updatedAt" to now
          ),
          "updatedAt" to now
        )
      )
    }
  }

  override suspend fun syncInitialDeviceLocation(
    latitude: Double,
    longitude: Double,
    locality: String?,
    pincode: String?,
    forceRefresh: Boolean
  ) {
    if (latitude == 0.0 && longitude == 0.0) return
    val current = _profile.value
    val isDefaultCoordinates =
      kotlin.math.abs(current.latitude - defaultProfile.latitude) < 0.0005 &&
        kotlin.math.abs(current.longitude - defaultProfile.longitude) < 0.0005
    if (!forceRefresh && current.hasCustomLocation && !isDefaultCoordinates) {
      return
    }

    val cleanLocality = locality?.trim()?.takeIf { it.isNotBlank() }
    val resolvedLocality = if (
      cleanLocality != null &&
      (forceRefresh || current.baseCityLocality == defaultProfile.baseCityLocality || current.baseCityLocality.isBlank())
    ) {
      cleanLocality
    } else {
      current.baseCityLocality
    }

    val cleanPin = pincode?.trim()?.takeIf { it.length == 6 && it.all { ch -> ch.isDigit() } }
    val resolvedPin = if (
      cleanPin != null &&
      (forceRefresh || current.pincode == defaultProfile.pincode || current.pincode.isBlank())
    ) {
      cleanPin
    } else {
      current.pincode
    }
    val resolvedPins = if (cleanPin != null && (forceRefresh || current.pincode == defaultProfile.pincode)) {
      listOf(cleanPin)
    } else {
      current.servicePincodes
    }

    _profile.update {
      it.copy(
        latitude = latitude,
        longitude = longitude,
        baseCityLocality = resolvedLocality,
        pincode = resolvedPin,
        servicePincodes = resolvedPins,
        hasCustomLocation = if (forceRefresh) false else it.hasCustomLocation
      )
    }

    val uid = auth?.currentUser?.uid
    if (uid != null && db != null) {
      val now = System.currentTimeMillis()
      runCatching {
        upsertNonPrivilegedMechanicProfileFields(
          uid,
          mapOf(
            "pincode" to resolvedPin,
            "servicePincodes" to resolvedPins,
            "hasCustomLocation" to _profile.value.hasCustomLocation,
            "currentLocation" to mapOf(
              "lat" to latitude,
              "lng" to longitude,
              "address" to resolvedLocality,
              "pincode" to resolvedPin,
              "updatedAt" to now
            ),
            "updatedAt" to now
          )
        )
      }
    }
  }

  override suspend fun updateVehicleCapability(capability: VehicleCapability): Result<Unit> = runCatching {
    _profile.update { it.copy(vehicleCapability = capability) }
    if (db != null) {
      val uid = requireUserId()
      upsertNonPrivilegedMechanicProfileFields(
        uid,
        mapOf("vehicleTypes" to capability.toBackendVehicleTypes())
      )
    }
  }

  override suspend fun updateServiceCategories(categories: Set<ServiceCategory>): Result<Unit> = runCatching {
    _profile.update { it.copy(supportedServices = categories) }
    if (db != null) {
      val uid = requireUserId()
      val codes = categories.map { it.toBackendCode() }.ifEmpty { listOf("FLAT_TYRE") }
      upsertNonPrivilegedMechanicProfileFields(
        uid,
        mapOf("serviceCategories" to codes)
      )
    }
  }

  override suspend fun updateServiceRadius(radiusKm: Int): Result<Unit> = runCatching {
    _profile.update { it.copy(serviceRadiusKm = radiusKm) }
    if (db != null) {
      val uid = requireUserId()
      upsertNonPrivilegedMechanicProfileFields(
        uid,
        mapOf("serviceRadiusKm" to radiusKm)
      )
    }
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
        detachOffersListeners()
        _availableOffers.value = emptyList()
        _currentOffer.value = null
      } else {
        attachOffersListener(uid)
      }
      isOnline
    }
  }

  override suspend fun submitKycDocuments(idNumber: String, docType: String): Result<Unit> = runCatching {
    _profile.update { it.copy(verificationIdNumber = "$docType: $idNumber") }
    if (db != null) {
      val uid = requireUserId()
      upsertNonPrivilegedMechanicProfileFields(
        uid,
        mapOf("verificationIdNumber" to "$docType: $idNumber")
      )
    }
  }

  override suspend fun updateGpsLocation(lat: Double, lng: Double, address: String?): Result<Unit> = runCatching {
    val now = System.currentTimeMillis()
    val apiResult = backendApiClient?.let { api ->
      runCatching {
        val payload = JSONObject()
          .put("lat", lat)
          .put("lng", lng)
        if (!address.isNullOrBlank()) {
          payload.put("address", address)
        }
        api.callFunction("updateMechanicLocation", payload)
      }
    }

    // If Spark backend is unreachable or unavailable, persist currentLocation directly to Firestore
    if (apiResult == null || apiResult.isFailure) {
      val uid = auth?.currentUser?.uid
      if (uid != null && db != null) {
        upsertNonPrivilegedMechanicProfileFields(
          uid,
          mapOf(
            "currentLocation" to mapOf(
              "lat" to lat,
              "lng" to lng,
              "address" to (address?.takeIf { it.isNotBlank() } ?: _profile.value.baseCityLocality),
              "pincode" to _profile.value.pincode,
              "updatedAt" to now
            ),
            "updatedAt" to now
          )
        )
      } else if (apiResult != null && apiResult.isFailure) {
        throw apiResult.exceptionOrNull()!!
      }
    }
  }

  override fun setSimulateCompetitorSteal(enabled: Boolean) {
    _isSimulateCompetitorSteal.value = enabled
  }

  // --- Job Dispatch Repository Implementation (Spark Backend) ---

  private class AlreadyAssignedByOtherException : IllegalStateException("This request has already been assigned")

  private suspend fun acceptServiceRequestDirectlyViaFirestore(
    requestId: String,
    offer: JobOffer?
  ): JobAcceptanceResult {
    val firestore = db ?: return JobAcceptanceResult.Error("Firestore unavailable.")
    val uid = auth?.currentUser?.uid
      ?: return JobAcceptanceResult.NotEligible("Please sign in before accepting a request.")
    val prof = _profile.value
    val now = System.currentTimeMillis()
    val reqRef = firestore.collection("serviceRequests").document(requestId)

    return try {
      var customerIdForChat = ""
      firestore.runTransaction { tx ->
        val snap = tx.get(reqRef)
        if (!snap.exists()) {
          throw IllegalStateException("Service request no longer exists.")
        }
        val currentStatus = (snap.getString("status") ?: "").uppercase()
        val existingMech = snap.getString("assignedMechanicId")?.trim().orEmpty()
        val legacyMech = snap.getString("mechanicId")?.trim().orEmpty()
        if (currentStatus !in OPEN_DISPATCH_STATUSES || existingMech.isNotBlank() || legacyMech.isNotBlank()) {
          throw AlreadyAssignedByOtherException()
        }
        customerIdForChat = snap.getString("customerId") ?: snap.getString("userId") ?: ""
        tx.update(
          reqRef,
          mapOf(
            "status" to "ASSIGNED",
            "assignedMechanicId" to uid,
            "mechanicId" to uid,
            "assignedMechanicName" to prof.fullName.ifBlank { "FixMechanic Partner" },
            "mechanicName" to prof.fullName.ifBlank { "FixMechanic Partner" },
            "assignedMechanicPhone" to prof.mobileNumber,
            "mechanicPhone" to prof.mobileNumber,
            "assignedMechanicWorkshop" to prof.workshopName,
            "mechanicLat" to prof.latitude,
            "mechanicLng" to prof.longitude,
            "assignedAt" to now,
            "updatedAt" to now
          )
        )
      }.awaitResult()

      directRequestIds.add(requestId)
      countdownJob?.cancel()
      _currentOffer.value = null
      _availableOffers.update { list -> list.filterNot { it.id == requestId || it.requestId == requestId } }

      // Sync mechanic profile activeJobId/isBusy and open private jobChat session
      runCatching {
        firestore.collection("mechanicProfiles").document(uid).update(
          mapOf("activeJobId" to requestId, "isBusy" to true, "updatedAt" to now)
        ).awaitResult()
      }
      runCatching {
        firestore.collection("jobChats").document(requestId).set(
          mapOf(
            "requestId" to requestId,
            "customerId" to customerIdForChat,
            "mechanicId" to uid,
            "status" to "OPEN",
            "createdAt" to now,
            "updatedAt" to now
          ),
          SetOptions.merge()
        ).awaitResult()
      }

      val freshDoc = runCatching { reqRef.get().awaitResult() }.getOrNull()
      val acceptedJob = if (freshDoc != null && freshDoc.exists()) {
        mapDocToActiveJob(freshDoc)
      } else {
        ActiveJob(
          id = requestId,
          requestCode = "FIX-${requestId.takeLast(4).uppercase()}",
          status = JobStatus.ASSIGNED,
          vehicleType = offer?.vehicleType ?: VehicleType.TWO_WHEELER,
          vehicleModel = offer?.vehicleModel ?: "Vehicle",
          problemCategory = offer?.problemCategory ?: ServiceCategory.PUNCTURE_TYRE,
          problemDescription = offer?.problemDescription ?: "Roadside breakdown",
          customerNotes = offer?.customerNotes ?: "Assigned via atomic lock",
          approxDistanceKm = offer?.approxDistanceKm ?: 1.5,
          locality = offer?.locality ?: prof.baseCityLocality,
          exactAddress = offer?.locality ?: prof.baseCityLocality,
          customerDisplayName = "Customer",
          customerSafetyStatus = "Verified FixMechanic Dispatch",
          destinationLat = prof.latitude,
          destinationLng = prof.longitude,
          currentEtaMinutes = 12,
          estimatedPayoutInr = offer?.estimatedPayoutMinInr ?: 349.0,
          createdAtEpochMs = now,
          assignedAtEpochMs = now,
          statusHistory = listOf(StatusTimestamp(JobStatus.ASSIGNED, now / 1000L, "Assigned"))
        )
      }
      _activeJob.value = acceptedJob
      attachActiveJobListener(requestId)
      JobAcceptanceResult.Assigned(acceptedJob)
    } catch (_: AlreadyAssignedByOtherException) {
      countdownJob?.cancel()
      _currentOffer.value = null
      JobAcceptanceResult.TakenByOther("This request has already been assigned")
    } catch (e: Exception) {
      JobAcceptanceResult.Error(e.localizedMessage ?: "Failed accepting service request.")
    }
  }

  override suspend fun acceptJobOffer(offerId: String): JobAcceptanceResult {
    val prof = _profile.value
    if (prof.registrationStatus != RegistrationStatus.APPROVED) {
      return JobAcceptanceResult.NotEligible(
        "Only APPROVED mechanics can accept dispatches (current: ${prof.registrationStatus.label})."
      )
    }

    val matchedOffer = _currentOffer.value?.takeIf { it.id == offerId || it.requestId == offerId }
      ?: _availableOffers.value.firstOrNull { it.id == offerId || it.requestId == offerId }
    val reqIdHint = matchedOffer?.requestId?.ifBlank { offerId } ?: offerId

    // Direct serviceRequests offers claim atomically via Firestore transaction immediately (zero timeout lag)
    if (matchedOffer?.isDirectServiceRequest == true || backendApiClient == null) {
      return acceptServiceRequestDirectlyViaFirestore(reqIdHint, matchedOffer)
    }

    return try {
      val payload = JSONObject().put("offerId", offerId)
      if (reqIdHint.isNotBlank()) payload.put("requestId", reqIdHint)
      val res = backendApiClient.callFunction("acceptJobOffer", payload)
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
          vehicleType = matchedOffer?.vehicleType ?: VehicleType.FOUR_WHEELER,
          vehicleModel = matchedOffer?.vehicleModel ?: "Vehicle",
          problemCategory = matchedOffer?.problemCategory ?: ServiceCategory.PUNCTURE_TYRE,
          problemDescription = matchedOffer?.problemDescription ?: "Roadside breakdown",
          customerNotes = "Assigned via atomic lock",
          approxDistanceKm = matchedOffer?.approxDistanceKm ?: 1.5,
          locality = matchedOffer?.locality ?: prof.baseCityLocality,
          exactAddress = matchedOffer?.locality ?: prof.baseCityLocality,
          customerDisplayName = "Customer",
          customerSafetyStatus = "Verified FixMechanic Dispatch",
          destinationLat = prof.latitude,
          destinationLng = prof.longitude,
          currentEtaMinutes = 12,
          estimatedPayoutInr = matchedOffer?.estimatedPayoutMinInr ?: 349.0,
          createdAtEpochMs = System.currentTimeMillis(),
          assignedAtEpochMs = System.currentTimeMillis(),
          statusHistory = listOf(StatusTimestamp(JobStatus.ASSIGNED, System.currentTimeMillis() / 1000L, "Assigned"))
        )
      }
      _activeJob.value = acceptedJob
      attachActiveJobListener(assignedReqId)
      JobAcceptanceResult.Assigned(acceptedJob)
    } catch (e: IOException) {
      // Spark backend unreachable -> fall back to atomic Firestore transaction
      acceptServiceRequestDirectlyViaFirestore(reqIdHint, matchedOffer)
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
    val matchedOffer = _currentOffer.value?.takeIf { it.id == offerId || it.requestId == offerId }
      ?: _availableOffers.value.firstOrNull { it.id == offerId || it.requestId == offerId }
    val key = matchedOffer?.requestId?.ifBlank { offerId } ?: offerId
    declinedRequestIds.add(key)

    countdownJob?.cancel()
    if (_currentOffer.value?.id == offerId || _currentOffer.value?.requestId == key) {
      _currentOffer.value = null
    }
    publishMergedIncomingOffers()

    val api = backendApiClient
    if (matchedOffer?.isDirectServiceRequest != true && api != null) {
      runCatching {
        api.callFunction(
          "rejectJobOffer",
          JSONObject().put("offerId", offerId).put("reason", "Declined by mechanic")
        )
      }
    }
  }

  override suspend fun advanceJobStatus(jobId: String, nextStatus: JobStatus): Result<JobStatus> = runCatching {
    val now = System.currentTimeMillis()
    val useDirectFirestore = jobId in directRequestIds || backendApiClient == null
    val backendSucceeded = if (!useDirectFirestore) {
      runCatching {
        backendApiClient!!.callFunction(
          "updateJobStatus",
          JSONObject().put("requestId", jobId).put("targetStatus", nextStatus.name)
        )
      }.isSuccess
    } else {
      false
    }

    if (!backendSucceeded) {
      val firestore = db ?: error("Firestore unavailable")
      firestore.collection("serviceRequests").document(jobId).update(
        mapOf(
          "status" to nextStatus.name,
          "updatedAt" to now
        )
      ).awaitResult()
    }

    _activeJob.update { curr ->
      if (curr == null || curr.id != jobId) curr
      else curr.copy(
        status = nextStatus,
        statusHistory = curr.statusHistory + StatusTimestamp(
          nextStatus,
          now / 1000L,
          "Status updated to ${nextStatus.label}"
        )
      )
    }
    nextStatus
  }

  override suspend fun cancelJob(jobId: String, reason: String): Result<Unit> = runCatching {
    val now = System.currentTimeMillis()
    val useDirectFirestore = jobId in directRequestIds || backendApiClient == null
    val backendSucceeded = if (!useDirectFirestore) {
      runCatching {
        backendApiClient!!.callFunction(
          "updateJobStatus",
          JSONObject()
            .put("requestId", jobId)
            .put("targetStatus", "CANCELLED")
            .put("cancellationReason", reason)
        )
      }.isSuccess
    } else {
      false
    }

    if (!backendSucceeded) {
      val firestore = db ?: error("Firestore unavailable")
      val uid = requireUserId()
      firestore.collection("serviceRequests").document(jobId).update(
        mapOf(
          "status" to "CANCELLED",
          "cancellationReason" to reason,
          "updatedAt" to now
        )
      ).awaitResult()
      runCatching {
        firestore.collection("mechanicProfiles").document(uid).update(
          mapOf("activeJobId" to null, "isBusy" to false, "updatedAt" to now)
        ).awaitResult()
      }
      runCatching {
        firestore.collection("jobChats").document(jobId).update(
          mapOf("status" to "CLOSED", "updatedAt" to now)
        ).awaitResult()
      }
    }
    directRequestIds.remove(jobId)
    _activeJob.value = null
  }

  override suspend fun submitJobQuote(jobId: String, quote: JobQuote): Result<JobQuote> = runCatching {
    val approvedQuote = quote.copy(status = QuoteStatus.SUBMITTED_TO_CUSTOMER)
    _activeJob.update { curr ->
      if (curr?.id == jobId) curr.copy(quote = approvedQuote) else curr
    }
    val firestore = db
    if (firestore != null && auth?.currentUser != null) {
      runCatching {
        firestore.collection("serviceRequests").document(jobId).update(
          mapOf(
            "estimatedPriceInr" to approvedQuote.totalCostInr,
            "quoteTotalInr" to approvedQuote.totalCostInr,
            "quoteNotes" to approvedQuote.notes,
            "updatedAt" to System.currentTimeMillis()
          )
        ).awaitResult()
      }
    }
    approvedQuote
  }

  override suspend fun finalizeJobCompletion(jobId: String, report: CompletionReport): Result<Unit> = runCatching {
    val now = System.currentTimeMillis()
    val useDirectFirestore = jobId in directRequestIds || backendApiClient == null
    val backendSucceeded = if (!useDirectFirestore) {
      runCatching {
        backendApiClient!!.callFunction(
          "updateJobStatus",
          JSONObject()
            .put("requestId", jobId)
            .put("targetStatus", "COMPLETED")
            .put("totalPaid", report.finalPayoutAmountInr)
            .put("note", report.workPerformedSummary)
        )
      }.isSuccess
    } else {
      false
    }

    if (!backendSucceeded) {
      val firestore = db ?: error("Firestore unavailable")
      val uid = requireUserId()
      firestore.collection("serviceRequests").document(jobId).update(
        mapOf(
          "status" to "COMPLETED",
          "totalPaid" to report.finalPayoutAmountInr,
          "completionSummary" to report.workPerformedSummary,
          "completedAt" to now,
          "updatedAt" to now
        )
      ).awaitResult()
      runCatching {
        val nextJobs = _profile.value.totalCompletedJobs + 1
        val nextEarnings = _profile.value.todayEarningsInr + report.finalPayoutAmountInr
        firestore.collection("mechanicProfiles").document(uid).update(
          mapOf(
            "activeJobId" to null,
            "isBusy" to false,
            "totalCompletedJobs" to nextJobs,
            "todayEarningsInr" to nextEarnings,
            "updatedAt" to now
          )
        ).awaitResult()
      }
      runCatching {
        firestore.collection("jobChats").document(jobId).update(
          mapOf("status" to "CLOSED", "updatedAt" to now)
        ).awaitResult()
      }
    }
    directRequestIds.remove(jobId)
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
    val trimmed = text.trim()
    val now = System.currentTimeMillis()
    val useDirectFirestore = jobId in directRequestIds || backendApiClient == null
    val backendRes = if (!useDirectFirestore) {
      runCatching {
        backendApiClient!!.callFunction(
          "sendJobChatMessage",
          JSONObject().put("requestId", jobId).put("text", trimmed)
        )
      }.getOrNull()
    } else {
      null
    }

    if (backendRes != null) {
      ChatMessage(
        id = backendRes.optString("messageId", "msg_$now"),
        jobId = jobId,
        senderRole = SenderRole.MECHANIC,
        senderName = senderName,
        text = trimmed,
        timestampEpoch = backendRes.optLong("timestamp", now),
        status = MessageDeliveryStatus.DELIVERED
      )
    } else {
      val firestore = db ?: error("Firestore unavailable")
      val uid = requireUserId()
      val msgRef = firestore.collection("jobChats").document(jobId)
        .collection("jobMessages")
        .add(
          mapOf(
            "senderId" to uid,
            "senderRole" to "MECHANIC",
            "senderName" to senderName,
            "text" to trimmed,
            "timestamp" to now
          )
        ).awaitResult()
      ChatMessage(
        id = msgRef.id,
        jobId = jobId,
        senderRole = SenderRole.MECHANIC,
        senderName = senderName,
        text = trimmed,
        timestampEpoch = now,
        status = MessageDeliveryStatus.DELIVERED
      )
    }
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
    private val OPEN_DISPATCH_STATUSES = listOf(
      "SEARCHING",
      "PENDING_DISPATCH",
      "SEARCHING_FOR_MECHANICS",
      "PENDING",
      "OPEN"
    )

    private fun ensureFirebaseApp(context: Context) {
      runCatching {
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
          com.google.firebase.FirebaseApp.initializeApp(context)
        }
      }
    }

    private fun createFirestore(context: Context): FirebaseFirestore? {
      ensureFirebaseApp(context)
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

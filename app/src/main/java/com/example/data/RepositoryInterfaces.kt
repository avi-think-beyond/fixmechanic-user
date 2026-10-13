package com.example.data

import android.content.Context
import com.example.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
  val currentUserMobile: StateFlow<String?>
  val isAuthenticated: StateFlow<Boolean>
  suspend fun sendOtp(mobileNumber: String): Result<String>
  suspend fun verifyOtp(mobileNumber: String, otpCode: String): Result<MechanicProfile>
  suspend fun registerMechanic(
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
  ): Result<MechanicProfile>
  suspend fun switchDemoProfile(profileId: String)
  suspend fun signOut()
}

interface MechanicProfileRepository {
  val profile: StateFlow<MechanicProfile>
  suspend fun updateProfileDetails(
    fullName: String,
    workshopName: String,
    capability: VehicleCapability,
    cityLocality: String,
    radiusKm: Int
  ): Result<Unit>
  suspend fun updateVehicleCapability(capability: VehicleCapability): Result<Unit>
  suspend fun updateServiceCategories(services: Set<ServiceCategory>): Result<Unit>
  suspend fun updateServiceRadius(radiusKm: Int): Result<Unit>
  suspend fun toggleOnlineStatus(online: Boolean): Result<Boolean>
  suspend fun submitKycDocuments(idNumber: String, docType: String): Result<Unit>
  suspend fun toggleDemoMode(enabled: Boolean)
  suspend fun setSimulateCompetitorSteal(enabled: Boolean)
  val isSimulateCompetitorSteal: StateFlow<Boolean>
}

interface JobDispatchRepository {
  val currentOffer: StateFlow<JobOffer?>
  val activeJob: StateFlow<ActiveJob?>
  val completedHistory: StateFlow<List<CompletedJobRecord>>
  val todayEarningsInr: StateFlow<Double>

  suspend fun acceptJobOffer(offerId: String): JobAcceptanceResult
  suspend fun rejectJobOffer(offerId: String): Result<Unit>
  suspend fun advanceJobStatus(jobId: String, nextStatus: JobStatus): Result<JobStatus>
  suspend fun cancelJob(jobId: String, reason: String): Result<Unit>
  suspend fun submitJobQuote(jobId: String, quote: JobQuote): Result<JobQuote>
  suspend fun finalizeJobCompletion(jobId: String, report: CompletionReport): Result<Unit>
  fun triggerSimulatedOffer(scenarioIndex: Int = 0)
  fun dismissCurrentOffer()
}

interface ChatRepository {
  fun getMessages(jobId: String): Flow<List<ChatMessage>>
  suspend fun sendMessage(jobId: String, text: String, senderName: String): Result<ChatMessage>
  suspend fun retryFailedMessage(messageId: String): Result<Unit>
  fun getQuickCannedResponses(status: JobStatus): List<String>
  fun isChatAllowedForStatus(status: JobStatus): Boolean
}

interface LocationNavigationService {
  fun openExternalTurnByTurn(context: Context, lat: Double, lng: Double, address: String)
  fun isLocationPermissionGranted(context: Context): Boolean
  fun isGpsProviderEnabled(context: Context): Boolean
}

interface FcmNotificationService {
  suspend fun getOrRegisterFcmToken(): String
  fun simulatePushAlert(title: String, message: String)
}

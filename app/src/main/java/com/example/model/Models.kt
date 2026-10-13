package com.example.model

enum class VehicleCapability(val label: String, val description: String) {
  BIKE("Bike / Two-Wheeler", "Motorcycles, scooters, electric 2-wheelers"),
  CAR("Car / Four-Wheeler", "Hatchbacks, sedans, SUVs, electric cars"),
  BOTH("Both Bike & Car", "Equipped to handle both 2-wheeler and 4-wheeler roadside breakdowns")
}

enum class VehicleType(val label: String, val capability: VehicleCapability) {
  TWO_WHEELER("Two-Wheeler (Bike / Scooter)", VehicleCapability.BIKE),
  FOUR_WHEELER("Four-Wheeler (Car / SUV)", VehicleCapability.CAR),
  COMMERCIAL("Light Commercial / Van", VehicleCapability.CAR)
}

enum class ServiceCategory(val label: String, val iconName: String) {
  PUNCTURE_TYRE("Tyre Puncture & Tube Repair", "tire_repair"),
  BATTERY_JUMPSTART("Battery Jumpstart / 12V Boost", "battery_charging_full"),
  FUEL_DELIVERY("Emergency Petrol / Diesel Delivery", "local_gas_station"),
  CLUTCH_BRAKE_CABLE("Clutch, Brake & Chain Cable Repair", "build"),
  LOCKOUT_KEY("Key Lockout Assistance", "lock_open"),
  STARTING_TROUBLE("Engine Starting Trouble & Spark Plug", "electric_bolt"),
  OVERHEATING_COOLANT("Radiator Overheating & Coolant Top-up", "thermostat"),
  TOWING_ASSISTANCE("Flatbed Towing & Recovery Assistance", "rv_hookup")
}

enum class RegistrationStatus(val label: String, val canReceiveOffers: Boolean) {
  APPROVED("Approved (Active)", true),
  PENDING("Pending Verification", false),
  REJECTED("Registration Rejected", false),
  SUSPENDED("Account Suspended", false)
}

enum class JobStatus(val label: String, val nextActionTitle: String) {
  ACCEPTED("Accepted", "Start Navigation (En Route)"),
  EN_ROUTE("En Route", "Confirm Arrival at Breakdown Site"),
  ARRIVED("Arrived on Site", "Begin Diagnostics & Repair"),
  IN_PROGRESS("In Progress", "Complete Job & Collect Payment"),
  COMPLETED("Completed", "Closed"),
  CANCELLED("Cancelled", "Cancelled")
}

enum class QuoteStatus {
  DRAFT,
  SUBMITTED_TO_CUSTOMER,
  APPROVED_BY_CUSTOMER,
  DECLINED
}

data class QuoteItem(
  val id: String,
  val description: String,
  val amountInr: Double
)

data class JobQuote(
  val id: String,
  val jobId: String,
  val items: List<QuoteItem>,
  val laborCostInr: Double,
  val partsCostInr: Double,
  val notes: String,
  val status: QuoteStatus
) {
  val totalCostInr: Double get() = laborCostInr + partsCostInr + items.sumOf { it.amountInr }
}

enum class SenderRole {
  MECHANIC,
  CUSTOMER,
  SYSTEM_DISPATCH
}

enum class MessageDeliveryStatus {
  SENDING,
  SENT,
  DELIVERED,
  FAILED
}

data class ChatMessage(
  val id: String,
  val jobId: String,
  val senderRole: SenderRole,
  val senderName: String,
  val text: String,
  val timestampEpoch: Long,
  val status: MessageDeliveryStatus = MessageDeliveryStatus.DELIVERED,
  val isQuickCanned: Boolean = false
)

data class StatusTimestamp(
  val status: JobStatus,
  val epochSeconds: Long,
  val note: String = ""
)

data class CompletionReport(
  val diagnosticFindings: String,
  val workPerformed: String,
  val partsReplaced: String,
  val photosAttachedCount: Int,
  val customerSignoffToken: String,
  val laborHours: Double,
  val finalPayoutAmountInr: Double,
  val customerRating: Float? = null,
  val customerReview: String? = null
)

data class MechanicProfile(
  val id: String,
  val fullName: String,
  val mobileNumber: String,
  val email: String,
  val workshopName: String,
  val vehicleCapability: VehicleCapability,
  val supportedServices: Set<ServiceCategory>,
  val serviceRadiusKm: Int,
  val baseCityLocality: String,
  val experienceYears: Int,
  val verificationIdNumber: String, // DL / Aadhaar / Trade license
  val registrationStatus: RegistrationStatus,
  val statusReason: String,
  val isOnline: Boolean,
  val isDemoMode: Boolean = true,
  val rating: Float = 4.92f,
  val totalCompletedJobs: Int = 184,
  val todayEarningsInr: Double = 2450.0,
  val activeResponseRatePercent: Int = 98,
  val activeJobId: String? = null
)

data class JobOffer(
  val id: String,
  val requestCode: String,
  val vehicleType: VehicleType,
  val vehicleModel: String,
  val problemCategory: ServiceCategory,
  val problemDescription: String,
  val customerNotes: String,
  val approxDistanceKm: Double,
  val locality: String,
  val safetyNotes: String,
  val estimatedPayoutMinInr: Double,
  val estimatedPayoutMaxInr: Double,
  val totalCountdownSeconds: Int = 30,
  val remainingCountdownSeconds: Int = 30,
  val expiresAtEpochMs: Long = System.currentTimeMillis() + 30000L,
  val simulateTakenByOther: Boolean = false
)

data class ActiveJob(
  val id: String,
  val requestCode: String,
  val status: JobStatus,
  val vehicleType: VehicleType,
  val vehicleModel: String,
  val problemCategory: ServiceCategory,
  val problemDescription: String,
  val customerNotes: String,
  val approxDistanceKm: Double,
  val locality: String,
  val exactAddress: String, // Revealed only for assigned job
  val customerDisplayName: String, // Masked name e.g. "Rohan S." - no raw phone number
  val customerSafetyStatus: String,
  val destinationLat: Double,
  val destinationLng: Double,
  val currentEtaMinutes: Int,
  val estimatedPayoutInr: Double,
  val createdAtEpochMs: Long,
  val assignedAtEpochMs: Long,
  val statusHistory: List<StatusTimestamp>,
  val quote: JobQuote? = null,
  val completionReport: CompletionReport? = null
)

data class CompletedJobRecord(
  val id: String,
  val requestCode: String,
  val completedAtDate: String,
  val vehicleInfo: String,
  val serviceType: String,
  val locality: String,
  val payoutInr: Double,
  val rating: Float,
  val review: String,
  val isCancelled: Boolean = false
)

sealed class JobAcceptanceResult {
  data class Assigned(val job: ActiveJob) : JobAcceptanceResult()
  data class TakenByOther(val message: String) : JobAcceptanceResult()
  data class Expired(val message: String) : JobAcceptanceResult()
  data class NotEligible(val reason: String) : JobAcceptanceResult()
  data class Error(val errorMessage: String) : JobAcceptanceResult()
}

package com.example.model

enum class VehicleCapability(val label: String, val description: String) {
  BIKE("Bike / Two-Wheeler", "Motorcycles, scooters, electric 2-wheelers"),
  CAR("Car / Four-Wheeler", "Hatchbacks, sedans, SUVs, electric cars"),
  BOTH("Both Bike & Car", "Equipped to handle both 2-wheeler and 4-wheeler roadside breakdowns");

  fun toBackendVehicleTypes(): List<String> = when (this) {
    BIKE -> listOf("TWO_WHEELER", "EV_TWO_WHEELER")
    CAR -> listOf("FOUR_WHEELER_CAR", "EV_FOUR_WHEELER", "COMMERCIAL_LIGHT")
    BOTH -> listOf("TWO_WHEELER", "FOUR_WHEELER_CAR", "EV_TWO_WHEELER", "EV_FOUR_WHEELER", "COMMERCIAL_LIGHT")
  }

  companion object {
    fun fromBackendVehicleTypes(types: List<String>): VehicleCapability {
      val norm = types.map { it.uppercase() }
      val hasBike = norm.any { it == "TWO_WHEELER" || it == "EV_TWO_WHEELER" || it == "BIKE" }
      val hasCar = norm.any { it == "FOUR_WHEELER_CAR" || it == "EV_FOUR_WHEELER" || it == "COMMERCIAL_LIGHT" || it == "CAR" }
      return when {
        hasBike && hasCar -> BOTH
        hasBike -> BIKE
        hasCar -> CAR
        else -> BOTH
      }
    }
  }
}

enum class VehicleType(val label: String, val capability: VehicleCapability) {
  TWO_WHEELER("Two-Wheeler (Bike / Scooter)", VehicleCapability.BIKE),
  FOUR_WHEELER("Four-Wheeler (Car / SUV)", VehicleCapability.CAR),
  COMMERCIAL("Light Commercial / Van", VehicleCapability.CAR);

  companion object {
    fun fromBackendCode(raw: String?): VehicleType {
      val s = (raw ?: "").trim().uppercase()
      return when {
        s.contains("TWO_WHEELER") || s == "BIKE" || s == "SCOOTER" -> TWO_WHEELER
        s.contains("COMMERCIAL") || s == "VAN" -> COMMERCIAL
        else -> FOUR_WHEELER
      }
    }
  }
}

enum class ServiceCategory(val label: String, val iconName: String) {
  PUNCTURE_TYRE("Tyre Puncture & Tube Repair", "tire_repair"),
  BATTERY_JUMPSTART("Battery Jumpstart / 12V Boost", "battery_charging_full"),
  FUEL_DELIVERY("Emergency Petrol / Diesel Delivery", "local_gas_station"),
  CLUTCH_BRAKE_CABLE("Clutch, Brake & Chain Cable Repair", "build"),
  LOCKOUT_KEY("Key Lockout Assistance", "lock_open"),
  STARTING_TROUBLE("Engine Starting Trouble & Spark Plug", "electric_bolt"),
  OVERHEATING_COOLANT("Radiator Overheating & Coolant Top-up", "thermostat"),
  TOWING_ASSISTANCE("Flatbed Towing & Recovery Assistance", "rv_hookup");

  fun toBackendCode(): String = when (this) {
    PUNCTURE_TYRE -> "FLAT_TYRE"
    BATTERY_JUMPSTART -> "BATTERY_JUMPSTART"
    FUEL_DELIVERY -> "FUEL_DELIVERY"
    CLUTCH_BRAKE_CABLE -> "CLUTCH_BRAKE_CHAIN"
    LOCKOUT_KEY -> "LOCKOUT"
    STARTING_TROUBLE -> "MINOR_MECHANICAL"
    OVERHEATING_COOLANT -> "OVERHEATING_COOLANT"
    TOWING_ASSISTANCE -> "TOWING"
  }

  companion object {
    fun fromBackendCode(raw: String?): ServiceCategory {
      val s = (raw ?: "").trim().uppercase()
      return when {
        s.contains("TYRE") || s.contains("TIRE") || s.contains("PUNCTURE") || s.contains("FLAT") -> PUNCTURE_TYRE
        s.contains("BATTERY") || s.contains("JUMP") -> BATTERY_JUMPSTART
        s.contains("FUEL") || s.contains("PETROL") -> FUEL_DELIVERY
        s.contains("BRAKE") || s.contains("CLUTCH") || s.contains("CHAIN") -> CLUTCH_BRAKE_CABLE
        s.contains("LOCK") || s.contains("KEY") -> LOCKOUT_KEY
        s.contains("COOLANT") || s.contains("OVERHEAT") -> OVERHEATING_COOLANT
        s.contains("TOW") -> TOWING_ASSISTANCE
        else -> STARTING_TROUBLE
      }
    }
  }
}

enum class RegistrationStatus(val label: String, val canReceiveOffers: Boolean) {
  APPROVED("Approved (Active)", true),
  PENDING("Pending Verification", false),
  REJECTED("Registration Rejected", false),
  SUSPENDED("Account Suspended", false)
}

enum class JobStatus(val label: String, val nextActionTitle: String) {
  ASSIGNED("Assigned", "Confirm Acceptance"),
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
  val workPerformedSummary: String,
  val safetyChecklistPassed: Boolean,
  val customerSignOffToken: String,
  val totalLaborHours: Double,
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
  val pincode: String = "122002",
  val servicePincodes: List<String> = listOf("122002"),
  val latitude: Double = 28.4595,
  val longitude: Double = 77.0266,
  val hasCustomLocation: Boolean = false,
  val experienceYears: Int,
  val verificationIdNumber: String,
  val registrationStatus: RegistrationStatus,
  val statusReason: String,
  val isOnline: Boolean,
  val isDemoMode: Boolean = false,
  val rating: Float = 5.0f,
  val totalCompletedJobs: Int = 0,
  val todayEarningsInr: Double = 0.0,
  val activeResponseRatePercent: Int = 100,
  val activeJobId: String? = null
)

data class JobOffer(
  val id: String,
  val requestId: String = "",
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
  val totalCountdownSeconds: Int = 45,
  val remainingCountdownSeconds: Int = 45,
  val expiresAtEpochMs: Long = System.currentTimeMillis() + 45000L,
  val simulateTakenByOther: Boolean = false,
  val isDirectServiceRequest: Boolean = false
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
  val exactAddress: String,
  val customerDisplayName: String,
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

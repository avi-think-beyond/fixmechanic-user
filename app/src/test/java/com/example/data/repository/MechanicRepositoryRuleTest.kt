package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.FixMechanicRepositoryImpl
import com.example.model.JobAcceptanceResult
import com.example.model.JobStatus
import com.example.model.ServiceCategory
import com.example.model.VehicleCapability
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.UUID

class MechanicRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun registerMechanic_authenticatedUser_createsDocumentInFirestore() = runBlocking {
    val email = "mechanic_${UUID.randomUUID().toString().take(8)}@test.com"
    val uid = signInTestUser(email)
    val scope = CoroutineScope(Dispatchers.IO)
    val repo = FixMechanicRepositoryImpl(firestore, auth, scope)

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.registerMechanic(
        fullName = "Ramesh Sharma",
        mobileNumber = "+919876543210",
        email = "mechanic_ramesh@test.com",
        workshopName = "Sharma Auto Garage",
        capability = VehicleCapability.BOTH,
        services = setOf(ServiceCategory.PUNCTURE_TYRE, ServiceCategory.BATTERY_JUMPSTART),
        radiusKm = 15,
        cityLocality = "Bengaluru",
        experienceYears = 8,
        verificationId = "DL-KA03-2021"
      )
    }

    assertTrue(result.isSuccess)
    val snap = firestore.collection("mechanics").document(uid).get().await()
    assertTrue(snap.exists())
    assertEquals("Ramesh Sharma", snap.getString("fullName"))
    assertEquals("+919876543210", snap.getString("mobileNumber"))
  }

  private suspend fun seedApprovedMechanic(uid: String) {
    firestore.collection("mechanics").document(uid).set(
      mapOf(
        "userId" to uid,
        "fullName" to "Mohan Mechanic",
        "mobileNumber" to "+919876543210",
        "registrationStatus" to "APPROVED",
        "isOnline" to true,
        "vehicleCapability" to "BOTH",
        "supportedServices" to listOf("PUNCTURE_TYRE", "BATTERY_JUMPSTART"),
        "serviceRadiusKm" to 15,
        "createdAt" to Date(),
        "updatedAt" to Date()
      )
    ).await()
  }

  @Test
  fun atomicClaimJobOffer_firstWins_secondReceivesTakenByOther() = runBlocking {
    val reqId = "req_" + UUID.randomUUID().toString().replace("-", "")

    // Customer creates pending emergency request
    val custUid = signInTestUser("cust_${UUID.randomUUID().toString().take(6)}@test.com")
    firestore.collection("emergency_requests").document(reqId).set(
      mapOf(
        "customerId" to custUid,
        "customerDisplayName" to "Customer A",
        "vehicleType" to "FOUR_WHEELER",
        "problemCategory" to "BATTERY_JUMPSTART",
        "status" to "PENDING_DISPATCH",
        "assignedMechanicId" to null,
        "createdAt" to Date()
      )
    ).await()

    val scope = CoroutineScope(Dispatchers.IO)

    // Mechanic 1 signs in, registers as approved, and claims the offer
    val m1Uid = signInTestUser("m1_${UUID.randomUUID().toString().take(6)}@test.com")
    seedApprovedMechanic(m1Uid)
    val m1Repo = FixMechanicRepositoryImpl(firestore, auth, scope)

    val claim1 = withTimeout(DEFAULT_TIMEOUT_MS) {
      m1Repo.acceptJobOffer(reqId)
    }
    assertTrue("Mechanic 1 should win the job", claim1 is JobAcceptanceResult.Assigned)

    // Verify status in Firestore is now ACCEPTED
    val updatedSnap = firestore.collection("emergency_requests").document(reqId).get().await()
    assertEquals("ACCEPTED", updatedSnap.getString("status"))
    assertEquals(m1Uid, updatedSnap.getString("assignedMechanicId"))

    // Mechanic 2 signs in, registers as approved, and tries to claim the same job
    val m2Uid = signInTestUser("m2_${UUID.randomUUID().toString().take(6)}@test.com")
    seedApprovedMechanic(m2Uid)
    val m2Repo = FixMechanicRepositoryImpl(firestore, auth, scope)

    val claim2 = withTimeout(DEFAULT_TIMEOUT_MS) {
      m2Repo.acceptJobOffer(reqId)
    }
    assertTrue("Mechanic 2 should receive TakenByOther", claim2 is JobAcceptanceResult.TakenByOther)
    val takenMsg = (claim2 as JobAcceptanceResult.TakenByOther).message
    assertEquals("This request has already been assigned", takenMsg)
  }

  @Test
  fun advanceJobStatus_assignedMechanic_updatesStatusInFirestore() = runBlocking {
    val reqId = "req_" + UUID.randomUUID().toString().replace("-", "")

    // Customer creates pending emergency request
    val custUid = signInTestUser("cust_${UUID.randomUUID().toString().take(6)}@test.com")
    firestore.collection("emergency_requests").document(reqId).set(
      mapOf(
        "customerId" to custUid,
        "customerDisplayName" to "Customer XYZ",
        "vehicleType" to "TWO_WHEELER",
        "problemCategory" to "PUNCTURE_TYRE",
        "status" to "PENDING_DISPATCH",
        "assignedMechanicId" to null,
        "createdAt" to Date()
      )
    ).await()

    val scope = CoroutineScope(Dispatchers.IO)

    // Mechanic signs in, registers as approved, and accepts the job
    val mUid = signInTestUser("mech_${UUID.randomUUID().toString().take(6)}@test.com")
    seedApprovedMechanic(mUid)
    val repo = FixMechanicRepositoryImpl(firestore, auth, scope)

    val claim = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.acceptJobOffer(reqId)
    }
    assertTrue("Mechanic should claim successfully", claim is JobAcceptanceResult.Assigned)

    // Advance status to EN_ROUTE
    val advanceResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.advanceJobStatus(reqId, JobStatus.EN_ROUTE)
    }
    assertTrue("Advance status should succeed", advanceResult.isSuccess)

    // Verify updated status in Firestore is EN_ROUTE
    val snap = firestore.collection("emergency_requests").document(reqId).get().await()
    assertEquals("EN_ROUTE", snap.getString("status"))
  }

  @Test
  fun unauthenticatedUser_cannotWriteToMechanics() = runBlocking {
    auth.signOut()
    var permissionDenied = false
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore.collection("mechanics").document("unauth_mech").set(
          mapOf("fullName" to "Hacker", "createdAt" to Date())
        ).await()
      }
    } catch (e: Exception) {
      val fe = e.unwrapFirestoreException()
      if (fe != null && fe.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        permissionDenied = true
      } else if (e.message?.contains("PERMISSION_DENIED") == true) {
        permissionDenied = true
      }
    }
    assertTrue("Unauthenticated write must be rejected with PERMISSION_DENIED", permissionDenied)
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 5000L
  }
}

package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.FirestoreProvider.awaitResult
import com.example.data.FixMechanicRepositoryImpl
import com.example.model.JobAcceptanceResult
import com.example.model.JobStatus
import com.example.model.RegistrationStatus
import com.example.model.ServiceCategory
import com.example.model.VehicleCapability
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.util.UUID

class MechanicRepositoryRuleTest : FirestoreEmulatorTestBase() {

  private fun seedUserRole(uid: String, role: String, email: String) {
    seedFirestoreDocViaRest(
      "users",
      uid,
      """{"uid":{"stringValue":"$uid"},"role":{"stringValue":"$role"},"email":{"stringValue":"$email"},"disabled":{"booleanValue":false}}"""
    )
  }

  private fun seedApprovedMechanicProfile(uid: String, name: String) {
    val now = System.currentTimeMillis()
    seedUserRole(uid, "MECHANIC", "$uid@test.com")
    seedFirestoreDocViaRest(
      "mechanicProfiles",
      uid,
      """{"mechanicId":{"stringValue":"$uid"},"name":{"stringValue":"$name"},"phone":{"stringValue":"9876543210"},"approvalStatus":{"stringValue":"APPROVED"},"isOnline":{"booleanValue":true},"isBusy":{"booleanValue":false},"vehicleTypes":{"arrayValue":{"values":[{"stringValue":"TWO_WHEELER"},{"stringValue":"FOUR_WHEELER_CAR"}]}},"serviceCategories":{"arrayValue":{"values":[{"stringValue":"FLAT_TYRE"},{"stringValue":"BATTERY_JUMPSTART"}]}},"serviceRadiusKm":{"integerValue":"15"},"currentLocation":{"mapValue":{"fields":{"lat":{"doubleValue":28.4595},"lng":{"doubleValue":77.0266},"address":{"stringValue":"Sector 29 Gurugram"},"updatedAt":{"integerValue":"$now"}}}}}"""
    )
  }

  @Test
  fun registerMechanic_createsPendingOfflineProfileInUnifiedCollection() = runBlocking(Dispatchers.IO) {
    Assume.assumeTrue("Emulators & Spark backend must run on 8085/9099/5055", areEmulatorsRunning())
    withTimeout(DEFAULT_TIMEOUT_MS) {
      val email = "mech_${UUID.randomUUID().toString().take(8)}@test.com"
      val uid = signInTestUser(email)
      val repo = FixMechanicRepositoryImpl(firestore, auth, CoroutineScope(Dispatchers.IO), "http://127.0.0.1:$SPARK_PORT")

      val result = repo.registerMechanic(
        fullName = "Ramesh Sharma",
        mobileNumber = "+919876543210",
        email = email,
        workshopName = "Sharma Auto Garage",
        capability = VehicleCapability.BOTH,
        services = setOf(ServiceCategory.PUNCTURE_TYRE, ServiceCategory.BATTERY_JUMPSTART),
        radiusKm = 15,
        cityLocality = "Sector 29 Gurugram",
        experienceYears = 8,
        verificationId = "DL-HR26-2021"
      )

      assertTrue("registerMechanic failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
      val snap = firestore.collection("mechanicProfiles").document(uid).get().awaitResult()
      assertTrue(snap.exists())
      assertEquals("Ramesh Sharma", snap.getString("name"))
      assertEquals("PENDING", snap.getString("approvalStatus"))
      assertEquals(false, snap.getBoolean("isOnline"))

      // Pending mechanic cannot go online
      val toggleRes = repo.toggleOnlineStatus(true)
      assertTrue(toggleRes.isFailure)
    }
  }

  @Test
  fun acceptJobOffer_sparkAtomicAcceptance_firstWinsSecondGetsTakenByOther() = runBlocking(Dispatchers.IO) {
    Assume.assumeTrue("Emulators & Spark backend must run on 8085/9099/5055", areEmulatorsRunning())
    withTimeout(DEFAULT_TIMEOUT_MS) {
      val now = System.currentTimeMillis()
      val reqId = "req_${UUID.randomUUID().toString().take(8)}"

      val m1Email = "m1_${UUID.randomUUID().toString().take(6)}@test.com"
      val m1Uid = signInTestUser(m1Email)
      seedApprovedMechanicProfile(m1Uid, "Mech Winner")
      val m2Email = "m2_${UUID.randomUUID().toString().take(6)}@test.com"
      val m2Uid = signInTestUser(m2Email)
      seedApprovedMechanicProfile(m2Uid, "Mech Second")

      seedFirestoreDocViaRest(
        "serviceRequests",
        reqId,
        """{"id":{"stringValue":"$reqId"},"customerId":{"stringValue":"cust_1"},"customerName":{"stringValue":"Customer A"},"vehicleType":{"stringValue":"TWO_WHEELER"},"serviceCategory":{"stringValue":"FLAT_TYRE"},"status":{"stringValue":"SEARCHING"},"createdAt":{"integerValue":"$now"}}"""
      )
      seedFirestoreDocViaRest(
        "jobOffers",
        "offer_m1_$reqId",
        """{"id":{"stringValue":"offer_m1_$reqId"},"requestId":{"stringValue":"$reqId"},"mechanicId":{"stringValue":"$m1Uid"},"status":{"stringValue":"PENDING"},"offeredAt":{"integerValue":"$now"},"expiresAt":{"integerValue":"${now + 60000}"}}"""
      )
      seedFirestoreDocViaRest(
        "jobOffers",
        "offer_m2_$reqId",
        """{"id":{"stringValue":"offer_m2_$reqId"},"requestId":{"stringValue":"$reqId"},"mechanicId":{"stringValue":"$m2Uid"},"status":{"stringValue":"PENDING"},"offeredAt":{"integerValue":"$now"},"expiresAt":{"integerValue":"${now + 60000}"}}"""
      )

      // Mechanic 1 signs in and accepts first via Spark backend
      auth.signInWithEmailAndPassword(m1Email, DEFAULT_PASSWORD).awaitResult()
      val m1Repo = FixMechanicRepositoryImpl(firestore, auth, CoroutineScope(Dispatchers.IO), "http://127.0.0.1:$SPARK_PORT")
      var attempts = 0
      while (m1Repo.profile.value.registrationStatus != RegistrationStatus.APPROVED && attempts++ < 30) {
        kotlinx.coroutines.delay(100)
      }
      assertEquals(RegistrationStatus.APPROVED, m1Repo.profile.value.registrationStatus)
      val firstRes = m1Repo.acceptJobOffer("offer_m1_$reqId")
      assertTrue("Expected Assigned for M1, got $firstRes", firstRes is JobAcceptanceResult.Assigned)

      // Mechanic 2 signs in and attempts to accept the already-assigned request
      auth.signInWithEmailAndPassword(m2Email, DEFAULT_PASSWORD).awaitResult()
      val m2Repo = FixMechanicRepositoryImpl(firestore, auth, CoroutineScope(Dispatchers.IO), "http://127.0.0.1:$SPARK_PORT")
      attempts = 0
      while (m2Repo.profile.value.registrationStatus != RegistrationStatus.APPROVED && attempts++ < 30) {
        kotlinx.coroutines.delay(100)
      }
      assertEquals(RegistrationStatus.APPROVED, m2Repo.profile.value.registrationStatus)
      val secondRes = m2Repo.acceptJobOffer("offer_m2_$reqId")
      assertTrue("Expected TakenByOther for M2, got $secondRes", secondRes is JobAcceptanceResult.TakenByOther)
    }
  }

  @Test
  fun advanceJobStatus_viaSparkBackend_updatesServiceRequestAndCompletes() = runBlocking(Dispatchers.IO) {
    Assume.assumeTrue("Emulators & Spark backend must run on 8085/9099/5055", areEmulatorsRunning())
    withTimeout(DEFAULT_TIMEOUT_MS) {
      val now = System.currentTimeMillis()
      val reqId = "req_${UUID.randomUUID().toString().take(8)}"
      val mEmail = "mech_flow_${UUID.randomUUID().toString().take(6)}@test.com"
      val mUid = signInTestUser(mEmail)
      seedApprovedMechanicProfile(mUid, "Flow Mechanic")

      seedFirestoreDocViaRest(
        "serviceRequests",
        reqId,
        """{"id":{"stringValue":"$reqId"},"customerId":{"stringValue":"cust_flow"},"customerName":{"stringValue":"Customer Flow"},"vehicleType":{"stringValue":"TWO_WHEELER"},"serviceCategory":{"stringValue":"FLAT_TYRE"},"status":{"stringValue":"SEARCHING"},"createdAt":{"integerValue":"$now"}}"""
      )
      val offerId = "offer_${UUID.randomUUID().toString().take(6)}"
      seedFirestoreDocViaRest(
        "jobOffers",
        offerId,
        """{"id":{"stringValue":"$offerId"},"requestId":{"stringValue":"$reqId"},"mechanicId":{"stringValue":"$mUid"},"status":{"stringValue":"PENDING"},"offeredAt":{"integerValue":"$now"},"expiresAt":{"integerValue":"${now + 60000}"}}"""
      )

      val repo = FixMechanicRepositoryImpl(firestore, auth, CoroutineScope(Dispatchers.IO), "http://127.0.0.1:$SPARK_PORT")
      // Wait for snapshot to mark profile APPROVED
      var attempts = 0
      while (repo.profile.value.registrationStatus != RegistrationStatus.APPROVED && attempts++ < 30) {
        kotlinx.coroutines.delay(100)
      }
      assertEquals(RegistrationStatus.APPROVED, repo.profile.value.registrationStatus)

      val acceptRes = repo.acceptJobOffer(offerId)
      assertTrue("Expected Assigned, got $acceptRes", acceptRes is JobAcceptanceResult.Assigned)

      val adv1 = repo.advanceJobStatus(reqId, JobStatus.ACCEPTED)
      assertTrue("Failed advancing to ACCEPTED: ${adv1.exceptionOrNull()?.message}", adv1.isSuccess)
      val adv2 = repo.advanceJobStatus(reqId, JobStatus.EN_ROUTE)
      assertTrue("Failed advancing to EN_ROUTE: ${adv2.exceptionOrNull()?.message}", adv2.isSuccess)

      val snap = firestore.collection("serviceRequests").document(reqId).get().awaitResult()
      assertEquals("EN_ROUTE", snap.getString("status"))
      assertEquals(mUid, snap.getString("assignedMechanicId"))
    }
  }

  @Test
  fun unauthenticatedUser_cannotWriteToMechanicProfiles() = runBlocking(Dispatchers.IO) {
    Assume.assumeTrue("Emulators & Spark backend must run on 8085/9099/5055", areEmulatorsRunning())
    withTimeout(DEFAULT_TIMEOUT_MS) {
      auth.signOut()
      var denied = false
      try {
        firestore.collection("mechanicProfiles").document("unauth_mech").set(
          mapOf("name" to "Hacker", "approvalStatus" to "PENDING")
        ).awaitResult()
      } catch (e: Exception) {
        val fe = e.unwrapFirestoreException()
        if (fe?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
          e.message?.contains("PERMISSION_DENIED") == true
        ) {
          denied = true
        }
      }
      assertTrue("Unauthenticated write must be rejected", denied)
    }
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 10000L
  }
}

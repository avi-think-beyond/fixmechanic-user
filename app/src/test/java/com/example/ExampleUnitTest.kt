package com.example

import com.example.data.FixMechanicRepositoryImpl
import com.example.model.JobAcceptanceResult
import com.example.model.JobStatus
import com.example.model.RegistrationStatus
import com.example.model.ServiceCategory
import com.example.model.VehicleCapability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {
  private val testDispatcher = StandardTestDispatcher()
  private val testScope = TestScope(testDispatcher)

  @Test
  fun testCanonicalBackendMappingAndEligibilityRules() = testScope.runTest {
    assertFalse(RegistrationStatus.PENDING.canReceiveOffers)
    assertFalse(RegistrationStatus.REJECTED.canReceiveOffers)
    assertFalse(RegistrationStatus.SUSPENDED.canReceiveOffers)
    assertTrue(RegistrationStatus.APPROVED.canReceiveOffers)

    assertEquals(listOf("TWO_WHEELER", "EV_TWO_WHEELER"), VehicleCapability.BIKE.toBackendVehicleTypes())
    assertEquals(
      listOf("FOUR_WHEELER_CAR", "EV_FOUR_WHEELER", "COMMERCIAL_LIGHT"),
      VehicleCapability.CAR.toBackendVehicleTypes()
    )
    assertEquals(
      VehicleCapability.BOTH,
      VehicleCapability.fromBackendVehicleTypes(listOf("TWO_WHEELER", "FOUR_WHEELER_CAR"))
    )

    assertEquals("FLAT_TYRE", ServiceCategory.PUNCTURE_TYRE.toBackendCode())
    assertEquals("BATTERY_JUMPSTART", ServiceCategory.BATTERY_JUMPSTART.toBackendCode())
    assertEquals("FUEL_DELIVERY", ServiceCategory.FUEL_DELIVERY.toBackendCode())
    assertEquals(ServiceCategory.PUNCTURE_TYRE, ServiceCategory.fromBackendCode("FLAT_TYRE"))
  }

  @Test
  fun testProductionRepositoryRequiresAuthenticationForOffersAndChat() = testScope.runTest {
    val repository = FixMechanicRepositoryImpl(
      db = null,
      auth = null,
      scope = testScope,
      backendBaseUrl = "http://127.0.0.1:5055"
    )
    assertEquals(RegistrationStatus.PENDING, repository.profile.value.registrationStatus)

    val offerResult = repository.acceptJobOffer("offer_test_1")
    assertTrue(
      "Unauthenticated/unapproved mechanic must not accept offers",
      offerResult is JobAcceptanceResult.NotEligible
    )

    assertTrue(repository.isChatAllowedForStatus(JobStatus.ASSIGNED))
    assertTrue(repository.isChatAllowedForStatus(JobStatus.ACCEPTED))
    assertTrue(repository.isChatAllowedForStatus(JobStatus.EN_ROUTE))
    assertTrue(repository.isChatAllowedForStatus(JobStatus.ARRIVED))
    assertTrue(repository.isChatAllowedForStatus(JobStatus.IN_PROGRESS))
    assertFalse(repository.isChatAllowedForStatus(JobStatus.COMPLETED))
    assertFalse(repository.isChatAllowedForStatus(JobStatus.CANCELLED))
  }

  @Test
  fun testUpdateServiceAreaAndPincode() = testScope.runTest {
    val repository = FixMechanicRepositoryImpl(
      db = null,
      auth = null,
      scope = testScope,
      backendBaseUrl = "http://127.0.0.1:5055"
    )
    val initialProfile = repository.profile.value
    assertEquals("122002", initialProfile.pincode)

    val updateResult = repository.updateServiceAreaAndPincode(
      cityLocality = "Koramangala 4th Block, Bengaluru",
      pincode = "560034",
      radiusKm = 20,
      servicePincodes = listOf("560034", "560095", "560047")
    )

    assertTrue(updateResult.isSuccess)
    val updatedProfile = repository.profile.value
    assertEquals("Koramangala 4th Block, Bengaluru", updatedProfile.baseCityLocality)
    assertEquals("560034", updatedProfile.pincode)
    assertEquals(20, updatedProfile.serviceRadiusKm)
    assertEquals(listOf("560034", "560095", "560047"), updatedProfile.servicePincodes)
  }

  @Test
  fun testUpdateProfileDetailsAndCoordinates() = testScope.runTest {
    val repository = FixMechanicRepositoryImpl(
      db = null,
      auth = null,
      scope = testScope,
      backendBaseUrl = "http://127.0.0.1:5055"
    )
    val res = repository.updateProfileDetails(
      fullName = "Vikram Nair",
      workshopName = "Nair Auto Care",
      capability = VehicleCapability.BOTH,
      cityLocality = "Andheri West, Mumbai",
      radiusKm = 25,
      pincode = "400053",
      latitude = 19.1363,
      longitude = 72.8277
    )

    assertTrue(res.isSuccess)
    val updated = repository.profile.value
    assertEquals("Vikram Nair", updated.fullName)
    assertEquals("Andheri West, Mumbai", updated.baseCityLocality)
    assertEquals("400053", updated.pincode)
    assertEquals(25, updated.serviceRadiusKm)
    assertEquals(19.1363, updated.latitude, 0.0001)
    assertEquals(72.8277, updated.longitude, 0.0001)
    assertTrue(updated.servicePincodes.contains("400053"))
  }

  @Test
  fun testSyncInitialDeviceLocation_DefaultsUntilCustomLocationSaved() = testScope.runTest {
    val repository = FixMechanicRepositoryImpl(
      db = null,
      auth = null,
      scope = testScope,
      backendBaseUrl = "http://127.0.0.1:5055"
    )
    assertFalse(repository.profile.value.hasCustomLocation)

    // 1. Before custom location is saved, mobile GPS coordinates update default locality/PIN/coords
    repository.syncInitialDeviceLocation(
      latitude = 17.4435,
      longitude = 78.3772,
      locality = "Madhapur, Hyderabad",
      pincode = "500081"
    )
    assertEquals(17.4435, repository.profile.value.latitude, 0.0001)
    assertEquals(78.3772, repository.profile.value.longitude, 0.0001)
    assertEquals("Madhapur, Hyderabad", repository.profile.value.baseCityLocality)
    assertEquals("500081", repository.profile.value.pincode)
    assertFalse(repository.profile.value.hasCustomLocation)

    // 2. Saving profile details marks hasCustomLocation = true
    repository.updateProfileDetails(
      fullName = "Suresh Reddy",
      workshopName = "Reddy Motors",
      capability = com.example.model.VehicleCapability.BOTH,
      cityLocality = "Gachibowli, Hyderabad",
      radiusKm = 20,
      pincode = "500032",
      latitude = 17.4401,
      longitude = 78.3489
    )
    assertTrue(repository.profile.value.hasCustomLocation)

    // 3. Subsequent non-forced mobile GPS sync must NOT clobber the explicitly saved workshop location
    repository.syncInitialDeviceLocation(
      latitude = 19.1363,
      longitude = 72.8277,
      locality = "Andheri West, Mumbai",
      pincode = "400053"
    )
    assertEquals(17.4401, repository.profile.value.latitude, 0.0001)
    assertEquals(78.3489, repository.profile.value.longitude, 0.0001)
    assertEquals("Gachibowli, Hyderabad", repository.profile.value.baseCityLocality)
    assertEquals("500032", repository.profile.value.pincode)
  }
}

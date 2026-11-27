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
}

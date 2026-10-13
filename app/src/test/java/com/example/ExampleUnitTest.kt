package com.example

import com.example.data.FixMechanicRepositoryImpl
import com.example.model.JobAcceptanceResult
import com.example.model.JobStatus
import com.example.model.RegistrationStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {

  private val testDispatcher = StandardTestDispatcher()
  private val testScope = TestScope(testDispatcher)

  @Test
  fun testMechanicEligibilityRule_UnapprovedCannotAcceptOffers() = testScope.runTest {
    val repository = FixMechanicRepositoryImpl(testScope)
    repository.switchDemoProfile("amit") // Amit is PENDING verification

    assertEquals(RegistrationStatus.PENDING, repository.profile.value.registrationStatus)
    assertFalse(repository.profile.value.registrationStatus.canReceiveOffers)

    repository.triggerSimulatedOffer(0)
    val offer = repository.currentOffer.value
    assertNotNull(offer)

    val result = repository.acceptJobOffer(offer!!.id)
    assertTrue("Unapproved mechanic must not be able to accept real emergency offers", result is JobAcceptanceResult.NotEligible)
  }

  @Test
  fun testBackendAssignmentRaceCondition_CompetitorWinsFirst() = testScope.runTest {
    val repository = FixMechanicRepositoryImpl(testScope)
    repository.switchDemoProfile("rajesh") // Rajesh is APPROVED
    repository.setSimulateCompetitorSteal(true)

    repository.triggerSimulatedOffer(0)
    val offer = repository.currentOffer.value
    assertNotNull(offer)

    val result = repository.acceptJobOffer(offer!!.id)
    assertTrue(result is JobAcceptanceResult.TakenByOther)
    val takenResult = result as JobAcceptanceResult.TakenByOther
    assertEquals("This request has already been assigned", takenResult.message)
    assertNull("Expired offer must be removed after competitor assignment", repository.currentOffer.value)
  }

  @Test
  fun testSuccessfulBackendAssignmentAndStrictStateTransitions() = testScope.runTest {
    val repository = FixMechanicRepositoryImpl(testScope)
    repository.switchDemoProfile("rajesh")
    repository.setSimulateCompetitorSteal(false)

    repository.triggerSimulatedOffer(0)
    val offer = repository.currentOffer.value
    assertNotNull(offer)

    val result = repository.acceptJobOffer(offer!!.id)
    assertTrue("Approved mechanic should win assignment when no competitor", result is JobAcceptanceResult.Assigned)
    val assignedJob = (result as JobAcceptanceResult.Assigned).job
    assertEquals(JobStatus.ACCEPTED, assignedJob.status)

    // Arbitrary skip test: Cannot jump directly from ACCEPTED to COMPLETED
    val invalidSkip = repository.advanceJobStatus(assignedJob.id, JobStatus.COMPLETED)
    assertTrue("Arbitrary state skip must be rejected by backend validation", invalidSkip.isFailure)

    // Valid progression: ACCEPTED -> EN_ROUTE
    val statusEnRoute = repository.advanceJobStatus(assignedJob.id, JobStatus.EN_ROUTE)
    assertTrue(statusEnRoute.isSuccess)
    assertEquals(JobStatus.EN_ROUTE, repository.activeJob.value?.status)

    // EN_ROUTE -> ARRIVED
    val statusArrived = repository.advanceJobStatus(assignedJob.id, JobStatus.ARRIVED)
    assertTrue(statusArrived.isSuccess)
    assertEquals(JobStatus.ARRIVED, repository.activeJob.value?.status)

    // ARRIVED -> IN_PROGRESS
    val statusInProgress = repository.advanceJobStatus(assignedJob.id, JobStatus.IN_PROGRESS)
    assertTrue(statusInProgress.isSuccess)
    assertEquals(JobStatus.IN_PROGRESS, repository.activeJob.value?.status)

    // Verify Chat is allowed while in progress but rejected when completed
    assertTrue(repository.isChatAllowedForStatus(JobStatus.IN_PROGRESS))
    assertFalse(repository.isChatAllowedForStatus(JobStatus.COMPLETED))
    assertFalse(repository.isChatAllowedForStatus(JobStatus.CANCELLED))
  }
}

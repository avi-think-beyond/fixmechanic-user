package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FixMechanicRepositoryImpl
import com.example.model.ServiceCategory
import com.example.model.VehicleCapability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FixMechanic Mechanic", appName)
  }

  @Test
  fun `vehicle capability and service category map to canonical backend enums`() {
    assertEquals(
      listOf("TWO_WHEELER", "EV_TWO_WHEELER"),
      VehicleCapability.BIKE.toBackendVehicleTypes()
    )
    assertEquals("FLAT_TYRE", ServiceCategory.PUNCTURE_TYRE.toBackendCode())
    assertEquals(ServiceCategory.PUNCTURE_TYRE, ServiceCategory.fromBackendCode("FLAT_TYRE"))
    assertTrue(ServiceCategory.fromBackendCode("BATTERY_JUMPSTART") == ServiceCategory.BATTERY_JUMPSTART)
    val repo = FixMechanicRepositoryImpl(CoroutineScope(Dispatchers.Unconfined))
    assertEquals(false, repo.profile.value.isDemoMode)
  }
}

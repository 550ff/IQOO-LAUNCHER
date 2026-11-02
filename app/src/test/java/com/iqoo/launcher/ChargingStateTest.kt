package com.iqoo.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingStateTest {
    @Test fun convertsBatteryScaleAndChargingStatus() {
        assertEquals(ChargingState(50, true, true), ChargingState.fromValues(50, 100, 2, 1))
    }

    @Test fun reportsPluggedButNotChargingAccurately() {
        val state = ChargingState.fromValues(74, 100, 3, 1)
        assertTrue(state.active)
        assertFalse(state.charging)
        assertEquals(74, state.percent)
    }

    @Test fun handlesUnknownBatteryValuesWithoutInventingPercentage() {
        assertEquals(ChargingState(null, false, false), ChargingState.fromValues(-1, 0, 1, 0))
    }
}

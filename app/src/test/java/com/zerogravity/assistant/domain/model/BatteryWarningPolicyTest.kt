package com.zerogravity.assistant.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BatteryWarningPolicyTest {
    private val policy = BatteryWarningPolicy()

    @Test
    fun warnsOnceAtTheConfiguredThreshold() {
        assertEquals(
            BatteryWarningDecision.SpeakLowBatteryWarning,
            policy.evaluate(15, warningAlreadyDelivered = false),
        )
        assertEquals(
            BatteryWarningDecision.None,
            policy.evaluate(14, warningAlreadyDelivered = true),
        )
    }

    @Test
    fun rearmsOnlyAfterChargingToTwentyPercent() {
        assertEquals(
            BatteryWarningDecision.None,
            policy.evaluate(19, warningAlreadyDelivered = true),
        )
        assertEquals(
            BatteryWarningDecision.RearmWarning,
            policy.evaluate(20, warningAlreadyDelivered = true),
        )
        assertEquals(
            BatteryWarningDecision.SpeakLowBatteryWarning,
            policy.evaluate(15, warningAlreadyDelivered = false),
        )
    }

    @Test
    fun supportsConfiguredThresholdAndRejectsInvalidThreshold() {
        assertEquals(
            BatteryWarningDecision.SpeakLowBatteryWarning,
            BatteryWarningPolicy(thresholdPercent = 10).evaluate(10, false),
        )
        assertThrows(IllegalArgumentException::class.java) {
            BatteryWarningPolicy(thresholdPercent = 0)
        }
    }
}

package io.github.latticenotes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCooldownTest {
    @Test
    fun retryAfterControlsCooldownWindow() {
        var now = 1_000L
        val cooldown = SyncCooldown(clock = { now })

        cooldown.block(retryAfterSeconds = 120)

        assertTrue(cooldown.isActive())
        assertEquals(120_000L, cooldown.remainingMillis())
        now += 120_000L
        assertFalse(cooldown.isActive())
    }

    @Test
    fun missingRetryAfterUsesConservativeDefault() {
        var now = 1_000L
        val cooldown = SyncCooldown(clock = { now })

        cooldown.block(retryAfterSeconds = null)

        assertEquals(300_000L, cooldown.remainingMillis())
    }
}

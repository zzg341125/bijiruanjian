package io.github.latticenotes.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test
    fun hash_isSaltedAndVerifiable() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("2468", salt)
        assertTrue(PinHasher.verify("2468", salt, hash))
        assertFalse(PinHasher.verify("1357", salt, hash))
        assertNotEquals(hash, PinHasher.hash("2468", PinHasher.newSalt()))
    }
}

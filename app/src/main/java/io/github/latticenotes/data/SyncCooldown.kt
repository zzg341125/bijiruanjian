package io.github.latticenotes.data

class SyncCooldown(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var blockedUntilMillis: Long = 0

    fun block(retryAfterSeconds: Long?) {
        val seconds = retryAfterSeconds
            ?.takeIf { it > 0 }
            ?.coerceAtMost(MAX_COOLDOWN_SECONDS)
            ?: DEFAULT_COOLDOWN_SECONDS
        blockedUntilMillis = clock() + seconds * 1_000L
    }

    fun isActive(): Boolean = remainingMillis() > 0

    fun remainingMillis(): Long =
        (blockedUntilMillis - clock()).coerceAtLeast(0)

    companion object {
        private const val DEFAULT_COOLDOWN_SECONDS = 300L
        private const val MAX_COOLDOWN_SECONDS = 1_800L
    }
}

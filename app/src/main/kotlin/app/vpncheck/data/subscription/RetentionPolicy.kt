package app.vpncheck.data.subscription

/**
 * Decides what happens to a stored config that was NOT present in the current refresh.
 * Goal: the database tracks the live subscriptions instead of growing forever, while configs
 * that still work somewhere are not thrown away just because the list author rotated them out.
 */
object RetentionPolicy {
    sealed class Decision {
        data object Keep : Decision()
        data class UpdateSources(val sourceIds: Set<String>) : Decision()
        data object Delete : Decision()
    }

    /**
     * @param previous source ids stored for the config
     * @param disabledIds sources switched off in settings (their membership is dropped)
     * @param failedIds sources that could not be downloaded this time (state unknown, membership kept)
     * @param worksSomewhere the config has an OK result on at least one network
     */
    fun forUnseen(
        previous: Set<String>,
        disabledIds: Set<String>,
        failedIds: Set<String>,
        worksSomewhere: Boolean,
    ): Decision {
        val remaining = previous.filter { it in failedIds }.toSet()
        if (remaining.isNotEmpty()) {
            return if (remaining == previous) Decision.Keep else Decision.UpdateSources(remaining)
        }
        val onlyDisabled = previous.isNotEmpty() && previous.all { it in disabledIds }
        if (onlyDisabled) return Decision.Delete
        return if (worksSomewhere) Decision.Keep else Decision.Delete
    }
}

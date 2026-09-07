package app.vpncheck.data.subscription

/**
 * A config can appear in several subscriptions (e.g. both a black-list and a white-list file),
 * so membership is stored as a comma-separated, sorted set of [SubscriptionSource.id].
 */
object SourceMembership {
    fun encode(ids: Collection<String>): String = ids.filter { it.isNotBlank() }.toSortedSet().joinToString(",")

    fun decode(encoded: String?): Set<String> =
        encoded.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /**
     * Merges membership after a refresh: sources seen now win; membership in sources that were not
     * refreshed this time (download failed or source disabled) is carried over; membership in a
     * successfully refreshed source that no longer lists the config is dropped.
     */
    fun merge(previous: Set<String>, seenNow: Set<String>, notRefreshed: Set<String>): Set<String> =
        seenNow + previous.filter { it in notRefreshed }
}

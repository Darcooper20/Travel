package com.travelbenefits.app.domain

/**
 * Merges the per-source result lists of a mailbox scan into one ordered list.
 *
 * This exists because the scan reads at most [com.travelbenefits.app.data.local.SyncSettings.maxEmailsPerSync]
 * emails per run and simply truncates whatever comes after that. Concatenating
 * each source's whole result list in turn therefore starves the end of the
 * list: with a cap of 60, around fifty booking emails plus fifteen programmes
 * returning ten messages each exhausts the budget before the sixteenth
 * programme is even considered. Programmes added to the catalog later sit at
 * the end of that list, so they were never read at all on a busy mailbox.
 *
 * Round-robin fixes the distribution without raising the cap: every source
 * contributes its first message before any source contributes its second.
 * A source with fewer results simply drops out of later rounds, so nothing is
 * padded and no budget is wasted on sources that found little.
 */
object SearchResultMerge {

    /**
     * [sources] is an ordered list of (owner, items). Order within a round
     * follows [sources], so a deliberate priority between sources is kept as a
     * tie-break; it just no longer decides who is read at all.
     *
     * Returns (owner, item) pairs. Duplicate items are not removed here - the
     * caller dedupes by message id, and which owner first claimed an id is
     * information this function must not throw away.
     */
    fun <K, V> roundRobin(sources: List<Pair<K, List<V>>>): List<Pair<K, V>> {
        if (sources.isEmpty()) return emptyList()
        val deepest = sources.maxOf { it.second.size }
        val merged = ArrayList<Pair<K, V>>(sources.sumOf { it.second.size })
        for (round in 0 until deepest) {
            for ((owner, items) in sources) {
                val item = items.getOrNull(round) ?: continue
                merged += owner to item
            }
        }
        return merged
    }
}

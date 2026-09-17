package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.LoyaltyProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scan reads a capped number of emails per run and throws away whatever
 * sits past the cap. These tests are about who survives that truncation.
 *
 * The bug they exist for: programme results used to be concatenated one whole
 * list at a time, so a programme near the end of the catalog contributed
 * nothing at all on a mailbox with plenty of mail. Qantas and the other
 * non-US programmes were appended to the end of the enum, which put them
 * squarely in the starved tail.
 */
class SearchResultMergeTest {

    @Test
    fun `every source is represented before any source gets a second turn`() {
        val merged = SearchResultMerge.roundRobin(
            listOf(
                "a" to listOf(1, 2, 3),
                "b" to listOf(4, 5, 6),
                "c" to listOf(7, 8, 9),
            ),
        )
        assertEquals(listOf("a" to 1, "b" to 4, "c" to 7, "a" to 2, "b" to 5, "c" to 8, "a" to 3, "b" to 6, "c" to 9), merged)
    }

    @Test
    fun `a source that runs out simply drops out, without padding the result`() {
        val merged = SearchResultMerge.roundRobin(
            listOf("long" to listOf(1, 2, 3), "short" to listOf(9)),
        )
        assertEquals(listOf("long" to 1, "short" to 9, "long" to 2, "long" to 3), merged)
    }

    @Test
    fun `nothing is lost - every item survives the merge exactly once`() {
        val sources = listOf("a" to listOf(1, 2), "b" to listOf(3, 4, 5), "c" to emptyList<Int>())
        val merged = SearchResultMerge.roundRobin(sources)
        assertEquals(sources.sumOf { it.second.size }, merged.size)
        assertEquals(setOf(1, 2, 3, 4, 5), merged.map { it.second }.toSet())
    }

    @Test
    fun `empty input and empty sources are handled`() {
        assertEquals(emptyList<Pair<String, Int>>(), SearchResultMerge.roundRobin<String, Int>(emptyList()))
        assertEquals(emptyList<Pair<String, Int>>(), SearchResultMerge.roundRobin(listOf("a" to emptyList<Int>())))
    }

    /**
     * The regression, stated in the terms it actually broke: a realistic set of
     * travel programmes, ten messages each, truncated at the real default cap.
     * Under the old concatenation the last programmes contributed zero.
     */
    @Test
    fun `under the per-sync cap a late-added programme still gets read`() {
        val programs = LoyaltyProgram.entries
            .filter { it.gmailSenderDomains.isNotEmpty() && it.kind != com.travelbenefits.app.domain.model.LoyaltyProgramKind.SHOP }
        val sources = programs.map { p -> p to (1..10).map { "${p.name}-$it" } }
        val cap = 60

        val roundRobin = SearchResultMerge.roundRobin(sources).take(cap)
        val concatenated = sources.flatMap { (p, ids) -> ids.map { p to it } }.take(cap)

        assertTrue("nothing to prove without more results than the cap", sources.sumOf { it.second.size } > cap)

        val qantasRoundRobin = roundRobin.count { it.first == LoyaltyProgram.QANTAS_FREQUENT_FLYER }
        val qantasConcatenated = concatenated.count { it.first == LoyaltyProgram.QANTAS_FREQUENT_FLYER }
        assertEquals("the old ordering is what starved Qantas", 0, qantasConcatenated)
        assertTrue("Qantas still gets nothing under the cap", qantasRoundRobin > 0)

        // Not just Qantas: no programme may be shut out entirely.
        val starved = programs.filter { p -> roundRobin.none { it.first == p } }
        assertEquals("programmes shut out of the cap entirely: $starved", emptyList<LoyaltyProgram>(), starved)
    }
}

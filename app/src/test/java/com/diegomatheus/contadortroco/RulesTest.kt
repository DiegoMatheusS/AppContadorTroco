package com.diegomatheus.contadortroco

import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class RulesTest {
    private val october = YearMonth.parse("2026-10")

    @Test fun exact150IsEligible() {
        val data = TrocoData(exchanges = listOf(Exchange("1", "Ana", "2026-10-04", 10, 10)))
        val ranking = TrocoRules.ranks(data, october)
        assertEquals(15_000L, ranking.single().totalCents)
        assertTrue(ranking.single().eligible)
        assertTrue(TrocoRules.canDraw(data, october, "ana"))
    }

    @Test fun notesAreSummedAutomatically() {
        val data = TrocoData(exchanges = listOf(Exchange("1", "Ana", "2026-10-10", 7, 5)))
        assertEquals(8_500L, TrocoRules.ranks(data, october).single().totalCents)
    }

    @Test fun sameNameIsGroupedAcrossAccentsAndCase() {
        val data = TrocoData(exchanges = listOf(
            Exchange("1", "José", "2026-10-01", 10, 0),
            Exchange("2", "JOSE", "2026-10-04", 0, 10),
            Exchange("3", "José", "2026-09-04", 0, 50)
        ))
        assertEquals(15_000L, TrocoRules.ranks(data, october).single().totalCents)
        assertEquals(1, TrocoRules.ranks(data, october).size)
    }

    @Test fun monthsAreIndependent() {
        val data = TrocoData(exchanges = listOf(Exchange("1", "Ana", "2026-09-04", 0, 20)))
        assertTrue(TrocoRules.ranks(data, october).isEmpty())
    }

    @Test fun anotherDrawForSamePersonSameMonthIsBlocked() {
        val award = Award("2026-10", "ana", "Ana", "g1", "Trufa", 12345L)
        val data = TrocoData(exchanges = listOf(Exchange("1", "Ana", "2026-10-04", 30, 0)), awards = listOf(award))
        assertFalse(TrocoRules.canDraw(data, october, "ana"))
        assertEquals("Trufa", TrocoRules.ranks(data, october).single().award?.giftName)
    }

    @Test fun emptyCountsAndNegativesNotAllowed() {
        assertFalse(TrocoRules.isValidExchange("Ana", "2026-10-01", 0, 0))
        assertFalse(TrocoRules.isValidExchange("Ana", "2026-10-01", -1, 20))
        assertFalse(TrocoRules.isValidExchange("Ana", "2026-02-30", 20, 0))
        assertTrue(TrocoRules.isValidExchange("Ana", "2026-10-01", 0, 15))
    }
}

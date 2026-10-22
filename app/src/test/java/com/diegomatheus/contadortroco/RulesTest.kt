package com.diegomatheus.contadortroco

import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class RulesTest {
    private val october = YearMonth.parse("2026-10")

    @Test fun directTotalDoesNotRequireNoteCounts() {
        val data = TrocoData(exchanges = listOf(
            Exchange("1", "Ana", "2026-10-04", 0, 0, enteredTotalCents = 15_000L),
            Exchange("2", "Bia", "2026-10-04", 0, 0, enteredTotalCents = 15_500L, noteHint = 5)
        ))
        val ranks = TrocoRules.ranks(data, october)
        assertTrue(ranks.all { it.eligible })
        assertEquals(15_000L, ranks.first { it.name == "Ana" }.totalCents)
        assertFalse(TrocoRules.isValidDirectTotal(0, 0))
        assertTrue(TrocoRules.isValidDirectTotal(15_001L, 0))
    }

    @Test fun exact150IsEligible() {
        val data = TrocoData(exchanges = listOf(Exchange("1", "Ana", "2026-10-04", 10, 10)))
        val ranking = TrocoRules.ranks(data, october)
        assertEquals(15_000L, ranking.single().totalCents)
        assertTrue(ranking.single().eligible)
        assertTrue(TrocoRules.canDraw(data, october, "ana"))
    }

    @Test fun anyCombinationOfNotesIsEligibleFrom150Reais() {
        val examples = listOf(
            Exchange("somente5", "Maria", "2026-10-04", 30, 0),
            Exchange("somente10", "Joao", "2026-10-05", 0, 15),
            Exchange("misturadas", "Carlos", "2026-10-06", 10, 10)
        )
        for (exchange in examples) {
            val data = TrocoData(exchanges = listOf(exchange))
            val rank = TrocoRules.ranks(data, october).single()
            assertEquals(15_000L, rank.totalCents)
            assertTrue("A combinação de notas não deve alterar a elegibilidade", rank.eligible)
            assertTrue(TrocoRules.canDraw(data, october, TrocoRules.personKey(exchange.name)))
        }
    }

    @Test fun amountsBelow150AreNotEligibleRegardlessOfNotes() {
        val examples = listOf(
            Exchange("somente5", "Maria", "2026-10-04", 29, 0),
            Exchange("somente10", "Joao", "2026-10-05", 0, 14),
            Exchange("misturadas", "Carlos", "2026-10-06", 9, 10)
        )
        for (exchange in examples) {
            val data = TrocoData(exchanges = listOf(exchange))
            val rank = TrocoRules.ranks(data, october).single()
            assertTrue(rank.totalCents < REWARD_THRESHOLD_CENTS)
            assertFalse(rank.eligible)
            assertFalse(TrocoRules.canDraw(data, october, TrocoRules.personKey(exchange.name)))
        }
    }

    @Test fun separateExchangesAccumulateUntilEligible() {
        val data = TrocoData(exchanges = listOf(
            Exchange("a", "Ana", "2026-10-01", 10, 0),
            Exchange("b", "Ana", "2026-10-22", 0, 10)
        ))
        val rank = TrocoRules.ranks(data, october).single()
        assertEquals(15_000L, rank.totalCents)
        assertEquals(2, rank.exchangeCount)
        assertTrue(rank.eligible)
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

    @Test fun nextMonthStartsAtZeroButPreviousMonthRemainsInHistory() {
        val october = YearMonth.parse("2026-10")
        val november = YearMonth.parse("2026-11")
        val octoberAward = Award("2026-10", "ana", "Ana", "g1", "Trufa", 12345L)
        val data = TrocoData(
            exchanges = listOf(Exchange("oct", "Ana", "2026-10-31", 30, 0)),
            awards = listOf(octoberAward)
        )
        assertEquals(15_000L, TrocoRules.ranks(data, october).single().totalCents)
        assertTrue(TrocoRules.ranks(data, november).isEmpty())
        assertFalse(TrocoRules.canDraw(data, november, "ana"))
        assertEquals(listOf(october), TrocoRules.historicalMonths(data, november))
        assertEquals(15_000L, TrocoRules.historicalTotalCents(data, november))
        assertEquals("Trufa", TrocoRules.ranks(data, october).single().award?.giftName)
    }

    @Test fun olderExchangesNeverCountForEligibilityInNewMonth() {
        val october = YearMonth.parse("2026-10")
        val november = YearMonth.parse("2026-11")
        val data = TrocoData(exchanges = listOf(
            Exchange("oct", "Ana", "2026-10-30", 100, 0),
            Exchange("nov", "Ana", "2026-11-01", 0, 5)
        ))
        assertEquals(50_000L, TrocoRules.ranks(data, october).single().totalCents)
        val current = TrocoRules.ranks(data, november).single()
        assertEquals(5_000L, current.totalCents)
        assertFalse(current.eligible)
        assertFalse(TrocoRules.canDraw(data, november, "ana"))
        assertEquals(50_000L, TrocoRules.historicalTotalCents(data, november))
    }

    @Test fun historyNeverIncludesActiveOrFutureMonth() {
        val november = YearMonth.parse("2026-11")
        val data = TrocoData(exchanges = listOf(
            Exchange("oct", "Ana", "2026-10-01", 30, 0),
            Exchange("nov", "Bia", "2026-11-01", 0, 15),
            Exchange("dec", "Bia", "2026-12-01", 30, 0)
        ))
        assertEquals(listOf(YearMonth.parse("2026-10")), TrocoRules.historicalMonths(data, november))
        assertEquals(15_000L, TrocoRules.historicalTotalCents(data, november))
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

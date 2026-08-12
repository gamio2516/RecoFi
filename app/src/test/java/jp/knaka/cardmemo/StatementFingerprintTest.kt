package jp.knaka.cardmemo

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementFingerprintTest {
    private val date = LocalDate.of(2026, 7, 1)

    @Test fun orderDoesNotAffectFingerprint() {
        val first = entry(date, 500, "A")
        val second = entry(date.plusDays(1), 600, "B")
        assertEquals(StatementFingerprint.calculate(listOf(first, second)), StatementFingerprint.calculate(listOf(second, first)))
    }

    @Test fun merchantFormattingIsNormalized() {
        assertEquals(StatementFingerprint.calculate(listOf(entry(date, 500, "STAR BUCKS"))), StatementFingerprint.calculate(listOf(entry(date, 500, "starbucks"))))
    }

    @Test fun merchantDifferencePreventsDateAmountCollision() {
        assertNotEquals(StatementFingerprint.calculate(listOf(entry(date, 500, "店舗A"))), StatementFingerprint.calculate(listOf(entry(date, 500, "店舗B"))))
    }

    @Test fun amountAndDateChangesAffectFingerprint() {
        val base = StatementFingerprint.calculate(listOf(entry(date, 500, "A")))
        assertNotEquals(base, StatementFingerprint.calculate(listOf(entry(date, 501, "A"))))
        assertNotEquals(base, StatementFingerprint.calculate(listOf(entry(date.plusDays(1), 500, "A"))))
        assertTrue(base.startsWith("statement:"))
    }

    private fun entry(date: LocalDate, amount: Int, merchant: String) = CardStatementEntry(date, amount, merchant, "")
}

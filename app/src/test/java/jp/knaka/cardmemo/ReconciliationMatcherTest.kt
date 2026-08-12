package jp.knaka.cardmemo

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReconciliationMatcherTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private val base = LocalDate.of(2026, 7, 10)

    @Test fun exactAmountDateAndMerchantMatches() = assertEquals(1L, match(entry(), tx()).transactionId)
    @Test fun oneDayDifferenceMatches() = assertEquals(1L, match(entry(), tx(date = base.plusDays(1))).transactionId)
    @Test fun sevenDayDifferenceMatches() = assertEquals(1L, match(entry(), tx(date = base.plusDays(7))).transactionId)
    @Test fun eightDayDifferenceDoesNotMatch() = assertNull(match(entry(), tx(date = base.plusDays(8))).transactionId)
    @Test fun differentAmountDoesNotMatch() = assertNull(match(entry(), tx(amount = 501)).transactionId)

    @Test fun differentPaymentSourceDoesNotMatch() =
        assertNull(ReconciliationMatcher.match(listOf(entry()), listOf(tx().copy(paymentSourceId = "other")), "card", zone).single().transactionId)

    @Test fun closerDateAndMerchantWinsAmongSameAmountTransactions() {
        val result = match(entry(merchant = "STARBUCKS"), tx(id = 1, date = base.plusDays(4), note = "別店舗"), tx(id = 2, date = base.plusDays(1), note = "Starbucks Coffee"))
        assertEquals(2L, result.transactionId)
    }

    @Test fun transactionIsNotAssignedTwice() {
        val results = ReconciliationMatcher.match(listOf(entry(), entry(merchant = "スターバックス")), listOf(tx()), "card", zone)
        assertEquals(1, results.count { it.transactionId == 1L })
    }

    @Test fun nearlyEqualTopCandidatesRemainAmbiguous() {
        val result = match(entry(), tx(id = 1), tx(id = 2))
        assertNull(result.transactionId)
    }

    private fun match(statement: CardStatementEntry, vararg transactions: Transaction) =
        ReconciliationMatcher.match(listOf(statement), transactions.toList(), "card", zone).single()

    private fun entry(amount: Int = 500, merchant: String = "スターバックス") =
        CardStatementEntry(base, amount, merchant, merchant)

    private fun tx(id: Long = 1, amount: Int = 500, date: LocalDate = base, note: String = "STARBUCKS") =
        Transaction(id, amount, "外食", note, date.atStartOfDay(zone).toInstant().toEpochMilli(), paymentSourceId = "card")
}

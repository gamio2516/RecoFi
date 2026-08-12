package jp.knaka.cardmemo

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringTransactionGeneratorTest {
    private val zone = ZoneId.of("Asia/Tokyo")

    @Test fun generatesBillingDateAndBaseAmount() {
        val result = generate(expense())
        assertEquals(1000, result.single().amount)
        assertEquals(LocalDate.of(2026, 8, 10), LocalDate.ofInstant(java.time.Instant.ofEpochMilli(result.single().usedAt), zone))
    }

    @Test fun contractAfterBillingDayStartsNextMonth() {
        assertTrue(generate(expense(contractDate = "2026-08-15")).isEmpty())
        assertEquals(1, generate(expense(contractDate = "2026-08-15"), YearMonth.of(2026, 9)).size)
    }

    @Test fun customIntervalOnlyGeneratesOnScheduledMonth() {
        val item = expense(contractDate = "2026-06-01", intervalMonths = 3)
        assertTrue(generate(item, YearMonth.of(2026, 7)).isEmpty())
        assertEquals(1, generate(item, YearMonth.of(2026, 9)).size)
    }

    @Test fun endDatePreventsLaterBilling() {
        assertTrue(generate(expense(endDate = "2026-08-09")).isEmpty())
    }

    @Test fun latestApplicablePriceRevisionIsUsed() {
        val item = expense(revisions = listOf(PriceRevision("2026-07-01", 1200), PriceRevision("2026-08-11", 1500)))
        assertEquals(1200, generate(item).single().amount)
    }

    @Test fun existingMonthlyTransactionPreventsDuplicate() {
        val item = expense()
        val existing = generate(item).single()
        assertTrue(RecurringTransactionGenerator.generate(listOf(item), listOf(existing), YearMonth.of(2026, 8), zone).isEmpty())
    }

    private fun generate(item: RecurringExpense, month: YearMonth = YearMonth.of(2026, 8)) =
        RecurringTransactionGenerator.generate(listOf(item), emptyList(), month, zone) { 99L }

    private fun expense(contractDate: String = "2026-06-01", intervalMonths: Int = 1, endDate: String? = null, revisions: List<PriceRevision> = emptyList()) =
        RecurringExpense(1L, 1000, "固定費", "サービス", 10, YearMonth.from(LocalDate.parse(contractDate)).toString(), contractDate, "rakuten", intervalMonths, endDate, revisions)
}

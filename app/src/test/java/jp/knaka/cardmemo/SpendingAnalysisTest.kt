package jp.knaka.cardmemo

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SpendingAnalysisTest {
    private val zone = ZoneId.of("Asia/Tokyo")

    @Test fun aggregatesOnlySelectedMonthAndSortsDescending() {
        val result = SpendingAnalysis.calculate(listOf(tx("食費", 500, "2026-08-01"), tx("外食", 1200, "2026-08-02"), tx("食費", 900, "2026-08-03"), tx("対象外", 9999, "2026-07-31")), YearMonth.of(2026, 8), false, zone)
        assertEquals(listOf("食費" to 1400, "外食" to 1200), result.categoryTotals)
        assertEquals(2600, result.total)
    }

    @Test fun fixedModeExcludesVariableTransactions() {
        val result = SpendingAnalysis.calculate(listOf(tx("固定費", 1000, "2026-08-01", 1L), tx("食費", 500, "2026-08-02")), YearMonth.of(2026, 8), true, zone)
        assertEquals(1, result.analysisRows.size)
        assertEquals(1000, result.total)
    }

    @Test fun previousMonthRowsAreProvidedForNoteComparison() {
        val result = SpendingAnalysis.calculate(listOf(tx("食費", 500, "2026-07-31"), tx("食費", 600, "2026-08-01")), YearMonth.of(2026, 8), false, zone)
        assertEquals(1, result.previousMonthRows.size)
        assertEquals(500, result.previousMonthRows.single().amount)
    }

    private fun tx(category: String, amount: Int, date: String, recurringId: Long? = null): Transaction =
        Transaction(date.hashCode().toLong(), amount, category, category, LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli(), recurringId = recurringId)
}

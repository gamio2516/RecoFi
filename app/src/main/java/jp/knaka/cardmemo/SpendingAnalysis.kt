package jp.knaka.cardmemo

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class SpendingAnalysisResult(
    val monthRows: List<Transaction>,
    val previousMonthRows: List<Transaction>,
    val analysisRows: List<Transaction>,
    val categoryTotals: List<Pair<String, Int>>,
    val total: Int,
)

object SpendingAnalysis {
    fun calculate(
        transactions: List<Transaction>,
        month: YearMonth,
        fixedOnly: Boolean,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): SpendingAnalysisResult {
        val monthRows = transactions.forMonth(month, zoneId)
        val previousMonthRows = transactions.forMonth(month.minusMonths(1), zoneId)
        val analysisRows = if (fixedOnly) monthRows.filter { it.recurringId != null } else monthRows
        val totals = analysisRows.groupBy { it.category }
            .mapValues { (_, rows) -> rows.sumOf(Transaction::amount) }
            .toList()
            .sortedByDescending { it.second }
        return SpendingAnalysisResult(monthRows, previousMonthRows, analysisRows, totals, totals.sumOf { it.second })
    }

    private fun List<Transaction>.forMonth(month: YearMonth, zoneId: ZoneId) =
        filter { YearMonth.from(Instant.ofEpochMilli(it.usedAt).atZone(zoneId)) == month }.sortedBy { it.usedAt }
}

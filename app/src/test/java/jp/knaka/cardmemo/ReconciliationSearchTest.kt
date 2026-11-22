package jp.knaka.cardmemo

import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class ReconciliationSearchTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private fun transaction(id: Long, date: String, amount: Long, merchant: String, description: String = "", sourceId: String = "rakuten") =
        Transaction(id, amount, "交通", merchant, description, LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli(), paymentSourceId = sourceId)

    @Test fun searchesAllTransactionsInSelectedMonthByMerchantContentAndAmount() {
        val rows = listOf(
            transaction(1, "2026-04-03", 3140, "ETC", "首都高速"),
            transaction(2, "2026-04-21", 687, "楽天市場"),
            transaction(3, "2026-05-03", 3140, "ETC"),
        )
        assertEquals(listOf(1L), searchReconciliationTransactions(rows, YearMonth.of(2026, 4), "ETC 3140", emptyMap(), zone).map { it.id })
        assertEquals(listOf(1L), searchReconciliationTransactions(rows, YearMonth.of(2026, 4), "首都高速", emptyMap(), zone).map { it.id })
    }

    @Test fun includesEveryPaymentSourceAndCanSearchItsName() {
        val rows = listOf(transaction(1, "2026-04-03", 500, "ETC", sourceId = "jal"))
        assertEquals(listOf(1L), searchReconciliationTransactions(rows, YearMonth.of(2026, 4), "JAL", mapOf("jal" to "JALカードSuica"), zone).map { it.id })
    }

    @Test fun manualChoicesContainOnlyMatchingRecoFiTransactions() {
        val cardDate = LocalDate.parse("2026-05-23")
        val rows = listOf(
            transaction(1, "2026-05-23", 9167, "貝・刺身専門店"),
            transaction(2, "2026-05-23", 9167, "別カード", sourceId = "jal"),
            transaction(3, "2026-05-23", 11550, "LOCAL CUT CLUB"),
            transaction(4, "2026-06-15", 9167, "日付範囲外"),
        )
        assertEquals(listOf(1L), manualRecoFiChoices(rows, "rakuten", cardDate, 9167, zoneId = zone).map { it.id })
        assertEquals(emptyList<Long>(), manualRecoFiChoices(rows, "rakuten", cardDate, 9167, excludedTransactionIds = setOf(1L), zoneId = zone).map { it.id })
    }
}


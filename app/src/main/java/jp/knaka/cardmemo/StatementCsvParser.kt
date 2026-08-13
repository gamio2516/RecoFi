package jp.knaka.cardmemo

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

object StatementCsvParser {
    fun parse(text: String, targetMonth: YearMonth): List<CardStatementEntry> {
        val rows = tokenize(text.removePrefix("\uFEFF"))
        if (rows.isEmpty()) return emptyList()
        val headerIndex = rows.indexOfFirst { row -> row.any { normalizeHeader(it) in DATE_HEADERS } && row.any { normalizeHeader(it) in AMOUNT_HEADERS } }.takeIf { it >= 0 } ?: 0
        val headers = rows[headerIndex].map(::normalizeHeader)
        val dateIndex = headers.indexOfFirst { it in DATE_HEADERS }
        val amountIndex = headers.indexOfFirst { it in AMOUNT_HEADERS }
        val merchantIndex = headers.indexOfFirst { it in MERCHANT_HEADERS }
        if (dateIndex < 0 || amountIndex < 0) return emptyList()
        return rows.drop(headerIndex + 1).mapNotNull { row ->
            val match = Regex("(?:(20\\d{2})[./-])?(\\d{1,2})[./-](\\d{1,2})").find(row.getOrNull(dateIndex).orEmpty().trim()) ?: return@mapNotNull null
            val date = runCatching { LocalDate.of(match.groupValues[1].toIntOrNull() ?: targetMonth.year, match.groupValues[2].toInt(), match.groupValues[3].toInt()) }.getOrNull() ?: return@mapNotNull null
            val amount = row.getOrNull(amountIndex).orEmpty().replace(Regex("[^0-9-]"), "").toLongOrNull()?.let(::abs) ?: return@mapNotNull null
            if (amount == 0L) return@mapNotNull null
            val merchant = row.getOrNull(merchantIndex).orEmpty().trim().ifBlank { "利用先不明" }
            CardStatementEntry(date, amount, merchant, row.joinToString(" "))
        }.distinctBy { Triple(it.date, it.amount, ReconciliationMatcher.normalizeMerchant(it.merchant)) }
    }

    internal fun tokenize(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>(); var row = mutableListOf<String>(); val cell = StringBuilder(); var quoted = false; var index = 0
        while (index < text.length) {
            val character = text[index]
            when {
                character == '"' && quoted && index + 1 < text.length && text[index + 1] == '"' -> { cell.append('"'); index++ }
                character == '"' -> quoted = !quoted
                character == ',' && !quoted -> { row += cell.toString(); cell.clear() }
                (character == '\n' || character == '\r') && !quoted -> { if (character == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++; row += cell.toString(); cell.clear(); if (row.any { it.isNotBlank() }) rows += row; row = mutableListOf() }
                else -> cell.append(character)
            }
            index++
        }
        row += cell.toString(); if (row.any { it.isNotBlank() }) rows += row
        return rows
    }

    private fun normalizeHeader(value: String) = value.replace(Regex("[\\s　・]"), "").trim()
    private val DATE_HEADERS = setOf("利用日", "ご利用日", "利用年月日", "ご利用年月日", "売上日", "日付")
    private val AMOUNT_HEADERS = setOf("利用金額", "ご利用金額", "ご利用額", "今回ご利用金額", "金額", "支払金額")
    private val MERCHANT_HEADERS = setOf("利用店名", "ご利用店名", "利用箇所", "ご利用箇所", "利用店名及び商品名", "ご利用店名及び商品名", "ご利用先", "加盟店名", "利用先")
}

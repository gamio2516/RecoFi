package jp.knaka.cardmemo

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun searchReconciliationTransactions(
    transactions: List<Transaction>,
    month: YearMonth,
    query: String,
    sourceNames: Map<String, String>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<Transaction> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
    return transactions.asSequence()
        .filter { YearMonth.from(Instant.ofEpochMilli(it.usedAt).atZone(zoneId)) == month }
        .filter { transaction ->
            if (words.isEmpty()) true else {
                val date = Instant.ofEpochMilli(transaction.usedAt).atZone(zoneId).toLocalDate()
                val searchable = listOf(
                    transaction.merchant,
                    transaction.description,
                    transaction.category,
                    transaction.amount.toString(),
                    date.toString(),
                    sourceNames[transaction.paymentSourceId].orEmpty(),
                ).joinToString(" ").lowercase()
                words.all(searchable::contains)
            }
        }
        .sortedWith(compareByDescending<Transaction> { it.usedAt }.thenByDescending { it.id })
        .toList()
}

internal fun manualRecoFiChoices(
    transactions: List<Transaction>,
    sourceId: String,
    cardDate: LocalDate,
    cardAmount: Long,
    excludedTransactionIds: Set<Long> = emptySet(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<Transaction> = transactions.asSequence()
    .filter { it.paymentSourceId == sourceId }
    .filter { it.amount == cardAmount }
    .filter { it.id !in excludedTransactionIds }
    .map { transaction ->
        val recoFiDate = Instant.ofEpochMilli(transaction.usedAt).atZone(zoneId).toLocalDate()
        transaction to kotlin.math.abs(ChronoUnit.DAYS.between(recoFiDate, cardDate))
    }
    .filter { (_, dayDifference) -> dayDifference <= 7 }
    .sortedWith(compareBy<Pair<Transaction, Long>> { it.second }.thenByDescending { it.first.usedAt }.thenByDescending { it.first.id })
    .map { it.first }
    .toList()


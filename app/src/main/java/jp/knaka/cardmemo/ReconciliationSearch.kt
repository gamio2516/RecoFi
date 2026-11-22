package jp.knaka.cardmemo

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

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


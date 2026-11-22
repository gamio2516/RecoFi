package jp.knaka.cardmemo

import java.time.YearMonth
import java.time.ZoneId

internal fun projectedRecurringTransactions(
    expenses: List<RecurringExpense>,
    year: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<Transaction> {
    if (year !in 2031..MAX_TRANSACTION_MONTH.year) return emptyList()
    var nextId = Long.MIN_VALUE + year * 1000L
    return (1..12).flatMap { month ->
        RecurringTransactionGenerator.generate(expenses, emptyList(), YearMonth.of(year, month), zoneId) { nextId++ }
    }
}

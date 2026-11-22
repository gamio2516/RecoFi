package jp.knaka.cardmemo

import java.time.YearMonth
import java.time.ZoneId
import java.time.Instant

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

internal fun analysisTransactionsForYear(
    stored: List<Transaction>,
    expenses: List<RecurringExpense>,
    year: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<Transaction> {
    val storedInYear=stored.filter{Instant.ofEpochMilli(it.usedAt).atZone(zoneId).year==year}
    val projected=projectedRecurringTransactions(expenses,year,zoneId)
    return if(projected.isEmpty())storedInYear else storedInYear.filter{it.recurringId==null}+projected
}

internal fun analysisTransactionsForMonth(
    stored: List<Transaction>,
    expenses: List<RecurringExpense>,
    month: YearMonth,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<Transaction> = analysisTransactionsForYear(stored,expenses,month.year,zoneId)
    .filter{YearMonth.from(Instant.ofEpochMilli(it.usedAt).atZone(zoneId))==month}

package jp.knaka.cardmemo

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object RecurringTransactionGenerator {
    fun generate(
        expenses: List<RecurringExpense>,
        existingTransactions: List<Transaction>,
        month: YearMonth,
        zoneId: ZoneId = ZoneId.systemDefault(),
        idProvider: () -> Long = System::nanoTime,
    ): List<Transaction> = expenses.mapNotNull { expense ->
        if (month < YearMonth.parse(expense.startMonth)) return@mapNotNull null
        val billingDate = month.atDay(expense.billingDay.coerceAtMost(month.lengthOfMonth()))
        val contractDate = LocalDate.parse(expense.contractDate)
        if (billingDate.isBefore(contractDate)) return@mapNotNull null
        if (expense.endDate?.let { billingDate.isAfter(LocalDate.parse(it)) } == true) return@mapNotNull null
        var firstBillingMonth = YearMonth.from(contractDate)
        if (firstBillingMonth.atDay(expense.billingDay.coerceAtMost(firstBillingMonth.lengthOfMonth())).isBefore(contractDate)) {
            firstBillingMonth = firstBillingMonth.plusMonths(1)
        }
        if (ChronoUnit.MONTHS.between(firstBillingMonth, month) % expense.intervalMonths != 0L) return@mapNotNull null
        val alreadyExists = existingTransactions.any { transaction ->
            transaction.recurringId == expense.id && YearMonth.from(Instant.ofEpochMilli(transaction.usedAt).atZone(zoneId)) == month
        }
        if (alreadyExists) return@mapNotNull null
        val amount = expense.priceRevisions
            .filter { !LocalDate.parse(it.effectiveDate).isAfter(billingDate) }
            .maxByOrNull { it.effectiveDate }
            ?.amount ?: expense.amount
        Transaction(
            id = idProvider(), amount = amount, category = expense.category, note = expense.note,
            usedAt = billingDate.atStartOfDay(zoneId).toInstant().toEpochMilli(), recurringId = expense.id,
            paymentSourceId = expense.paymentSourceId,
        )
    }
}

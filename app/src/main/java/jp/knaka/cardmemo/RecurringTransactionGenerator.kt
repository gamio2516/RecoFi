package jp.knaka.cardmemo

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

object RecurringTransactionGenerator {
    fun generate(
        expenses: List<RecurringExpense>,
        existingTransactions: List<Transaction>,
        month: YearMonth,
        zoneId: ZoneId = ZoneId.systemDefault(),
        idProvider: () -> Long = System::nanoTime,
    ): List<Transaction> = expenses.mapNotNull { expense ->
        if (!RecurringExpensePolicy.isScheduledFor(expense, month)) return@mapNotNull null
        val billingDate = RecurringExpensePolicy.billingDate(expense, month)
        val alreadyExists = existingTransactions.any { transaction ->
            transaction.recurringId == expense.id && YearMonth.from(Instant.ofEpochMilli(transaction.usedAt).atZone(zoneId)) == month
        }
        if (alreadyExists) return@mapNotNull null
        val amount = RecurringExpensePolicy.amountOn(expense, billingDate)
        Transaction(
            id = idProvider(), amount = amount, category = expense.category, merchant = expense.merchant, description = expense.description,
            usedAt = billingDate.atStartOfDay(zoneId).toInstant().toEpochMilli(), recurringId = expense.id,
            paymentSourceId = expense.paymentSourceId,
        )
    }
}

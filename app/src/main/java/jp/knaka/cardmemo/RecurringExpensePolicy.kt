package jp.knaka.cardmemo

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object RecurringExpensePolicy {
    fun billingDate(expense: RecurringExpense, month: YearMonth): LocalDate =
        month.atDay(expense.paymentDay.coerceAtMost(month.lengthOfMonth()))

    fun isActiveOn(expense: RecurringExpense, date: LocalDate): Boolean =
        !date.isBefore(LocalDate.parse(expense.contractDate)) &&
            expense.endDate?.let { !date.isAfter(LocalDate.parse(it)) } != false

    fun isScheduledFor(expense: RecurringExpense, month: YearMonth): Boolean {
        if (month < YearMonth.parse(expense.startMonth)) return false
        val date = billingDate(expense, month)
        if (!isActiveOn(expense, date)) return false
        val contract = LocalDate.parse(expense.contractDate)
        var firstMonth = YearMonth.from(contract)
        if (billingDate(expense, firstMonth).isBefore(contract)) firstMonth = firstMonth.plusMonths(1)
        return ChronoUnit.MONTHS.between(firstMonth, month) % expense.intervalMonths == 0L
    }

    fun amountOn(expense: RecurringExpense, date: LocalDate): Long = expense.priceRevisions
        .filter { !LocalDate.parse(it.effectiveDate).isAfter(date) }
        .maxByOrNull { it.effectiveDate }
        ?.amount ?: expense.amount
}

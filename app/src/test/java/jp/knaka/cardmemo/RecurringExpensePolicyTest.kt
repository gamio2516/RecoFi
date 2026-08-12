package jp.knaka.cardmemo

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringExpensePolicyTest {
    @Test fun endDateIsInclusiveAndFollowingDayIsInactive() {
        val expense = expense(endDate = "2026-08-10")
        assertTrue(RecurringExpensePolicy.isActiveOn(expense, LocalDate.of(2026, 8, 10)))
        assertFalse(RecurringExpensePolicy.isActiveOn(expense, LocalDate.of(2026, 8, 11)))
    }

    @Test fun contractDateIsInclusive() {
        val expense = expense(contractDate = "2026-08-10")
        assertTrue(RecurringExpensePolicy.isActiveOn(expense, LocalDate.of(2026, 8, 10)))
        assertFalse(RecurringExpensePolicy.isActiveOn(expense, LocalDate.of(2026, 8, 9)))
    }

    @Test fun priceRevisionStartsOnEffectiveDate() {
        val expense = expense(revisions = listOf(PriceRevision("2026-08-10", 1500)))
        assertEquals(1000, RecurringExpensePolicy.amountOn(expense, LocalDate.of(2026, 8, 9)))
        assertEquals(1500, RecurringExpensePolicy.amountOn(expense, LocalDate.of(2026, 8, 10)))
    }

    @Test fun latestRevisionWinsEvenWhenInputIsUnsorted() {
        val expense = expense(revisions = listOf(PriceRevision("2026-08-01", 1200), PriceRevision("2026-07-01", 1100)))
        assertEquals(1200, RecurringExpensePolicy.amountOn(expense, LocalDate.of(2026, 8, 10)))
    }

    @Test fun billingDayUsesLastDayForShortMonth() {
        assertEquals(LocalDate.of(2026, 2, 28), RecurringExpensePolicy.billingDate(expense(billingDay = 31), YearMonth.of(2026, 2)))
    }

    private fun expense(contractDate: String = "2026-01-01", endDate: String? = null, billingDay: Int = 10, revisions: List<PriceRevision> = emptyList()) =
        RecurringExpense(1L, 1000, "固定費", "サービス", billingDay, YearMonth.from(LocalDate.parse(contractDate)).toString(), contractDate, "rakuten", 1, endDate, revisions)
}

package jp.knaka.cardmemo
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
class RecurringExpensePolicyTest {
 @Test fun endDateInclusive(){val e=expense(end="2026-07-10");assertTrue(RecurringExpensePolicy.isActiveOn(e,LocalDate.parse("2026-07-10")));assertFalse(RecurringExpensePolicy.isActiveOn(e,LocalDate.parse("2026-07-11")))}
 @Test fun contractDateInclusive(){val e=expense(contract="2026-07-10");assertFalse(RecurringExpensePolicy.isActiveOn(e,LocalDate.parse("2026-07-09")));assertTrue(RecurringExpensePolicy.isActiveOn(e,LocalDate.parse("2026-07-10")))}
 @Test fun revisionStartsOnDate(){val e=expense(revisions=listOf(PriceRevision("2026-07-10",1200L)));assertEquals(1000L,RecurringExpensePolicy.amountOn(e,LocalDate.parse("2026-07-09")));assertEquals(1200L,RecurringExpensePolicy.amountOn(e,LocalDate.parse("2026-07-10")))}
 @Test fun latestRevisionWinsUnsorted(){val e=expense(revisions=listOf(PriceRevision("2026-08-01",1500L),PriceRevision("2026-07-01",1200L)));assertEquals(1500L,RecurringExpensePolicy.amountOn(e,LocalDate.parse("2026-09-01")))}
 @Test fun paymentDayUsesMonthEnd(){assertEquals(LocalDate.parse("2026-02-28"),RecurringExpensePolicy.billingDate(expense().copy(paymentDay=31),java.time.YearMonth.of(2026,2)))}
 @Test fun leapYearPaymentDayUsesFebruary29(){assertEquals(LocalDate.parse("2028-02-29"),RecurringExpensePolicy.billingDate(expense().copy(paymentDay=31),java.time.YearMonth.of(2028,2)))}
 private fun expense(contract:String="2026-01-01",end:String?=null,revisions:List<PriceRevision> = emptyList())=RecurringExpense(id=1,amount=1000L,category="固定費",merchant="Netflix",description="動画",paymentDay=10,startMonth="2026-01",contractDate=contract,paymentSourceId="card",endDate=end,priceRevisions=revisions)
}

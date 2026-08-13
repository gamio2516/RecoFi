package jp.knaka.cardmemo
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test
class RecurringTransactionGeneratorTest {
 @Test fun generatesMerchantDescriptionAndBaseAmount(){val r=generate(YearMonth.of(2026,7),expense()).single();assertEquals(1000L,r.amount);assertEquals("Netflix",r.merchant);assertEquals("動画",r.description)}
 @Test fun contractAfterBillingStartsNextMonth(){assertTrue(generate(YearMonth.of(2026,7),expense(contract="2026-07-20")).isEmpty())}
 @Test fun intervalOnlyScheduledMonth(){assertTrue(generate(YearMonth.of(2026,8),expense(interval=3)).isEmpty());assertEquals(1,generate(YearMonth.of(2026,10),expense(interval=3)).size)}
 @Test fun endPreventsLaterBilling(){assertTrue(generate(YearMonth.of(2026,8),expense(end="2026-07-31")).isEmpty())}
 @Test fun revisionApplied(){assertEquals(1500L,generate(YearMonth.of(2026,8),expense(revisions=listOf(PriceRevision("2026-08-01",1500L)))).single().amount)}
 @Test fun existingRecurringPreventsDuplicate(){val existing=Transaction(2,1000L,"固定費","Netflix","動画",java.time.LocalDate.of(2026,7,10).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),recurringId=1);assertTrue(generate(YearMonth.of(2026,7),expense(),listOf(existing)).isEmpty())}
 private fun generate(month:YearMonth,e:RecurringExpense,existing:List<Transaction> = emptyList())=RecurringTransactionGenerator.generate(listOf(e),existing,month,idProvider={1L})
 private fun expense(contract:String="2026-07-01",interval:Int=1,end:String?=null,revisions:List<PriceRevision> = emptyList())=RecurringExpense(id=1,amount=1000L,category="固定費",merchant="Netflix",description="動画",billingDay=10,startMonth="2026-07",contractDate=contract,paymentSourceId="card",intervalMonths=interval,endDate=end,priceRevisions=revisions)
}

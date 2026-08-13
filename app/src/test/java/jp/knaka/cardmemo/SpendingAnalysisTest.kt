package jp.knaka.cardmemo
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test
class SpendingAnalysisTest {
 @Test fun aggregatesSelectedMonth(){val r=SpendingAnalysis.calculate(listOf(tx("食費",500L,"2026-07-01"),tx("食費",700L,"2026-07-02"),tx("外食",300L,"2026-07-03"),tx("外食",999L,"2026-08-01")),YearMonth.of(2026,7),false);assertEquals(1500L,r.total);assertEquals("食費",r.categoryTotals.first().first)}
 @Test fun fixedModeExcludesVariable(){val r=SpendingAnalysis.calculate(listOf(tx("固定費",1000L,"2026-07-01",1),tx("食費",500L,"2026-07-02")),YearMonth.of(2026,7),true);assertEquals(1000L,r.total)}
 @Test fun previousMonthRowsProvided(){val r=SpendingAnalysis.calculate(listOf(tx("食費",500L,"2026-06-01"),tx("食費",700L,"2026-07-01")),YearMonth.of(2026,7),false);assertEquals(1,r.previousMonthRows.size)}
 private fun tx(category:String,amount:Long,date:String,recurringId:Long?=null)=Transaction(System.nanoTime(),amount,category,"店","内容",LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),recurringId=recurringId)
}

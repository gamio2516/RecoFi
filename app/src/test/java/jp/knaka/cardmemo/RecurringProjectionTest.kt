package jp.knaka.cardmemo

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringProjectionTest {
    private val zone=ZoneId.of("Asia/Tokyo")
    private val monthly=RecurringExpense(1,1000,"固定費","Netflix","動画",10,"2026-01","2026-01-01","card",categoryId="fixed")
    private val yearly=RecurringExpense(2,12000,"固定費","年会費","",5,"2026-08","2026-08-01","card",intervalMonths=12,categoryId="fixed")
    @Test fun projectsEveryScheduledExpenseWithoutSavingRows(){
        val rows=projectedRecurringTransactions(listOf(monthly,yearly),2041,zone)
        assertEquals(13,rows.size)
        assertEquals(24000L,rows.sumOf{it.amount})
        assertEquals(12,rows.count{it.recurringId==1L})
        assertEquals(8,Instant.ofEpochMilli(rows.single{it.recurringId==2L}.usedAt).atZone(zone).monthValue)
    }
    @Test fun projectionStopsAfter2050(){assertTrue(projectedRecurringTransactions(listOf(monthly),2051,zone).isEmpty())}
    @Test fun everyAnalysisReplacesPartialStoredFixedRowsButKeepsManualRows(){
        val partialFixed=Transaction(10,1000,"固定費","Netflix","動画",java.time.LocalDate.of(2041,11,10).atStartOfDay(zone).toInstant().toEpochMilli(),recurringId=1,paymentSourceId="card",categoryId="fixed")
        val manual=Transaction(11,500,"その他","店","手入力",java.time.LocalDate.of(2041,2,1).atStartOfDay(zone).toInstant().toEpochMilli(),paymentSourceId="card",categoryId="other")
        val rows=analysisTransactionsForYear(listOf(partialFixed,manual),listOf(monthly,yearly),2041,zone)
        assertEquals(14,rows.size)
        assertEquals(24500L,rows.sumOf{it.amount})
        assertEquals(1,rows.count{it.recurringId==1L&&Instant.ofEpochMilli(it.usedAt).atZone(zone).monthValue==11})
        assertTrue(manual in rows)
    }
}

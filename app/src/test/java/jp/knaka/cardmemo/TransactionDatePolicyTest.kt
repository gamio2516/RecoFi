package jp.knaka.cardmemo

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionDatePolicyTest {
    private val zone=ZoneId.of("Asia/Tokyo")
    private fun millis(date:String)=LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
    @Test fun accepts2050AndRejects2051(){
        assertTrue(canStoreTransactionAt(millis("2050-12-31"),zone))
        assertFalse(canStoreTransactionAt(millis("2051-01-01"),zone))
    }
}

package jp.knaka.cardmemo
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
class ReconciliationMatcherTest {
 private val base=LocalDate.of(2026,7,10)
 @Test fun exactMatches(){assertEquals(1L,match(entry(),tx()).transactionId)}
 @Test fun sevenDaysMatches(){assertEquals(1L,match(entry(),tx(date=base.plusDays(7))).transactionId)}
 @Test fun minusSevenDaysMatches(){assertEquals(1L,match(entry(),tx(date=base.minusDays(7))).transactionId)}
 @Test fun eightDaysDoesNot(){assertNull(match(entry(),tx(date=base.plusDays(8))).transactionId)}
 @Test fun minusEightDaysDoesNot(){assertNull(match(entry(),tx(date=base.minusDays(8))).transactionId)}
 @Test fun differentAmountDoesNot(){assertNull(match(entry(),tx(amount=501L)).transactionId)}
 @Test fun differentSourceDoesNot(){assertNull(ReconciliationMatcher.match(listOf(entry()),listOf(tx(source="other")),"card").single().transactionId)}
 @Test fun merchantSimilarityAffectsScore(){assertTrue(match(entry("STARBUCKS"),tx(merchant="スターバックス")).score>match(entry("無関係"),tx(merchant="スターバックス")).score)}
 @Test fun descriptionAloneDoesNotRaiseScore(){assertEquals(match(entry("無関係"),tx(description="無関係")).score,match(entry("無関係"),tx(description="別内容")).score)}
 @Test fun closerDateAndMerchantWins(){val rows=listOf(tx(1,date=base.plusDays(5),merchant="別店舗"),tx(2,date=base,merchant="スターバックス"));assertEquals(2L,ReconciliationMatcher.match(listOf(entry()),rows,"card").single().transactionId)}
 @Test fun transactionNotAssignedTwice(){val r=ReconciliationMatcher.match(listOf(entry(),entry()),listOf(tx()),"card");assertEquals(1,r.count{it.transactionId==1L})}
 @Test fun ambiguousCandidatesNotConfirmed(){val r=ReconciliationMatcher.match(listOf(entry()),listOf(tx(1),tx(2)),"card");assertNull(r.single().transactionId)}
 @Test fun unrelatedMerchantIsNotHighConfidenceCandidate(){assertNull(ReconciliationMatcher.candidate(entry("楽天市場"),tx(merchant="成城石井")))}
 @Test fun amazonAliasIsAccepted(){assertNotNull(ReconciliationMatcher.candidate(entry("AMZN MKTP JP"),tx(merchant="Amazon")))}
 private fun entry(merchant:String="スターバックス",amount:Long=500L)=CardStatementEntry(base,amount,merchant,"")
 private fun tx(id:Long=1,amount:Long=500L,date:LocalDate=base,merchant:String="スターバックス",description:String="",source:String="card")=Transaction(id,amount,"カフェ",merchant,description,date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),paymentSourceId=source)
 private fun match(e:CardStatementEntry,t:Transaction)=ReconciliationMatcher.match(listOf(e),listOf(t),"card").single()
}

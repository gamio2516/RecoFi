package jp.knaka.cardmemo
import org.junit.Assert.*
import org.junit.Test
class MerchantSpendingAnalysisTest {
 @Test fun groupsNormalizedMerchantsAndRanksByAmount(){val r=MerchantSpendingAnalysis.calculate(listOf(tx("Amazon",500),tx("Ａｍａｚｏｎ",700)),emptyList());assertEquals(1200L,r.ranking.single().amount);assertEquals(2,r.ranking.single().count)}
 @Test fun calculatesPreviousMonthChange(){val r=MerchantSpendingAnalysis.calculate(listOf(tx("西友",800)),listOf(tx("西友",300)));assertEquals(500L,r.ranking.single().changeFromPrevious)}
 @Test fun identifiesMostFrequent(){val r=MerchantSpendingAnalysis.calculate(listOf(tx("A",1000),tx("B",100),tx("B",100)),emptyList());assertEquals("B",r.mostFrequent?.label);assertEquals("A",r.largestSpending?.label)}
 @Test fun blankMerchantsIgnored(){assertTrue(MerchantSpendingAnalysis.calculate(listOf(tx("",500)),emptyList()).ranking.isEmpty())}
 private fun tx(merchant:String,amount:Long)=Transaction(System.nanoTime(),amount,"その他",merchant,"",0L)
}

package jp.knaka.cardmemo
data class MerchantSpendingEntry(val key:String,val label:String,val amount:Long,val count:Int,val average:Long,val changeFromPrevious:Long)
data class MerchantSpendingAnalysisResult(val ranking:List<MerchantSpendingEntry>,val largestSpending:MerchantSpendingEntry?,val mostFrequent:MerchantSpendingEntry?,val biggestIncrease:MerchantSpendingEntry?)
object MerchantSpendingAnalysis{
 fun calculate(current:List<Transaction>,previous:List<Transaction>):MerchantSpendingAnalysisResult{
  val groups=current.filter{it.merchant.isNotBlank()}.groupBy{normalize(it.merchant)}.filterKeys{it.isNotBlank()}
  val prior=previous.filter{it.merchant.isNotBlank()}.groupBy{normalize(it.merchant)}.mapValues{(_,rows)->rows.sumOf{it.amount}}
  val ranking=groups.map{(key,rows)->val amount=rows.sumOf{it.amount};val label=rows.groupingBy{it.merchant.trim()}.eachCount().maxByOrNull{it.value}?.key.orEmpty();MerchantSpendingEntry(key,label,amount,rows.size,amount/rows.size,amount-prior.getOrDefault(key,0L))}.sortedByDescending{it.amount}
  return MerchantSpendingAnalysisResult(ranking,ranking.maxByOrNull{it.amount},ranking.maxWithOrNull(compareBy<MerchantSpendingEntry>{it.count}.thenBy{it.amount}),ranking.maxByOrNull{it.changeFromPrevious})
 }
 fun normalize(value:String)=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC).lowercase().filter{it.isLetterOrDigit()}
}

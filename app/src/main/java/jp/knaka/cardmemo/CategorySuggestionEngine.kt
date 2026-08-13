package jp.knaka.cardmemo
data class CategorySuggestionInput(val merchant:String,val description:String,val paymentSourceId:String)
object CategorySuggestionEngine{
 fun suggest(input:CategorySuggestionInput,history:List<Transaction>,categories:List<String>):List<String>{
  val merchant=normalize(input.merchant);val description=normalize(input.description)
  if(merchant.isBlank()&&description.isBlank())return emptyList()
  return history.asSequence().filter{it.category in categories}.mapNotNull{tx->
   val ms=similarity(merchant,normalize(tx.merchant));val ds=similarity(description,normalize(tx.description));val score=ms*3+ds*2+if(tx.paymentSourceId==input.paymentSourceId&&ms+ds>0)1 else 0
   tx.category.takeIf{ms+ds>0}?.let{it to score}
  }.groupBy({it.first},{it.second}).mapValues{it.value.sum()}.entries.sortedByDescending{it.value}.take(3).map{it.key}
 }
 private fun normalize(v:String)=v.lowercase().filter{it.isLetterOrDigit()}
 private fun similarity(a:String,b:String)=when{a.isBlank()||b.isBlank()->0;a==b->4;a.contains(b)||b.contains(a)->2;else->0}
}

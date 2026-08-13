package jp.knaka.cardmemo
import android.content.Context
import jp.knaka.cardmemo.storage.*
class TransactionRepository(context:Context){
 private val db=StorageProvider.database(context)
 fun load()=db.transactions().loadAll().map { Transaction(it.id,it.amount,it.category,it.merchant,it.description,it.usedAt,it.recurringId,it.paymentSourceId) }
 fun save(items:List<Transaction>)=db.runInTransaction {
  val keep=items.map{it.id}.toSet();val removed=db.transactions().loadAll().filter{it.id !in keep}
  removed.forEach{old->db.reconciliation().loadMatchesForTransaction(old.id).forEach{match->db.reconciliation().upsertMatch(match.copy(transactionId=null,status=ReconciliationStatus.PENDING.name,matchSource=null,confidence=null,score=null,reasonCode=null,dayDifference=null,updatedAt=System.currentTimeMillis(),confirmedAt=null))};db.transactions().deleteById(old.id)}
  db.transactions().upsertAll(items.map { TransactionEntity(it.id,it.amount,it.category,it.merchant,it.description,it.usedAt,it.recurringId,it.paymentSourceId) })
 }
}

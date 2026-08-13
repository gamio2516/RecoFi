package jp.knaka.cardmemo
import android.content.Context
import jp.knaka.cardmemo.storage.*
class TransactionRepository(context:Context){
 private val db=StorageProvider.database(context)
 fun load()=db.transactions().loadAll().map { Transaction(it.id,it.amount,it.category,it.merchant,it.description,it.usedAt,it.confirmed,it.recurringId,it.paymentSourceId,it.reconciledMonth,it.suggested) }
 fun save(items:List<Transaction>)=db.runInTransaction { db.transactions().deleteAll();db.transactions().upsertAll(items.map { TransactionEntity(it.id,it.amount,it.category,it.merchant,it.description,it.usedAt,it.confirmed,it.recurringId,it.paymentSourceId,it.reconciledMonth,it.suggested) }) }
}

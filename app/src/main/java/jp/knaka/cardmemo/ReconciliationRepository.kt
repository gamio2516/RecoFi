package jp.knaka.cardmemo

import android.content.Context
import jp.knaka.cardmemo.storage.*
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class ReconciliationReviewRow(val entry:StatementEntryEntity,val match:ReconciliationMatchEntity?,val transaction:TransactionEntity?,val categoryName:String?)
data class LockBlocker(val sourceName:String,val needsReview:Int=0,val unresolved:Int=0,val missingStatement:Boolean=false,val amountDifference:Long?=null) {
    val message: String get() = when {
        missingStatement -> "$sourceName：明細未取込（利用がなければ『この月は利用なし』を選択）"
        needsReview > 0 -> "$sourceName：要確認${needsReview}件"
        unresolved > 0 -> "$sourceName：未対応${unresolved}件"
        amountDifference != null && amountDifference != 0L -> "$sourceName：差額${signedAmount(amountDifference)}"
        else -> sourceName
    }

    private fun signedAmount(value:Long)="${if(value>0) "+" else "-"}¥${kotlin.math.abs(value)}"
}

class ReconciliationRepository private constructor(private val db: RecoFiDatabase){
 constructor(context:Context):this(StorageProvider.database(context))
 internal constructor(database: RecoFiDatabase, testOnly: Boolean = true):this(database)
 fun reviewRows(fileHash:String):List<ReconciliationReviewRow>{val matches=db.reconciliation().loadMatches().associateBy{it.statementEntryId};val tx=db.transactions().loadAll().associateBy{it.id};val categoryNames=db.referenceData().loadCategories().associate{it.id to it.name};return db.statements().loadEntries().filter{it.fileHash==fileHash}.map{e->val m=matches[e.id];val transaction=m?.transactionId?.let(tx::get);ReconciliationReviewRow(e,m,transaction,transaction?.categoryId?.let(categoryNames::get))}}
 fun progress(month:YearMonth,sourceId:String):MonthlyReconciliationProgress{val statement=db.statements().loadStatements().firstOrNull{it.statementMonth==month.toString()&&it.paymentSourceId==sourceId}?:return MonthlyReconciliationProgress(0,0,0,0);val entries=db.statements().loadEntries().filter{it.fileHash==statement.fileHash};val states=db.reconciliation().loadMatches().associateBy{it.statementEntryId};return MonthlyReconciliationProgress(entries.size,entries.count{states[it.id]?.status==ReconciliationStatus.CONFIRMED.name},entries.count{states[it.id]?.status==ReconciliationStatus.SUGGESTED.name},entries.count{states[it.id]==null||states[it.id]?.status==ReconciliationStatus.PENDING.name})}
 fun autoMatch(fileHash:String,sourceId:String,transactions:List<Transaction>){db.runInTransaction{val dao=db.reconciliation();val rejected=dao.loadRejected().groupBy{it.statementEntryId}.mapValues{it.value.map{r->r.transactionId}.toSet()};val used=dao.loadMatches().mapNotNull{if(it.status==ReconciliationStatus.CONFIRMED.name)it.transactionId else null}.toMutableSet();val now=System.currentTimeMillis();db.statements().loadEntries().filter{it.fileHash==fileHash}.forEach{entry->val existing=dao.loadMatch(entry.id);if(existing?.status==ReconciliationStatus.CONFIRMED.name||existing?.matchSource==MatchSource.USER.name)return@forEach;val card=CardStatementEntry(java.time.LocalDate.parse(entry.date),entry.amount,entry.merchant,entry.rawText);val ranked=transactions.asSequence().filter{it.paymentSourceId==sourceId&&it.id !in used&&it.id !in rejected.getOrDefault(entry.id,emptySet())}.mapNotNull{tx->ReconciliationMatcher.candidate(card,tx)?.let{it to tx}}.sortedWith(compareBy<Pair<ReconciliationMatcher.CandidateInfo,Transaction>>{it.first.dayDifference}.thenBy{it.second.id}).toList();val best=ranked.firstOrNull();val ambiguous=best!=null&&ranked.getOrNull(1)?.first?.dayDifference==best.first.dayDifference;val candidate=best?.takeUnless{ambiguous};if(candidate!=null){val info=candidate.first;dao.upsertMatch(ReconciliationMatchEntity(entry.id,candidate.second.id,ReconciliationStatus.CONFIRMED.name,MatchSource.RULE.name,info.confidence.name,info.score,info.reason,info.dayDifference,existing?.createdAt?:now,now,now));used+=candidate.second.id}else dao.upsertMatch(ReconciliationMatchEntity(entry.id,null,ReconciliationStatus.PENDING.name,null,null,null,null,null,existing?.createdAt?:now,now,null))}}}
 fun confirm(entryId:Long,transactionId:Long,source:MatchSource=MatchSource.USER){db.runInTransaction{
  require(db.statements().loadEntry(entryId)!=null){"カード明細が見つかりません"}
  require(db.transactions().loadById(transactionId)!=null){"RecoFi明細が見つかりません"}
  val dao=db.reconciliation();val now=System.currentTimeMillis()
  dao.loadMatchesForTransaction(transactionId).filter{it.statementEntryId!=entryId}.forEach{old->dao.upsertMatch(old.copy(transactionId=null,status=ReconciliationStatus.PENDING.name,matchSource=null,confidence=null,score=null,reasonCode=null,dayDifference=null,updatedAt=now,confirmedAt=null))}
  val previous=dao.loadMatch(entryId)
  dao.deleteMatch(entryId)
  dao.upsertMatch(ReconciliationMatchEntity(entryId,transactionId,ReconciliationStatus.CONFIRMED.name,source.name,previous?.confidence,previous?.score,previous?.reasonCode,previous?.dayDifference,previous?.createdAt?:now,now,now))
  val saved=dao.loadMatch(entryId);check(saved?.transactionId==transactionId&&saved.status==ReconciliationStatus.CONFIRMED.name){"消込結果を保存できませんでした"}
 }}
 fun reject(entryId:Long,transactionId:Long){db.runInTransaction{db.reconciliation().upsertRejected(RejectedReconciliationCandidateEntity(entryId,transactionId,System.currentTimeMillis()));val old=db.reconciliation().loadMatch(entryId);db.reconciliation().upsertMatch(ReconciliationMatchEntity(entryId,null,ReconciliationStatus.PENDING.name,null,null,null,null,null,old?.createdAt?:System.currentTimeMillis(),System.currentTimeMillis(),null))}}
 fun createAndConfirm(entryId:Long,transaction:Transaction){db.runInTransaction{
  require(db.statements().loadEntry(entryId)!=null){"取込明細が見つかりません"}
  val occupied=db.transactions().loadAll().asSequence().map{it.id}.toHashSet()
  var transactionId=maxOf(System.currentTimeMillis(),(occupied.maxOrNull()?:0L)+1L)
  while(transactionId in occupied) transactionId++
  db.transactions().upsertAll(listOf(TransactionEntity(transactionId,transaction.amount,transaction.categoryId,transaction.merchant,transaction.description,transaction.usedAt,transaction.recurringId,transaction.paymentSourceId)))
  val now=System.currentTimeMillis();val previous=db.reconciliation().loadMatch(entryId)
  db.reconciliation().upsertMatch(ReconciliationMatchEntity(entryId,transactionId,ReconciliationStatus.CONFIRMED.name,MatchSource.USER.name,previous?.confidence,previous?.score,previous?.reasonCode,previous?.dayDifference,previous?.createdAt?:now,now,now))
 }}
 fun declareNoActivity(month:YearMonth,sourceId:String){db.reconciliation().upsertDeclarations(listOf(MonthlyPaymentSourceDeclarationEntity(month.toString(),sourceId,"NO_ACTIVITY",System.currentTimeMillis())))}
 fun reconciliationTotal(month:YearMonth,sourceId:String):Long{
  val statement=db.statements().loadStatements().firstOrNull{it.statementMonth==month.toString()&&it.paymentSourceId==sourceId}
  val entries=db.statements().loadEntries()
  val currentEntryIds=statement?.let{s->entries.filter{it.fileHash==s.fileHash}.mapTo(hashSetOf()){it.id}}.orEmpty()
  val matches=db.reconciliation().loadMatches().filter{it.transactionId!=null}
  val currentTransactionIds=matches.filter{it.statementEntryId in currentEntryIds}.mapTo(hashSetOf()){it.transactionId!!}
  val linkedTransactionIds=matches.mapTo(hashSetOf()){it.transactionId!!}
  val zone=ZoneId.systemDefault()
  return db.transactions().loadAll().asSequence().filter{it.paymentSourceId==sourceId}.filter{transaction->
   transaction.id in currentTransactionIds || (transaction.id !in linkedTransactionIds && YearMonth.from(Instant.ofEpochMilli(transaction.usedAt).atZone(zone))==month)
  }.distinctBy{it.id}.sumOf{it.amount}
 }
 fun canLock(month:YearMonth,sources:List<PaymentSource>):List<LockBlocker>{val declarations=db.reconciliation().loadDeclarations().associateBy{it.month to it.paymentSourceId};return sources.filter{it.isCard}.mapNotNull{s->val p=progress(month,s.id);val statement=db.statements().loadStatements().firstOrNull{it.statementMonth==month.toString()&&it.paymentSourceId==s.id};when{p.imported==0&&declarations[month.toString() to s.id]?.status!="NO_ACTIVITY"->LockBlocker(s.name,missingStatement=true);p.remaining>0->LockBlocker(s.name,p.needsReview,p.unresolved);statement!=null->{val statementTotal=statement.declaredTotal?:db.statements().loadEntries().filter{it.fileHash==statement.fileHash}.sumOf{it.amount};val difference=statementTotal-reconciliationTotal(month,s.id);if(difference!=0L)LockBlocker(s.name,amountDifference=difference)else null};else->null}}}
}


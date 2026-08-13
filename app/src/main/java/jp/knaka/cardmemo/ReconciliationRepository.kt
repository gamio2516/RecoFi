package jp.knaka.cardmemo

import android.content.Context
import jp.knaka.cardmemo.storage.*
import java.time.YearMonth

data class ReconciliationReviewRow(val entry:StatementEntryEntity,val match:ReconciliationMatchEntity?,val transaction:TransactionEntity?)
data class LockBlocker(val sourceName:String,val needsReview:Int=0,val unresolved:Int=0,val missingStatement:Boolean=false) {
    val message: String get() = when {
        missingStatement -> "$sourceName：明細未取込（利用がなければ『この月は利用なし』を選択）"
        needsReview > 0 -> "$sourceName：要確認${needsReview}件"
        unresolved > 0 -> "$sourceName：未対応${unresolved}件"
        else -> sourceName
    }
}

class ReconciliationRepository private constructor(private val db: RecoFiDatabase){
 constructor(context:Context):this(StorageProvider.database(context))
 internal constructor(database: RecoFiDatabase, testOnly: Boolean = true):this(database)
 fun reviewRows(fileHash:String):List<ReconciliationReviewRow>{val matches=db.reconciliation().loadMatches().associateBy{it.statementEntryId};val tx=db.transactions().loadAll().associateBy{it.id};return db.statements().loadEntries().filter{it.fileHash==fileHash}.map{e->val m=matches[e.id];ReconciliationReviewRow(e,m,m?.transactionId?.let(tx::get))}}
 fun progress(month:YearMonth,sourceId:String):MonthlyReconciliationProgress{val statement=db.statements().loadStatements().firstOrNull{it.statementMonth==month.toString()&&it.paymentSourceId==sourceId}?:return MonthlyReconciliationProgress(0,0,0,0);val entries=db.statements().loadEntries().filter{it.fileHash==statement.fileHash};val states=db.reconciliation().loadMatches().associateBy{it.statementEntryId};return MonthlyReconciliationProgress(entries.size,entries.count{states[it.id]?.status==ReconciliationStatus.CONFIRMED.name},entries.count{states[it.id]?.status==ReconciliationStatus.SUGGESTED.name},entries.count{states[it.id]==null||states[it.id]?.status==ReconciliationStatus.PENDING.name})}
 fun autoMatch(fileHash:String,sourceId:String,transactions:List<Transaction>){db.runInTransaction{val dao=db.reconciliation();val rejected=dao.loadRejected().groupBy{it.statementEntryId}.mapValues{it.value.map{r->r.transactionId}.toSet()};val used=dao.loadMatches().mapNotNull{if(it.status==ReconciliationStatus.CONFIRMED.name)it.transactionId else null}.toMutableSet();val now=System.currentTimeMillis();db.statements().loadEntries().filter{it.fileHash==fileHash}.forEach{entry->val existing=dao.loadMatch(entry.id);if(existing?.status==ReconciliationStatus.CONFIRMED.name||existing?.matchSource==MatchSource.USER.name)return@forEach;val card=CardStatementEntry(java.time.LocalDate.parse(entry.date),entry.amount,entry.merchant,entry.rawText);val ranked=transactions.asSequence().filter{it.paymentSourceId==sourceId&&it.id !in used&&it.id !in rejected.getOrDefault(entry.id,emptySet())}.mapNotNull{tx->ReconciliationMatcher.candidate(card,tx)?.let{it to tx}}.sortedByDescending{it.first.score}.toList();val best=ranked.firstOrNull();val ambiguous=best!=null&&ranked.getOrNull(1)?.let{best.first.score-it.first.score<=5}==true;val candidate=best?.takeUnless{ambiguous};if(candidate!=null){val info=candidate.first;dao.upsertMatch(ReconciliationMatchEntity(entry.id,candidate.second.id,ReconciliationStatus.SUGGESTED.name,MatchSource.RULE.name,info.confidence.name,info.score,info.reason,info.dayDifference,existing?.createdAt?:now,now,null));used+=candidate.second.id}else dao.upsertMatch(ReconciliationMatchEntity(entry.id,null,ReconciliationStatus.PENDING.name,null,null,null,null,null,existing?.createdAt?:now,now,null))}}}
 fun confirm(entryId:Long,transactionId:Long,source:MatchSource=MatchSource.USER){db.runInTransaction{val now=System.currentTimeMillis();val previous=db.reconciliation().loadMatch(entryId);db.reconciliation().upsertMatch(ReconciliationMatchEntity(entryId,transactionId,ReconciliationStatus.CONFIRMED.name,source.name,previous?.confidence,previous?.score,previous?.reasonCode,previous?.dayDifference,previous?.createdAt?:now,now,now))}}
 fun reject(entryId:Long,transactionId:Long){db.runInTransaction{db.reconciliation().upsertRejected(RejectedReconciliationCandidateEntity(entryId,transactionId,System.currentTimeMillis()));val old=db.reconciliation().loadMatch(entryId);db.reconciliation().upsertMatch(ReconciliationMatchEntity(entryId,null,ReconciliationStatus.PENDING.name,null,null,null,null,null,old?.createdAt?:System.currentTimeMillis(),System.currentTimeMillis(),null))}}
 fun createAndConfirm(entryId:Long,transaction:Transaction){db.runInTransaction{check(db.transactions().loadById(transaction.id)==null);db.transactions().upsertAll(listOf(TransactionEntity(transaction.id,transaction.amount,transaction.category,transaction.merchant,transaction.description,transaction.usedAt,transaction.recurringId,transaction.paymentSourceId)));confirm(entryId,transaction.id,MatchSource.USER)}}
 fun declareNoActivity(month:YearMonth,sourceId:String){db.reconciliation().upsertDeclarations(listOf(MonthlyPaymentSourceDeclarationEntity(month.toString(),sourceId,"NO_ACTIVITY",System.currentTimeMillis())))}
 fun canLock(month:YearMonth,sources:List<PaymentSource>):List<LockBlocker>{val declarations=db.reconciliation().loadDeclarations().associateBy{it.month to it.paymentSourceId};return sources.filter{it.isCard}.mapNotNull{s->val p=progress(month,s.id);when{p.imported==0&&declarations[month.toString() to s.id]?.status!="NO_ACTIVITY"->LockBlocker(s.name,missingStatement=true);p.remaining>0->LockBlocker(s.name,p.needsReview,p.unresolved);else->null}}}
}

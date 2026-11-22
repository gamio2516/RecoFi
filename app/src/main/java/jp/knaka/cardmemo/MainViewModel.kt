package jp.knaka.cardmemo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import jp.knaka.cardmemo.storage.StorageProvider

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val transactionRepository = TransactionRepository(application)
    private val settingsRepository = AppSettingsRepository(application)
    private val reconciliationRepository=ReconciliationRepository(application)
    private val _transactions = MutableStateFlow(transactionRepository.load())
    val transactions = _transactions.asStateFlow()
    private val _categories = MutableStateFlow(settingsRepository.loadCategories())
    val categories = _categories.asStateFlow()
    private val initialSources = settingsRepository.loadPaymentSources()
    private val _merchantTemplates = MutableStateFlow(settingsRepository.loadMerchants())
    val merchantTemplates = _merchantTemplates.asStateFlow()
    private val _descriptionTemplates = MutableStateFlow(settingsRepository.loadDescriptions())
    val descriptionTemplates = _descriptionTemplates.asStateFlow()
    private val _recurringExpenses = MutableStateFlow(settingsRepository.loadRecurringExpenses())
    val recurringExpenses = _recurringExpenses.asStateFlow()
    private val _monthlyBudget = MutableStateFlow(settingsRepository.loadMonthlyBudgets())
    val monthlyBudget = _monthlyBudget.asStateFlow()
    private val _defaultMonthlyBudget = MutableStateFlow(settingsRepository.loadDefaultMonthlyBudget())
    val defaultMonthlyBudget = _defaultMonthlyBudget.asStateFlow()
    private val _paymentSources = MutableStateFlow(initialSources)
    val paymentSources = _paymentSources.asStateFlow()
    private val _defaultPaymentSourceId = MutableStateFlow(settingsRepository.loadDefaultPaymentSourceId())
    val defaultPaymentSourceId = _defaultPaymentSourceId.asStateFlow()
    private val _lockedMonths = MutableStateFlow(settingsRepository.loadLockedMonths())
    val lockedMonths = _lockedMonths.asStateFlow()
    private val _importedFileHashes = MutableStateFlow(settingsRepository.loadImportedFileHashes())
    private val _reconciliationProgress = MutableStateFlow<Map<String,ReconciliationProgress>>(emptyMap())
    val reconciliationProgress = _reconciliationProgress.asStateFlow()
    private val _confirmedTransactionIds=MutableStateFlow<Set<Long>>(emptySet());val confirmedTransactionIds=_confirmedTransactionIds.asStateFlow()
    private val _suggestedTransactionIds=MutableStateFlow<Set<Long>>(emptySet());val suggestedTransactionIds=_suggestedTransactionIds.asStateFlow()
    private val _importedStatements = MutableStateFlow(settingsRepository.loadImportedStatements())
    val importedStatements = _importedStatements.asStateFlow()

    init { linkExistingRecurringTransactions();ensureRecurringFor(YearMonth.now());refreshReconciliation() }

    fun addTransaction(amount: Long, category: String, merchant: String, description: String, usedAt: Long, paymentSourceId: String, id: Long? = null) {
        val month=YearMonth.from(Instant.ofEpochMilli(usedAt).atZone(ZoneId.systemDefault()));if(month.toString() in _lockedMonths.value)return
        val old=_transactions.value.firstOrNull{it.id==id};val item = Transaction(id ?: System.currentTimeMillis(), amount, category, merchant, description, usedAt, recurringId=old?.recurringId, paymentSourceId = paymentSourceId,categoryId=if(old != null && old.category==category)old.categoryId else settingsRepository.categoryId(category))
        updateTransactions(if (id == null) _transactions.value + item else _transactions.value.map { if (it.id == id) item else it })
    }

    fun linkTransactionToRecurring(transactionId:Long,recurringId:Long?){
        val target=_transactions.value.firstOrNull{it.id==transactionId}?:return
        if(target.yearMonth().toString() in _lockedMonths.value)return
        if(recurringId!=null&&_recurringExpenses.value.none{it.id==recurringId})return
        updateTransactions(_transactions.value.map{if(it.id==transactionId)it.copy(recurringId=recurringId)else it})
    }

    fun deleteTransaction(id: Long) {val target=_transactions.value.firstOrNull{it.id==id}?:return;val month=target.yearMonth().toString();if(month in _lockedMonths.value)return;target.recurringId?.let{settingsRepository.suppressRecurringOccurrence(it,month)};updateTransactions(_transactions.value.filterNot { it.id == id })}
    fun toggleConfirmed(id: Long):String?=runCatching {
        val db=StorageProvider.database(getApplication())
        val transaction=_transactions.value.firstOrNull{it.id==id}?:error("RecoFi明細が見つかりません")
        val existing=db.reconciliation().loadMatches().firstOrNull{it.transactionId==id}
        if(existing!=null){
            val statementEntry=db.statements().loadEntry(existing.statementEntryId)?:error("カード明細が見つかりません")
            val imported=_importedStatements.value.firstOrNull{it.fileHash==statementEntry.fileHash}?:error("取込ファイルが見つかりません")
            check(imported.statementMonth !in _lockedMonths.value){"この月はロックされています"}
            if(existing.status==ReconciliationStatus.SUGGESTED.name)reconciliationRepository.confirm(existing.statementEntryId,id)
            else if(existing.status==ReconciliationStatus.CONFIRMED.name){val now=System.currentTimeMillis();db.reconciliation().upsertMatch(existing.copy(status=ReconciliationStatus.SUGGESTED.name,updatedAt=now,confirmedAt=null))}
            else error("この明細はチェックで変更できません")
        }else{
            val month=transaction.yearMonth();check(month.toString() !in _lockedMonths.value){"この月はロックされています"}
            val imported=_importedStatements.value.lastOrNull{it.statementMonth==month.toString()&&it.paymentSourceId==transaction.paymentSourceId}?:error("この月のカード明細が取り込まれていません")
            val usedEntryIds=db.reconciliation().loadMatches().filter{it.transactionId!=null}.mapTo(hashSetOf()){it.statementEntryId}
            val transactionDate=Instant.ofEpochMilli(transaction.usedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            val candidates=db.statements().loadEntries().filter{entry->entry.fileHash==imported.fileHash&&entry.id !in usedEntryIds&&entry.amount==transaction.amount}.map{it to kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(transactionDate,LocalDate.parse(it.date)))}.filter{it.second<=7}
            val exact=candidates.filter{it.second==0L};val selected=when{exact.size==1->exact.single().first;exact.size>1->error("同じ日付・金額のカード明細が複数あります。照合画面で選んでください");candidates.size==1->candidates.single().first;candidates.isEmpty()->error("同じ日付・金額のカード明細が見つかりません");else->error("候補が複数あります。照合画面で選んでください")}
            reconciliationRepository.confirm(selected.id,id)
        }
        refreshReconciliation()
    }.exceptionOrNull()?.let{it.message?:"消込状態を変更できませんでした"}
    fun confirmTransactions(ids: Set<Long>, statementMonth: YearMonth) {
        if(statementMonth.toString() in _lockedMonths.value)return;val db=StorageProvider.database(getApplication());db.reconciliation().loadMatches().filter{it.transactionId in ids&&it.status==ReconciliationStatus.SUGGESTED.name}.forEach{reconciliationRepository.confirm(it.statementEntryId,it.transactionId!!)};refreshReconciliation()
    }
    fun setMonthLocked(month: YearMonth, locked: Boolean) {
        if(locked&&reconciliationRepository.canLock(month,_paymentSources.value).isNotEmpty())return
        _lockedMonths.value = if (locked) _lockedMonths.value + month.toString() else _lockedMonths.value - month.toString()
        settingsRepository.saveLockedMonths(_lockedMonths.value)
    }
    fun lockBlockers(month:YearMonth)=reconciliationRepository.canLock(month,_paymentSources.value)
    fun reconciliationTotal(month:YearMonth,sourceId:String)=reconciliationRepository.reconciliationTotal(month,sourceId)
    fun declareNoActivity(month:YearMonth,sourceId:String){reconciliationRepository.declareNoActivity(month,sourceId);refreshReconciliation()}
    fun reviewRows(fileHash:String)=reconciliationRepository.reviewRows(fileHash)
    fun linkedReconciliationTransactionIds()=reconciliationRepository.linkedTransactionIds()
    fun refreshReconciliationState()=refreshReconciliation()
    fun runAutoReconciliation(fileHash:String,sourceId:String,month:YearMonth){if(month.toString() in _lockedMonths.value)return;reconciliationRepository.autoMatch(fileHash,sourceId,_transactions.value);refreshReconciliation()}
    fun confirmMatch(entryId:Long,transactionId:Long):String?=runCatching{
        val db=StorageProvider.database(getApplication());val entry=db.statements().loadEntry(entryId)?:error("カード明細が見つかりません");val transaction=_transactions.value.firstOrNull{it.id==transactionId}?:error("RecoFi明細が見つかりません");val imported=_importedStatements.value.firstOrNull{it.fileHash==entry.fileHash}?:error("取込ファイルが見つかりません")
        check(imported.statementMonth !in _lockedMonths.value){"2026年${YearMonth.parse(imported.statementMonth).monthValue}月はロックされています"};check(transaction.paymentSourceId==imported.paymentSourceId){"支払方法が異なる明細は選択できません"}
        reconciliationRepository.confirm(entryId,transactionId)
        val saved=db.reconciliation().loadMatch(entryId);check(saved?.transactionId==transactionId&&saved.status==ReconciliationStatus.CONFIRMED.name){"消込結果を確認できませんでした"}
        refreshReconciliation()
    }.exceptionOrNull()?.let{it.message?:"明細を紐づけできませんでした"}
    fun rejectMatch(entryId:Long,transactionId:Long){reconciliationRepository.reject(entryId,transactionId);refreshReconciliation()}
    fun createMissingAndConfirm(entryId:Long,amount:Long,category:String,merchant:String,description:String,usedAt:Long,sourceId:String):String?=runCatching{
        val month=YearMonth.from(Instant.ofEpochMilli(usedAt).atZone(ZoneId.systemDefault()));require(month.toString() !in _lockedMonths.value){"この月はロックされています"}
        val tx=Transaction(0L,amount,category,merchant,description,usedAt,paymentSourceId=sourceId,categoryId=settingsRepository.categoryId(category));reconciliationRepository.createAndConfirm(entryId,tx);_transactions.value=transactionRepository.load();refreshReconciliation()
    }.exceptionOrNull()?.let{it.message?:"明細を保存できませんでした"}
    fun isImportedFile(hash: String): Boolean = hash in _importedFileHashes.value
    fun recordImportedFile(hash: String) {
        _importedFileHashes.value = _importedFileHashes.value + hash
        settingsRepository.saveImportedFileHashes(_importedFileHashes.value)
    }
    fun setReconciliationProgress(month: YearMonth, sourceId: String, imported: Int, matched: Int, suggested: Int, confirmed: Int)=refreshReconciliation()
    fun saveImportedStatement(statement: ImportedStatement) {
        _importedStatements.value = _importedStatements.value.filterNot { it.fileHash == statement.fileHash } + statement
        settingsRepository.saveImportedStatements(_importedStatements.value)
        refreshReconciliation()
    }
    fun linkImportedStatement(fileHash: String, month: YearMonth, sourceId: String): Boolean {
        if (month.toString() in _lockedMonths.value) return false
        if (_importedStatements.value.any { it.fileHash != fileHash && it.statementMonth == month.toString() && it.paymentSourceId == sourceId }) return false
        _importedStatements.value = _importedStatements.value.map { if (it.fileHash == fileHash) it.copy(statementMonth = month.toString(), paymentSourceId = sourceId) else it }
        settingsRepository.saveImportedStatements(_importedStatements.value)
        val entries = _importedStatements.value.firstOrNull { it.fileHash == fileHash }?.entries?.size ?: 0
        refreshReconciliation()
        return true
    }
    fun deleteImportedStatement(fileHash: String) {
        val target = _importedStatements.value.firstOrNull { it.fileHash == fileHash } ?: return
        if (target.statementMonth in _lockedMonths.value) return
        _importedStatements.value = _importedStatements.value.filterNot { it.fileHash == fileHash }
        settingsRepository.saveImportedStatements(_importedStatements.value)
        if (target.paymentSourceId.isNotBlank()) {
            refreshReconciliation()
        }
        _importedFileHashes.value = _importedFileHashes.value - fileHash - StatementTools.statementFingerprint(target.entries)
        settingsRepository.saveImportedFileHashes(_importedFileHashes.value)
    }
    fun addSuggestedTransactions(entries: List<CardStatementEntry>, sourceId: String, statementMonth: YearMonth) {
        if(statementMonth.toString() in _lockedMonths.value)return;val statement=_importedStatements.value.firstOrNull{it.statementMonth==statementMonth.toString()&&it.paymentSourceId==sourceId}?:return;reconciliationRepository.autoMatch(statement.fileHash,sourceId,_transactions.value);refreshReconciliation()
    }

    fun addCategory(value: String) { val clean = value.trim(); if (canAddManagedValue(_categories.value,clean)) { _categories.value += clean; settingsRepository.saveCategories(_categories.value) } }
    fun deleteCategory(value: String) { if (_categories.value.size > 1 && _transactions.value.none{it.category==value} && _recurringExpenses.value.none{it.category==value}) { _categories.value -= value; settingsRepository.saveCategories(_categories.value) } }
    fun moveCategory(value: String, offset: Int) { val index = _categories.value.indexOf(value); val target = index + offset; if (index >= 0 && target in _categories.value.indices) { val updated = _categories.value.toMutableList(); val moved = updated.removeAt(index); updated.add(target, moved); _categories.value = updated; settingsRepository.saveCategories(updated) } }
    fun reorderCategories(items:List<String>){if(items.toSet()==_categories.value.toSet()){_categories.value=items;settingsRepository.saveCategories(items)}}
    fun renameCategory(old:String,new:String):String?=runCatching{settingsRepository.renameCategory(old,new);val clean=new.trim();_categories.value=_categories.value.map{if(it==old)clean else it};_transactions.value=_transactions.value.map{if(it.category==old)it.copy(category=clean)else it};_recurringExpenses.value=_recurringExpenses.value.map{if(it.category==old)it.copy(category=clean)else it};null}.getOrElse{it.message?:"変更できませんでした"}
    fun addMerchant(value:String)=addTemplate(_merchantTemplates,value,settingsRepository::saveMerchants)
    fun deleteMerchant(value:String){_merchantTemplates.value-=value;settingsRepository.saveMerchants(_merchantTemplates.value)}
    fun moveMerchant(value:String,offset:Int)=moveTemplate(_merchantTemplates,value,offset,settingsRepository::saveMerchants)
    fun reorderMerchants(items:List<String>)=reorderTemplate(_merchantTemplates,items,settingsRepository::saveMerchants)
    fun renameMerchant(old:String,new:String):String?=renameTemplate(_merchantTemplates,old,new,settingsRepository::renameMerchant)
    fun addDescription(value:String)=addTemplate(_descriptionTemplates,value,settingsRepository::saveDescriptions)
    fun deleteDescription(value:String){_descriptionTemplates.value-=value;settingsRepository.saveDescriptions(_descriptionTemplates.value)}
    fun moveDescription(value:String,offset:Int)=moveTemplate(_descriptionTemplates,value,offset,settingsRepository::saveDescriptions)
    fun reorderDescriptions(items:List<String>)=reorderTemplate(_descriptionTemplates,items,settingsRepository::saveDescriptions)
    fun renameDescription(old:String,new:String):String?=renameTemplate(_descriptionTemplates,old,new,settingsRepository::renameDescription)

    fun setMonthlyBudget(month: YearMonth, amount: Long) { val clean = amount.coerceAtLeast(0); _monthlyBudget.value = if (clean == 0L) _monthlyBudget.value - month.toString() else _monthlyBudget.value + (month.toString() to clean); settingsRepository.saveMonthlyBudgets(_monthlyBudget.value) }
    fun setDefaultMonthlyBudget(amount: Long) { _defaultMonthlyBudget.value = amount.coerceAtLeast(0); settingsRepository.saveDefaultMonthlyBudget(_defaultMonthlyBudget.value) }

    fun addPaymentSource(name: String,type:PaymentSourceType=PaymentSourceType.OTHER) { val clean = name.trim(); if (clean.isNotEmpty() && _paymentSources.value.none { it.name == clean }) { val source = PaymentSource("source_${System.currentTimeMillis()}", clean, type); _paymentSources.value += source; settingsRepository.savePaymentSources(_paymentSources.value) } }
    fun editPaymentSource(id:String,name:String,type:PaymentSourceType,colorKey:String?=null){_paymentSources.value=_paymentSources.value.map{if(it.id==id)it.copy(name=name.trim(),type=type,colorKey=colorKey?:it.colorKey)else it};settingsRepository.savePaymentSources(_paymentSources.value)}
    fun setDefaultPaymentSource(id:String){if(_paymentSources.value.any{it.id==id}){_defaultPaymentSourceId.value=id;settingsRepository.saveDefaultPaymentSourceId(id)}}
    fun deletePaymentSource(id: String) { if (_paymentSources.value.size > 1 && _transactions.value.none { it.paymentSourceId == id } && _recurringExpenses.value.none { it.paymentSourceId == id }) { _paymentSources.value = _paymentSources.value.filterNot { it.id == id }; settingsRepository.savePaymentSources(_paymentSources.value);if(_defaultPaymentSourceId.value==id)_defaultPaymentSourceId.value=null } }

    fun saveRecurringExpense(id: Long?, amount: Long, paymentDay: Int, category: String, merchant: String, description: String, contractDate: LocalDate, paymentSourceId: String, intervalMonths: Int) {
        val previous = _recurringExpenses.value.firstOrNull { it.id == id }
        val item = RecurringExpense(id ?: System.currentTimeMillis(), amount, category, merchant.trim(), description.trim(), paymentDay.coerceIn(1, 31), YearMonth.from(contractDate).toString(), contractDate.toString(), paymentSourceId, intervalMonths.coerceAtLeast(1), previous?.endDate, previous?.priceRevisions.orEmpty(),if(previous != null && previous.category==category)previous.categoryId else settingsRepository.categoryId(category))
        _recurringExpenses.value = if (id == null) _recurringExpenses.value + item else _recurringExpenses.value.map { if (it.id == id) item else it }
        settingsRepository.saveRecurringExpenses(_recurringExpenses.value)
        if (id != null) updateTransactions(_transactions.value.filterNot { it.recurringId == id && it.yearMonth() >= YearMonth.now() && it.yearMonth().toString() !in _lockedMonths.value })
        linkExistingRecurringTransactions()
        ensureRecurringFor(YearMonth.now())
    }

    fun endRecurringExpense(id: Long, endDate: LocalDate) {
        _recurringExpenses.value = _recurringExpenses.value.map { if (it.id == id) it.copy(endDate = endDate.toString()) else it }
        settingsRepository.saveRecurringExpenses(_recurringExpenses.value)
        updateTransactions(_transactions.value.filterNot { it.recurringId == id && it.yearMonth().toString() !in _lockedMonths.value && Instant.ofEpochMilli(it.usedAt).atZone(ZoneId.systemDefault()).toLocalDate().isAfter(endDate) })
    }

    fun reviseRecurringExpense(id: Long, effectiveDate: LocalDate, amount: Long) {
        _recurringExpenses.value = _recurringExpenses.value.map { expense -> if (expense.id == id) expense.copy(priceRevisions = (expense.priceRevisions.filterNot { it.effectiveDate == effectiveDate.toString() } + PriceRevision(effectiveDate.toString(), amount)).sortedBy { it.effectiveDate }) else expense }
        settingsRepository.saveRecurringExpenses(_recurringExpenses.value)
        updateTransactions(_transactions.value.filterNot { transaction -> transaction.recurringId == id && transaction.yearMonth().toString() !in _lockedMonths.value && !Instant.ofEpochMilli(transaction.usedAt).atZone(ZoneId.systemDefault()).toLocalDate().isBefore(effectiveDate) })
        ensureRecurringFor(YearMonth.now())
    }

    fun deleteRecurringExpense(id: Long) {
        val transactionsWithoutPlan = removeGeneratedTransactions(_transactions.value,id)
        _recurringExpenses.value = _recurringExpenses.value.filterNot { it.id == id }
        settingsRepository.saveRecurringExpenses(_recurringExpenses.value)
        settingsRepository.clearRecurringSuppressions(id)
        updateTransactions(transactionsWithoutPlan)
        refreshReconciliation()
    }

    fun duplicateRecurringExpense(id: Long) {
        val source = _recurringExpenses.value.firstOrNull { it.id == id } ?: return
        val today = LocalDate.now()
        val currentAmount = source.priceRevisions.filter { !LocalDate.parse(it.effectiveDate).isAfter(today) }.maxByOrNull { it.effectiveDate }?.amount ?: source.amount
        val duplicate = source.copy(id = System.currentTimeMillis(), amount = currentAmount, contractDate = today.toString(), startMonth = YearMonth.from(today).toString(), endDate = null, priceRevisions = emptyList())
        _recurringExpenses.value += duplicate
        settingsRepository.saveRecurringExpenses(_recurringExpenses.value)
        ensureRecurringFor(YearMonth.now())
    }

    fun reorderRecurringExpenses(items: List<RecurringExpense>) {
        if (items.map { it.id }.toSet() == _recurringExpenses.value.map { it.id }.toSet()) {
            _recurringExpenses.value = items
            settingsRepository.saveRecurringExpenseOrder(items)
        }
    }

    fun ensureRecurringFor(month: YearMonth) {
        // Opening an old month must be read-only. Otherwise a recurring entry
        // intentionally removed from a restored/cleaned backup is recreated merely
        // by viewing that month. Current and future months remain automatic.
        if (!shouldGenerateRecurringFor(month, YearMonth.now())) return
        if (month.toString() in _lockedMonths.value) return
        val suppressed=settingsRepository.loadSuppressedRecurringOccurrences()
        val activeExpenses=_recurringExpenses.value.filterNot{"${it.id}|$month" in suppressed}
        val generated = RecurringTransactionGenerator.generate(activeExpenses, _transactions.value, month)
        val updated = _transactions.value + generated
        if (updated != _transactions.value) updateTransactions(updated)
    }

    private fun linkExistingRecurringTransactions() {
        val zone=ZoneId.systemDefault()
        val linked=_transactions.value.map { transaction ->
            if(transaction.recurringId!=null)return@map transaction
            val date=Instant.ofEpochMilli(transaction.usedAt).atZone(zone).toLocalDate();val transactionMonth=YearMonth.from(date)
            val candidates=_recurringExpenses.value.filter { expense ->
                if(!RecurringExpensePolicy.isScheduledFor(expense,transactionMonth))return@filter false
                val scheduled=RecurringExpensePolicy.billingDate(expense,transactionMonth)
                val merchantMatch=normalizeMerchant(expense.merchant).let{it.isNotBlank()&&it==normalizeMerchant(transaction.merchant)}
                val descriptionMatch=normalizeMerchant(expense.description).let{it.isNotBlank()&&it==normalizeMerchant(transaction.description)}
                expense.paymentSourceId==transaction.paymentSourceId&&expense.category==transaction.category&&RecurringExpensePolicy.amountOn(expense,scheduled)==transaction.amount&&kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(scheduled,date))<=3&&(merchantMatch||descriptionMatch)
            }
            if(candidates.size==1)transaction.copy(recurringId=candidates.single().id)else transaction
        }
        if(linked!=_transactions.value)updateTransactions(linked)
    }

    private fun Transaction.yearMonth(): YearMonth = YearMonth.from(Instant.ofEpochMilli(usedAt).atZone(ZoneId.systemDefault()))
    private fun normalizeMerchant(value: String) = value.lowercase().filter { it.isLetterOrDigit() }
    private fun updateTransactions(items: List<Transaction>) { _transactions.value = items.sortedByDescending { it.usedAt }; transactionRepository.save(_transactions.value) }
    private fun refreshReconciliation(){val db=StorageProvider.database(getApplication());val matches=db.reconciliation().loadMatches();_confirmedTransactionIds.value=matches.filter{it.status==ReconciliationStatus.CONFIRMED.name}.mapNotNull{it.transactionId}.toSet();_suggestedTransactionIds.value=matches.filter{it.status==ReconciliationStatus.SUGGESTED.name}.mapNotNull{it.transactionId}.toSet();_reconciliationProgress.value=_importedStatements.value.filter{it.paymentSourceId.isNotBlank()}.associate{val m=YearMonth.parse(it.statementMonth);val p=reconciliationRepository.progress(m,it.paymentSourceId);"$m|${it.paymentSourceId}" to ReconciliationProgress(p.imported,p.confirmed+p.needsReview,p.needsReview,p.confirmed)}}
    private fun addTemplate(flow:MutableStateFlow<List<String>>,value:String,save:(List<String>)->Unit){val clean=value.trim();if(canAddManagedValue(flow.value,clean)){flow.value+=clean;save(flow.value)}}
    private fun moveTemplate(flow:MutableStateFlow<List<String>>,value:String,offset:Int,save:(List<String>)->Unit){val i=flow.value.indexOf(value);val target=i+offset;if(i>=0&&target in flow.value.indices){val list=flow.value.toMutableList();val moved=list.removeAt(i);list.add(target,moved);flow.value=list;save(list)}}
    private fun reorderTemplate(flow:MutableStateFlow<List<String>>,items:List<String>,save:(List<String>)->Unit){if(items.toSet()==flow.value.toSet()){flow.value=items;save(items)}}
    private fun renameTemplate(flow:MutableStateFlow<List<String>>,old:String,new:String,rename:(String,String)->Unit):String?=runCatching{rename(old,new);val clean=new.trim();flow.value=flow.value.map{if(it==old)clean else it};null}.getOrElse{it.message?:"変更できませんでした"}
}

internal fun shouldGenerateRecurringFor(requestedMonth: YearMonth, currentMonth: YearMonth): Boolean =
    !requestedMonth.isBefore(currentMonth)


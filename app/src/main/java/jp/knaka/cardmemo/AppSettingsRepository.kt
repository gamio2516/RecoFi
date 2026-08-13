package jp.knaka.cardmemo

import android.content.Context
import jp.knaka.cardmemo.storage.*

class AppSettingsRepository(context: Context) {
    private val db = StorageProvider.database(context)
    init {
        if (db.referenceData().loadPaymentSources().isEmpty()) db.referenceData().upsertPaymentSources(DefaultPaymentSources.mapIndexed { i, it -> PaymentSourceEntity(it.id, it.name, it.type.name, i) })
        if (db.referenceData().loadCategories().isEmpty()) db.referenceData().upsertCategories(DefaultCategories.mapIndexed { i, it -> CategoryEntity(it, i) })
    }
    fun loadCategories() = db.referenceData().loadCategories().map { it.name }
    fun saveCategories(items: List<String>) = db.runInTransaction { val dao=db.referenceData(); dao.loadCategories().filter { it.name !in items }.forEach { dao.deleteCategory(it.name) }; dao.upsertCategories(items.mapIndexed { i,v -> CategoryEntity(v,i) }) }
    fun loadMerchants() = db.referenceData().loadMerchants().map { it.value }
    fun saveMerchants(items: List<String>) = db.runInTransaction { val dao=db.referenceData(); dao.deleteMerchants(); dao.upsertMerchants(items.mapIndexed { i,v -> MerchantTemplateEntity(v,i) }) }
    fun loadDescriptions() = db.referenceData().loadDescriptions().map { it.value }
    fun saveDescriptions(items: List<String>) = db.runInTransaction { val dao=db.referenceData(); dao.deleteDescriptions(); dao.upsertDescriptions(items.mapIndexed { i,v -> DescriptionTemplateEntity(v,i) }) }
    fun loadMonthlyBudgets(): Map<String, Long> = db.monthlyState().loadBudgets().associate { it.month to it.amount }
    fun saveMonthlyBudgets(items: Map<String, Long>) = db.runInTransaction { db.monthlyState().deleteBudgets(); db.monthlyState().upsertBudgets(items.filterValues { it > 0 }.map { MonthlyBudgetEntity(it.key,it.value) }) }
    fun loadDefaultMonthlyBudget(): Long = db.monthlyState().loadBudgetSettings()?.defaultMonthlyBudget ?: 0
    fun saveDefaultMonthlyBudget(amount: Long) = db.monthlyState().upsertBudgetSettings(AppBudgetSettingsEntity(defaultMonthlyBudget=amount.coerceAtLeast(0)))
    fun loadPaymentSources() = db.referenceData().loadPaymentSources().map { PaymentSource(it.id,it.name,runCatching{PaymentSourceType.valueOf(it.type)}.getOrDefault(PaymentSourceType.OTHER)) }
    fun savePaymentSources(items: List<PaymentSource>) = db.runInTransaction { val dao=db.referenceData(); dao.loadPaymentSources().filter { old -> items.none { it.id==old.id } }.forEach { dao.deletePaymentSource(it.id) }; dao.upsertPaymentSources(items.mapIndexed { i,v -> PaymentSourceEntity(v.id,v.name,v.type.name,i) }) }
    fun loadLockedMonths() = db.monthlyState().loadLocks().map { it.month }.toSet()
    fun saveLockedMonths(items:Set<String>) = db.runInTransaction { db.monthlyState().deleteLocks(); db.monthlyState().upsertLocks(items.map { MonthlyLockEntity(it,System.currentTimeMillis()) }) }
    fun loadImportedFileHashes() = db.statements().loadFingerprints().map { it.fingerprint }.toSet()
    fun saveImportedFileHashes(items:Set<String>) = db.runInTransaction { db.statements().deleteFingerprints(); db.statements().upsertFingerprints(items.toList().takeLast(100).map { ImportedFingerprintEntity(it,System.currentTimeMillis()) }) }
    fun loadImportedStatements():List<ImportedStatement> { val rows=db.statements().loadEntries().groupBy { it.fileHash }; return db.statements().loadStatements().map { s-> ImportedStatement(s.statementMonth.orEmpty(),s.paymentSourceId.orEmpty(),s.fileName,s.fileHash,rows[s.fileHash].orEmpty().map { CardStatementEntry(java.time.LocalDate.parse(it.date),it.amount,it.merchant,it.rawText) }) } }
    fun saveImportedStatements(items:List<ImportedStatement>)=db.runInTransaction { val dao=db.statements();val existing=dao.loadStatements().map{it.fileHash}.toSet();dao.loadStatements().filter{old->items.none{it.fileHash==old.fileHash}}.forEach{dao.deleteStatement(it.fileHash)};dao.upsertStatements(items.map { ImportedStatementEntity(it.fileHash,it.statementMonth.ifBlank { null },it.paymentSourceId.ifBlank { null },it.fileName) });dao.upsertEntries(items.filter{it.fileHash !in existing}.flatMap { s->s.entries.mapIndexed { i,e->StatementEntryEntity(stableEntryId(s.fileHash,i),s.fileHash,i,e.date.toString(),e.amount,e.merchant,e.rawText) } }) }
    fun loadRecurringExpenses():List<RecurringExpense> { val rev=db.recurringExpenses().loadRevisions().groupBy { it.recurringId };return db.recurringExpenses().loadAll().map { e->RecurringExpense(e.id,e.amount,e.category,e.merchant,e.description,e.billingDay,e.startMonth,e.contractDate,e.paymentSourceId,e.intervalMonths,e.endDate,rev[e.id].orEmpty().map { PriceRevision(it.effectiveDate,it.amount) }) } }
    fun saveRecurringExpenses(items:List<RecurringExpense>)=db.runInTransaction { val dao=db.recurringExpenses();dao.upsertAll(items.map { RecurringExpenseEntity(it.id,it.amount,it.category,it.merchant,it.description,it.billingDay,it.startMonth,it.contractDate,it.paymentSourceId,it.intervalMonths,it.endDate) });dao.deleteRevisions();dao.upsertRevisions(items.flatMap { e->e.priceRevisions.map { RecurringPriceRevisionEntity(e.id,it.effectiveDate,it.amount) } });dao.loadAll().filter { old->items.none { it.id==old.id } }.forEach { dao.delete(it.id) } }
}
private fun stableEntryId(hash:String,row:Int):Long { var value=1125899906842597L; "$hash|$row".forEach{value=31*value+it.code};return value and Long.MAX_VALUE }

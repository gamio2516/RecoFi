package jp.knaka.cardmemo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import jp.knaka.cardmemo.storage.*

class AppSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("card_memo", Context.MODE_PRIVATE)
    private val room = (StorageProvider.get(context) as? StorageBootstrapResult.RoomReady)?.database

    fun loadCategories(): List<String> = room?.referenceData()?.loadCategories()?.map { it.name } ?: loadStrings(CATEGORIES, DefaultCategories)
    fun saveCategories(items: List<String>) { room?.let { db -> db.runInTransaction { val dao = db.referenceData(); dao.upsertCategories(items.mapIndexed { index, value -> CategoryEntity(value, index) }); dao.loadCategories().filter { it.name !in items }.forEach { dao.deleteCategory(it.name) } }; return }; saveStrings(CATEGORIES, items) }
    fun loadNotes(): List<String> = loadStrings(NOTES, DefaultFrequentNotes)
    fun saveNotes(items: List<String>) = saveStrings(NOTES, items)
    fun loadNotes(sourceId: String): List<String> {
        room?.let { return it.referenceData().loadNotes().filter { note -> note.paymentSourceId == sourceId }.map { note -> note.note } }
        val key = "${NOTES}_$sourceId"
        return if (preferences.contains(key)) loadStrings(key, emptyList()) else if (sourceId == "rakuten") loadNotes() else emptyList()
    }
    fun saveNotes(sourceId: String, items: List<String>) { room?.let { db -> db.runInTransaction { val dao = db.referenceData(); val all = dao.loadNotes().filterNot { it.paymentSourceId == sourceId } + items.mapIndexed { index, note -> NoteTemplateEntity(sourceId, note, index) }; dao.deleteNotes(); dao.upsertNotes(all) }; return }; saveStrings("${NOTES}_$sourceId", items) }
    fun loadMonthlyBudgets(): Map<String, Int> {
        room?.let { return it.monthlyState().loadBudgets().associate { budget -> budget.month to budget.amount.toIntChecked() } }
        val saved = preferences.getString(BUDGET_BY_MONTH, null)
        if (saved != null) return runCatching { val json = org.json.JSONObject(saved); json.keys().asSequence().associateWith { json.optInt(it, 0) }.filterValues { it > 0 } }.getOrDefault(emptyMap())
        val legacy = preferences.getInt(BUDGET, 0)
        return if (legacy > 0) mapOf(java.time.YearMonth.now().toString() to legacy) else emptyMap()
    }
    fun saveMonthlyBudgets(items: Map<String, Int>) { room?.let { db -> db.runInTransaction { db.monthlyState().deleteBudgets(); db.monthlyState().upsertBudgets(items.filterValues { it > 0 }.map { MonthlyBudgetEntity(it.key, it.value.toLong()) }) }; return }; val json = org.json.JSONObject(); items.filterValues { it > 0 }.forEach { (month, amount) -> json.put(month, amount) }; preferences.edit().putString(BUDGET_BY_MONTH, json.toString()).apply() }
    fun loadDefaultMonthlyBudget(): Int = room?.monthlyState()?.loadBudgetSettings()?.defaultMonthlyBudget?.toIntChecked() ?: preferences.getInt(DEFAULT_BUDGET, 0)
    fun saveDefaultMonthlyBudget(amount: Int) { room?.let { it.monthlyState().upsertBudgetSettings(AppBudgetSettingsEntity(defaultMonthlyBudget = amount.coerceAtLeast(0).toLong())); return }; preferences.edit().putInt(DEFAULT_BUDGET, amount.coerceAtLeast(0)).apply() }
    fun loadPaymentSources(): List<PaymentSource> = room?.referenceData()?.loadPaymentSources()?.map { PaymentSource(it.id, it.name, it.isCard) } ?: runCatching {
        val array = JSONArray(preferences.getString(SOURCES, null) ?: return DefaultPaymentSources)
        List(array.length()) { index -> val item = array.getJSONObject(index); PaymentSource(item.getString("id"), item.getString("name"), item.optBoolean("isCard", true)) }
    }.getOrDefault(DefaultPaymentSources)
    fun savePaymentSources(items: List<PaymentSource>) { room?.let { db -> db.runInTransaction { val dao = db.referenceData(); dao.upsertPaymentSources(items.mapIndexed { index, source -> PaymentSourceEntity(source.id, source.name, source.isCard, index) }); dao.loadPaymentSources().filter { current -> items.none { it.id == current.id } }.forEach { dao.deletePaymentSource(it.id) } }; return }; val array = JSONArray(); items.forEach { source -> array.put(JSONObject().apply { put("id", source.id); put("name", source.name); put("isCard", source.isCard) }) }; preferences.edit().putString(SOURCES, array.toString()).apply() }
    fun loadLockedMonths(): Set<String> = room?.monthlyState()?.loadLocks()?.map { it.month }?.toSet() ?: loadStrings(LOCKED_MONTHS, emptyList()).toSet()
    fun saveLockedMonths(items: Set<String>) { room?.let { db -> db.runInTransaction { db.monthlyState().deleteLocks(); db.monthlyState().upsertLocks(items.map { MonthlyLockEntity(it, System.currentTimeMillis()) }) }; return }; saveStrings(LOCKED_MONTHS, items.sorted()) }
    fun loadImportedFileHashes(): Set<String> = room?.statements()?.loadFingerprints()?.map { it.fingerprint }?.toSet() ?: loadStrings(IMPORTED_HASHES, emptyList()).toSet()
    fun saveImportedFileHashes(items: Set<String>) { room?.let { db -> db.runInTransaction { db.statements().deleteFingerprints(); db.statements().upsertFingerprints(items.toList().takeLast(100).map { ImportedFingerprintEntity(it, System.currentTimeMillis()) }) }; return }; saveStrings(IMPORTED_HASHES, items.toList().takeLast(100)) }
    fun loadReconciliationProgress(): Map<String, ReconciliationProgress> = room?.monthlyState()?.loadProgress()?.associate { "${it.statementMonth}|${it.paymentSourceId}" to ReconciliationProgress(it.imported, it.matched, it.suggested, it.confirmed) } ?: runCatching {
        val json = JSONObject(preferences.getString(RECONCILIATION_PROGRESS, "{}") ?: "{}")
        json.keys().asSequence().associateWith { key -> val item = json.getJSONObject(key); ReconciliationProgress(item.optInt("imported", item.optInt("total", 0)), item.optInt("matched", 0), item.optInt("suggested", 0), item.optInt("confirmed", 0)) }
    }.getOrDefault(emptyMap())
    fun saveReconciliationProgress(items: Map<String, ReconciliationProgress>) {
        room?.let { db -> db.runInTransaction { db.monthlyState().deleteProgress(); db.monthlyState().upsertProgress(items.mapNotNull { (key, value) -> val split = key.lastIndexOf('|'); if (split <= 0) null else ReconciliationProgressEntity(key.substring(0, split), key.substring(split + 1), value.imported, value.matched, value.suggested, value.confirmed) }) }; return }
        val json = JSONObject(); items.forEach { (key, value) -> json.put(key, JSONObject().apply { put("imported", value.imported); put("matched", value.matched); put("suggested", value.suggested); put("confirmed", value.confirmed) }) }
        preferences.edit().putString(RECONCILIATION_PROGRESS, json.toString()).apply()
    }
    fun loadImportedStatements(): List<ImportedStatement> = room?.let { db -> val entries = db.statements().loadEntries().groupBy { it.fileHash }; db.statements().loadStatements().map { statement -> ImportedStatement(statement.statementMonth.orEmpty(), statement.paymentSourceId.orEmpty(), statement.fileName, statement.fileHash, entries[statement.fileHash].orEmpty().map { CardStatementEntry(java.time.LocalDate.parse(it.date), it.amount.toIntChecked(), it.merchant, it.rawText) }) } } ?: runCatching {
        val array = JSONArray(preferences.getString(IMPORTED_STATEMENTS, "[]"))
        List(array.length()) { index -> val item = array.getJSONObject(index); val rows = item.getJSONArray("entries"); ImportedStatement(item.getString("statementMonth"), item.getString("paymentSourceId"), item.getString("fileName"), item.getString("fileHash"), List(rows.length()) { rowIndex -> val row = rows.getJSONObject(rowIndex); CardStatementEntry(java.time.LocalDate.parse(row.getString("date")), row.getInt("amount"), row.getString("merchant"), row.optString("rawText")) }) }
    }.getOrDefault(emptyList())
    fun saveImportedStatements(items: List<ImportedStatement>) {
        room?.let { db -> db.runInTransaction { db.statements().deleteEntries(); db.statements().deleteStatements(); db.statements().upsertStatements(items.map { ImportedStatementEntity(it.fileHash, it.statementMonth.ifBlank { null }, it.paymentSourceId.ifBlank { null }, it.fileName) }); db.statements().upsertEntries(items.flatMap { statement -> statement.entries.mapIndexed { index, entry -> StatementEntryEntity(fileHash = statement.fileHash, rowOrder = index, date = entry.date.toString(), amount = entry.amount.toLong(), merchant = entry.merchant, rawText = entry.rawText) } }) }; return }
        val array = JSONArray(); items.forEach { statement -> array.put(JSONObject().apply { put("statementMonth", statement.statementMonth); put("paymentSourceId", statement.paymentSourceId); put("fileName", statement.fileName); put("fileHash", statement.fileHash); put("entries", JSONArray().apply { statement.entries.forEach { entry -> put(JSONObject().apply { put("date", entry.date.toString()); put("amount", entry.amount); put("merchant", entry.merchant); put("rawText", entry.rawText) }) } }) }) }
        preferences.edit().putString(IMPORTED_STATEMENTS, array.toString()).apply()
    }

    fun loadRecurringExpenses(): List<RecurringExpense> = room?.let { db -> val revisions = db.recurringExpenses().loadRevisions().groupBy { it.recurringId }; db.recurringExpenses().loadAll().map { expense -> RecurringExpense(expense.id, expense.amount.toIntChecked(), expense.category, expense.note, expense.billingDay, expense.startMonth, expense.contractDate, expense.paymentSourceId, expense.intervalMonths, expense.endDate, revisions[expense.id].orEmpty().map { PriceRevision(it.effectiveDate, it.amount.toIntChecked()) }) } } ?: runCatching {
        val array = JSONArray(preferences.getString(RECURRING, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val revisionsJson = item.optJSONArray("priceRevisions") ?: JSONArray()
                val revisions = buildList { for (revisionIndex in 0 until revisionsJson.length()) { val revision = revisionsJson.getJSONObject(revisionIndex); add(PriceRevision(revision.getString("effectiveDate"), revision.getInt("amount"))) } }
                add(RecurringExpense(
                    id = item.getLong("id"), amount = item.getInt("amount"),
                    category = item.getString("category"), note = item.optString("note"),
                    billingDay = item.getInt("billingDay"), startMonth = item.getString("startMonth"),
                    contractDate = item.optString("contractDate", item.getString("startMonth") + "-01"),
                    paymentSourceId = item.optString("paymentSourceId", "rakuten"),
                    intervalMonths = item.optInt("intervalMonths", 1),
                    endDate = item.optString("endDate").ifBlank { null },
                    priceRevisions = revisions,
                ))
            }
        }
    }.getOrDefault(emptyList())

    fun saveRecurringExpenses(items: List<RecurringExpense>) {
        room?.let { db -> db.runInTransaction { val dao = db.recurringExpenses(); dao.upsertAll(items.map { RecurringExpenseEntity(it.id, it.amount.toLong(), it.category, it.note, it.billingDay, it.startMonth, it.contractDate, it.paymentSourceId, it.intervalMonths, it.endDate) }); dao.deleteRevisions(); dao.upsertRevisions(items.flatMap { expense -> expense.priceRevisions.map { RecurringPriceRevisionEntity(expense.id, it.effectiveDate, it.amount.toLong()) } }); dao.loadAll().filter { current -> items.none { it.id == current.id } }.forEach { dao.delete(it.id) } }; return }
        val array = JSONArray()
        items.forEach { expense -> array.put(JSONObject().apply {
            put("id", expense.id); put("amount", expense.amount); put("category", expense.category)
            put("note", expense.note); put("billingDay", expense.billingDay); put("startMonth", expense.startMonth); put("contractDate", expense.contractDate); put("paymentSourceId", expense.paymentSourceId); put("intervalMonths", expense.intervalMonths); expense.endDate?.let { put("endDate", it) }; put("priceRevisions", JSONArray().apply { expense.priceRevisions.forEach { revision -> put(JSONObject().apply { put("effectiveDate", revision.effectiveDate); put("amount", revision.amount) }) } })
        }) }
        preferences.edit().putString(RECURRING, array.toString()).apply()
    }

    private fun loadStrings(key: String, defaults: List<String>): List<String> = runCatching {
        val saved = preferences.getString(key, null) ?: return defaults
        val array = JSONArray(saved)
        List(array.length()) { array.getString(it) }
    }.getOrDefault(defaults)

    private fun saveStrings(key: String, items: List<String>) {
        preferences.edit().putString(key, JSONArray(items).toString()).apply()
    }

    private companion object { const val CATEGORIES = "categories"; const val NOTES = "frequent_notes"; const val RECURRING = "recurring_expenses"; const val BUDGET = "monthly_budget"; const val BUDGET_BY_MONTH = "monthly_budgets"; const val DEFAULT_BUDGET = "default_monthly_budget"; const val SOURCES = "payment_sources"; const val LOCKED_MONTHS = "locked_months"; const val IMPORTED_HASHES = "imported_file_hashes"; const val RECONCILIATION_PROGRESS = "reconciliation_progress"; const val IMPORTED_STATEMENTS = "imported_statements" }
}

private fun Long.toIntChecked(): Int {
    require(this in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "金額が現行UIの範囲を超えています" }
    return toInt()
}

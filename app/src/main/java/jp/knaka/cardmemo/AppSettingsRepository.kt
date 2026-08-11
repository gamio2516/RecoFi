package jp.knaka.cardmemo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AppSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("card_memo", Context.MODE_PRIVATE)

    fun loadCategories(): List<String> = loadStrings(CATEGORIES, DefaultCategories)
    fun saveCategories(items: List<String>) = saveStrings(CATEGORIES, items)
    fun loadNotes(): List<String> = loadStrings(NOTES, DefaultFrequentNotes)
    fun saveNotes(items: List<String>) = saveStrings(NOTES, items)
    fun loadNotes(sourceId: String): List<String> {
        val key = "${NOTES}_$sourceId"
        return if (preferences.contains(key)) loadStrings(key, emptyList()) else if (sourceId == "rakuten") loadNotes() else emptyList()
    }
    fun saveNotes(sourceId: String, items: List<String>) = saveStrings("${NOTES}_$sourceId", items)
    fun loadMonthlyBudgets(): Map<String, Int> {
        val saved = preferences.getString(BUDGET_BY_MONTH, null)
        if (saved != null) return runCatching { val json = org.json.JSONObject(saved); json.keys().asSequence().associateWith { json.optInt(it, 0) }.filterValues { it > 0 } }.getOrDefault(emptyMap())
        val legacy = preferences.getInt(BUDGET, 0)
        return if (legacy > 0) mapOf(java.time.YearMonth.now().toString() to legacy) else emptyMap()
    }
    fun saveMonthlyBudgets(items: Map<String, Int>) { val json = org.json.JSONObject(); items.filterValues { it > 0 }.forEach { (month, amount) -> json.put(month, amount) }; preferences.edit().putString(BUDGET_BY_MONTH, json.toString()).apply() }
    fun loadDefaultMonthlyBudget(): Int = preferences.getInt(DEFAULT_BUDGET, 0)
    fun saveDefaultMonthlyBudget(amount: Int) { preferences.edit().putInt(DEFAULT_BUDGET, amount.coerceAtLeast(0)).apply() }
    fun loadPaymentSources(): List<PaymentSource> = runCatching {
        val array = JSONArray(preferences.getString(SOURCES, null) ?: return DefaultPaymentSources)
        List(array.length()) { index -> val item = array.getJSONObject(index); PaymentSource(item.getString("id"), item.getString("name"), item.optBoolean("isCard", true)) }
    }.getOrDefault(DefaultPaymentSources)
    fun savePaymentSources(items: List<PaymentSource>) { val array = JSONArray(); items.forEach { source -> array.put(JSONObject().apply { put("id", source.id); put("name", source.name); put("isCard", source.isCard) }) }; preferences.edit().putString(SOURCES, array.toString()).apply() }
    fun loadLockedMonths(): Set<String> = loadStrings(LOCKED_MONTHS, emptyList()).toSet()
    fun saveLockedMonths(items: Set<String>) = saveStrings(LOCKED_MONTHS, items.sorted())
    fun loadImportedFileHashes(): Set<String> = loadStrings(IMPORTED_HASHES, emptyList()).toSet()
    fun saveImportedFileHashes(items: Set<String>) = saveStrings(IMPORTED_HASHES, items.toList().takeLast(100))
    fun loadReconciliationProgress(): Map<String, Pair<Int, Int>> = runCatching {
        val json = JSONObject(preferences.getString(RECONCILIATION_PROGRESS, "{}") ?: "{}")
        json.keys().asSequence().associateWith { key -> val item = json.getJSONObject(key); item.getInt("total") to item.getInt("matched") }
    }.getOrDefault(emptyMap())
    fun saveReconciliationProgress(items: Map<String, Pair<Int, Int>>) {
        val json = JSONObject(); items.forEach { (key, value) -> json.put(key, JSONObject().apply { put("total", value.first); put("matched", value.second) }) }
        preferences.edit().putString(RECONCILIATION_PROGRESS, json.toString()).apply()
    }
    fun loadImportedStatements(): List<ImportedStatement> = runCatching {
        val array = JSONArray(preferences.getString(IMPORTED_STATEMENTS, "[]"))
        List(array.length()) { index -> val item = array.getJSONObject(index); val rows = item.getJSONArray("entries"); ImportedStatement(item.getString("statementMonth"), item.getString("paymentSourceId"), item.getString("fileName"), item.getString("fileHash"), List(rows.length()) { rowIndex -> val row = rows.getJSONObject(rowIndex); CardStatementEntry(java.time.LocalDate.parse(row.getString("date")), row.getInt("amount"), row.getString("merchant"), row.optString("rawText")) }) }
    }.getOrDefault(emptyList())
    fun saveImportedStatements(items: List<ImportedStatement>) {
        val array = JSONArray(); items.forEach { statement -> array.put(JSONObject().apply { put("statementMonth", statement.statementMonth); put("paymentSourceId", statement.paymentSourceId); put("fileName", statement.fileName); put("fileHash", statement.fileHash); put("entries", JSONArray().apply { statement.entries.forEach { entry -> put(JSONObject().apply { put("date", entry.date.toString()); put("amount", entry.amount); put("merchant", entry.merchant); put("rawText", entry.rawText) }) } }) }) }
        preferences.edit().putString(IMPORTED_STATEMENTS, array.toString()).apply()
    }

    fun loadRecurringExpenses(): List<RecurringExpense> = runCatching {
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

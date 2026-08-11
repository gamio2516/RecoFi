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
    fun loadMonthlyBudget(): Int = preferences.getInt(BUDGET, 0)
    fun saveMonthlyBudget(amount: Int) { preferences.edit().putInt(BUDGET, amount.coerceAtLeast(0)).apply() }
    fun loadPaymentSources(): List<PaymentSource> = runCatching {
        val array = JSONArray(preferences.getString(SOURCES, null) ?: return DefaultPaymentSources)
        List(array.length()) { index -> val item = array.getJSONObject(index); PaymentSource(item.getString("id"), item.getString("name"), item.optBoolean("isCard", true)) }
    }.getOrDefault(DefaultPaymentSources)
    fun savePaymentSources(items: List<PaymentSource>) { val array = JSONArray(); items.forEach { source -> array.put(JSONObject().apply { put("id", source.id); put("name", source.name); put("isCard", source.isCard) }) }; preferences.edit().putString(SOURCES, array.toString()).apply() }

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

    private companion object { const val CATEGORIES = "categories"; const val NOTES = "frequent_notes"; const val RECURRING = "recurring_expenses"; const val BUDGET = "monthly_budget"; const val SOURCES = "payment_sources" }
}

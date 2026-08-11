package jp.knaka.cardmemo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class TransactionRepository(context: Context) {
    private val preferences = context.getSharedPreferences("card_memo", Context.MODE_PRIVATE)

    fun load(): List<Transaction> = runCatching {
        val array = JSONArray(preferences.getString(KEY, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    Transaction(
                        id = item.getLong("id"),
                        amount = item.getInt("amount"),
                        category = item.getString("category"),
                        note = item.optString("note"),
                        usedAt = item.getLong("usedAt"),
                        confirmed = item.optBoolean("confirmed"),
                        recurringId = if (item.has("recurringId")) item.getLong("recurringId") else null,
                        paymentSourceId = item.optString("paymentSourceId", "rakuten"),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun save(items: List<Transaction>) {
        val array = JSONArray()
        items.forEach { transaction ->
            array.put(JSONObject().apply {
                put("id", transaction.id)
                put("amount", transaction.amount)
                put("category", transaction.category)
                put("note", transaction.note)
                put("usedAt", transaction.usedAt)
                put("confirmed", transaction.confirmed)
                transaction.recurringId?.let { put("recurringId", it) }
                put("paymentSourceId", transaction.paymentSourceId)
            })
        }
        preferences.edit().putString(KEY, array.toString()).apply()
    }

    private companion object { const val KEY = "transactions" }
}

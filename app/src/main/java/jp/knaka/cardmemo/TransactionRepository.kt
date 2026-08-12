package jp.knaka.cardmemo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import jp.knaka.cardmemo.storage.StorageBootstrapResult
import jp.knaka.cardmemo.storage.StorageProvider
import jp.knaka.cardmemo.storage.TransactionEntity

class TransactionRepository(context: Context) {
    private val preferences = context.getSharedPreferences("card_memo", Context.MODE_PRIVATE)
    private val room = (StorageProvider.get(context) as? StorageBootstrapResult.RoomReady)?.database

    fun load(): List<Transaction> = room?.transactions()?.loadAll()?.map { item ->
        Transaction(item.id, item.amount.toIntExact(), item.category, item.note, item.usedAt, item.confirmed, item.recurringId, item.paymentSourceId, item.reconciledMonth, item.suggested)
    } ?: runCatching {
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
                        reconciledMonth = item.optString("reconciledMonth").ifBlank { null },
                        suggested = item.optBoolean("suggested", false),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun save(items: List<Transaction>) {
        room?.let { database -> database.runInTransaction {
            database.transactions().deleteAll()
            database.transactions().upsertAll(items.map { TransactionEntity(it.id, it.amount.toLong(), it.category, it.note, it.usedAt, it.confirmed, it.recurringId, it.paymentSourceId, it.reconciledMonth, it.suggested) })
        }; return }
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
                transaction.reconciledMonth?.let { put("reconciledMonth", it) }
                put("suggested", transaction.suggested)
            })
        }
        preferences.edit().putString(KEY, array.toString()).apply()
    }

    private companion object { const val KEY = "transactions" }
}

private fun Long.toIntExact(): Int {
    require(this in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "金額が現行UIの範囲を超えています" }
    return toInt()
}

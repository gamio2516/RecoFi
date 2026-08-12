package jp.knaka.cardmemo

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class BackupCounts(
    val transactions: Int,
    val recurringExpenses: Int,
    val importedStatements: Int,
    val paymentSources: Int,
    val categories: Int,
)

data class ValidatedBackup(
    val createdAt: String,
    val appVersion: String,
    val counts: BackupCounts,
    val preferences: Map<String, Any>,
)

object BackupCodec {
    const val FORMAT_VERSION = 1

    fun encode(preferences: Map<String, *>, appVersion: String, createdAt: String = Instant.now().toString()): String {
        require(appVersion.isNotBlank())
        Instant.parse(createdAt)
        val safePreferences = normalizeLegacyState(preferences.mapValues { (_, value) -> copySupportedValue(value) })
        val counts = countAndValidate(safePreferences)
        return JSONObject().apply {
            put("backupFormatVersion", FORMAT_VERSION)
            put("createdAt", createdAt)
            put("appVersion", appVersion)
            put("counts", counts.toJson())
            put("preferences", preferencesToJson(safePreferences))
        }.toString(2)
    }

    fun decodeAndValidate(text: String): ValidatedBackup {
        val root = try { JSONObject(text) } catch (_: Exception) { throw IllegalArgumentException("バックアップファイルが破損しています") }
        requireField(root, "backupFormatVersion")
        val version = root.optInt("backupFormatVersion", -1)
        require(version == FORMAT_VERSION) { "非対応のバックアップ形式です（version=$version）" }
        requireField(root, "createdAt")
        requireField(root, "appVersion")
        requireField(root, "counts")
        requireField(root, "preferences")
        val createdAt = root.optString("createdAt")
        runCatching { Instant.parse(createdAt) }.getOrElse { throw IllegalArgumentException("バックアップ作成日時が不正です") }
        val appVersion = root.optString("appVersion")
        require(appVersion.isNotBlank()) { "アプリバージョンがありません" }
        val preferences = jsonToPreferences(root.getJSONObject("preferences"))
        val actualCounts = countAndValidate(preferences)
        val declaredCounts = countsFromJson(root.getJSONObject("counts"))
        require(actualCounts == declaredCounts) { "データ件数が一致しないため復元できません" }
        return ValidatedBackup(createdAt, appVersion, actualCounts, preferences)
    }

    private fun countAndValidate(preferences: Map<String, Any>): BackupCounts {
        fun array(key: String): JSONArray {
            val raw = preferences[key] as? String ?: return JSONArray()
            return try { JSONArray(raw) } catch (_: Exception) { throw IllegalArgumentException("$key の形式が不正です") }
        }
        val transactions = array("transactions")
        repeat(transactions.length()) { index ->
            val item = transactions.optJSONObject(index) ?: throw IllegalArgumentException("transactions[$index] が不正です")
            listOf("id", "amount", "category", "usedAt", "confirmed", "paymentSourceId", "suggested").forEach { requireField(item, it) }
            item.optString("reconciledMonth").takeIf { it.isNotBlank() }?.let { month ->
                runCatching { java.time.YearMonth.parse(month) }.getOrElse { throw IllegalArgumentException("transactions[$index].reconciledMonth が不正です") }
            }
        }
        val recurring = array("recurring_expenses")
        repeat(recurring.length()) { index ->
            val item = recurring.optJSONObject(index) ?: throw IllegalArgumentException("recurring_expenses[$index] が不正です")
            listOf("id", "amount", "category", "billingDay", "startMonth").forEach { requireField(item, it) }
        }
        val imported = array("imported_statements")
        repeat(imported.length()) { index ->
            val item = imported.optJSONObject(index) ?: throw IllegalArgumentException("imported_statements[$index] が不正です")
            listOf("statementMonth", "paymentSourceId", "fileName", "fileHash", "entries").forEach { requireField(item, it) }
            val entries = item.getJSONArray("entries")
            repeat(entries.length()) { rowIndex ->
                val row = entries.optJSONObject(rowIndex) ?: throw IllegalArgumentException("明細データが不正です")
                listOf("date", "amount", "merchant").forEach { requireField(row, it) }
            }
        }
        val sources = array("payment_sources")
        repeat(sources.length()) { index ->
            val item = sources.optJSONObject(index) ?: throw IllegalArgumentException("payment_sources[$index] が不正です")
            listOf("id", "name").forEach { requireField(item, it) }
        }
        val categories = array("categories")
        repeat(categories.length()) { index -> require(categories.optString(index).isNotBlank()) { "categories[$index] が不正です" } }
        preferences.filterKeys { it == "frequent_notes" || it.startsWith("frequent_notes_") }
            .forEach { (key, _) -> array(key) }
        array("locked_months").let { months -> repeat(months.length()) { index -> runCatching { java.time.YearMonth.parse(months.getString(index)) }.getOrElse { throw IllegalArgumentException("locked_months[$index] が不正です") } } }
        array("imported_file_hashes").let { hashes -> repeat(hashes.length()) { index -> require(hashes.getString(index).isNotBlank()) { "imported_file_hashes[$index] が不正です" } } }
        (preferences["monthly_budgets"] as? String)?.let { raw ->
            val budgets = runCatching { JSONObject(raw) }.getOrElse { throw IllegalArgumentException("monthly_budgets の形式が不正です") }
            budgets.keys().forEach { month -> runCatching { java.time.YearMonth.parse(month) }.getOrElse { throw IllegalArgumentException("monthly_budgets の月が不正です") }; require(budgets.getInt(month) >= 0) { "monthly_budgets の金額が不正です" } }
        }
        (preferences["reconciliation_progress"] as? String)?.let { raw ->
            val progress = runCatching { JSONObject(raw) }.getOrElse { throw IllegalArgumentException("reconciliation_progress の形式が不正です") }
            progress.keys().forEach { key ->
                require(Regex("\\d{4}-\\d{2}\\|.+").matches(key)) { "reconciliation_progress のキーが不正です" }
                val item = progress.optJSONObject(key) ?: throw IllegalArgumentException("reconciliation_progress[$key] が不正です")
                listOf("imported", "matched", "suggested", "confirmed").forEach { field -> requireField(item, field); require(item.getInt(field) >= 0) { "reconciliation_progress[$key].$field が不正です" } }
                require(item.getInt("matched") <= item.getInt("imported") && item.getInt("suggested") <= item.getInt("imported") && item.getInt("confirmed") <= item.getInt("imported")) { "reconciliation_progress[$key] の件数関係が不正です" }
            }
        }
        val sourceCount = if (preferences.containsKey("payment_sources")) sources.length() else DefaultPaymentSources.size
        val categoryCount = if (preferences.containsKey("categories")) categories.length() else DefaultCategories.size
        return BackupCounts(transactions.length(), recurring.length(), imported.length(), sourceCount, categoryCount)
    }

    private fun preferencesToJson(preferences: Map<String, Any>): JSONObject = JSONObject().apply {
        preferences.toSortedMap().forEach { (key, value) -> put(key, JSONObject().apply {
            when (value) {
                is String -> { put("type", "string"); put("value", value) }
                is Int -> { put("type", "int"); put("value", value) }
                is Long -> { put("type", "long"); put("value", value) }
                is Float -> { put("type", "float"); put("value", value.toDouble()) }
                is Boolean -> { put("type", "boolean"); put("value", value) }
                is Set<*> -> { put("type", "stringSet"); put("value", JSONArray(value.map { it as String }.sorted())) }
                else -> throw IllegalArgumentException("保存できない設定値です: $key")
            }
        }) }
    }

    private fun jsonToPreferences(json: JSONObject): Map<String, Any> = buildMap {
        json.keys().forEach { key ->
            val item = json.optJSONObject(key) ?: throw IllegalArgumentException("設定 $key の形式が不正です")
            requireField(item, "type"); requireField(item, "value")
            val value: Any = when (item.getString("type")) {
                "string" -> item.getString("value")
                "int" -> item.getInt("value")
                "long" -> item.getLong("value")
                "float" -> item.getDouble("value").toFloat()
                "boolean" -> item.getBoolean("value")
                "stringSet" -> item.getJSONArray("value").let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }
                else -> throw IllegalArgumentException("設定 $key の型に対応していません")
            }
            put(key, value)
        }
    }

    private fun copySupportedValue(value: Any?): Any = when (value) {
        is String, is Int, is Long, is Float, is Boolean -> value
        is Set<*> -> value.map { it as? String ?: throw IllegalArgumentException("文字列以外のSetは保存できません") }.toSet()
        else -> throw IllegalArgumentException("対応していない設定値があります")
    }

    private fun normalizeLegacyState(preferences: Map<String, Any>): Map<String, Any> {
        val normalized = preferences.toMutableMap()
        (normalized["transactions"] as? String)?.let { raw ->
            val array = JSONArray(raw)
            repeat(array.length()) { index -> array.getJSONObject(index).apply {
                if (!has("confirmed")) put("confirmed", false)
                if (!has("paymentSourceId")) put("paymentSourceId", "rakuten")
                if (!has("suggested")) put("suggested", false)
            } }
            normalized["transactions"] = array.toString()
        }
        (normalized["reconciliation_progress"] as? String)?.let { raw ->
            val progress = JSONObject(raw)
            progress.keys().forEach { key -> progress.getJSONObject(key).apply {
                if (!has("imported")) put("imported", optInt("total", 0))
                if (!has("matched")) put("matched", 0)
                if (!has("suggested")) put("suggested", 0)
                if (!has("confirmed")) put("confirmed", 0)
                remove("total")
            } }
            normalized["reconciliation_progress"] = progress.toString()
        }
        return normalized
    }

    private fun BackupCounts.toJson() = JSONObject().apply {
        put("transactions", transactions); put("recurringExpenses", recurringExpenses)
        put("importedStatements", importedStatements); put("paymentSources", paymentSources); put("categories", categories)
    }

    private fun countsFromJson(json: JSONObject): BackupCounts {
        listOf("transactions", "recurringExpenses", "importedStatements", "paymentSources", "categories").forEach { requireField(json, it) }
        return BackupCounts(json.getInt("transactions"), json.getInt("recurringExpenses"), json.getInt("importedStatements"), json.getInt("paymentSources"), json.getInt("categories")).also {
            require(listOf(it.transactions, it.recurringExpenses, it.importedStatements, it.paymentSources, it.categories).all { count -> count >= 0 }) { "データ件数が不正です" }
        }
    }

    private fun requireField(json: JSONObject, name: String) {
        require(json.has(name) && !json.isNull(name)) { "必須項目 $name がありません" }
    }
}

package jp.knaka.cardmemo.storage

import jp.knaka.cardmemo.DefaultCategories
import jp.knaka.cardmemo.DefaultFrequentNotes
import jp.knaka.cardmemo.DefaultPaymentSources
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth

data class LegacySnapshot(val values: Map<String, Any?>) {
    val fingerprint: String = MessageDigest.getInstance("SHA-256")
        .digest(values.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

data class LegacyRoomData(
    val transactions: List<TransactionEntity>,
    val paymentSources: List<PaymentSourceEntity>,
    val categories: List<CategoryEntity>,
    val notes: List<NoteTemplateEntity>,
    val recurringExpenses: List<RecurringExpenseEntity>,
    val priceRevisions: List<RecurringPriceRevisionEntity>,
    val importedStatements: List<ImportedStatementEntity>,
    val statementEntries: List<StatementEntryEntity>,
    val fingerprints: List<ImportedFingerprintEntity>,
    val reconciliationProgress: List<ReconciliationProgressEntity>,
    val monthlyLocks: List<MonthlyLockEntity>,
    val monthlyBudgets: List<MonthlyBudgetEntity>,
    val budgetSettings: AppBudgetSettingsEntity,
    val legacyFingerprint: String,
    val corrections: List<String>,
)

sealed interface LegacyParseResult {
    data class Success(val data: LegacyRoomData) : LegacyParseResult
    data class Failure(val errors: List<String>) : LegacyParseResult
}

/** Completely decodes and validates the retained SharedPreferences source without writing anywhere. */
class LegacyMigrationParser(private val nowMillis: Long = System.currentTimeMillis()) {
    fun parse(snapshot: LegacySnapshot): LegacyParseResult {
        val errors = mutableListOf<String>()
        val corrections = mutableListOf<String>()
        fun jsonArray(key: String, default: List<String> = emptyList()): JSONArray? = runCatching {
            val raw = snapshot.values[key] as? String
            if (raw == null) JSONArray(default) else JSONArray(raw)
        }.getOrElse { errors += "$key: JSON形式が不正です"; null }
        fun jsonObject(key: String): JSONObject? = runCatching {
            JSONObject((snapshot.values[key] as? String) ?: "{}")
        }.getOrElse { errors += "$key: JSON形式が不正です"; null }
        fun validMonth(value: String, label: String): String? = runCatching { YearMonth.parse(value).toString() }
            .getOrElse { errors += "$label: 年月が不正です ($value)"; null }
        fun validDate(value: String, label: String): String? = runCatching { LocalDate.parse(value).toString() }
            .getOrElse { errors += "$label: 日付が不正です ($value)"; null }
        fun nonNegativeLong(value: Long, label: String): Long? = if (value >= 0) value else { errors += "$label: 金額が負数です"; null }

        val sourceArray = jsonArray("payment_sources", DefaultPaymentSources.map { JSONObject().put("id", it.id).put("name", it.name).put("isCard", it.isCard).toString() })
        val sourceCandidates = mutableListOf<PaymentSourceEntity>()
        sourceArray?.let { array ->
            for (index in 0 until array.length()) runCatching {
                val item = if (array.opt(index) is String) JSONObject(array.getString(index)) else array.getJSONObject(index)
                val id = item.getString("id").trim(); val name = item.getString("name").trim()
                require(id.isNotEmpty() && name.isNotEmpty())
                sourceCandidates += PaymentSourceEntity(id, name, item.optBoolean("isCard", true), index)
            }.onFailure { errors += "payment_sources[$index]: 必須項目が不正です" }
        }
        val paymentSources = dedupeSources(sourceCandidates, errors, corrections)
        val sourceIds = paymentSources.map { it.id }.toSet()

        val categoryNames = mutableListOf<String>()
        jsonArray("categories", DefaultCategories)?.let { array ->
            for (index in 0 until array.length()) runCatching { array.getString(index).trim() }
                .onSuccess { if (it.isEmpty()) errors += "categories[$index]: 空のカテゴリです" else categoryNames += it }
                .onFailure { errors += "categories[$index]: 文字列ではありません" }
        }
        val categories = categoryNames.distinct().mapIndexed { index, name -> CategoryEntity(name, index) }
        if (categories.size != categoryNames.size) corrections += "完全一致する重複カテゴリを統合しました"
        if (categories.isEmpty()) errors += "categories: 1件以上必要です"
        val categorySet = categories.map { it.name }.toSet()

        val notes = mutableListOf<NoteTemplateEntity>()
        paymentSources.forEach { source ->
            val key = "frequent_notes_${source.id}"
            val fallback = if (source.id == "rakuten") {
                val raw = snapshot.values["frequent_notes"] as? String
                if (raw == null) DefaultFrequentNotes else emptyList()
            } else emptyList()
            val actualKey = if (snapshot.values.containsKey(key)) key else if (source.id == "rakuten") "frequent_notes" else key
            jsonArray(actualKey, fallback)?.let { array ->
                val seen = mutableSetOf<String>()
                for (index in 0 until array.length()) runCatching { array.getString(index).trim() }.onSuccess { note ->
                    if (note.isNotEmpty() && seen.add(note)) notes += NoteTemplateEntity(source.id, note, seen.size - 1)
                    else if (note.isNotEmpty()) corrections += "$actualKey: 完全一致する重複備考を統合しました"
                }.onFailure { errors += "$actualKey[$index]: 文字列ではありません" }
            }
        }

        val recurring = mutableListOf<RecurringExpenseEntity>()
        val revisions = mutableListOf<RecurringPriceRevisionEntity>()
        val recurringIds = mutableSetOf<Long>()
        jsonArray("recurring_expenses")?.let { array -> for (index in 0 until array.length()) runCatching {
            val item = array.getJSONObject(index); val id = item.getLong("id")
            if (!recurringIds.add(id)) { errors += "recurring_expenses: ID $id が重複しています"; return@runCatching }
            val category = item.getString("category"); val sourceId = item.optString("paymentSourceId", "rakuten")
            if (category !in categorySet) errors += "recurring_expenses[$index]: 存在しないカテゴリ $category"
            if (sourceId !in sourceIds) errors += "recurring_expenses[$index]: 存在しない支払方法 $sourceId"
            val startMonth = validMonth(item.getString("startMonth"), "recurring_expenses[$index].startMonth") ?: return@runCatching
            val contractDate = validDate(item.optString("contractDate", "$startMonth-01"), "recurring_expenses[$index].contractDate") ?: return@runCatching
            val endDate = item.optString("endDate").ifBlank { null }?.let { validDate(it, "recurring_expenses[$index].endDate") ?: return@runCatching }
            val billingDay = item.getInt("billingDay"); val interval = item.optInt("intervalMonths", 1)
            if (billingDay !in 1..31) errors += "recurring_expenses[$index]: 請求日が範囲外です"
            if (interval < 1) errors += "recurring_expenses[$index]: 支払間隔が不正です"
            val amount = nonNegativeLong(item.getLong("amount"), "recurring_expenses[$index].amount") ?: return@runCatching
            recurring += RecurringExpenseEntity(id, amount, category, item.optString("note"), billingDay, startMonth, contractDate, sourceId, interval, endDate)
            val revisionArray = item.optJSONArray("priceRevisions") ?: JSONArray(); val revisionDates = mutableSetOf<String>()
            for (revisionIndex in 0 until revisionArray.length()) {
                val revision = revisionArray.getJSONObject(revisionIndex)
                val date = validDate(revision.getString("effectiveDate"), "recurring_expenses[$index].priceRevisions[$revisionIndex]") ?: continue
                if (!revisionDates.add(date)) { errors += "recurring_expenses[$index]: 同じ改定日が重複しています"; continue }
                nonNegativeLong(revision.getLong("amount"), "priceRevisions[$revisionIndex].amount")?.let { revisions += RecurringPriceRevisionEntity(id, date, it) }
            }
        }.onFailure { errors += "recurring_expenses[$index]: 必須項目が不正です" } }

        val transactions = mutableListOf<TransactionEntity>(); val transactionIds = mutableSetOf<Long>()
        jsonArray("transactions")?.let { array -> for (index in 0 until array.length()) runCatching {
            val item = array.getJSONObject(index); val id = item.getLong("id")
            if (!transactionIds.add(id)) { errors += "transactions: ID $id が重複しています"; return@runCatching }
            val category = item.getString("category"); val sourceId = item.optString("paymentSourceId", "rakuten")
            val recurringId = if (item.has("recurringId")) item.getLong("recurringId") else null
            if (category !in categorySet) errors += "transactions[$index]: 存在しないカテゴリ $category"
            if (sourceId !in sourceIds) errors += "transactions[$index]: 存在しない支払方法 $sourceId"
            if (recurringId != null && recurringId !in recurringIds) errors += "transactions[$index]: 存在しない固定費 $recurringId"
            val reconciled = item.optString("reconciledMonth").ifBlank { null }?.let { validMonth(it, "transactions[$index].reconciledMonth") ?: return@runCatching }
            val amount = nonNegativeLong(item.getLong("amount"), "transactions[$index].amount") ?: return@runCatching
            transactions += TransactionEntity(id, amount, category, item.optString("note"), item.getLong("usedAt"), item.optBoolean("confirmed"), recurringId, sourceId, reconciled, item.optBoolean("suggested"))
        }.onFailure { errors += "transactions[$index]: 必須項目が不正です" } }

        val statementEntities = mutableListOf<ImportedStatementEntity>(); val entryEntities = mutableListOf<StatementEntryEntity>(); val statementHashes = mutableSetOf<String>()
        jsonArray("imported_statements")?.let { array -> for (index in 0 until array.length()) runCatching {
            val item = array.getJSONObject(index); val hash = item.getString("fileHash").trim()
            if (hash.isEmpty() || !statementHashes.add(hash)) { errors += "imported_statements[$index]: fingerprintが空または重複です"; return@runCatching }
            val rawMonth = item.optString("statementMonth").ifBlank { null }; val rawSource = item.optString("paymentSourceId").ifBlank { null }
            val month = rawMonth?.let { validMonth(it, "imported_statements[$index].statementMonth") ?: return@runCatching }
            if (rawSource != null && rawSource !in sourceIds) errors += "imported_statements[$index]: 存在しない支払方法 $rawSource"
            statementEntities += ImportedStatementEntity(hash, month, rawSource, item.getString("fileName"))
            val rows = item.getJSONArray("entries")
            for (row in 0 until rows.length()) {
                val entry = rows.getJSONObject(row); val date = validDate(entry.getString("date"), "imported_statements[$index].entries[$row]") ?: continue
                val amount = nonNegativeLong(entry.getLong("amount"), "statement entry amount") ?: continue
                entryEntities += StatementEntryEntity(fileHash = hash, rowOrder = row, date = date, amount = amount, merchant = entry.getString("merchant"), rawText = entry.optString("rawText"))
            }
        }.onFailure { errors += "imported_statements[$index]: 必須項目が不正です" } }

        val fingerprints = mutableListOf<ImportedFingerprintEntity>()
        jsonArray("imported_file_hashes")?.let { array -> val seen = mutableSetOf<String>(); for (index in 0 until array.length()) runCatching { array.getString(index) }.onSuccess { hash -> if (hash.isNotBlank() && seen.add(hash)) fingerprints += ImportedFingerprintEntity(hash, nowMillis) else corrections += "imported_file_hashes: 重複値を統合しました" }.onFailure { errors += "imported_file_hashes[$index]: 文字列ではありません" } }

        val progress = mutableListOf<ReconciliationProgressEntity>()
        jsonObject("reconciliation_progress")?.let { json -> json.keys().forEach { key -> runCatching {
            val separator = key.lastIndexOf('|'); require(separator > 0)
            val month = validMonth(key.substring(0, separator), "reconciliation_progress.$key") ?: return@runCatching
            val sourceId = key.substring(separator + 1); if (sourceId !in sourceIds) errors += "reconciliation_progress: 存在しない支払方法 $sourceId"
            val item = json.getJSONObject(key); val counts = listOf(item.optInt("imported", item.optInt("total", 0)), item.optInt("matched"), item.optInt("suggested"), item.optInt("confirmed"))
            if (counts.any { it < 0 } || counts.drop(1).any { it > counts[0] }) errors += "reconciliation_progress.$key: 件数が不整合です"
            progress += ReconciliationProgressEntity(month, sourceId, counts[0], counts[1], counts[2], counts[3])
        }.onFailure { errors += "reconciliation_progress.$key: 形式が不正です" } } }

        val locks = mutableListOf<MonthlyLockEntity>()
        jsonArray("locked_months")?.let { array -> for (index in 0 until array.length()) runCatching { array.getString(index) }.onSuccess { validMonth(it, "locked_months[$index]")?.let { month -> locks += MonthlyLockEntity(month, nowMillis) } }.onFailure { errors += "locked_months[$index]: 文字列ではありません" } }

        val budgets = mutableListOf<MonthlyBudgetEntity>()
        val budgetJson = jsonObject("monthly_budgets")
        budgetJson?.keys()?.forEach { monthRaw -> validMonth(monthRaw, "monthly_budgets.$monthRaw")?.let { month -> nonNegativeLong(budgetJson.getLong(monthRaw), "monthly_budgets.$monthRaw")?.let { if (it > 0) budgets += MonthlyBudgetEntity(month, it) } } }
        if (!snapshot.values.containsKey("monthly_budgets")) {
            val legacy = (snapshot.values["monthly_budget"] as? Number)?.toLong() ?: 0L
            if (legacy > 0) errors += "monthly_budget: 対象月を特定できないため自動移行できません"
        }
        val defaultBudget = ((snapshot.values["default_monthly_budget"] as? Number)?.toLong() ?: 0L).let { nonNegativeLong(it, "default_monthly_budget") ?: 0L }

        return if (errors.isNotEmpty()) LegacyParseResult.Failure(errors.distinct()) else LegacyParseResult.Success(
            LegacyRoomData(transactions, paymentSources, categories, notes, recurring, revisions, statementEntities, entryEntities, fingerprints, progress, locks.distinctBy { it.month }, budgets.distinctBy { it.month }, AppBudgetSettingsEntity(defaultMonthlyBudget = defaultBudget), snapshot.fingerprint, corrections.distinct())
        )
    }

    private fun dedupeSources(items: List<PaymentSourceEntity>, errors: MutableList<String>, corrections: MutableList<String>): List<PaymentSourceEntity> {
        val byId = linkedMapOf<String, PaymentSourceEntity>(); val names = mutableMapOf<String, String>()
        items.forEach { item ->
            val existing = byId[item.id]
            when {
                existing == null && names[item.name] == null -> { byId[item.id] = item.copy(sortOrder = byId.size); names[item.name] = item.id }
                existing != null && existing.name == item.name && existing.isCard == item.isCard -> corrections += "完全一致する重複支払方法 ${item.id} を統合しました"
                existing != null -> errors += "payment_sources: 同じID ${item.id} に異なる内容があります"
                else -> errors += "payment_sources: 同じ名称 ${item.name} に異なるIDがあります"
            }
        }
        return byId.values.toList()
    }
}

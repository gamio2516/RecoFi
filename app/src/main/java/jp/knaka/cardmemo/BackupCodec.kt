package jp.knaka.cardmemo

import jp.knaka.cardmemo.storage.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

data class BackupCounts(val transactions: Int, val recurringExpenses: Int, val importedStatements: Int, val paymentSources: Int, val categories: Int)
data class ValidatedBackup(val createdAt: String, val appVersion: String, val counts: BackupCounts, val snapshot: RoomSnapshot)

object BackupCodec {
    const val FORMAT_VERSION = 2

    fun encode(db: RecoFiDatabase, appVersion: String, createdAt: String = Instant.now().toString()): String {
        require(appVersion.isNotBlank()) { "アプリバージョンがありません" }
        Instant.parse(createdAt)
        val data = RoomBackupAdapter.exportJson(db)
        val counts = counts(data)
        return JSONObject()
            .put("backupFormatVersion", FORMAT_VERSION)
            .put("createdAt", createdAt)
            .put("appVersion", appVersion)
            .put("counts", counts.toJson())
            .put("data", data)
            .toString(2)
    }

    fun decodeAndValidate(text: String): ValidatedBackup = try {
        val root = JSONObject(text)
        require(root.getInt("backupFormatVersion") == FORMAT_VERSION) { "非対応のバックアップ形式です" }
        val createdAt = root.getString("createdAt").also(Instant::parse)
        val appVersion = root.getString("appVersion").also { require(it.isNotBlank()) { "アプリバージョンがありません" } }
        val data = root.getJSONObject("data")
        val snapshot = parseSnapshot(data)
        val actual = counts(data)
        require(actual == root.getJSONObject("counts").toCounts()) { "データ件数が一致しません" }
        ValidatedBackup(createdAt, appVersion, actual, snapshot)
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (error: Exception) {
        throw IllegalArgumentException("バックアップファイルが破損しているか、必須項目が不足しています", error)
    }

    private fun parseSnapshot(data: JSONObject): RoomSnapshot {
        val sources = data.getJSONArray("paymentSources").objects { item ->
            PaymentSourceEntity(item.requiredText("id"), item.requiredText("name"), item.getBoolean("isCard"), item.getInt("sortOrder"))
        }.also { requireUnique(it.map(PaymentSourceEntity::id), "支払方法ID") }
        val categories = data.getJSONArray("categories").objects { item ->
            CategoryEntity(item.requiredText("name"), item.getInt("sortOrder"))
        }.also { requireUnique(it.map(CategoryEntity::name), "カテゴリ") }
        val merchants = data.getJSONArray("merchantTemplates").objects { item ->
            MerchantTemplateEntity(item.requiredText("value"), item.getInt("sortOrder"))
        }.also { requireUnique(it.map(MerchantTemplateEntity::value), "取引先テンプレート") }
        val descriptions = data.getJSONArray("descriptionTemplates").objects { item ->
            DescriptionTemplateEntity(item.requiredText("value"), item.getInt("sortOrder"))
        }.also { requireUnique(it.map(DescriptionTemplateEntity::value), "内容テンプレート") }

        val sourceIds = sources.map(PaymentSourceEntity::id).toSet()
        val categoryNames = categories.map(CategoryEntity::name).toSet()
        val recurringEntities = mutableListOf<RecurringExpenseEntity>()
        val revisions = mutableListOf<RecurringPriceRevisionEntity>()
        data.getJSONArray("recurringExpenses").forEachObject { item ->
            val id = item.getLong("id")
            val category = item.requiredText("category").also { require(it in categoryNames) { "固定費のカテゴリ参照が不正です" } }
            val sourceId = item.requiredText("paymentSourceId").also { require(it in sourceIds) { "固定費の支払方法参照が不正です" } }
            val startMonth = item.requiredText("startMonth").also { YearMonth.parse(it) }
            val contractDate = item.requiredText("contractDate").also { LocalDate.parse(it) }
            val billingDay = item.getInt("billingDay").also { require(it in 1..31) { "請求日が不正です" } }
            val interval = item.getInt("intervalMonths").also { require(it > 0) { "支払間隔が不正です" } }
            val amount = item.getLong("amount").also(::requireNonNegative)
            val endDate = item.optionalText("endDate")?.also { LocalDate.parse(it) }
            recurringEntities += RecurringExpenseEntity(id, amount, category, item.getString("merchant"), item.getString("description"), billingDay, startMonth, contractDate, sourceId, interval, endDate)
            item.getJSONArray("priceRevisions").forEachObject { revision ->
                val date = revision.requiredText("effectiveDate").also { LocalDate.parse(it) }
                val revisedAmount = revision.getLong("amount").also(::requireNonNegative)
                revisions += RecurringPriceRevisionEntity(id, date, revisedAmount)
            }
        }
        requireUnique(recurringEntities.map(RecurringExpenseEntity::id), "固定費ID")
        requireUnique(revisions.map { it.recurringId to it.effectiveDate }, "料金改定")

        val recurringIds = recurringEntities.map(RecurringExpenseEntity::id).toSet()
        val transactions = data.getJSONArray("transactions").objects { item ->
            val category = item.requiredText("category").also { require(it in categoryNames) { "明細のカテゴリ参照が不正です" } }
            val sourceId = item.requiredText("paymentSourceId").also { require(it in sourceIds) { "明細の支払方法参照が不正です" } }
            val recurringId = item.optionalLong("recurringId").also { require(it == null || it in recurringIds) { "明細の固定費参照が不正です" } }
            val reconciledMonth = item.optionalText("reconciledMonth")?.also { YearMonth.parse(it) }
            TransactionEntity(item.getLong("id"), item.getLong("amount").also(::requireNonNegative), category, item.getString("merchant"), item.getString("description"), item.getLong("usedAt"), item.getBoolean("confirmed"), recurringId, sourceId, reconciledMonth, item.getBoolean("suggested"))
        }.also { requireUnique(it.map(TransactionEntity::id), "明細ID") }

        val statements = mutableListOf<ImportedStatementEntity>()
        val entries = mutableListOf<StatementEntryEntity>()
        data.getJSONArray("importedStatements").forEachObject { item ->
            val hash = item.requiredText("fileHash")
            val statementMonth = item.optionalText("statementMonth")?.also { YearMonth.parse(it) }
            val sourceId = item.optionalText("paymentSourceId").also { require(it == null || it in sourceIds) { "取込ファイルの支払方法参照が不正です" } }
            statements += ImportedStatementEntity(hash, statementMonth, sourceId, item.requiredText("fileName"))
            item.getJSONArray("entries").forEachObject { entry ->
                entries += StatementEntryEntity(fileHash = hash, rowOrder = entry.getInt("rowOrder"), date = entry.requiredText("date").also { LocalDate.parse(it) }, amount = entry.getLong("amount").also(::requireNonNegative), merchant = entry.getString("merchant"), rawText = entry.getString("rawText"))
            }
        }
        requireUnique(statements.map(ImportedStatementEntity::fileHash), "取込ファイルhash")
        requireUnique(statements.mapNotNull { statement -> statement.statementMonth?.let { month -> statement.paymentSourceId?.let { month to it } } }, "取込先")
        requireUnique(entries.map { it.fileHash to it.rowOrder }, "取込明細行")

        val fingerprints = data.getJSONArray("fingerprints").strings().also { requireUnique(it, "fingerprint") }.map { ImportedFingerprintEntity(it, 0L) }
        val progress = data.getJSONArray("progress").objects { item ->
            val month = item.requiredText("month").also { YearMonth.parse(it) }
            val sourceId = item.requiredText("sourceId").also { require(it in sourceIds) { "消込状態の支払方法参照が不正です" } }
            val imported = item.getInt("imported")
            val matched = item.getInt("matched")
            val suggested = item.getInt("suggested")
            val confirmed = item.getInt("confirmed")
            require(imported >= 0 && matched >= 0 && suggested >= 0 && confirmed >= 0 && matched + suggested <= imported && confirmed <= imported) { "消込件数が不正です" }
            ReconciliationProgressEntity(month, sourceId, imported, matched, suggested, confirmed)
        }.also { requireUnique(it.map { row -> row.statementMonth to row.paymentSourceId }, "消込状態") }
        val locks = data.getJSONArray("locks").strings().onEach { YearMonth.parse(it) }.also { requireUnique(it, "月次ロック") }.map { MonthlyLockEntity(it, 0L) }

        val budgetsObject = data.getJSONObject("budgets")
        val budgets = budgetsObject.keys().asSequence().map { month ->
            YearMonth.parse(month)
            MonthlyBudgetEntity(month, budgetsObject.getLong(month).also(::requireNonNegative))
        }.toList()
        val defaultBudget = data.getLong("defaultMonthlyBudget").also(::requireNonNegative)
        return RoomSnapshot(transactions, sources, categories, merchants, descriptions, recurringEntities, revisions, statements, entries, fingerprints, progress, locks, budgets, AppBudgetSettingsEntity(defaultMonthlyBudget = defaultBudget))
    }

    private fun counts(data: JSONObject) = BackupCounts(data.getJSONArray("transactions").length(), data.getJSONArray("recurringExpenses").length(), data.getJSONArray("importedStatements").length(), data.getJSONArray("paymentSources").length(), data.getJSONArray("categories").length())
    private fun BackupCounts.toJson() = JSONObject().put("transactions", transactions).put("recurringExpenses", recurringExpenses).put("importedStatements", importedStatements).put("paymentSources", paymentSources).put("categories", categories)
    private fun JSONObject.toCounts() = BackupCounts(getInt("transactions"), getInt("recurringExpenses"), getInt("importedStatements"), getInt("paymentSources"), getInt("categories"))
    private fun JSONObject.requiredText(name: String) = getString(name).also { require(it.isNotBlank()) { "$name が空です" } }
    private fun JSONObject.optionalText(name: String): String? = if (!has(name) || isNull(name)) null else getString(name).ifBlank { null }
    private fun JSONObject.optionalLong(name: String): Long? = if (!has(name) || isNull(name)) null else getLong(name)
    private inline fun <T> JSONArray.objects(read: (JSONObject) -> T): List<T> = List(length()) { read(getJSONObject(it)) }
    private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) { repeat(length()) { block(getJSONObject(it)) } }
    private fun JSONArray.strings() = List(length()) { getString(it) }
    private fun requireNonNegative(value: Long) { require(value >= 0L) { "金額が不正です" } }
    private fun <T> requireUnique(values: List<T>, label: String) { require(values.size == values.toSet().size) { "$label が重複しています" } }
}


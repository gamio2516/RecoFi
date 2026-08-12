package jp.knaka.cardmemo.storage

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

object RoomBackupAdapter {
    fun exportPreferences(db: RecoFiDatabase): Map<String, Any> {
        val sources = db.referenceData().loadPaymentSources()
        val categories = db.referenceData().loadCategories()
        val notes = db.referenceData().loadNotes().groupBy { it.paymentSourceId }
        val recurring = db.recurringExpenses().loadAll()
        val revisions = db.recurringExpenses().loadRevisions().groupBy { it.recurringId }
        val statements = db.statements().loadStatements()
        val entries = db.statements().loadEntries().groupBy { it.fileHash }
        return buildMap {
            put("transactions", JSONArray().apply { db.transactions().loadAll().forEach { item -> put(JSONObject().apply {
                put("id", item.id); put("amount", item.amount); put("category", item.category); put("note", item.note); put("usedAt", item.usedAt)
                put("confirmed", item.confirmed); item.recurringId?.let { put("recurringId", it) }; put("paymentSourceId", item.paymentSourceId)
                item.reconciledMonth?.let { put("reconciledMonth", it) }; put("suggested", item.suggested)
            }) } }.toString())
            put("categories", JSONArray(categories.map { it.name }).toString())
            put("payment_sources", JSONArray().apply { sources.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("isCard", it.isCard)) } }.toString())
            sources.forEach { source -> put("frequent_notes_${source.id}", JSONArray(notes[source.id].orEmpty().sortedBy { it.sortOrder }.map { it.note }).toString()) }
            put("recurring_expenses", JSONArray().apply { recurring.forEach { item -> put(JSONObject().apply {
                put("id", item.id); put("amount", item.amount); put("category", item.category); put("note", item.note); put("billingDay", item.billingDay)
                put("startMonth", item.startMonth); put("contractDate", item.contractDate); put("paymentSourceId", item.paymentSourceId); put("intervalMonths", item.intervalMonths)
                item.endDate?.let { put("endDate", it) }; put("priceRevisions", JSONArray().apply { revisions[item.id].orEmpty().forEach { put(JSONObject().put("effectiveDate", it.effectiveDate).put("amount", it.amount)) } })
            }) } }.toString())
            put("monthly_budgets", JSONObject().apply { db.monthlyState().loadBudgets().forEach { put(it.month, it.amount) } }.toString())
            put("default_monthly_budget", db.monthlyState().loadBudgetSettings()?.defaultMonthlyBudget ?: 0L)
            put("locked_months", JSONArray(db.monthlyState().loadLocks().map { it.month }).toString())
            put("imported_file_hashes", JSONArray(db.statements().loadFingerprints().map { it.fingerprint }).toString())
            put("reconciliation_progress", JSONObject().apply { db.monthlyState().loadProgress().forEach { item -> put("${item.statementMonth}|${item.paymentSourceId}", JSONObject().put("imported", item.imported).put("matched", item.matched).put("suggested", item.suggested).put("confirmed", item.confirmed)) } }.toString())
            put("imported_statements", JSONArray().apply { statements.forEach { statement -> put(JSONObject().apply {
                put("statementMonth", statement.statementMonth.orEmpty()); put("paymentSourceId", statement.paymentSourceId.orEmpty()); put("fileName", statement.fileName); put("fileHash", statement.fileHash)
                put("entries", JSONArray().apply { entries[statement.fileHash].orEmpty().sortedBy { it.rowOrder }.forEach { put(JSONObject().put("date", it.date).put("amount", it.amount).put("merchant", it.merchant).put("rawText", it.rawText)) } })
            }) } }.toString())
        }
    }

    suspend fun replace(db: RecoFiDatabase, data: LegacyRoomData, beforeCommitCheck: () -> Unit = {}) {
        db.withTransaction {
            db.transactions().deleteAll()
            db.statements().deleteEntries(); db.statements().deleteStatements(); db.statements().deleteFingerprints()
            db.monthlyState().deleteProgress(); db.monthlyState().deleteLocks(); db.monthlyState().deleteBudgets(); db.monthlyState().deleteBudgetSettings()
            db.referenceData().deleteNotes(); db.recurringExpenses().deleteRevisions(); db.recurringExpenses().deleteAll()
            db.referenceData().deleteCategories(); db.referenceData().deletePaymentSources()
            db.referenceData().upsertPaymentSources(data.paymentSources); db.referenceData().upsertCategories(data.categories); db.referenceData().upsertNotes(data.notes)
            db.recurringExpenses().upsertAll(data.recurringExpenses); db.recurringExpenses().upsertRevisions(data.priceRevisions); db.transactions().upsertAll(data.transactions)
            db.statements().upsertStatements(data.importedStatements); db.statements().upsertEntries(data.statementEntries); db.statements().upsertFingerprints(data.fingerprints)
            db.monthlyState().upsertProgress(data.reconciliationProgress); db.monthlyState().upsertLocks(data.monthlyLocks); db.monthlyState().upsertBudgets(data.monthlyBudgets); db.monthlyState().upsertBudgetSettings(data.budgetSettings)
            require(db.transactions().loadAll().size == data.transactions.size && db.transactions().loadAll().sumOf { it.amount } == data.transactions.sumOf { it.amount })
            require(db.statements().loadEntries().size == data.statementEntries.size)
            require(db.referenceData().loadCategories().size == data.categories.size && db.referenceData().loadPaymentSources().size == data.paymentSources.size)
            beforeCommitCheck()
        }
    }
}

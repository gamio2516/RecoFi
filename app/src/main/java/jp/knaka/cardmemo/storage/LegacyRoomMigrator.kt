package jp.knaka.cardmemo.storage

import androidx.room.withTransaction

sealed interface StorageMigrationResult {
    data object Migrated : StorageMigrationResult
    data object AlreadyMigrated : StorageMigrationResult
    data class Failed(val reason: String) : StorageMigrationResult
}

class LegacyRoomMigrator(private val clock: () -> Long = System::currentTimeMillis) {
    suspend fun migrate(
        database: RecoFiDatabase,
        data: LegacyRoomData,
        beforeCommitCheck: () -> Unit = {},
    ): StorageMigrationResult = runCatching {
        val prior = database.migrations().find(MIGRATION_VERSION)
        if (prior != null) {
            require(prior.legacyFingerprint == data.legacyFingerprint) { "別のLegacy原本による移行が既に完了しています" }
            return StorageMigrationResult.AlreadyMigrated
        }
        database.withTransaction {
            require(isEmpty(database)) { "移行先Room DBが空ではありません" }
            database.referenceData().upsertPaymentSources(data.paymentSources)
            database.referenceData().upsertCategories(data.categories)
            database.referenceData().upsertNotes(data.notes)
            database.recurringExpenses().upsertAll(data.recurringExpenses)
            database.recurringExpenses().upsertRevisions(data.priceRevisions)
            database.transactions().upsertAll(data.transactions)
            database.statements().upsertStatements(data.importedStatements)
            database.statements().upsertEntries(data.statementEntries)
            database.statements().upsertFingerprints(data.fingerprints)
            database.monthlyState().upsertProgress(data.reconciliationProgress)
            database.monthlyState().upsertLocks(data.monthlyLocks)
            database.monthlyState().upsertBudgets(data.monthlyBudgets)
            database.monthlyState().upsertBudgetSettings(data.budgetSettings)
            verify(database, data)
            beforeCommitCheck()
            database.migrations().upsert(StorageMigrationEntity(MIGRATION_VERSION, data.legacyFingerprint, clock(), sourceRetained = true))
        }
        StorageMigrationResult.Migrated
    }.getOrElse { StorageMigrationResult.Failed(it.message ?: it::class.java.simpleName) }

    private fun isEmpty(db: RecoFiDatabase): Boolean = db.transactions().loadAll().isEmpty() &&
        db.referenceData().loadPaymentSources().isEmpty() && db.referenceData().loadCategories().isEmpty() &&
        db.recurringExpenses().loadAll().isEmpty() && db.statements().loadStatements().isEmpty() &&
        db.monthlyState().loadLocks().isEmpty() && db.monthlyState().loadBudgets().isEmpty()

    private fun verify(db: RecoFiDatabase, expected: LegacyRoomData) {
        require(db.transactions().loadAll().size == expected.transactions.size)
        require(db.transactions().loadAll().sumOf { it.amount } == expected.transactions.sumOf { it.amount })
        require(db.referenceData().loadPaymentSources().size == expected.paymentSources.size)
        require(db.referenceData().loadCategories().size == expected.categories.size)
        require(db.referenceData().loadNotes().size == expected.notes.size)
        require(db.recurringExpenses().loadAll().size == expected.recurringExpenses.size)
        require(db.recurringExpenses().loadRevisions().size == expected.priceRevisions.size)
        require(db.statements().loadStatements().size == expected.importedStatements.size)
        require(db.statements().loadEntries().size == expected.statementEntries.size)
        require(db.statements().loadFingerprints().size == expected.fingerprints.size)
        require(db.monthlyState().loadProgress().size == expected.reconciliationProgress.size)
        require(db.monthlyState().loadLocks().size == expected.monthlyLocks.size)
        require(db.monthlyState().loadBudgets().size == expected.monthlyBudgets.size)
        require(db.monthlyState().loadBudgetSettings() == expected.budgetSettings)
    }

    companion object { const val MIGRATION_VERSION = 1 }
}

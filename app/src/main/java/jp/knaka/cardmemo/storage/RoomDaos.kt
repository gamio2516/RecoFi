package jp.knaka.cardmemo.storage

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY usedAt DESC") fun loadAll(): List<TransactionEntity>
    @Upsert fun upsertAll(items: List<TransactionEntity>)
    @Query("DELETE FROM transactions") fun deleteAll()
}

@Dao interface ReferenceDataDao {
    @Query("SELECT * FROM payment_sources ORDER BY sortOrder") fun loadPaymentSources(): List<PaymentSourceEntity>
    @Query("SELECT * FROM categories ORDER BY sortOrder") fun loadCategories(): List<CategoryEntity>
    @Query("SELECT * FROM note_templates ORDER BY paymentSourceId, sortOrder") fun loadNotes(): List<NoteTemplateEntity>
    @Upsert fun upsertPaymentSources(items: List<PaymentSourceEntity>)
    @Upsert fun upsertCategories(items: List<CategoryEntity>)
    @Upsert fun upsertNotes(items: List<NoteTemplateEntity>)
    @Query("DELETE FROM note_templates") fun deleteNotes()
    @Query("DELETE FROM categories") fun deleteCategories()
    @Query("DELETE FROM payment_sources") fun deletePaymentSources()
    @Query("DELETE FROM categories WHERE name = :name") fun deleteCategory(name: String)
    @Query("DELETE FROM payment_sources WHERE id = :id") fun deletePaymentSource(id: String)
}

@Dao interface RecurringExpenseDao {
    @Query("SELECT * FROM recurring_expenses ORDER BY id") fun loadAll(): List<RecurringExpenseEntity>
    @Query("SELECT * FROM recurring_price_revisions ORDER BY recurringId, effectiveDate") fun loadRevisions(): List<RecurringPriceRevisionEntity>
    @Upsert fun upsertAll(items: List<RecurringExpenseEntity>)
    @Upsert fun upsertRevisions(items: List<RecurringPriceRevisionEntity>)
    @Query("DELETE FROM recurring_price_revisions") fun deleteRevisions()
    @Query("DELETE FROM recurring_expenses") fun deleteAll()
    @Query("DELETE FROM recurring_expenses WHERE id = :id") fun delete(id: Long)
}

@Dao interface StatementDao {
    @Query("SELECT * FROM imported_statements ORDER BY statementMonth, paymentSourceId") fun loadStatements(): List<ImportedStatementEntity>
    @Query("SELECT * FROM statement_entries ORDER BY fileHash, rowOrder") fun loadEntries(): List<StatementEntryEntity>
    @Query("SELECT * FROM imported_fingerprints") fun loadFingerprints(): List<ImportedFingerprintEntity>
    @Upsert fun upsertStatements(items: List<ImportedStatementEntity>)
    @Upsert fun upsertEntries(items: List<StatementEntryEntity>)
    @Upsert fun upsertFingerprints(items: List<ImportedFingerprintEntity>)
    @Query("DELETE FROM statement_entries") fun deleteEntries()
    @Query("DELETE FROM imported_statements") fun deleteStatements()
    @Query("DELETE FROM imported_fingerprints") fun deleteFingerprints()
}

@Dao interface MonthlyStateDao {
    @Query("SELECT * FROM reconciliation_progress") fun loadProgress(): List<ReconciliationProgressEntity>
    @Query("SELECT * FROM monthly_locks") fun loadLocks(): List<MonthlyLockEntity>
    @Query("SELECT * FROM monthly_budgets") fun loadBudgets(): List<MonthlyBudgetEntity>
    @Query("SELECT * FROM app_budget_settings WHERE id = 1") fun loadBudgetSettings(): AppBudgetSettingsEntity?
    @Upsert fun upsertProgress(items: List<ReconciliationProgressEntity>)
    @Upsert fun upsertLocks(items: List<MonthlyLockEntity>)
    @Upsert fun upsertBudgets(items: List<MonthlyBudgetEntity>)
    @Upsert fun upsertBudgetSettings(item: AppBudgetSettingsEntity)
    @Query("DELETE FROM reconciliation_progress") fun deleteProgress()
    @Query("DELETE FROM monthly_locks") fun deleteLocks()
    @Query("DELETE FROM monthly_budgets") fun deleteBudgets()
    @Query("DELETE FROM app_budget_settings") fun deleteBudgetSettings()
}

@Dao interface StorageMigrationDao {
    @Query("SELECT * FROM storage_migrations WHERE migrationVersion = :version") fun find(version: Int): StorageMigrationEntity?
    @Upsert fun upsert(item: StorageMigrationEntity)
}

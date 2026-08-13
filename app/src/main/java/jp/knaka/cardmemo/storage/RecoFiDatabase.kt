package jp.knaka.cardmemo.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PaymentSourceEntity::class, CategoryEntity::class, MerchantTemplateEntity::class, DescriptionTemplateEntity::class,
        RecurringExpenseEntity::class, RecurringPriceRevisionEntity::class, TransactionEntity::class,
        ImportedStatementEntity::class, StatementEntryEntity::class, ImportedFingerprintEntity::class,
        ReconciliationMatchEntity::class, RejectedReconciliationCandidateEntity::class, MonthlyPaymentSourceDeclarationEntity::class,
        MonthlyLockEntity::class, MonthlyBudgetEntity::class,
        AppBudgetSettingsEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class RecoFiDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun referenceData(): ReferenceDataDao
    abstract fun recurringExpenses(): RecurringExpenseDao
    abstract fun statements(): StatementDao
    abstract fun monthlyState(): MonthlyStateDao
    abstract fun reconciliation(): ReconciliationDao

    companion object {
        const val DATABASE_NAME = "recofi.db"
        val MIGRATION_2_3 = object:Migration(2,3){override fun migrate(db:SupportSQLiteDatabase){
            db.execSQL("ALTER TABLE payment_sources ADD COLUMN type TEXT NOT NULL DEFAULT 'OTHER'")
            db.execSQL("UPDATE payment_sources SET type=CASE WHEN isCard=1 THEN 'CREDIT_CARD' ELSE 'OTHER' END")
            db.execSQL("CREATE TABLE transactions_new (id INTEGER NOT NULL PRIMARY KEY, amount INTEGER NOT NULL, category TEXT NOT NULL, merchant TEXT NOT NULL, description TEXT NOT NULL, usedAt INTEGER NOT NULL, recurringId INTEGER, paymentSourceId TEXT NOT NULL, FOREIGN KEY(category) REFERENCES categories(name) ON DELETE RESTRICT, FOREIGN KEY(paymentSourceId) REFERENCES payment_sources(id) ON DELETE RESTRICT, FOREIGN KEY(recurringId) REFERENCES recurring_expenses(id) ON DELETE SET NULL)")
            db.execSQL("INSERT INTO transactions_new(id,amount,category,merchant,description,usedAt,recurringId,paymentSourceId) SELECT id,amount,category,merchant,description,usedAt,recurringId,paymentSourceId FROM transactions")
            db.execSQL("DROP TABLE transactions");db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
            db.execSQL("CREATE INDEX index_transactions_category ON transactions(category)");db.execSQL("CREATE INDEX index_transactions_paymentSourceId ON transactions(paymentSourceId)");db.execSQL("CREATE INDEX index_transactions_recurringId ON transactions(recurringId)");db.execSQL("CREATE INDEX index_transactions_usedAt ON transactions(usedAt)")
            db.execSQL("DROP TABLE reconciliation_progress")
            db.execSQL("CREATE UNIQUE INDEX index_statement_entries_fileHash_rowOrder ON statement_entries(fileHash,rowOrder)")
            db.execSQL("CREATE TABLE reconciliation_matches (statementEntryId INTEGER NOT NULL PRIMARY KEY, transactionId INTEGER, status TEXT NOT NULL, matchSource TEXT, confidence TEXT, score INTEGER, reasonCode TEXT, dayDifference INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, confirmedAt INTEGER, FOREIGN KEY(statementEntryId) REFERENCES statement_entries(id) ON DELETE CASCADE, FOREIGN KEY(transactionId) REFERENCES transactions(id) ON DELETE RESTRICT)")
            db.execSQL("CREATE UNIQUE INDEX index_reconciliation_matches_statementEntryId ON reconciliation_matches(statementEntryId)");db.execSQL("CREATE UNIQUE INDEX index_reconciliation_matches_transactionId ON reconciliation_matches(transactionId)");db.execSQL("CREATE INDEX index_reconciliation_matches_status ON reconciliation_matches(status)")
            db.execSQL("INSERT INTO reconciliation_matches(statementEntryId,transactionId,status,matchSource,confidence,score,reasonCode,dayDifference,createdAt,updatedAt,confirmedAt) SELECT id,NULL,'PENDING',NULL,NULL,NULL,NULL,NULL,0,0,NULL FROM statement_entries")
            db.execSQL("CREATE TABLE rejected_reconciliation_candidates (statementEntryId INTEGER NOT NULL, transactionId INTEGER NOT NULL, rejectedAt INTEGER NOT NULL, PRIMARY KEY(statementEntryId,transactionId), FOREIGN KEY(statementEntryId) REFERENCES statement_entries(id) ON DELETE CASCADE, FOREIGN KEY(transactionId) REFERENCES transactions(id) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_rejected_reconciliation_candidates_statementEntryId ON rejected_reconciliation_candidates(statementEntryId)");db.execSQL("CREATE INDEX index_rejected_reconciliation_candidates_transactionId ON rejected_reconciliation_candidates(transactionId)")
            db.execSQL("CREATE TABLE monthly_payment_source_declarations (month TEXT NOT NULL, paymentSourceId TEXT NOT NULL, status TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(month,paymentSourceId), FOREIGN KEY(paymentSourceId) REFERENCES payment_sources(id) ON DELETE CASCADE)");db.execSQL("CREATE INDEX index_monthly_payment_source_declarations_paymentSourceId ON monthly_payment_source_declarations(paymentSourceId)")
            db.execSQL("DELETE FROM monthly_locks")
        }}
        fun open(context: Context): RecoFiDatabase = Room.databaseBuilder(context, RecoFiDatabase::class.java, DATABASE_NAME)
            .allowMainThreadQueries()
            .addMigrations(MIGRATION_2_3)
            .build()
    }
}

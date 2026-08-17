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
    version = 5,
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
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_budget_settings ADD COLUMN defaultPaymentSourceId TEXT")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                fun scalar(sql:String):Long=db.query(sql).use{cursor->check(cursor.moveToFirst());cursor.getLong(0)}
                val categoryCount=scalar("SELECT COUNT(*) FROM categories")
                val transactionCount=scalar("SELECT COUNT(*) FROM transactions")
                val recurringCount=scalar("SELECT COUNT(*) FROM recurring_expenses")
                check(scalar("SELECT COUNT(*) FROM transactions t LEFT JOIN categories c ON c.name=t.category WHERE c.name IS NULL")==0L){"不明なカテゴリを参照する明細があります"}
                check(scalar("SELECT COUNT(*) FROM recurring_expenses r LEFT JOIN categories c ON c.name=r.category WHERE c.name IS NULL")==0L){"不明なカテゴリを参照する固定費があります"}
                db.execSQL("PRAGMA defer_foreign_keys=ON")
                db.execSQL("CREATE TABLE categories_new (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, sortOrder INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX index_categories_new_name ON categories_new(name)")
                db.execSQL("INSERT INTO categories_new(id,name,sortOrder) SELECT 'category_' || lower(hex(CAST(name AS BLOB))),name,sortOrder FROM categories")
                db.execSQL("CREATE TABLE recurring_expenses_new (id INTEGER NOT NULL PRIMARY KEY, amount INTEGER NOT NULL, categoryId TEXT NOT NULL, merchant TEXT NOT NULL, description TEXT NOT NULL, paymentDay INTEGER NOT NULL, startMonth TEXT NOT NULL, contractDate TEXT NOT NULL, paymentSourceId TEXT NOT NULL, intervalMonths INTEGER NOT NULL, endDate TEXT, FOREIGN KEY(categoryId) REFERENCES categories_new(id) ON DELETE RESTRICT, FOREIGN KEY(paymentSourceId) REFERENCES payment_sources(id) ON DELETE RESTRICT)")
                db.execSQL("INSERT INTO recurring_expenses_new(id,amount,categoryId,merchant,description,paymentDay,startMonth,contractDate,paymentSourceId,intervalMonths,endDate) SELECT r.id,r.amount,c.id,r.merchant,r.description,r.billingDay,r.startMonth,r.contractDate,r.paymentSourceId,r.intervalMonths,r.endDate FROM recurring_expenses r JOIN categories_new c ON c.name=r.category")
                db.execSQL("CREATE TABLE transactions_new (id INTEGER NOT NULL PRIMARY KEY, amount INTEGER NOT NULL, categoryId TEXT NOT NULL, merchant TEXT NOT NULL, description TEXT NOT NULL, usedAt INTEGER NOT NULL, recurringId INTEGER, paymentSourceId TEXT NOT NULL, FOREIGN KEY(categoryId) REFERENCES categories_new(id) ON DELETE RESTRICT, FOREIGN KEY(paymentSourceId) REFERENCES payment_sources(id) ON DELETE RESTRICT, FOREIGN KEY(recurringId) REFERENCES recurring_expenses_new(id) ON DELETE SET NULL)")
                db.execSQL("INSERT INTO transactions_new(id,amount,categoryId,merchant,description,usedAt,recurringId,paymentSourceId) SELECT t.id,t.amount,c.id,t.merchant,t.description,t.usedAt,t.recurringId,t.paymentSourceId FROM transactions t JOIN categories_new c ON c.name=t.category")
                db.execSQL("CREATE TABLE recurring_price_revisions_new (recurringId INTEGER NOT NULL, effectiveDate TEXT NOT NULL, amount INTEGER NOT NULL, PRIMARY KEY(recurringId,effectiveDate), FOREIGN KEY(recurringId) REFERENCES recurring_expenses_new(id) ON DELETE CASCADE)")
                db.execSQL("INSERT INTO recurring_price_revisions_new SELECT * FROM recurring_price_revisions")
                db.execSQL("CREATE TABLE reconciliation_matches_new (statementEntryId INTEGER NOT NULL PRIMARY KEY, transactionId INTEGER, status TEXT NOT NULL, matchSource TEXT, confidence TEXT, score INTEGER, reasonCode TEXT, dayDifference INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, confirmedAt INTEGER, FOREIGN KEY(statementEntryId) REFERENCES statement_entries(id) ON DELETE CASCADE, FOREIGN KEY(transactionId) REFERENCES transactions_new(id) ON DELETE RESTRICT)")
                db.execSQL("INSERT INTO reconciliation_matches_new SELECT * FROM reconciliation_matches")
                db.execSQL("CREATE TABLE rejected_reconciliation_candidates_new (statementEntryId INTEGER NOT NULL, transactionId INTEGER NOT NULL, rejectedAt INTEGER NOT NULL, PRIMARY KEY(statementEntryId,transactionId), FOREIGN KEY(statementEntryId) REFERENCES statement_entries(id) ON DELETE CASCADE, FOREIGN KEY(transactionId) REFERENCES transactions_new(id) ON DELETE CASCADE)")
                db.execSQL("INSERT INTO rejected_reconciliation_candidates_new SELECT * FROM rejected_reconciliation_candidates")
                db.execSQL("DROP TABLE rejected_reconciliation_candidates");db.execSQL("DROP TABLE reconciliation_matches");db.execSQL("DROP TABLE transactions");db.execSQL("DROP TABLE recurring_price_revisions");db.execSQL("DROP TABLE recurring_expenses");db.execSQL("DROP TABLE categories")
                db.execSQL("ALTER TABLE categories_new RENAME TO categories");db.execSQL("ALTER TABLE recurring_expenses_new RENAME TO recurring_expenses");db.execSQL("ALTER TABLE transactions_new RENAME TO transactions");db.execSQL("ALTER TABLE recurring_price_revisions_new RENAME TO recurring_price_revisions");db.execSQL("ALTER TABLE reconciliation_matches_new RENAME TO reconciliation_matches");db.execSQL("ALTER TABLE rejected_reconciliation_candidates_new RENAME TO rejected_reconciliation_candidates")
                db.execSQL("DROP INDEX IF EXISTS index_categories_new_name");db.execSQL("CREATE UNIQUE INDEX index_categories_name ON categories(name)")
                db.execSQL("CREATE INDEX index_recurring_expenses_categoryId ON recurring_expenses(categoryId)");db.execSQL("CREATE INDEX index_recurring_expenses_paymentSourceId ON recurring_expenses(paymentSourceId)")
                db.execSQL("CREATE INDEX index_transactions_categoryId ON transactions(categoryId)");db.execSQL("CREATE INDEX index_transactions_paymentSourceId ON transactions(paymentSourceId)");db.execSQL("CREATE INDEX index_transactions_recurringId ON transactions(recurringId)");db.execSQL("CREATE INDEX index_transactions_usedAt ON transactions(usedAt)")
                db.execSQL("CREATE INDEX index_recurring_price_revisions_recurringId ON recurring_price_revisions(recurringId)")
                db.execSQL("CREATE UNIQUE INDEX index_reconciliation_matches_statementEntryId ON reconciliation_matches(statementEntryId)");db.execSQL("CREATE UNIQUE INDEX index_reconciliation_matches_transactionId ON reconciliation_matches(transactionId)");db.execSQL("CREATE INDEX index_reconciliation_matches_status ON reconciliation_matches(status)")
                db.execSQL("CREATE INDEX index_rejected_reconciliation_candidates_statementEntryId ON rejected_reconciliation_candidates(statementEntryId)");db.execSQL("CREATE INDEX index_rejected_reconciliation_candidates_transactionId ON rejected_reconciliation_candidates(transactionId)")
                check(scalar("SELECT COUNT(*) FROM categories")==categoryCount);check(scalar("SELECT COUNT(*) FROM transactions")==transactionCount);check(scalar("SELECT COUNT(*) FROM recurring_expenses")==recurringCount)
                db.query("PRAGMA foreign_key_check").use{check(!it.moveToFirst()){"外部キー整合性を確認できませんでした"}}
            }
        }
        fun open(context: Context): RecoFiDatabase = Room.databaseBuilder(context, RecoFiDatabase::class.java, DATABASE_NAME)
            .allowMainThreadQueries()
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
    }
}

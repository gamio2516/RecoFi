package jp.knaka.cardmemo.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PaymentSourceEntity::class, CategoryEntity::class, NoteTemplateEntity::class,
        RecurringExpenseEntity::class, RecurringPriceRevisionEntity::class, TransactionEntity::class,
        ImportedStatementEntity::class, StatementEntryEntity::class, ImportedFingerprintEntity::class,
        ReconciliationProgressEntity::class, MonthlyLockEntity::class, MonthlyBudgetEntity::class,
        AppBudgetSettingsEntity::class, StorageMigrationEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class RecoFiDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun referenceData(): ReferenceDataDao
    abstract fun recurringExpenses(): RecurringExpenseDao
    abstract fun statements(): StatementDao
    abstract fun monthlyState(): MonthlyStateDao
    abstract fun migrations(): StorageMigrationDao

    companion object {
        const val DATABASE_NAME = "recofi.db"
        fun open(context: Context): RecoFiDatabase = Room.databaseBuilder(context, RecoFiDatabase::class.java, DATABASE_NAME)
            .build()
    }
}

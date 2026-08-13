package jp.knaka.cardmemo.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "payment_sources", indices = [Index(value = ["name"], unique = true)])
data class PaymentSourceEntity(@PrimaryKey val id: String, val name: String, val isCard: Boolean, val sortOrder: Int)

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(@PrimaryKey val name: String, val sortOrder: Int)

@Entity(tableName = "merchant_templates")
data class MerchantTemplateEntity(@PrimaryKey val value: String, val sortOrder: Int)

@Entity(tableName = "description_templates")
data class DescriptionTemplateEntity(@PrimaryKey val value: String, val sortOrder: Int)

@Entity(
    tableName = "recurring_expenses",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["name"], childColumns = ["category"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PaymentSourceEntity::class, parentColumns = ["id"], childColumns = ["paymentSourceId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("category"), Index("paymentSourceId")],
)
data class RecurringExpenseEntity(
    @PrimaryKey val id: Long,
    val amount: Long,
    val category: String,
    val merchant: String,
    val description: String,
    val billingDay: Int,
    val startMonth: String,
    val contractDate: String,
    val paymentSourceId: String,
    val intervalMonths: Int,
    val endDate: String?,
)

@Entity(
    tableName = "recurring_price_revisions",
    primaryKeys = ["recurringId", "effectiveDate"],
    foreignKeys = [ForeignKey(entity = RecurringExpenseEntity::class, parentColumns = ["id"], childColumns = ["recurringId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recurringId")],
)
data class RecurringPriceRevisionEntity(val recurringId: Long, val effectiveDate: String, val amount: Long)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["name"], childColumns = ["category"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PaymentSourceEntity::class, parentColumns = ["id"], childColumns = ["paymentSourceId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = RecurringExpenseEntity::class, parentColumns = ["id"], childColumns = ["recurringId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("category"), Index("paymentSourceId"), Index("recurringId"), Index("usedAt")],
)
data class TransactionEntity(
    @PrimaryKey val id: Long,
    val amount: Long,
    val category: String,
    val merchant: String,
    val description: String,
    val usedAt: Long,
    val confirmed: Boolean,
    val recurringId: Long?,
    val paymentSourceId: String,
    val reconciledMonth: String?,
    val suggested: Boolean,
)

@Entity(
    tableName = "imported_statements",
    indices = [Index(value = ["statementMonth", "paymentSourceId"], unique = true), Index("paymentSourceId")],
    foreignKeys = [ForeignKey(entity = PaymentSourceEntity::class, parentColumns = ["id"], childColumns = ["paymentSourceId"], onDelete = ForeignKey.RESTRICT)],
)
data class ImportedStatementEntity(
    @PrimaryKey val fileHash: String,
    val statementMonth: String?,
    val paymentSourceId: String?,
    val fileName: String,
)

@Entity(
    tableName = "statement_entries",
    foreignKeys = [ForeignKey(entity = ImportedStatementEntity::class, parentColumns = ["fileHash"], childColumns = ["fileHash"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("fileHash"), Index("date")],
)
data class StatementEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileHash: String,
    val rowOrder: Int,
    val date: String,
    val amount: Long,
    val merchant: String,
    val rawText: String,
)

@Entity(tableName = "imported_fingerprints")
data class ImportedFingerprintEntity(@PrimaryKey val fingerprint: String, val importedAt: Long)

@Entity(
    tableName = "reconciliation_progress",
    primaryKeys = ["statementMonth", "paymentSourceId"],
    foreignKeys = [ForeignKey(entity = PaymentSourceEntity::class, parentColumns = ["id"], childColumns = ["paymentSourceId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("paymentSourceId")],
)
data class ReconciliationProgressEntity(val statementMonth: String, val paymentSourceId: String, val imported: Int, val matched: Int, val suggested: Int, val confirmed: Int)

@Entity(tableName = "monthly_locks")
data class MonthlyLockEntity(@PrimaryKey val month: String, val lockedAt: Long)

@Entity(tableName = "monthly_budgets")
data class MonthlyBudgetEntity(@PrimaryKey val month: String, val amount: Long)

@Entity(tableName = "app_budget_settings")
data class AppBudgetSettingsEntity(@PrimaryKey val id: Int = SINGLETON_ID, val defaultMonthlyBudget: Long) {
    companion object { const val SINGLETON_ID = 1 }
}

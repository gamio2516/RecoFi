package jp.knaka.cardmemo.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "payment_sources", indices = [Index(value = ["name"], unique = true)])
data class PaymentSourceEntity(@PrimaryKey val id: String, val name: String, val type: String, val sortOrder: Int, val isCard: Boolean = type == "CREDIT_CARD")

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
    val recurringId: Long?,
    val paymentSourceId: String,
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
    indices = [Index("fileHash"), Index("date"), Index(value = ["fileHash", "rowOrder"], unique = true)],
)
data class StatementEntryEntity(
    @PrimaryKey val id: Long,
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
    tableName = "reconciliation_matches",
    foreignKeys = [
        ForeignKey(entity = StatementEntryEntity::class, parentColumns = ["id"], childColumns = ["statementEntryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("statementEntryId", unique = true), Index("transactionId", unique = true), Index("status")],
)
data class ReconciliationMatchEntity(
    @PrimaryKey val statementEntryId: Long,
    val transactionId: Long?,
    val status: String,
    val matchSource: String?,
    val confidence: String?,
    val score: Int?,
    val reasonCode: String?,
    val dayDifference: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val confirmedAt: Long?,
)

@Entity(
    tableName = "rejected_reconciliation_candidates",
    primaryKeys = ["statementEntryId", "transactionId"],
    foreignKeys = [
        ForeignKey(entity = StatementEntryEntity::class, parentColumns = ["id"], childColumns = ["statementEntryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("statementEntryId"), Index("transactionId")],
)
data class RejectedReconciliationCandidateEntity(val statementEntryId: Long, val transactionId: Long, val rejectedAt: Long)

@Entity(
    tableName = "monthly_payment_source_declarations",
    primaryKeys = ["month", "paymentSourceId"],
    foreignKeys = [ForeignKey(entity = PaymentSourceEntity::class, parentColumns = ["id"], childColumns = ["paymentSourceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("paymentSourceId")],
)
data class MonthlyPaymentSourceDeclarationEntity(val month: String, val paymentSourceId: String, val status: String, val updatedAt: Long)

@Entity(tableName = "monthly_locks")
data class MonthlyLockEntity(@PrimaryKey val month: String, val lockedAt: Long)

@Entity(tableName = "monthly_budgets")
data class MonthlyBudgetEntity(@PrimaryKey val month: String, val amount: Long)

@Entity(tableName = "app_budget_settings")
data class AppBudgetSettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val defaultMonthlyBudget: Long,
    val defaultPaymentSourceId: String? = null,
) {
    companion object { const val SINGLETON_ID = 1 }
}

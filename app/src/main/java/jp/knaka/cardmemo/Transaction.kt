package jp.knaka.cardmemo

data class Transaction(
    val id: Long,
    val amount: Long,
    val category: String,
    val merchant: String,
    val description: String,
    val usedAt: Long,
    val confirmed: Boolean = false,
    val recurringId: Long? = null,
    val paymentSourceId: String = "rakuten",
    val reconciledMonth: String? = null,
    val suggested: Boolean = false,
)

data class RecurringExpense(
    val id: Long,
    val amount: Long,
    val category: String,
    val merchant: String,
    val description: String,
    val billingDay: Int,
    val startMonth: String,
    val contractDate: String,
    val paymentSourceId: String = "rakuten",
    val intervalMonths: Int = 1,
    val endDate: String? = null,
    val priceRevisions: List<PriceRevision> = emptyList(),
)

data class PriceRevision(val effectiveDate: String, val amount: Long)

data class PaymentSource(val id: String, val name: String, val isCard: Boolean)

data class CardStatementEntry(
    val date: java.time.LocalDate,
    val amount: Long,
    val merchant: String,
    val rawText: String,
)

data class StatementMatch(
    val statement: CardStatementEntry,
    val transactionId: Long?,
    val score: Int,
)

data class ImportedStatement(
    val statementMonth: String,
    val paymentSourceId: String,
    val fileName: String,
    val fileHash: String,
    val entries: List<CardStatementEntry>,
)

data class ReconciliationProgress(
    val imported: Int = 0,
    val matched: Int = 0,
    val suggested: Int = 0,
    val confirmed: Int = 0,
)

val DefaultPaymentSources = listOf(
    PaymentSource("rakuten", "楽天カード", true),
    PaymentSource("other", "その他の支払い", false),
)

val DefaultCategories = listOf("食料品", "外食", "日用品", "交通", "医療", "娯楽", "衣服", "その他")
val DefaultFrequentNotes = listOf("西友", "成城石井", "楽天市場")

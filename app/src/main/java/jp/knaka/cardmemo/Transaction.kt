package jp.knaka.cardmemo

data class Transaction(
    val id: Long,
    val amount: Long,
    val category: String,
    val merchant: String,
    val description: String,
    val usedAt: Long,
    val recurringId: Long? = null,
    val paymentSourceId: String = "",
    val categoryId: String = stableCategoryId(category),
)

data class RecurringExpense(
    val id: Long,
    val amount: Long,
    val category: String,
    val merchant: String,
    val description: String,
    val paymentDay: Int,
    val startMonth: String,
    val contractDate: String,
    val paymentSourceId: String = "",
    val intervalMonths: Int = 1,
    val endDate: String? = null,
    val priceRevisions: List<PriceRevision> = emptyList(),
    val categoryId: String = stableCategoryId(category),
)

data class Category(val id: String, val name: String, val sortOrder: Int)

fun stableCategoryId(name: String): String = "category_" + name.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }

data class PriceRevision(val effectiveDate: String, val amount: Long)

enum class PaymentSourceType { CREDIT_CARD, CASH, OTHER }
data class PaymentSource(val id: String, val name: String, val type: PaymentSourceType) {
    val isCard: Boolean get() = type == PaymentSourceType.CREDIT_CARD
}

data class CardStatementEntry(
    val date: java.time.LocalDate,
    val amount: Long,
    val merchant: String,
    val rawText: String,
)

data class StatementMatch(
    val statementEntryId: Long = 0,
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

enum class ReconciliationStatus { PENDING, SUGGESTED, CONFIRMED }
enum class MatchSource { RULE, USER, AI }
enum class MatchConfidence { HIGH, MEDIUM }
data class MonthlyReconciliationProgress(val imported:Int,val confirmed:Int,val needsReview:Int,val unresolved:Int) { val remaining:Int get()=needsReview+unresolved }
data class ReconciliationProgress(val imported:Int=0,val matched:Int=0,val suggested:Int=0,val confirmed:Int=0)

val DefaultPaymentSources = listOf(
    PaymentSource("rakuten", "楽天カード", PaymentSourceType.CREDIT_CARD),
    PaymentSource("other", "その他の支払い", PaymentSourceType.OTHER),
)

val DefaultCategories = listOf("食料品", "外食", "日用品", "交通", "医療", "娯楽", "衣服", "その他")
val DefaultFrequentNotes = listOf("西友", "成城石井", "楽天市場")

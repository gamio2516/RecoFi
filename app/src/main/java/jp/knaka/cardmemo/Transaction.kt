package jp.knaka.cardmemo

data class Transaction(
    val id: Long,
    val amount: Int,
    val category: String,
    val note: String,
    val usedAt: Long,
    val confirmed: Boolean = false,
    val recurringId: Long? = null,
    val paymentSourceId: String = "rakuten",
)

data class RecurringExpense(
    val id: Long,
    val amount: Int,
    val category: String,
    val note: String,
    val billingDay: Int,
    val startMonth: String,
    val contractDate: String,
    val paymentSourceId: String = "rakuten",
    val intervalMonths: Int = 1,
    val endDate: String? = null,
    val priceRevisions: List<PriceRevision> = emptyList(),
)

data class PriceRevision(val effectiveDate: String, val amount: Int)

data class PaymentSource(val id: String, val name: String, val isCard: Boolean)

val DefaultPaymentSources = listOf(
    PaymentSource("rakuten", "楽天カード", true),
    PaymentSource("other", "その他の支払い", false),
)

val DefaultCategories = listOf("食料品", "外食", "日用品", "交通", "医療", "娯楽", "衣服", "その他")
val DefaultFrequentNotes = listOf("西友", "成城石井", "楽天市場")

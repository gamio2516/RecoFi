package jp.knaka.cardmemo

const val USER_MANAGED_LIST_LIMIT = 20
internal fun canAddManagedValue(values:List<String>,value:String)=values.size<USER_MANAGED_LIST_LIMIT&&value.trim().isNotEmpty()&&value.trim() !in values

internal fun <T> moveItem(values:List<T>,from:Int,to:Int):List<T>{
    if(from !in values.indices || to !in values.indices || from==to)return values
    return values.toMutableList().apply{add(to,removeAt(from))}
}
internal fun datePickerMillis(date:java.time.LocalDate)=date.toEpochDay()*86_400_000L
internal fun dateFromPickerMillis(millis:Long)=java.time.LocalDate.ofEpochDay(millis/86_400_000L)

internal fun resolveInitialPaymentSourceId(
    sources: List<PaymentSource>,
    defaultPaymentSourceId: String?,
    contextualPaymentSourceId: String? = null,
): String = contextualPaymentSourceId?.takeIf { id -> sources.any { it.id == id } }
    ?: defaultPaymentSourceId?.takeIf { id -> sources.any { it.id == id } }
    ?: sources.firstOrNull()?.id.orEmpty()

internal enum class ProgressiveHint { FIRST_EXPENSE, IMPORT_STATEMENT, REVIEW_CANDIDATES, NONE }

internal fun progressiveHint(
    transactionCount: Int,
    isCreditCard: Boolean,
    importedCount: Int,
    remainingReviewCount: Int,
): ProgressiveHint = when {
    transactionCount == 0 -> ProgressiveHint.FIRST_EXPENSE
    isCreditCard && importedCount == 0 -> ProgressiveHint.IMPORT_STATEMENT
    importedCount > 0 && remainingReviewCount > 0 -> ProgressiveHint.REVIEW_CANDIDATES
    else -> ProgressiveHint.NONE
}

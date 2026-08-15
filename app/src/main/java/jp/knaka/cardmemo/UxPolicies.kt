package jp.knaka.cardmemo

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

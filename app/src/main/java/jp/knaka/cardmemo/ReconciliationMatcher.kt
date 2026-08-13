package jp.knaka.cardmemo

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Pure rule-based reconciliation. AI can later consume ambiguous candidates separately. */
object ReconciliationMatcher {
    private const val MAX_DAY_GAP = 7L
    private const val MIN_SCORE = 45
    private const val AMBIGUITY_MARGIN = 5

    fun match(
        entries: List<CardStatementEntry>,
        transactions: List<Transaction>,
        sourceId: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<StatementMatch> {
        val sourceTransactions = transactions.filter { it.paymentSourceId == sourceId }
        val usedIds = mutableSetOf<Long>()

        return entries.map { entry ->
            val ranked = sourceTransactions.asSequence()
                .filter { it.id !in usedIds && it.amount == entry.amount }
                .mapNotNull { transaction ->
                    val date = transaction.localDate(zoneId)
                    val dayGap = abs(ChronoUnit.DAYS.between(date, entry.date))
                    if (dayGap > MAX_DAY_GAP) return@mapNotNull null
                    Candidate(transaction, score(dayGap, transaction.merchant, entry.merchant))
                }
                .filter { it.score >= MIN_SCORE }
                .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.transaction.id })
                .toList()

            val best = ranked.firstOrNull()
            val second = ranked.getOrNull(1)
            val ambiguous = best != null && second != null && best.score - second.score <= AMBIGUITY_MARGIN
            val selected = best?.takeUnless { ambiguous }
            selected?.let { usedIds += it.transaction.id }
            StatementMatch(entry, selected?.transaction?.id, selected?.score ?: best?.score ?: 0)
        }
    }

    fun normalizeMerchant(value: String): String {
        var normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC).lowercase()
            .replace("スターバックス", "starbucks")
            .replace("スタバ", "starbucks")
            .replace("珈琲", "coffee")
            .replace("コーヒー", "coffee")
            .replace(Regex("(株式会社|有限会社|incorporated|corporation|company|inc|corp|co|ltd)"), "")
        normalized = normalized.filter { it.isLetterOrDigit() }
        return normalized
    }

    private fun score(dayGap: Long, transactionMerchant: String, statementMerchant: String): Int {
        val dateScore = 80 - dayGap.toInt() * 5
        val merchantScore = (similarity(normalizeMerchant(transactionMerchant), normalizeMerchant(statementMerchant)) * 20).toInt()
        return dateScore + merchantScore
    }

    private fun similarity(left: String, right: String): Double {
        if (left.isBlank() || right.isBlank()) return 0.0
        if (left == right || left in right || right in left) return 1.0
        val leftPairs = left.windowed(2).toSet()
        val rightPairs = right.windowed(2).toSet()
        if (leftPairs.isEmpty() || rightPairs.isEmpty()) return 0.0
        return leftPairs.intersect(rightPairs).size.toDouble() / leftPairs.union(rightPairs).size
    }

    private fun Transaction.localDate(zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(usedAt).atZone(zoneId).toLocalDate()

    private data class Candidate(val transaction: Transaction, val score: Int)
}

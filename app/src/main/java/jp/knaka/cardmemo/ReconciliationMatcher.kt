package jp.knaka.cardmemo

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Pure rule-based reconciliation. AI can later consume ambiguous candidates separately. */
object ReconciliationMatcher {
    private const val MAX_DAY_GAP = 7L
    private const val MIN_MERCHANT_SIMILARITY = 0.22
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
                    val merchantSimilarity=similarity(normalizeMerchant(transaction.merchant),normalizeMerchant(entry.merchant))
                    if(merchantSimilarity<MIN_MERCHANT_SIMILARITY)return@mapNotNull null
                    Candidate(transaction, score(dayGap, merchantSimilarity),dayGap.toInt(),merchantSimilarity)
                }
                .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.transaction.id })
                .toList()

            val best = ranked.firstOrNull()
            val second = ranked.getOrNull(1)
            val ambiguous = best != null && second != null && best.score - second.score <= AMBIGUITY_MARGIN
            val selected = best?.takeUnless { ambiguous }
            selected?.let { usedIds += it.transaction.id }
            StatementMatch(statement=entry, transactionId=selected?.transaction?.id, score=selected?.score ?: best?.score ?: 0)
        }
    }

    fun normalizeMerchant(value: String): String {
        var normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC).lowercase().filter { it.isLetterOrDigit() }
            .replace("スターバックス", "starbucks")
            .replace("スタバ", "starbucks")
            .replace("amznmktpjp", "amazon")
            .replace("amazoncojp", "amazon")
            .replace("珈琲", "coffee")
            .replace("コーヒー", "coffee")
            .replace(Regex("(株式会社|有限会社|incorporated|corporation|company|inc|corp|co|ltd)"), "")
        return normalized
    }

    fun candidate(entry:CardStatementEntry,transaction:Transaction,zoneId:ZoneId=ZoneId.systemDefault()):CandidateInfo?{
        if(entry.amount!=transaction.amount)return null
        val gap=abs(ChronoUnit.DAYS.between(transaction.localDate(zoneId),entry.date));if(gap>MAX_DAY_GAP)return null
        val similarity=similarity(normalizeMerchant(transaction.merchant),normalizeMerchant(entry.merchant));if(similarity<MIN_MERCHANT_SIMILARITY)return null
        val confidence=if(similarity>=.72&&gap<=3)MatchConfidence.HIGH else MatchConfidence.MEDIUM
        return CandidateInfo(transaction.id,score(gap,similarity),gap.toInt(),confidence,"金額一致・利用日${gap}日差・取引先${if(confidence==MatchConfidence.HIGH)"が高類似" else "が類似"}")
    }
    private fun score(dayGap: Long, merchantSimilarity:Double) = ((1.0-dayGap/14.0)*40+merchantSimilarity*60).toInt().coerceIn(0,100)

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

    data class CandidateInfo(val transactionId:Long,val score:Int,val dayDifference:Int,val confidence:MatchConfidence,val reason:String)
    private data class Candidate(val transaction: Transaction, val score: Int,val dayDifference:Int,val similarity:Double)
}

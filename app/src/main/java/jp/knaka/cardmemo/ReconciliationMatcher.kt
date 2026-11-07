package jp.knaka.cardmemo

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Pure rule-based reconciliation. AI can later consume ambiguous candidates separately. */
object ReconciliationMatcher {
    private const val MAX_DAY_GAP = 7L

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
                    Candidate(transaction, score(dayGap), dayGap.toInt())
                }
                .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.transaction.id })
                .toList()

            val best = ranked.firstOrNull()
            val second = ranked.getOrNull(1)
            val ambiguous = best != null && second != null && best.dayDifference == second.dayDifference
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
        val confidence=if(gap==0L)MatchConfidence.HIGH else MatchConfidence.MEDIUM
        return CandidateInfo(transaction.id,score(gap),gap.toInt(),confidence,"金額一致・利用日${gap}日差")
    }
    private fun score(dayGap: Long) = (100-dayGap*8).toInt().coerceIn(0,100)

    private fun Transaction.localDate(zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(usedAt).atZone(zoneId).toLocalDate()

    data class CandidateInfo(val transactionId:Long,val score:Int,val dayDifference:Int,val confidence:MatchConfidence,val reason:String)
    private data class Candidate(val transaction: Transaction, val score: Int,val dayDifference:Int)
}

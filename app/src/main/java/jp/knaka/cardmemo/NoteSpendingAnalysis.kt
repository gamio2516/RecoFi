package jp.knaka.cardmemo

data class NoteSpendingEntry(
    val normalizedNote: String,
    val label: String,
    val amount: Int,
    val count: Int,
    val average: Int,
    val increase: Int,
)

data class NoteSpendingAnalysisResult(
    val ranking: List<NoteSpendingEntry>,
    val largestSpending: NoteSpendingEntry?,
    val mostFrequent: NoteSpendingEntry?,
    val biggestIncrease: NoteSpendingEntry?,
)

object NoteSpendingAnalysis {
    fun calculate(current: List<Transaction>, previous: List<Transaction>): NoteSpendingAnalysisResult {
        val groups = current.filter { it.note.isNotBlank() }.groupBy { normalize(it.note) }.filterKeys { it.isNotBlank() }
        val previousTotals = previous.filter { it.note.isNotBlank() }.groupBy { normalize(it.note) }.mapValues { (_, rows) -> rows.sumOf(Transaction::amount) }
        val ranking = groups.map { (key, rows) ->
            val amount = rows.sumOf(Transaction::amount)
            val label = rows.groupingBy { it.note.trim() }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
            NoteSpendingEntry(key, label, amount, rows.size, amount / rows.size, amount - previousTotals.getOrDefault(key, 0))
        }.sortedByDescending { it.amount }
        return NoteSpendingAnalysisResult(
            ranking = ranking,
            largestSpending = ranking.firstOrNull(),
            mostFrequent = ranking.maxByOrNull { it.count },
            biggestIncrease = ranking.maxByOrNull { it.increase },
        )
    }

    fun normalize(value: String): String = value.lowercase().filterNot { it.isWhitespace() || it in "・･-ー_/()（）" }
}

package jp.knaka.cardmemo

object CategorySuggestionEngine {
    fun suggest(note: String, sourceId: String, history: List<Transaction>, categories: List<String>): List<String> {
        val input = normalize(note)
        if (input.isBlank()) return emptyList()
        val scores = mutableMapOf<String, Double>()
        history.asSequence().filter { it.note.isNotBlank() && it.category in categories }.forEach { transaction ->
            val past = normalize(transaction.note)
            val similarity = when {
                input == past -> 10.0
                input.length >= 2 && past.length >= 2 && (input.contains(past) || past.contains(input)) -> 4.0
                else -> bigramSimilarity(input, past)
            }
            if (similarity >= 0.25) {
                val sourceBonus = if (transaction.paymentSourceId == sourceId) 1.35 else 1.0
                scores[transaction.category] = scores.getOrDefault(transaction.category, 0.0) + similarity * sourceBonus
            }
        }
        return scores.entries.sortedByDescending { it.value }.take(3).map { it.key }
    }

    private fun normalize(value: String): String =
        value.lowercase().filterNot { it.isWhitespace() || it in "・･-ー_/()（）" }

    private fun bigramSimilarity(first: String, second: String): Double {
        if (first.length < 2 || second.length < 2) return 0.0
        val left = first.windowed(2).toSet()
        val right = second.windowed(2).toSet()
        return left.intersect(right).size.toDouble() / left.union(right).size.coerceAtLeast(1)
    }
}

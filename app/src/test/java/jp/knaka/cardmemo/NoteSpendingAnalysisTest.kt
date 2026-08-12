package jp.knaka.cardmemo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSpendingAnalysisTest {
    @Test fun groupsNormalizedNotesAndRanksByAmount() {
        val result = NoteSpendingAnalysis.calculate(listOf(tx("楽天 市場", 500), tx("楽天市場", 800), tx("Netflix", 1000)), emptyList())
        assertEquals("楽天市場", result.ranking.first().normalizedNote)
        assertEquals(1300, result.ranking.first().amount)
        assertEquals(2, result.ranking.first().count)
    }

    @Test fun calculatesPreviousMonthIncrease() {
        val result = NoteSpendingAnalysis.calculate(listOf(tx("Netflix", 1500)), listOf(tx("Netflix", 1000)))
        assertEquals(500, result.ranking.single().increase)
    }

    @Test fun identifiesMostFrequentIndependentlyFromLargestAmount() {
        val result = NoteSpendingAnalysis.calculate(listOf(tx("カフェ", 300), tx("カフェ", 300), tx("家電", 5000)), emptyList())
        assertEquals("カフェ", result.mostFrequent?.label)
        assertEquals("家電", result.largestSpending?.label)
    }

    @Test fun blankNotesAreIgnored() {
        assertTrue(NoteSpendingAnalysis.calculate(listOf(tx(" ", 1000)), emptyList()).ranking.isEmpty())
    }

    private fun tx(note: String, amount: Int) = Transaction(System.nanoTime(), amount, "その他", note, 0L)
}

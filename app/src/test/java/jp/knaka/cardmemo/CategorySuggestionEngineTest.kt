package jp.knaka.cardmemo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorySuggestionEngineTest {
    @Test fun exactNoteSuggestsItsCategory() {
        val result = CategorySuggestionEngine.suggest("楽天市場", "rakuten", listOf(transaction("楽天市場", "日用品")), listOf("日用品", "食費"))
        assertEquals("日用品", result.first())
    }

    @Test fun unavailableCategoriesAreExcluded() {
        val result = CategorySuggestionEngine.suggest("楽天市場", "rakuten", listOf(transaction("楽天市場", "削除済み")), listOf("日用品"))
        assertTrue(result.isEmpty())
    }

    @Test fun blankInputHasNoSuggestions() {
        assertTrue(CategorySuggestionEngine.suggest("  ", "rakuten", emptyList(), listOf("食費")).isEmpty())
    }

    @Test fun samePaymentSourceWinsWhenScoresOtherwiseMatch() {
        val history = listOf(transaction("カフェ", "外食", "other"), transaction("カフェ", "交際費", "rakuten"))
        assertEquals("交際費", CategorySuggestionEngine.suggest("カフェ", "rakuten", history, listOf("外食", "交際費")).first())
    }

    @Test fun suggestionsAreLimitedToThree() {
        val history = listOf("A", "B", "C", "D").map { category -> transaction("共通店舗", category) }
        assertEquals(3, CategorySuggestionEngine.suggest("共通店舗", "rakuten", history, listOf("A", "B", "C", "D")).size)
    }

    private fun transaction(note: String, category: String, sourceId: String = "rakuten") =
        Transaction(System.nanoTime(), 500, category, note, 0L, paymentSourceId = sourceId)
}

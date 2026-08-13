package jp.knaka.cardmemo
import org.junit.Assert.*
import org.junit.Test
class CategorySuggestionEngineTest {
 private val history=listOf(
  Transaction(1,500L,"カフェ","スターバックス","コーヒー",1L,paymentSourceId="card"),
  Transaction(2,900L,"日用品","Amazon","洗剤",2L,paymentSourceId="card")
 )
 @Test fun merchantSuggestsCategory(){assertEquals("カフェ",CategorySuggestionEngine.suggest(CategorySuggestionInput("スターバックス","","card"),history,listOf("カフェ","日用品")).first())}
 @Test fun descriptionSuggestsCategory(){assertEquals("日用品",CategorySuggestionEngine.suggest(CategorySuggestionInput("","洗剤","card"),history,listOf("カフェ","日用品")).first())}
 @Test fun merchantAndDescriptionSuggestCategory(){assertEquals("カフェ",CategorySuggestionEngine.suggest(CategorySuggestionInput("スターバックス","コーヒー","card"),history,listOf("カフェ","日用品")).first())}
 @Test fun bothBlankIsSafe(){assertTrue(CategorySuggestionEngine.suggest(CategorySuggestionInput("","","card"),history,listOf("カフェ")).isEmpty())}
 @Test fun unavailableCategoryIsExcluded(){assertTrue(CategorySuggestionEngine.suggest(CategorySuggestionInput("Amazon","洗剤","card"),history,listOf("カフェ")).isEmpty())}
}

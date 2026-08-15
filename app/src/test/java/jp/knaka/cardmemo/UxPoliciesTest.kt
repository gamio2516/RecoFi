package jp.knaka.cardmemo

import org.junit.Assert.assertEquals
import org.junit.Test

class UxPoliciesTest {
    private val sources=listOf(PaymentSource("first","現金",PaymentSourceType.CASH),PaymentSource("default","カード",PaymentSourceType.CREDIT_CARD),PaymentSource("context","別カード",PaymentSourceType.CREDIT_CARD))
    @Test fun defaultUsedForAppInitialAndOrdinaryInput()=assertEquals("default",resolveInitialPaymentSourceId(sources,"default"))
    @Test fun displayedOrStatementContextTakesPriority()=assertEquals("context",resolveInitialPaymentSourceId(sources,"default","context"))
    @Test fun missingDefaultFallsBackToFirst()=assertEquals("first",resolveInitialPaymentSourceId(sources,"deleted"))
    @Test fun zeroTransactionsShowsFirstExpense()=assertEquals(ProgressiveHint.FIRST_EXPENSE,progressiveHint(0,true,0,0))
    @Test fun creditCardWithoutStatementShowsImport()=assertEquals(ProgressiveHint.IMPORT_STATEMENT,progressiveHint(1,true,0,0))
    @Test fun importedUnconfirmedShowsReview()=assertEquals(ProgressiveHint.REVIEW_CANDIDATES,progressiveHint(1,true,5,2))
    @Test fun normalStateShowsNoHint()=assertEquals(ProgressiveHint.NONE,progressiveHint(1,true,5,0))
}

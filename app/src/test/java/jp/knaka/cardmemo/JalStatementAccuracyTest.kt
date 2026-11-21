package jp.knaka.cardmemo

import org.junit.Assert.assertEquals
import org.junit.Test

class JalStatementAccuracyTest {
    @Test fun splitThousandsAreRejoined() {
        assertEquals(5_841L, StatementTools.combineMoneyPieces(listOf("5" to 660, "841" to 700), 650, 780))
        assertEquals(10_000L, StatementTools.combineMoneyPieces(listOf("10" to 660, "000" to 710), 650, 780))
    }

    @Test fun piecesOutsideBillingColumnAreIgnored() {
        assertEquals(7_480L, StatementTools.combineMoneyPieces(listOf("7" to 665, "480" to 705, "123" to 810), 650, 780))
    }

    @Test fun commaGroupJustOutsideBillingBoundaryIsRecovered() {
        assertEquals(3_140L, StatementTools.combineMoneyPieces(listOf("3" to 748, "140" to 786, "0" to 920), 650, 780))
    }

    @Test fun jalTotalPrefersTheAmountMatchingImportedRows() {
        assertEquals(28_978L, StatementTools.selectDeclaredTotal(listOf(30_000L, 28_978L, 28_978L), 28_978L, true))
    }

    @Test fun explanatoryAmountIsNotReadAsJalTotal() {
        val text = "お支払額 28,978円\nご案内 30,000円コース"
        assertEquals(28_978L, StatementTools.parseJalDeclaredTotal(text))
    }

    @Test fun amountOnUnrelatedFollowingLineIsNotAStatementTotal() {
        val text = "お支払額\nご案内 30,000円コース"
        assertEquals(null, StatementTools.parseJalDeclaredTotal(text))
    }
}


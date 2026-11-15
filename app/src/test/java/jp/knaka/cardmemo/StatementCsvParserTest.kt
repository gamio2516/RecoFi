package jp.knaka.cardmemo

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementCsvParserTest {
    private val month = YearMonth.of(2026, 7)

    @Test fun parsesQuotedCommaAndEscapedQuote() {
        val csv = "利用日,利用店名,利用金額\r\n7/1,\"店舗, \"\"本店\"\"\",\"1,200円\""
        val result = StatementCsvParser.parse(csv, month)
        assertEquals("店舗, \"本店\"", result.single().merchant)
        assertEquals(1200, result.single().amount)
    }

    @Test fun findsHeaderAfterPreambleAndUsesTargetYear() {
        val csv = "ビューカード明細\nご利用日,ご利用箇所,ご利用額\n07/15,JR東日本,500"
        val result = StatementCsvParser.parse(csv, month)
        assertEquals(LocalDate.of(2026, 7, 15), result.single().date)
    }

    @Test fun supportsRakutenHeaderNamesAndExplicitYear() {
        val csv = "ご利用年月日,ご利用店名,今回ご利用金額\n2026/07/20,楽天市場,\"-3,000\""
        assertEquals(3000, StatementCsvParser.parse(csv, month).single().amount)
    }

    @Test fun rakutenUsesCurrentMonthBillingAmountAndSkipsBlankRows() {
        val csv = "利用日,利用店名,利用金額,当月請求額\n7/1,当月の店,1000,600\n7/2,翌月扱いの店,2000,"
        val result = StatementCsvParser.parse(csv, month)
        assertEquals(1, result.size)
        assertEquals("当月の店", result.single().merchant)
        assertEquals(600, result.single().amount)
    }

    @Test fun readsRakutenPrintedStatementTotal() {
        assertEquals(123456L, StatementTools.parseRakutenDeclaredTotal("明細中の利用金額合計：123,456円"))
        assertEquals(358824L, StatementTools.parseRakutenDeclaredTotal("明細中の利用金額合計\n内）キャッシング 0\n358,824"))
        assertEquals(358824L, StatementTools.parseRakutenDeclaredTotal("2026年10月 明細中の利用金額合計\n358,824"))
        assertEquals(358824L, StatementTools.parseRakutenDeclaredTotal("10月お支払予定金額の明細\n358,824\n内）キャッシング 0\n本カード会員様利用分 358,824\n2026/10 358,824円"))
        assertEquals(358824L, StatementTools.parseRakutenSummaryTotal("10月お支払予定金額の明細 358,824 本カード会員様利用分 358,824"))
        assertEquals(300929L, StatementTools.parseRakutenSummaryTotal("9月お支払予定金額 300.929 本カード会員様利用分 300.929"))
    }

    @Test fun repairsAmountFromAnotherResolutionToMatchPrintedTotal() {
        val date = LocalDate.of(2026, 1, 10)
        val selected = listOf(
            CardStatementEntry(date, 984, "PAYPAL *KOPEKJP", ""),
            CardStatementEntry(date.minusDays(1), 1_000, "OTHER", ""),
        )
        val alternate = selected + CardStatementEntry(date, 5_984, "PAYPAL *KOPEKJP", "")
        val repaired = StatementTools.reconcileScaleResults(selected, alternate, 6_984)
        assertEquals(6_984, repaired.sumOf { it.amount })
        assertEquals(5_984, repaired.first { it.merchant == "PAYPAL *KOPEKJP" }.amount)
    }

    @Test fun addsRowSeenOnlyAtAnotherResolutionToMatchPrintedTotal() {
        val selected = listOf(CardStatementEntry(LocalDate.of(2026, 1, 10), 1_000, "A", ""))
        val alternate = selected + CardStatementEntry(LocalDate.of(2026, 1, 9), 5_000, "B", "")
        val repaired = StatementTools.reconcileScaleResults(selected, alternate, 6_000)
        assertEquals(listOf(1_000L, 5_000L), repaired.map { it.amount })
    }

    @Test fun identicalPurchasesArePreserved() {
        val csv = "日付,加盟店名,金額\n7/1,STAR BUCKS,500\n7/1,starbucks,500"
        assertEquals(2, StatementCsvParser.parse(csv, month).size)
    }

    @Test fun identicalRowsSeenTwiceAtOneResolutionAreNotCollapsed() {
        val row = CardStatementEntry(LocalDate.of(2026, 7, 4), 11_000, "同一店舗", "")
        val merged = StatementTools.mergeScaleRows(listOf(listOf(row), listOf(row, row), listOf(row)))
        val repaired = StatementTools.reconcileScaleResults(listOf(row), merged, 22_000)
        assertEquals(2, repaired.size)
        assertEquals(22_000, repaired.sumOf { it.amount })
    }

    @Test fun splitCurrentBillingHeaderUsesTheWholeColumn() {
        val bounds=StatementTools.billingColumnBounds(listOf("支払総額" to 500,"当月" to 600,"請求額" to 660,"翌月繰越" to 760))
        assertEquals(565,bounds?.first)
        assertEquals(695,bounds?.second)
        assertTrue(680 in bounds!!.first until bounds.second)
    }

    @Test fun missingRequiredHeaderReturnsEmpty() {
        assertTrue(StatementCsvParser.parse("店名,メモ\n店,A", month).isEmpty())
    }

    @Test fun multilineQuotedCellRemainsOneRow() {
        val rows = StatementCsvParser.tokenize("a,b\n\"line1\nline2\",c")
        assertEquals("line1\nline2", rows[1][0])
    }
}


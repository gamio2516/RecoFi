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

    @Test fun duplicateRowsAreRemovedUsingNormalizedMerchant() {
        val csv = "日付,加盟店名,金額\n7/1,STAR BUCKS,500\n7/1,starbucks,500"
        assertEquals(1, StatementCsvParser.parse(csv, month).size)
    }

    @Test fun missingRequiredHeaderReturnsEmpty() {
        assertTrue(StatementCsvParser.parse("店名,メモ\n店,A", month).isEmpty())
    }

    @Test fun multilineQuotedCellRemainsOneRow() {
        val rows = StatementCsvParser.tokenize("a,b\n\"line1\nline2\",c")
        assertEquals("line1\nline2", rows[1][0])
    }
}

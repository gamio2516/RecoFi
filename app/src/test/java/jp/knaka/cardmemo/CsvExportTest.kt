package jp.knaka.cardmemo
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
class CsvExportTest {
 @Test fun exportsMerchantAndDescriptionAsSeparateColumns(){val usedAt=LocalDate.of(2026,7,5).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();val tx=Transaction(1,1200L,"日用品","Amazon","洗剤",usedAt,paymentSourceId="card");val out=ByteArrayOutputStream();StatementTools.writeMonthlyCsv(out,YearMonth.of(2026,7),listOf(tx),mapOf("card" to "カード"));val csv=out.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF");assertTrue(csv.startsWith("利用日,支払方法,項目,金額,取引先,内容,固定費"));assertTrue(csv.contains("2026-07-05,カード,日用品,1200,Amazon,洗剤,いいえ"))}
 @Test fun escapesMerchantAndDescriptionIndependently(){val usedAt=LocalDate.of(2026,7,5).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();val tx=Transaction(1,1200L,"日用品","A,店","\"洗剤\"",usedAt,paymentSourceId="card");val out=ByteArrayOutputStream();StatementTools.writeMonthlyCsv(out,YearMonth.of(2026,7),listOf(tx),mapOf("card" to "カード"));val csv=out.toString(Charsets.UTF_8.name());assertTrue(csv.contains("\"A,店\""));assertTrue(csv.contains("\"\"\"洗剤\"\"\""))}
}

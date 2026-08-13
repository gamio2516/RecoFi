package jp.knaka.cardmemo
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
class StatementFingerprintTest {
 @Test fun orderDoesNotAffect(){val a=listOf(entry("2026-07-01",100L,"A"),entry("2026-07-02",200L,"B"));assertEquals(StatementTools.statementFingerprint(a),StatementTools.statementFingerprint(a.reversed()))}
 @Test fun merchantFormattingNormalized(){assertEquals(StatementTools.statementFingerprint(listOf(entry("2026-07-01",100L,"ＡＢＣ 店"))),StatementTools.statementFingerprint(listOf(entry("2026-07-01",100L,"abc店"))))}
 @Test fun merchantDifferenceChanges(){assertNotEquals(StatementTools.statementFingerprint(listOf(entry("2026-07-01",100L,"A"))),StatementTools.statementFingerprint(listOf(entry("2026-07-01",100L,"B"))))}
 @Test fun amountAndDateChangeFingerprint(){val base=StatementTools.statementFingerprint(listOf(entry("2026-07-01",100L,"A")));assertNotEquals(base,StatementTools.statementFingerprint(listOf(entry("2026-07-01",101L,"A"))));assertNotEquals(base,StatementTools.statementFingerprint(listOf(entry("2026-07-02",100L,"A"))))}
 private fun entry(date:String,amount:Long,merchant:String)=CardStatementEntry(LocalDate.parse(date),amount,merchant,"")
}

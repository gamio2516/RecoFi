package jp.knaka.cardmemo

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object StatementFingerprint {
    fun calculate(entries: List<CardStatementEntry>): String {
        val canonical = entries.sortedWith(compareBy<CardStatementEntry> { it.date }.thenBy { it.amount }.thenBy { ReconciliationMatcher.normalizeMerchant(it.merchant) })
            .joinToString("\n") { "${it.date}|${it.amount}|${ReconciliationMatcher.normalizeMerchant(it.merchant)}" }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8))
        return "statement:" + digest.joinToString("") { "%02x".format(it) }
    }
}

package jp.knaka.cardmemo.storage

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyMigrationParserTest {
    private val parser = LegacyMigrationParser(nowMillis = 100L)
    private fun base() = mutableMapOf<String, Any?>(
        "categories" to JSONArray(listOf("食料品", "その他")).toString(),
        "payment_sources" to JSONArray().put(JSONObject().put("id", "rakuten").put("name", "楽天カード").put("isCard", true)).toString(),
    )
    private fun transaction(category: String = "食料品", source: String = "rakuten", recurringId: Long? = null) = JSONObject().apply {
        put("id", 1L); put("amount", 1234L); put("category", category); put("note", "店"); put("usedAt", 1L); put("paymentSourceId", source); recurringId?.let { put("recurringId", it) }
    }
    private fun failure(values: Map<String, Any?>) = (parser.parse(LegacySnapshot(values)) as LegacyParseResult.Failure).errors

    @Test fun emptyValidSnapshotUsesDefaults() {
        val result = parser.parse(LegacySnapshot(emptyMap())) as LegacyParseResult.Success
        assertEquals(8, result.data.categories.size)
        assertEquals(2, result.data.paymentSources.size)
    }

    @Test fun amountsAreDecodedAsLong() {
        val values = base(); values["transactions"] = JSONArray().put(transaction()).toString()
        val result = parser.parse(LegacySnapshot(values)) as LegacyParseResult.Success
        assertEquals(1234L, result.data.transactions.single().amount)
    }

    @Test fun duplicateIdenticalCategoriesAreSafelyMerged() {
        val values = base(); values["categories"] = JSONArray(listOf("食料品", "食料品", "その他")).toString()
        val result = parser.parse(LegacySnapshot(values)) as LegacyParseResult.Success
        assertEquals(2, result.data.categories.size)
        assertTrue(result.data.corrections.any { it.contains("重複カテゴリ") })
    }

    @Test fun conflictingPaymentSourceIdStopsMigration() {
        val values = base(); values["payment_sources"] = JSONArray()
            .put(JSONObject().put("id", "x").put("name", "A"))
            .put(JSONObject().put("id", "x").put("name", "B")).toString()
        assertTrue(failure(values).any { it.contains("同じID") })
    }

    @Test fun duplicatePaymentSourceNameWithDifferentIdsStopsMigration() {
        val values = base(); values["payment_sources"] = JSONArray()
            .put(JSONObject().put("id", "x").put("name", "A"))
            .put(JSONObject().put("id", "y").put("name", "A")).toString()
        assertTrue(failure(values).any { it.contains("同じ名称") })
    }

    @Test fun missingTransactionCategoryStopsMigration() {
        val values = base(); values["transactions"] = JSONArray().put(transaction(category = "不明")).toString()
        assertTrue(failure(values).any { it.contains("存在しないカテゴリ") })
    }

    @Test fun missingTransactionPaymentSourceStopsMigration() {
        val values = base(); values["transactions"] = JSONArray().put(transaction(source = "missing")).toString()
        assertTrue(failure(values).any { it.contains("存在しない支払方法") })
    }

    @Test fun missingRecurringReferenceStopsMigration() {
        val values = base(); values["transactions"] = JSONArray().put(transaction(recurringId = 99)).toString()
        assertTrue(failure(values).any { it.contains("存在しない固定費") })
    }

    @Test fun malformedMonthStopsMigration() {
        val values = base(); values["locked_months"] = JSONArray(listOf("2026-13")).toString()
        assertTrue(failure(values).any { it.contains("年月が不正") })
    }

    @Test fun negativeMoneyStopsMigration() {
        val values = base(); values["transactions"] = JSONArray().put(transaction().put("amount", -1)).toString()
        assertTrue(failure(values).any { it.contains("金額が負数") })
    }

    @Test fun ambiguousLegacyBudgetStopsMigration() {
        val values = base(); values["monthly_budget"] = 300000
        assertTrue(failure(values).any { it.contains("対象月を特定できない") })
    }

    @Test fun fingerprintIsStableRegardlessOfMapOrder() {
        val first = LegacySnapshot(linkedMapOf("b" to "2", "a" to "1"))
        val second = LegacySnapshot(linkedMapOf("a" to "1", "b" to "2"))
        assertEquals(first.fingerprint, second.fingerprint)
    }
}

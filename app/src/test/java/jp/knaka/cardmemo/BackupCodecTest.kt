package jp.knaka.cardmemo

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private val preferences = mapOf<String, Any>(
        "transactions" to JSONArray().put(JSONObject().apply { put("id", 1L); put("amount", 500); put("category", "食費"); put("usedAt", 1L) }).toString(),
        "recurring_expenses" to "[]",
        "imported_statements" to "[]",
        "payment_sources" to JSONArray().put(JSONObject().apply { put("id", "card"); put("name", "カード") }).toString(),
        "categories" to JSONArray(listOf("食費")).toString(),
        "default_monthly_budget" to 100_000,
        "locked_months" to setOf("2026-07"),
    )

    @Test fun roundTripPreservesMetadataCountsAndPreferences() {
        val decoded = BackupCodec.decodeAndValidate(BackupCodec.encode(preferences, "1.0", "2026-08-12T00:00:00Z"))
        assertEquals("1.0", decoded.appVersion)
        assertEquals(1, decoded.counts.transactions)
        assertEquals(1, decoded.counts.paymentSources)
        assertEquals(100_000, decoded.preferences["default_monthly_budget"])
        assertEquals(setOf("2026-07"), decoded.preferences["locked_months"])
    }

    @Test fun unsupportedFormatVersionIsRejected() {
        val json = JSONObject(BackupCodec.encode(preferences, "1.0")).put("backupFormatVersion", 99).toString()
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decodeAndValidate(json) }
    }

    @Test fun missingRequiredMetadataIsRejected() {
        val json = JSONObject(BackupCodec.encode(preferences, "1.0")).apply { remove("createdAt") }.toString()
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decodeAndValidate(json) }
    }

    @Test fun corruptJsonIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decodeAndValidate("{broken") }
    }

    @Test fun mismatchedCountsAreRejected() {
        val json = JSONObject(BackupCodec.encode(preferences, "1.0"))
        json.getJSONObject("counts").put("transactions", 999)
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decodeAndValidate(json.toString()) }
    }

    @Test fun malformedMajorDataIsRejected() {
        val invalid = preferences + ("transactions" to JSONArray().put(JSONObject().apply { put("amount", 500) }).toString())
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.encode(invalid, "1.0") }
    }

    @Test fun reconciliationAndLockStateArePreserved() {
        val transaction = JSONObject().apply {
            put("id", 7L); put("amount", 1200); put("category", "外食"); put("usedAt", 1L)
            put("confirmed", true); put("paymentSourceId", "rakuten"); put("reconciledMonth", "2026-07"); put("suggested", true)
        }
        val progress = JSONObject().put("2026-07|rakuten", JSONObject().apply {
            put("imported", 3); put("matched", 2); put("suggested", 1); put("confirmed", 3)
        })
        val source = preferences + mapOf(
            "transactions" to JSONArray().put(transaction).toString(),
            "reconciliation_progress" to progress.toString(),
            "locked_months" to JSONArray(listOf("2026-07")).toString(),
        )
        val decoded = BackupCodec.decodeAndValidate(BackupCodec.encode(source, "1.0"))
        val restoredTransaction = JSONArray(decoded.preferences["transactions"] as String).getJSONObject(0)
        val restoredProgress = JSONObject(decoded.preferences["reconciliation_progress"] as String).getJSONObject("2026-07|rakuten")
        assertEquals(true, restoredTransaction.getBoolean("confirmed"))
        assertEquals(true, restoredTransaction.getBoolean("suggested"))
        assertEquals("2026-07", restoredTransaction.getString("reconciledMonth"))
        assertEquals(2, restoredProgress.getInt("matched"))
        assertEquals(1, restoredProgress.getInt("suggested"))
        assertEquals(3, restoredProgress.getInt("confirmed"))
        assertEquals("2026-07", JSONArray(decoded.preferences["locked_months"] as String).getString(0))
    }

    @Test fun missingReconciliationStateFieldIsRejected() {
        val source = preferences + ("reconciliation_progress" to JSONObject().put("2026-07|rakuten", JSONObject().apply {
            put("imported", 1); put("matched", 1); put("suggested", 0); put("confirmed", 1)
        }).toString())
        val root = JSONObject(BackupCodec.encode(source, "1.0"))
        val stored = root.getJSONObject("preferences").getJSONObject("reconciliation_progress")
        val progress = JSONObject(stored.getString("value"))
        progress.getJSONObject("2026-07|rakuten").remove("matched")
        stored.put("value", progress.toString())
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decodeAndValidate(root.toString()) }
    }
}

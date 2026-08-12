package jp.knaka.cardmemo.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomSchemaContractTest {
    @Test fun monetaryFieldsUseLong() {
        listOf(
            TransactionEntity::class.java to "amount",
            RecurringExpenseEntity::class.java to "amount",
            RecurringPriceRevisionEntity::class.java to "amount",
            StatementEntryEntity::class.java to "amount",
            MonthlyBudgetEntity::class.java to "amount",
            AppBudgetSettingsEntity::class.java to "defaultMonthlyBudget",
        ).forEach { (type, field) -> assertEquals("$type.$field", Long::class.javaPrimitiveType, type.getDeclaredField(field).type) }
    }

    @Test fun databaseUsesStableNonLegacyFileName() {
        assertEquals("recofi.db", RecoFiDatabase.DATABASE_NAME)
        assertFalse(RecoFiDatabase.DATABASE_NAME.contains("card_memo"))
    }

    @Test fun importedStatementCanRemainUnlinkedUntilUserLinksIt() {
        val item = ImportedStatementEntity("hash", null, null, "statement.csv")
        assertEquals(null, item.statementMonth)
        assertEquals(null, item.paymentSourceId)
    }

    @Test fun migrationRecordExplicitlyMarksLegacyAsRetained() {
        val item = StorageMigrationEntity(1, "fingerprint", 123L, sourceRetained = true)
        assertTrue(item.sourceRetained)
        assertFalse(item.legacyFingerprint.isBlank())
    }
}

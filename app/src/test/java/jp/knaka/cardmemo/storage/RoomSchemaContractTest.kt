package jp.knaka.cardmemo.storage
import org.junit.Assert.*
import org.junit.Test
class RoomSchemaContractTest {
 @Test fun monetaryFieldsUseLong(){assertEquals(Long::class.javaPrimitiveType,TransactionEntity::class.java.getDeclaredField("amount").type);assertEquals(Long::class.javaPrimitiveType,RecurringExpenseEntity::class.java.getDeclaredField("amount").type);assertEquals(Long::class.javaPrimitiveType,RecurringPriceRevisionEntity::class.java.getDeclaredField("amount").type);assertEquals(Long::class.javaPrimitiveType,StatementEntryEntity::class.java.getDeclaredField("amount").type);assertEquals(Long::class.javaPrimitiveType,MonthlyBudgetEntity::class.java.getDeclaredField("amount").type)}
 @Test fun transactionHasMerchantAndDescription(){assertNotNull(TransactionEntity::class.java.getDeclaredField("merchant"));assertNotNull(TransactionEntity::class.java.getDeclaredField("description"));assertThrows(NoSuchFieldException::class.java){TransactionEntity::class.java.getDeclaredField("note")}}
 @Test fun categoryUsesStableIdAndRecurringUsesPaymentDay(){assertNotNull(CategoryEntity::class.java.getDeclaredField("id"));assertNotNull(TransactionEntity::class.java.getDeclaredField("categoryId"));assertNotNull(RecurringExpenseEntity::class.java.getDeclaredField("categoryId"));assertNotNull(RecurringExpenseEntity::class.java.getDeclaredField("paymentDay"))}
 @Test fun databaseUsesStableFileName(){assertEquals("recofi.db",RecoFiDatabase.DATABASE_NAME)}
 @Test fun importedStatementMayRemainUnlinked(){val e=ImportedStatementEntity("hash",null,null,"file.csv");assertNull(e.statementMonth);assertNull(e.paymentSourceId)}
}

package jp.knaka.cardmemo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import jp.knaka.cardmemo.storage.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ReconciliationRepositoryTest {
    private lateinit var db: RecoFiDatabase
    private lateinit var repository: ReconciliationRepository
    private val month = YearMonth.of(2026, 8)
    private val usedAt = LocalDate.of(2026, 8, 10).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RecoFiDatabase::class.java).allowMainThreadQueries().build()
        repository = ReconciliationRepository(db, true)
        db.referenceData().upsertPaymentSources(listOf(PaymentSourceEntity("card", "楽天カード", "CREDIT_CARD", 0)))
        db.referenceData().upsertCategories(listOf(CategoryEntity("外食", 0)))
        db.statements().upsertStatements(listOf(ImportedStatementEntity("hash", month.toString(), "card", "statement.csv")))
        db.statements().upsertEntries(listOf(StatementEntryEntity(101, "hash", 0, "2026-08-10", 1200, "STARBUCKS", "raw")))
    }
    @After fun close() = db.close()

    private fun tx(id: Long = 1, merchant: String = "スターバックス") = Transaction(id, 1200, "外食", merchant, "コーヒー", usedAt, paymentSourceId = "card")
    private fun persist(vararg items: Transaction) = db.transactions().upsertAll(items.map { TransactionEntity(it.id,it.amount,it.category,it.merchant,it.description,it.usedAt,it.recurringId,it.paymentSourceId) })

    @Test fun `match relation is persisted and loaded`() {
        db.transactions().upsertAll(listOf(TransactionEntity(1, 1200, "外食", "スターバックス", "コーヒー", usedAt, null, "card")))
        repository.confirm(101, 1)
        val restored = ReconciliationRepository(db, true).reviewRows("hash").single()
        assertEquals(ReconciliationStatus.CONFIRMED.name, restored.match?.status)
        assertEquals(1L, restored.transaction?.id)
    }

    @Test fun `automatic matching is idempotent`() {
        persist(tx())
        repository.autoMatch("hash", "card", listOf(tx()))
        val first = db.reconciliation().loadMatch(101)
        repository.autoMatch("hash", "card", listOf(tx()))
        val second = db.reconciliation().loadMatch(101)
        assertEquals(first?.transactionId, second?.transactionId)
        assertEquals(first?.status, second?.status)
        assertEquals(first?.matchSource, second?.matchSource)
    }

    @Test fun `confirmed user match survives automatic rerun`() {
        db.transactions().upsertAll(listOf(TransactionEntity(1, 1200, "外食", "手動選択", "", usedAt, null, "card")))
        repository.confirm(101, 1)
        persist(tx(2)); repository.autoMatch("hash", "card", listOf(tx(2)))
        val match = db.reconciliation().loadMatch(101)!!
        assertEquals(1L, match.transactionId)
        assertEquals(MatchSource.USER.name, match.matchSource)
    }

    @Test fun `rejected pair is not suggested again`() {
        persist(tx())
        repository.autoMatch("hash", "card", listOf(tx()))
        repository.reject(101, 1)
        repository.autoMatch("hash", "card", listOf(tx()))
        assertEquals(ReconciliationStatus.PENDING.name, db.reconciliation().loadMatch(101)?.status)
        assertEquals(1, db.reconciliation().loadRejected().size)
    }

    @Test fun `manual confirmation changes derived progress`() {
        db.transactions().upsertAll(listOf(TransactionEntity(1, 1200, "外食", "スターバックス", "", usedAt, null, "card")))
        repository.autoMatch("hash", "card", listOf(tx()))
        assertEquals(1, repository.progress(month, "card").needsReview)
        repository.confirm(101, 1)
        val progress = repository.progress(month, "card")
        assertEquals(1, progress.confirmed)
        assertEquals(0, progress.remaining)
    }

    @Test fun `missing transaction is created only by explicit save and confirmed atomically`() {
        assertTrue(db.transactions().loadAll().isEmpty())
        repository.createAndConfirm(101, tx(9))
        assertEquals(1, db.transactions().loadAll().size)
        assertEquals(9L, db.reconciliation().loadMatch(101)?.transactionId)
        assertEquals(ReconciliationStatus.CONFIRMED.name, db.reconciliation().loadMatch(101)?.status)
    }

    @Test fun `credit card requires import or explicit no activity declaration`() {
        db.statements().deleteStatement("hash")
        val source = PaymentSource("card", "楽天カード", PaymentSourceType.CREDIT_CARD)
        assertTrue(repository.canLock(month, listOf(source)).single().missingStatement)
        repository.declareNoActivity(month, "card")
        assertTrue(repository.canLock(month, listOf(source)).isEmpty())
    }

    @Test fun `cash source does not require statement import`() {
        db.statements().deleteStatement("hash")
        assertTrue(repository.canLock(month, listOf(PaymentSource("cash", "現金", PaymentSourceType.CASH))).isEmpty())
    }
}

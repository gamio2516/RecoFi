package jp.knaka.cardmemo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconciliationPolicyTest {
    @Test fun noImportedStatementCannotBeLocked() {
        assertFalse(ReconciliationPolicy.canLock(emptyList()))
    }

    @Test fun incompleteStatementCannotBeLocked() {
        assertFalse(ReconciliationPolicy.canLock(listOf(ReconciliationProgress(imported = 3, confirmed = 2))))
    }

    @Test fun fullyConfirmedStatementCanBeLocked() {
        assertTrue(ReconciliationPolicy.canLock(listOf(ReconciliationProgress(imported = 3, confirmed = 3))))
    }

    @Test fun everyPaymentSourceMustBeComplete() {
        val progress = listOf(
            ReconciliationProgress(imported = 2, confirmed = 2),
            ReconciliationProgress(imported = 2, confirmed = 1),
        )
        assertFalse(ReconciliationPolicy.canLock(progress))
    }
}

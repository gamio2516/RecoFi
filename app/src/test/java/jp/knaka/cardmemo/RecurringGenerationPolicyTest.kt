package jp.knaka.cardmemo

import java.time.YearMonth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringGenerationPolicyTest {
    private val current = YearMonth.of(2026, 11)

    @Test fun pastMonthIsReadOnly() {
        assertFalse(shouldGenerateRecurringFor(YearMonth.of(2025, 12), current))
        assertFalse(shouldGenerateRecurringFor(YearMonth.of(2026, 1), current))
    }

    @Test fun currentAndFutureMonthsRemainAutomatic() {
        assertTrue(shouldGenerateRecurringFor(current, current))
        assertTrue(shouldGenerateRecurringFor(YearMonth.of(2026, 12), current))
    }
}



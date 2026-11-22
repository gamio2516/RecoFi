package jp.knaka.cardmemo

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal val MAX_TRANSACTION_DATE: LocalDate = LocalDate.of(2050, 12, 31)
internal val MAX_TRANSACTION_MONTH: YearMonth = YearMonth.from(MAX_TRANSACTION_DATE)

internal fun canStoreTransactionAt(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Boolean =
    !Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate().isAfter(MAX_TRANSACTION_DATE)

package jp.knaka.cardmemo.storage

/** Room access boundary. UI/ViewModel code must depend on repositories rather than DAOs. */
class RecoFiRoomRepository(val database: RecoFiDatabase) {
    val transactions: TransactionDao get() = database.transactions()
    val referenceData: ReferenceDataDao get() = database.referenceData()
    val recurringExpenses: RecurringExpenseDao get() = database.recurringExpenses()
    val statements: StatementDao get() = database.statements()
    val monthlyState: MonthlyStateDao get() = database.monthlyState()
}

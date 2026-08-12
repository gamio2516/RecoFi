package jp.knaka.cardmemo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val transactionRepository = TransactionRepository(application)
    private val settingsRepository = AppSettingsRepository(application)
    private val _transactions = MutableStateFlow(transactionRepository.load())
    val transactions = _transactions.asStateFlow()
    private val _categories = MutableStateFlow(settingsRepository.loadCategories())
    val categories = _categories.asStateFlow()
    private val initialSources = settingsRepository.loadPaymentSources()
    private val _notesBySource = MutableStateFlow(initialSources.associate { it.id to settingsRepository.loadNotes(it.id) })
    val notesBySource = _notesBySource.asStateFlow()
    private val _recurringExpenses = MutableStateFlow(settingsRepository.loadRecurringExpenses())
    val recurringExpenses = _recurringExpenses.asStateFlow()
    private val _monthlyBudget = MutableStateFlow(settingsRepository.loadMonthlyBudgets())
    val monthlyBudget = _monthlyBudget.asStateFlow()
    private val _defaultMonthlyBudget = MutableStateFlow(settingsRepository.loadDefaultMonthlyBudget())
    val defaultMonthlyBudget = _defaultMonthlyBudget.asStateFlow()
    private val _paymentSources = MutableStateFlow(initialSources)
    val paymentSources = _paymentSources.asStateFlow()
    private val _lockedMonths = MutableStateFlow(settingsRepository.loadLockedMonths())
    val lockedMonths = _lockedMonths.asStateFlow()
    private val _importedFileHashes = MutableStateFlow(settingsRepository.loadImportedFileHashes())
    private val _reconciliationProgress = MutableStateFlow(settingsRepository.loadReconciliationProgress())
    val reconciliationProgress = _reconciliationProgress.asStateFlow()
    private val _importedStatements = MutableStateFlow(settingsRepository.loadImportedStatements())
    val importedStatements = _importedStatements.asStateFlow()

    init { ensureRecurringFor(YearMonth.now()) }

    fun addTransaction(amount: Int, category: String, note: String, usedAt: Long, paymentSourceId: String, id: Long? = null) {
        val item = Transaction(id ?: System.currentTimeMillis(), amount, category, note, usedAt, paymentSourceId = paymentSourceId)
        updateTransactions(if (id == null) _transactions.value + item else _transactions.value.map { if (it.id == id) item.copy(confirmed = it.confirmed, recurringId = it.recurringId, reconciledMonth = it.reconciledMonth, suggested = it.suggested) else it })
    }

    fun deleteTransaction(id: Long) = updateTransactions(_transactions.value.filterNot { it.id == id })
    fun toggleConfirmed(id: Long) {
        val target = _transactions.value.firstOrNull { it.id == id } ?: return
        val usedMonth = target.yearMonth().toString()
        if (target.reconciledMonth in _lockedMonths.value || usedMonth in _lockedMonths.value) return
        updateTransactions(_transactions.value.map { if (it.id == id) it.copy(confirmed = !it.confirmed, reconciledMonth = if (it.confirmed) null else it.reconciledMonth, suggested = if (!it.confirmed) false else it.suggested) else it })
    }
    fun confirmTransactions(ids: Set<Long>, statementMonth: YearMonth) {
        if (ids.isEmpty()) return
        if (statementMonth.toString() in _lockedMonths.value) return
        updateTransactions(_transactions.value.map { if (it.id in ids) it.copy(confirmed = true, reconciledMonth = statementMonth.toString()) else it })
        ids.mapNotNull { id -> _transactions.value.firstOrNull { it.id == id }?.paymentSourceId }.toSet().forEach { sourceId ->
            val key = "${statementMonth}|$sourceId"
            val current = _reconciliationProgress.value[key] ?: return@forEach
            val count = _transactions.value.count { it.confirmed && it.reconciledMonth == statementMonth.toString() && it.paymentSourceId == sourceId }
            _reconciliationProgress.value = _reconciliationProgress.value + (key to current.copy(confirmed = count.coerceAtMost(current.imported)))
        }
        settingsRepository.saveReconciliationProgress(_reconciliationProgress.value)
    }
    fun setMonthLocked(month: YearMonth, locked: Boolean) {
        _lockedMonths.value = if (locked) _lockedMonths.value + month.toString() else _lockedMonths.value - month.toString()
        settingsRepository.saveLockedMonths(_lockedMonths.value)
    }
    fun isImportedFile(hash: String): Boolean = hash in _importedFileHashes.value
    fun recordImportedFile(hash: String) {
        _importedFileHashes.value = _importedFileHashes.value + hash
        settingsRepository.saveImportedFileHashes(_importedFileHashes.value)
    }
    fun setReconciliationProgress(month: YearMonth, sourceId: String, imported: Int, matched: Int, suggested: Int, confirmed: Int) {
        _reconciliationProgress.value = _reconciliationProgress.value + ("${month}|$sourceId" to ReconciliationProgress(imported, matched, suggested, confirmed))
        settingsRepository.saveReconciliationProgress(_reconciliationProgress.value)
    }
    fun saveImportedStatement(statement: ImportedStatement) {
        _importedStatements.value = _importedStatements.value.filterNot { it.fileHash == statement.fileHash } + statement
        settingsRepository.saveImportedStatements(_importedStatements.value)
    }
    fun linkImportedStatement(fileHash: String, month: YearMonth, sourceId: String): Boolean {
        if (_importedStatements.value.any { it.fileHash != fileHash && it.statementMonth == month.toString() && it.paymentSourceId == sourceId }) return false
        _importedStatements.value = _importedStatements.value.map { if (it.fileHash == fileHash) it.copy(statementMonth = month.toString(), paymentSourceId = sourceId) else it }
        settingsRepository.saveImportedStatements(_importedStatements.value)
        val entries = _importedStatements.value.firstOrNull { it.fileHash == fileHash }?.entries?.size ?: 0
        setReconciliationProgress(month, sourceId, entries, 0, 0, 0)
        return true
    }
    fun deleteImportedStatement(fileHash: String) {
        val target = _importedStatements.value.firstOrNull { it.fileHash == fileHash } ?: return
        _importedStatements.value = _importedStatements.value.filterNot { it.fileHash == fileHash }
        settingsRepository.saveImportedStatements(_importedStatements.value)
        if (target.paymentSourceId.isNotBlank()) {
            _reconciliationProgress.value = _reconciliationProgress.value - "${target.statementMonth}|${target.paymentSourceId}"
            settingsRepository.saveReconciliationProgress(_reconciliationProgress.value)
        }
        _importedFileHashes.value = _importedFileHashes.value - fileHash - StatementTools.statementFingerprint(target.entries)
        settingsRepository.saveImportedFileHashes(_importedFileHashes.value)
    }
    fun addSuggestedTransactions(entries: List<CardStatementEntry>, sourceId: String, statementMonth: YearMonth) {
        var updated = _transactions.value
        entries.forEach { entry ->
            val usedAt = entry.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val exists = updated.any { it.paymentSourceId == sourceId && it.amount == entry.amount && it.usedAt == usedAt && normalizeMerchant(it.note) == normalizeMerchant(entry.merchant) }
            if (!exists) {
                val category = _transactions.value.filter { normalizeMerchant(it.note) == normalizeMerchant(entry.merchant) }.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key ?: _categories.value.firstOrNull { it == "その他" } ?: _categories.value.lastOrNull().orEmpty()
                updated += Transaction(System.nanoTime(), entry.amount, category, entry.merchant, usedAt, confirmed = false, paymentSourceId = sourceId, reconciledMonth = statementMonth.toString(), suggested = true)
            }
        }
        updateTransactions(updated)
    }

    fun addCategory(value: String) { val clean = value.trim(); if (_categories.value.size < 15 && clean.isNotEmpty() && clean !in _categories.value) { _categories.value += clean; settingsRepository.saveCategories(_categories.value) } }
    fun deleteCategory(value: String) { if (_categories.value.size > 1) { _categories.value -= value; settingsRepository.saveCategories(_categories.value) } }
    fun moveCategory(value: String, offset: Int) { val index = _categories.value.indexOf(value); val target = index + offset; if (index >= 0 && target in _categories.value.indices) { val updated = _categories.value.toMutableList(); val moved = updated.removeAt(index); updated.add(target, moved); _categories.value = updated; settingsRepository.saveCategories(updated) } }
    fun addFrequentNote(sourceId: String, value: String) { val clean = value.trim(); val current = _notesBySource.value[sourceId].orEmpty(); if (current.size < 15 && clean.isNotEmpty() && clean !in current) { val updated = current + clean; _notesBySource.value = _notesBySource.value + (sourceId to updated); settingsRepository.saveNotes(sourceId, updated) } }
    fun deleteFrequentNote(sourceId: String, value: String) { val updated = _notesBySource.value[sourceId].orEmpty() - value; _notesBySource.value = _notesBySource.value + (sourceId to updated); settingsRepository.saveNotes(sourceId, updated) }
    fun moveFrequentNote(sourceId: String, value: String, offset: Int) { val current = _notesBySource.value[sourceId].orEmpty(); val index = current.indexOf(value); val target = index + offset; if (index >= 0 && target in current.indices) { val updated = current.toMutableList(); val moved = updated.removeAt(index); updated.add(target, moved); _notesBySource.value = _notesBySource.value + (sourceId to updated); settingsRepository.saveNotes(sourceId, updated) } }

    fun setMonthlyBudget(month: YearMonth, amount: Int) { val clean = amount.coerceAtLeast(0); _monthlyBudget.value = if (clean == 0) _monthlyBudget.value - month.toString() else _monthlyBudget.value + (month.toString() to clean); settingsRepository.saveMonthlyBudgets(_monthlyBudget.value) }
    fun setDefaultMonthlyBudget(amount: Int) { _defaultMonthlyBudget.value = amount.coerceAtLeast(0); settingsRepository.saveDefaultMonthlyBudget(_defaultMonthlyBudget.value) }

    fun addPaymentSource(name: String) { val clean = name.trim(); if (clean.isNotEmpty() && _paymentSources.value.none { it.name == clean }) { val source = PaymentSource("source_${System.currentTimeMillis()}", clean, false); _paymentSources.value += source; _notesBySource.value = _notesBySource.value + (source.id to emptyList()); settingsRepository.savePaymentSources(_paymentSources.value) } }
    fun deletePaymentSource(id: String) { if (_paymentSources.value.size > 1 && _transactions.value.none { it.paymentSourceId == id } && _recurringExpenses.value.none { it.paymentSourceId == id }) { _paymentSources.value = _paymentSources.value.filterNot { it.id == id }; settingsRepository.savePaymentSources(_paymentSources.value) } }

    fun saveRecurringExpense(id: Long?, amount: Int, billingDay: Int, category: String, note: String, contractDate: LocalDate, paymentSourceId: String, intervalMonths: Int) {
        val previous = _recurringExpenses.value.firstOrNull { it.id == id }
        val item = RecurringExpense(id ?: System.currentTimeMillis(), amount, category, note.trim(), billingDay.coerceIn(1, 31), YearMonth.from(contractDate).toString(), contractDate.toString(), paymentSourceId, intervalMonths.coerceAtLeast(1), previous?.endDate, previous?.priceRevisions.orEmpty())
        _recurringExpenses.value = if (id == null) _recurringExpenses.value + item else _recurringExpenses.value.map { if (it.id == id) item else it }
        settingsRepository.saveRecurringExpenses(_recurringExpenses.value)
        if (id != null) updateTransactions(_transactions.value.filterNot { it.recurringId == id && it.yearMonth() >= YearMonth.now() })
        ensureRecurringFor(YearMonth.now())
    }

    fun endRecurringExpense(id: Long, endDate: LocalDate) {
        _recurringExpenses.value = _recurringExpenses.value.map { if (it.id == id) it.copy(endDate = endDate.toString()) else it }
        settingsRepository.saveRecurringExpenses(_recurringExpenses.value)
        updateTransactions(_transactions.value.filterNot { it.recurringId == id && Instant.ofEpochMilli(it.usedAt).atZone(ZoneId.systemDefault()).toLocalDate().isAfter(endDate) })
    }

    fun reviseRecurringExpense(id: Long, effectiveDate: LocalDate, amount: Int) {
        _recurringExpenses.value = _recurringExpenses.value.map { expense -> if (expense.id == id) expense.copy(priceRevisions = (expense.priceRevisions.filterNot { it.effectiveDate == effectiveDate.toString() } + PriceRevision(effectiveDate.toString(), amount)).sortedBy { it.effectiveDate }) else expense }
        settingsRepository.saveRecurringExpenses(_recurringExpenses.value)
        updateTransactions(_transactions.value.filterNot { transaction -> transaction.recurringId == id && !Instant.ofEpochMilli(transaction.usedAt).atZone(ZoneId.systemDefault()).toLocalDate().isBefore(effectiveDate) })
        ensureRecurringFor(YearMonth.now())
    }

    fun deleteRecurringExpense(id: Long) {
        _recurringExpenses.value = _recurringExpenses.value.filterNot { it.id == id }
        settingsRepository.saveRecurringExpenses(_recurringExpenses.value)
    }

    fun duplicateRecurringExpense(id: Long) {
        val source = _recurringExpenses.value.firstOrNull { it.id == id } ?: return
        val today = LocalDate.now()
        val currentAmount = source.priceRevisions.filter { !LocalDate.parse(it.effectiveDate).isAfter(today) }.maxByOrNull { it.effectiveDate }?.amount ?: source.amount
        val duplicate = source.copy(id = System.currentTimeMillis(), amount = currentAmount, contractDate = today.toString(), startMonth = YearMonth.from(today).toString(), endDate = null, priceRevisions = emptyList())
        _recurringExpenses.value += duplicate
        settingsRepository.saveRecurringExpenses(_recurringExpenses.value)
        ensureRecurringFor(YearMonth.now())
    }

    fun ensureRecurringFor(month: YearMonth) {
        var updated = _transactions.value
        _recurringExpenses.value.filter { month >= YearMonth.parse(it.startMonth) }.forEach { expense ->
            val date = month.atDay(expense.billingDay.coerceAtMost(month.lengthOfMonth()))
            if (date.isBefore(LocalDate.parse(expense.contractDate))) return@forEach
            if (expense.endDate?.let { date.isAfter(LocalDate.parse(it)) } == true) return@forEach
            val contract = LocalDate.parse(expense.contractDate)
            var firstMonth = YearMonth.from(contract)
            if (firstMonth.atDay(expense.billingDay.coerceAtMost(firstMonth.lengthOfMonth())).isBefore(contract)) firstMonth = firstMonth.plusMonths(1)
            if ((java.time.temporal.ChronoUnit.MONTHS.between(firstMonth, month) % expense.intervalMonths) != 0L) return@forEach
            val exists = updated.any { transaction -> transaction.recurringId == expense.id && transaction.yearMonth() == month }
            if (!exists) {
                updated += Transaction(
                    id = System.nanoTime(), amount = expense.priceRevisions.filter { !LocalDate.parse(it.effectiveDate).isAfter(date) }.maxByOrNull { it.effectiveDate }?.amount ?: expense.amount, category = expense.category,
                    note = expense.note, usedAt = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    recurringId = expense.id,
                    paymentSourceId = expense.paymentSourceId,
                )
            }
        }
        if (updated != _transactions.value) updateTransactions(updated)
    }

    private fun Transaction.yearMonth(): YearMonth = YearMonth.from(Instant.ofEpochMilli(usedAt).atZone(ZoneId.systemDefault()))
    private fun normalizeMerchant(value: String) = value.lowercase().filter { it.isLetterOrDigit() }
    private fun updateTransactions(items: List<Transaction>) { _transactions.value = items.sortedByDescending { it.usedAt }; transactionRepository.save(_transactions.value) }
}

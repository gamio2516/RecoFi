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
    private val _monthlyBudget = MutableStateFlow(settingsRepository.loadMonthlyBudget())
    val monthlyBudget = _monthlyBudget.asStateFlow()
    private val _paymentSources = MutableStateFlow(initialSources)
    val paymentSources = _paymentSources.asStateFlow()

    init { ensureRecurringFor(YearMonth.now()) }

    fun addTransaction(amount: Int, category: String, note: String, usedAt: Long, paymentSourceId: String, id: Long? = null) {
        val item = Transaction(id ?: System.currentTimeMillis(), amount, category, note, usedAt, paymentSourceId = paymentSourceId)
        updateTransactions(if (id == null) _transactions.value + item else _transactions.value.map { if (it.id == id) item.copy(confirmed = it.confirmed, recurringId = it.recurringId) else it })
    }

    fun deleteTransaction(id: Long) = updateTransactions(_transactions.value.filterNot { it.id == id })
    fun toggleConfirmed(id: Long) = updateTransactions(_transactions.value.map { if (it.id == id) it.copy(confirmed = !it.confirmed) else it })

    fun addCategory(value: String) { val clean = value.trim(); if (_categories.value.size < 15 && clean.isNotEmpty() && clean !in _categories.value) { _categories.value += clean; settingsRepository.saveCategories(_categories.value) } }
    fun deleteCategory(value: String) { if (_categories.value.size > 1) { _categories.value -= value; settingsRepository.saveCategories(_categories.value) } }
    fun moveCategory(value: String, offset: Int) { val index = _categories.value.indexOf(value); val target = index + offset; if (index >= 0 && target in _categories.value.indices) { val updated = _categories.value.toMutableList(); val moved = updated.removeAt(index); updated.add(target, moved); _categories.value = updated; settingsRepository.saveCategories(updated) } }
    fun addFrequentNote(sourceId: String, value: String) { val clean = value.trim(); val current = _notesBySource.value[sourceId].orEmpty(); if (current.size < 15 && clean.isNotEmpty() && clean !in current) { val updated = current + clean; _notesBySource.value = _notesBySource.value + (sourceId to updated); settingsRepository.saveNotes(sourceId, updated) } }
    fun deleteFrequentNote(sourceId: String, value: String) { val updated = _notesBySource.value[sourceId].orEmpty() - value; _notesBySource.value = _notesBySource.value + (sourceId to updated); settingsRepository.saveNotes(sourceId, updated) }
    fun moveFrequentNote(sourceId: String, value: String, offset: Int) { val current = _notesBySource.value[sourceId].orEmpty(); val index = current.indexOf(value); val target = index + offset; if (index >= 0 && target in current.indices) { val updated = current.toMutableList(); val moved = updated.removeAt(index); updated.add(target, moved); _notesBySource.value = _notesBySource.value + (sourceId to updated); settingsRepository.saveNotes(sourceId, updated) } }

    fun setMonthlyBudget(amount: Int) { _monthlyBudget.value = amount.coerceAtLeast(0); settingsRepository.saveMonthlyBudget(_monthlyBudget.value) }

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
    private fun updateTransactions(items: List<Transaction>) { _transactions.value = items.sortedByDescending { it.usedAt }; transactionRepository.save(_transactions.value) }
}

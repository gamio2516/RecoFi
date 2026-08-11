package jp.knaka.cardmemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WinterBlue = Color(0xFF173B8F)
private val IceBlue = Color(0xFFF3F7FF)
private val DeepNavy = Color(0xFF102A67)
private val Cyan = Color(0xFF007F9E)
private val PieColors = listOf(Color(0xFF173B8F), Color(0xFF3165D4), Color(0xFF6555C7), Color(0xFF0086A8), Color(0xFFB52B78), Color(0xFF5478B8), Color(0xFF9146A8), Color(0xFF48A1B8))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = WinterBlue, secondary = Cyan, background = IceBlue, surface = Color.White, onPrimary = Color.White)) {
                CardMemoApp()
            }
        }
    }
}

private enum class AppTab(val label: String) { DETAILS("明細"), ANALYSIS("分析"), SETTINGS("設定") }
private enum class SettingsPage { MENU, CATEGORIES, NOTES, SOURCES, BUDGET, RECURRING }

@Composable
private fun CardMemoApp(vm: MainViewModel = viewModel()) {
    val transactions by vm.transactions.collectAsState()
    val categories by vm.categories.collectAsState()
    val notesBySource by vm.notesBySource.collectAsState()
    val recurring by vm.recurringExpenses.collectAsState()
    val budget by vm.monthlyBudget.collectAsState()
    val paymentSources by vm.paymentSources.collectAsState()
    var selectedSourceId by remember { mutableStateOf("rakuten") }
    LaunchedEffect(paymentSources) { if (paymentSources.none { it.id == selectedSourceId }) selectedSourceId = paymentSources.firstOrNull()?.id.orEmpty() }
    var tab by remember { mutableStateOf(AppTab.DETAILS) }
    var showEntry by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<Transaction?>(null) }

    Scaffold(
        containerColor = IceBlue,
        topBar = { Surface(color = DeepNavy) { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text("RecoFi", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp); Spacer(Modifier.width(8.dp)); Text("Record your finance", color = Color(0xFFAFC6FF), fontSize = 11.sp) } } },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                AppTab.entries.forEach { item -> NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Text(when(item) { AppTab.DETAILS -> "≡"; AppTab.ANALYSIS -> "◕"; AppTab.SETTINGS -> "⚙" }, fontSize = 20.sp) }, label = { Text(item.label) }) }
            }
        },
        floatingActionButton = { if (tab != AppTab.SETTINGS) FloatingActionButton(onClick = { showEntry = true }, containerColor = WinterBlue) { Text("＋", color = Color.White, fontSize = 26.sp) } }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                AppTab.DETAILS -> DetailsScreen(transactions, paymentSources, selectedSourceId, { selectedSourceId = it }, vm::ensureRecurringFor, vm::toggleConfirmed, { editItem = it })
                AppTab.ANALYSIS -> AnalysisScreen(transactions, budget, vm::ensureRecurringFor)
                AppTab.SETTINGS -> SettingsScreen(categories, notesBySource, recurring, budget, paymentSources, vm)
            }
        }
    }
    if (showEntry || editItem != null) {
        EntryDialog(categories, notesBySource, paymentSources, transactions, selectedSourceId, editItem, onDismiss = { showEntry = false; editItem = null }, onDelete = { id -> vm.deleteTransaction(id); showEntry = false; editItem = null }) { amount, category, note, date, sourceId ->
            vm.addTransaction(amount, category, note, date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), sourceId, editItem?.id)
            showEntry = false; editItem = null
        }
    }
}

@Composable private fun MonthHeader(month: YearMonth, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        FilledTonalButton(onClick = previous) { Text("‹") }
        Text(month.format(DateTimeFormatter.ofPattern("yyyy年 M月")), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DeepNavy)
        FilledTonalButton(onClick = next) { Text("›") }
    }
}

@Composable private fun PaymentSourcePicker(sources: List<PaymentSource>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = sources.firstOrNull { it.id == selectedId } ?: sources.firstOrNull()
    Box { OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected?.name ?: "支払方法") }; DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { sources.forEach { source -> DropdownMenuItem(text = { Text(source.name) }, onClick = { onSelect(source.id); expanded = false }) } } }
}

@Composable private fun HomeScreen(all: List<Transaction>, ensure: (YearMonth) -> Unit, delete: (Long) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    LaunchedEffect(month) { ensure(month) }
    val rows = all.forMonth(month)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MonthHeader(month, { month = month.minusMonths(1) }, { month = month.plusMonths(1) })
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE2EAFF)), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Text("✦", fontSize = 34.sp, color = WinterBlue); Spacer(Modifier.width(14.dp)); Column { Text("使ったら、すぐメモ。", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = DeepNavy); Text("今月 ${rows.size}件を記録しています", color = Color(0xFF53658F)) } }
        }
        Text("最近のメモ", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = DeepNavy)
        if (rows.isEmpty()) EmptyMessage("右下の＋から最初のメモを追加しましょう") else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(rows.take(8), key = { it.id }) { item -> Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(40.dp).background(Color(0xFFDDE7FF), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Text("●", color = PieColors[kotlin.math.abs(item.category.hashCode()) % PieColors.size]) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(item.note.ifBlank { item.category }, fontWeight = FontWeight.SemiBold); Text("${item.dateText()}  ${item.category}", color = Color.Gray, fontSize = 12.sp) }; TextButton(onClick = { delete(item.id) }) { Text("削除") } } } } }
    }
}

@Composable private fun DetailsScreen(all: List<Transaction>, sources: List<PaymentSource>, sourceId: String, selectSource: (String) -> Unit, ensure: (YearMonth) -> Unit, toggle: (Long) -> Unit, edit: (Transaction) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var onlyOpen by remember { mutableStateOf(false) }
    LaunchedEffect(month) { ensure(month) }
    val monthRows = all.forMonth(month).filter { it.paymentSourceId == sourceId }
    val rows = if (onlyOpen) monthRows.filterNot { it.confirmed } else monthRows
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PaymentSourcePicker(sources, sourceId, selectSource)
        MonthHeader(month, { month = month.minusMonths(1) }, { month = month.plusMonths(1) })
        Card(colors = CardDefaults.cardColors(containerColor = WinterBlue), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("この月の利用額", color = Color.White.copy(.8f)); Text(yen(monthRows.sumOf { it.amount }), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold); Text("${monthRows.size}件 ・ 未確認 ${monthRows.count { !it.confirmed }}件", color = Color.White.copy(.85f)) } }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(onlyOpen, { onlyOpen = it }); Spacer(Modifier.width(8.dp)); Text("未確認のみ") }
        Text("${rows.size}件  ${yen(rows.sumOf { it.amount })}", color = DeepNavy, fontWeight = FontWeight.Bold)
        if (rows.isEmpty()) EmptyMessage("該当する明細はありません") else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth().clickable { edit(item) }, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = if (item.recurringId != null) 4.dp else 1.dp), border = if (item.recurringId != null) BorderStroke(1.dp, Color(0xFF89A9FF)) else null) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(item.confirmed, { toggle(item.id) })
                    Column(Modifier.weight(1f)) { Text(item.note.ifBlank { item.category }, fontWeight = FontWeight.SemiBold, maxLines = 1); Text("${item.dateText()}  ${item.category}${if(item.recurringId != null) "  固定費" else ""}", fontSize = 12.sp, color = Color.Gray) }
                    Text(yen(item.amount), fontWeight = FontWeight.Bold)
                } }
            }
        }
    }
}

@Composable private fun AnalysisScreen(all: List<Transaction>, budget: Int, ensure: (YearMonth) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var analysisMode by remember { mutableIntStateOf(0) }
    var detailCategory by remember { mutableStateOf<String?>(null) }
    var detailNote by remember { mutableStateOf<String?>(null) }
    val fixedOnly = analysisMode == 1
    val noteMode = analysisMode == 2
    LaunchedEffect(month) { ensure(month) }
    val monthRows = all.forMonth(month)
    val analysisRows = if (fixedOnly) monthRows.filter { it.recurringId != null } else monthRows
    val totals = analysisRows.groupBy { it.category }.mapValues { it.value.sumOf(Transaction::amount) }.toList().sortedByDescending { it.second }
    val total = totals.sumOf { it.second }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TabRow(selectedTabIndex = analysisMode, containerColor = Color.Transparent) { Tab(selected = analysisMode == 0, onClick = { analysisMode = 0 }, text = { Text("全体") }); Tab(selected = analysisMode == 1, onClick = { analysisMode = 1 }, text = { Text("固定費") }); Tab(selected = analysisMode == 2, onClick = { analysisMode = 2 }, text = { Text("備考AI") }) }
        MonthHeader(month, { month = month.minusMonths(1) }, { month = month.plusMonths(1) })
        Text(when { fixedOnly -> "固定費の分析"; noteMode -> "備考を軸にしたAI分析"; else -> "項目別支出" }, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy)
        if (fixedOnly) Text("この月の固定費 ${analysisRows.size}件・${yen(total)}（月予算の${if (budget > 0) total * 100 / budget else 0}%）", color = Color.Gray)
        if (noteMode) {
            NoteAiAnalysis(analysisRows, all.forMonth(month.minusMonths(1))) { detailNote = it }
        } else {
        if (budget <= 0) { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1C7))) { Text("設定から毎月の予算を登録すると、使用状況を比較できます。", modifier = Modifier.padding(16.dp), color = DeepNavy) } }
        if (total == 0 && budget <= 0) EmptyMessage("分析できる明細がありません") else {
            Box(Modifier.fillMaxWidth().height(245.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(220.dp)) { val denominator = if (budget > 0) budget else total.coerceAtLeast(1); drawArc(Color(0xFFE3E6ED), -90f, 360f, useCenter = false, style = Stroke(34f, cap = StrokeCap.Butt)); var start = -90f; totals.forEachIndexed { index, entry -> val sweep = (entry.second.toFloat() / denominator * 360f).coerceAtMost(360f - (start + 90f)); if (sweep > 0) drawArc(PieColors[index % PieColors.size], start, sweep, useCenter = false, style = Stroke(34f, cap = StrokeCap.Butt)); start += sweep }; if (budget > 0 && total > budget) drawArc(Color(0xFFD62F55), -90f, ((total - budget).toFloat() / budget * 360f).coerceAtMost(360f), useCenter = false, style = Stroke(9f, cap = StrokeCap.Round)) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (budget > 0 && total > budget) "予算オーバー" else "予算残り", color = if (budget > 0 && total > budget) Color(0xFFD62F55) else Color.Gray); Text(if (budget > 0 && total > budget) yen(total - budget) else yen((budget - total).coerceAtLeast(0)), fontWeight = FontWeight.Bold, color = DeepNavy); if (budget > 0) Text("予算 ${yen(budget)}", fontSize = 12.sp, color = Color.Gray) }
            }
            totals.forEachIndexed { index, (category, amount) -> Card(Modifier.fillMaxWidth().clickable { detailCategory = category }, colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(14.dp).background(PieColors[index % PieColors.size], RoundedCornerShape(3.dp))); Spacer(Modifier.width(10.dp)); Text(category, Modifier.weight(1f)); Text("${if(total > 0) amount * 100 / total else 0}%  ${yen(amount)}", fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(6.dp)); Text("›", color = WinterBlue) } } }
        }
        }
    }
    detailCategory?.let { category -> TransactionDetailDialog("$category の明細", analysisRows.filter { it.category == category }, { detailCategory = null }) }
    detailNote?.let { normalized -> TransactionDetailDialog("備考別の明細", analysisRows.filter { normalizeForLearning(it.note) == normalized }, { detailNote = null }) }
}

@Composable private fun SettingsScreen(categories: List<String>, notesBySource: Map<String, List<String>>, recurring: List<RecurringExpense>, budget: Int, sources: List<PaymentSource>, vm: MainViewModel) {
    var page by remember { mutableStateOf(SettingsPage.MENU) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (page != SettingsPage.MENU) TextButton(onClick = { page = SettingsPage.MENU }) { Text("‹ 設定に戻る") }
        when (page) {
            SettingsPage.MENU -> { Text("設定", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = DeepNavy); Spacer(Modifier.height(12.dp)); SettingsMenuItem("毎月の予算", if (budget > 0) "現在 ${yen(budget)}" else "分析に使う予算を登録") { page = SettingsPage.BUDGET }; SettingsMenuItem("固定費の設定", "サブスクなどを毎月自動で明細へ追加") { page = SettingsPage.RECURRING }; SettingsMenuItem("支払方法の設定", "カードやその他の支払いを管理") { page = SettingsPage.SOURCES }; SettingsMenuItem("項目の設定", "外食・食料品などを追加／削除") { page = SettingsPage.CATEGORIES }; SettingsMenuItem("備考の設定", "入力時に選べる定型文を管理") { page = SettingsPage.NOTES } }
            SettingsPage.SOURCES -> PaymentSourceSettings(sources, vm::addPaymentSource, vm::deletePaymentSource)
            SettingsPage.CATEGORIES -> StringSettings("項目の設定", "新しい項目", categories, vm::addCategory, vm::deleteCategory, { vm.moveCategory(it, -1) }, { vm.moveCategory(it, 1) })
            SettingsPage.NOTES -> NoteSettings(sources, notesBySource, vm::addFrequentNote, vm::deleteFrequentNote, { source, value -> vm.moveFrequentNote(source, value, -1) }, { source, value -> vm.moveFrequentNote(source, value, 1) })
            SettingsPage.BUDGET -> BudgetSettings(budget, vm::setMonthlyBudget)
            SettingsPage.RECURRING -> RecurringSettings(categories, sources, recurring, vm::saveRecurringExpense, vm::deleteRecurringExpense, vm::duplicateRecurringExpense, vm::endRecurringExpense, vm::reviseRecurringExpense)
        }
    }
}

@Composable private fun PaymentSourceSettings(sources: List<PaymentSource>, add: (String) -> Unit, delete: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("支払方法の設定", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy); Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(name, { name = it }, label = { Text("カード名・支払方法名") }, modifier = Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); Button(enabled = name.trim().isNotEmpty(), onClick = { add(name); name = "" }) { Text("追加") } }; LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(sources, key = { it.id }) { source -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(source.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); TextButton(onClick = { delete(source.id) }) { Text("削除") } } } } } }
}

@Composable private fun NoteSettings(sources: List<PaymentSource>, notesBySource: Map<String, List<String>>, add: (String, String) -> Unit, delete: (String, String) -> Unit, moveUp: (String, String) -> Unit, moveDown: (String, String) -> Unit) {
    var sourceId by remember(sources) { mutableStateOf(sources.firstOrNull()?.id.orEmpty()) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("備考の設定", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy); PaymentSourcePicker(sources, sourceId) { sourceId = it }; Box(Modifier.weight(1f)) { StringSettings("${sources.firstOrNull { it.id == sourceId }?.name.orEmpty()}の備考", "よく使う備考", notesBySource[sourceId].orEmpty(), { add(sourceId, it) }, { delete(sourceId, it) }, { moveUp(sourceId, it) }, { moveDown(sourceId, it) }) } }
}

@Composable private fun BudgetSettings(current: Int, save: (Int) -> Unit) {
    var value by remember(current) { mutableStateOf(if (current > 0) current.toString() else "") }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { Text("毎月の予算", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy); Text("分析画面で、すべての支払方法の合計と比較します。", color = Color.Gray); OutlinedTextField(value, { value = it.filter(Char::isDigit) }, label = { Text("予算額") }, prefix = { Text("¥") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()); Button(onClick = { save(value.toIntOrNull() ?: 0) }, enabled = value.toIntOrNull() != null, modifier = Modifier.fillMaxWidth()) { Text("保存") } }
}

@Composable private fun SettingsMenuItem(title: String, description: String, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, color = DeepNavy); Text(description, color = Color.Gray, fontSize = 13.sp) }; Text("›", color = WinterBlue, fontSize = 28.sp) } } }

@Composable private fun StringSettings(title: String, hint: String, values: List<String>, add: (String) -> Unit, delete: (String) -> Unit, moveUp: (String) -> Unit, moveDown: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) { Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy, modifier = Modifier.weight(1f)); Text("${values.size}/15", color = if (values.size >= 15) Color(0xFFD62F55) else Color.Gray) }
        Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value, { value = it }, label = { Text(hint) }, modifier = Modifier.weight(1f), enabled = values.size < 15); Spacer(Modifier.width(8.dp)); Button(enabled = values.size < 15 && value.trim().isNotEmpty(), onClick = { add(value); value = "" }) { Text("追加") } }
        if (values.size >= 15) Text("登録できるのは15個までです。", color = Color(0xFFD62F55), fontSize = 13.sp)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) { itemsIndexed(values, key = { _, item -> item }) { index, item -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(item, Modifier.weight(1f)); Text("☰", fontSize = 24.sp, color = WinterBlue, modifier = Modifier.padding(12.dp).pointerInput(item, index) { var dragged = 0f; detectDragGesturesAfterLongPress(onDragStart = { dragged = 0f }, onDrag = { _, amount -> dragged += amount.y }, onDragEnd = { if (dragged < -20f && index > 0) moveUp(item) else if (dragged > 20f && index < values.lastIndex) moveDown(item) }) }); TextButton(onClick = { delete(item) }) { Text("削除") } } } } }
    }
}

@Composable private fun RecurringSettings(categories: List<String>, sources: List<PaymentSource>, rows: List<RecurringExpense>, save: (Long?, Int, Int, String, String, LocalDate, String, Int) -> Unit, delete: (Long) -> Unit, duplicate: (Long) -> Unit, endContract: (Long, LocalDate) -> Unit, revisePrice: (Long, LocalDate, Int) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringExpense?>(null) }
    var showArchive by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<RecurringExpense?>(null) }
    val today = LocalDate.now()
    val visibleRows = rows.filter { item -> val ended = item.endDate?.let { LocalDate.parse(it).isBefore(today) } == true; ended == showArchive }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("固定費の設定", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy); Text(if (showArchive) "終了済みの固定費" else "タップで編集できます", color = Color.Gray, fontSize = 13.sp) }; if (!showArchive) Button(onClick = { editing = null; showDialog = true }) { Text("追加") } }; TabRow(selectedTabIndex = if (showArchive) 1 else 0, containerColor = Color.Transparent) { Tab(selected = !showArchive, onClick = { showArchive = false }, text = { Text("契約中") }); Tab(selected = showArchive, onClick = { showArchive = true }, text = { Text("アーカイブ") }) }; if (visibleRows.isEmpty()) EmptyMessage(if (showArchive) "アーカイブはありません" else "固定費はまだありません") else visibleRows.forEach { item -> Card(Modifier.fillMaxWidth().clickable { editing = item; showDialog = true }) { Column(Modifier.padding(14.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.note.ifBlank { item.category }, fontWeight = FontWeight.Bold); Text("${item.contractDate.replace('-', '/')}契約 ・ ${intervalLabel(item.intervalMonths)}・${item.billingDay}日", color = Color.Gray, fontSize = 12.sp); Text("${item.category}  ${yen(item.priceRevisions.lastOrNull()?.amount ?: item.amount)}${item.endDate?.let { " ・ ${it.replace('-', '/')}終了" }.orEmpty()}", color = Color.Gray, fontSize = 13.sp) } }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { duplicate(item.id) }) { Text("複製") }; TextButton(onClick = { deleteCandidate = item }) { Text("削除") } } } } } }
    if (showDialog) RecurringDialog(categories, sources, editing, { showDialog = false; editing = null }, { id, date -> endContract(id, date); editing = editing?.copy(endDate = date.toString()) }, { id, date, amount -> revisePrice(id, date, amount); editing = editing?.copy(priceRevisions = editing!!.priceRevisions + PriceRevision(date.toString(), amount)) }) { id, amount, day, category, note, contractDate, sourceId, interval -> save(id, amount, day, category, note, contractDate, sourceId, interval); showDialog = false; editing = null }
    deleteCandidate?.let { item -> ConfirmDeleteDialog("固定費を削除しますか？", item.note.ifBlank { item.category }, { deleteCandidate = null }) { delete(item.id); deleteCandidate = null } }
}

@Composable private fun RecurringDialog(categories: List<String>, sources: List<PaymentSource>, existing: RecurringExpense?, dismiss: () -> Unit, endContract: (Long, LocalDate) -> Unit, revisePrice: (Long, LocalDate, Int) -> Unit, save: (Long?, Int, Int, String, String, LocalDate, String, Int) -> Unit) {
    var amount by remember(existing) { mutableStateOf(existing?.amount?.toString().orEmpty()) }; var day by remember(existing) { mutableIntStateOf(existing?.billingDay ?: 1) }; var note by remember(existing) { mutableStateOf(existing?.note.orEmpty()) }; var contractDate by remember(existing) { mutableStateOf(existing?.contractDate ?: LocalDate.now().toString()) }; var category by remember(existing, categories) { mutableStateOf(existing?.category ?: categories.firstOrNull().orEmpty()) }; var categoryExpanded by remember { mutableStateOf(false) }; var dayExpanded by remember { mutableStateOf(false) }; var intervalExpanded by remember { mutableStateOf(false) }; var interval by remember(existing) { mutableIntStateOf(existing?.intervalMonths ?: 1) }; var sourceId by remember(existing, sources) { mutableStateOf(existing?.paymentSourceId ?: sources.firstOrNull()?.id.orEmpty()) }; var showEnd by remember { mutableStateOf(false) }; var showRevision by remember { mutableStateOf(false) }; val parsedDate = runCatching { LocalDate.parse(contractDate) }.getOrNull()
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (existing == null) "固定費を追加" else "固定費を編集") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { PaymentSourcePicker(sources, sourceId) { sourceId = it }; Box { OutlinedButton(onClick = { categoryExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(category.ifBlank { "項目を選択" }) }; DropdownMenu(categoryExpanded, { categoryExpanded = false }) { categories.forEach { DropdownMenuItem({ Text(it) }, { category = it; categoryExpanded = false }) } } }; OutlinedTextField(contractDate, { contractDate = it }, label = { Text("契約日（例：2026-08-09）") }); Box { OutlinedButton(onClick = { intervalExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("支払い間隔：${intervalLabel(interval)}") }; DropdownMenu(intervalExpanded, { intervalExpanded = false }) { listOf(1, 3, 6, 12, 24).forEach { candidate -> DropdownMenuItem({ Text(intervalLabel(candidate)) }, { interval = candidate; intervalExpanded = false }) } } }; Box { OutlinedButton(onClick = { dayExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("請求日：${day}日") }; DropdownMenu(dayExpanded, { dayExpanded = false }, modifier = Modifier.heightIn(max = 280.dp)) { (1..31).forEach { candidate -> DropdownMenuItem({ Text("${candidate}日") }, { day = candidate; dayExpanded = false }) } } }; OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("基本料金") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); OutlinedTextField(note, { note = it }, label = { Text("備考（例：動画配信サービス）") }); if (existing != null) { HorizontalDivider(); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { showEnd = true }, modifier = Modifier.weight(1f)) { Text("契約終了") }; OutlinedButton(onClick = { showRevision = true }, modifier = Modifier.weight(1f)) { Text("料金改定") } }; existing.endDate?.let { Text("終了日：${it.replace('-', '/')}", color = Color.Gray, fontSize = 12.sp) }; if (existing.priceRevisions.isNotEmpty()) Text("料金改定 ${existing.priceRevisions.size}件", color = Color.Gray, fontSize = 12.sp) } } }, confirmButton = { Button(enabled = amount.toIntOrNull() != null && category in categories && parsedDate != null && sourceId.isNotBlank(), onClick = { save(existing?.id, amount.toInt(), day, category, note, parsedDate!!, sourceId, interval) }) { Text("保存") } }, dismissButton = { TextButton(onClick = dismiss) { Text("キャンセル") } })
    if (showEnd && existing != null) DateAmountActionDialog("契約終了日", false, existing.endDate ?: LocalDate.now().toString(), "", { showEnd = false }) { date, _ -> endContract(existing.id, date); showEnd = false }
    if (showRevision && existing != null) DateAmountActionDialog("料金改定", true, LocalDate.now().toString(), existing.priceRevisions.lastOrNull()?.amount?.toString() ?: existing.amount.toString(), { showRevision = false }) { date, revised -> revisePrice(existing.id, date, revised); showRevision = false }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable private fun EntryDialog(categories: List<String>, notesBySource: Map<String, List<String>>, sources: List<PaymentSource>, history: List<Transaction>, defaultSourceId: String, existing: Transaction?, onDismiss: () -> Unit, onDelete: (Long) -> Unit, save: (Int, String, String, LocalDate, String) -> Unit) {
    var amount by remember { mutableStateOf(existing?.amount?.toString().orEmpty()) }; var note by remember { mutableStateOf(existing?.note.orEmpty()) }; var category by remember { mutableStateOf(existing?.category ?: categories.firstOrNull().orEmpty()) }; var expanded by remember { mutableStateOf(false) }; var date by remember(existing) { mutableStateOf(existing?.let { Instant.ofEpochMilli(it.usedAt).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now()) }; var showDatePicker by remember { mutableStateOf(false) }; var showDeleteConfirm by remember { mutableStateOf(false) }; var sourceId by remember(existing, defaultSourceId) { mutableStateOf(existing?.paymentSourceId ?: defaultSourceId) }
    val notes = notesBySource[sourceId].orEmpty()
    val aiSuggestions = remember(note, sourceId, history, categories) { categorySuggestions(note, sourceId, history, categories) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if(existing == null) "利用内容を追加" else "明細を編集") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { PaymentSourcePicker(sources, sourceId) { sourceId = it }; Box { OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text(category.ifBlank { "項目を選択" }) }; DropdownMenu(expanded, { expanded = false }) { categories.forEach { DropdownMenuItem({ Text(it) }, { category = it; expanded = false }) } } }; OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) { Text("利用日：${date.format(DateTimeFormatter.ofPattern("yyyy/M/d"))}　変更") }; OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("金額") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); OutlinedTextField(note, { note = it }, label = { Text("備考") }); if (aiSuggestions.isNotEmpty()) { Text("✦ 端末内AIの項目候補", fontSize = 12.sp, color = WinterBlue, fontWeight = FontWeight.Bold); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { aiSuggestions.forEach { candidate -> SuggestionChip(onClick = { category = candidate }, label = { Text(candidate) }, colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFFE3EBFF))) } } }; if (notes.isNotEmpty()) { Text("設定済みの備考", fontSize = 12.sp, color = Color.Gray); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { notes.forEach { SuggestionChip(onClick = { note = it }, label = { Text(it) }) } } }; if (existing != null) { HorizontalDivider(); TextButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.fillMaxWidth()) { Text("この明細を削除", color = Color(0xFFD62F55)) } } } }, confirmButton = { Button(enabled = amount.toIntOrNull() != null && category.isNotBlank() && sourceId.isNotBlank(), onClick = { save(amount.toInt(), category, note, date, sourceId) }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } })
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * 86_400_000L)
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { pickerState.selectedDateMillis?.let { date = LocalDate.ofEpochDay(it / 86_400_000L) }; showDatePicker = false }) { Text("決定") } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") } }) { DatePicker(state = pickerState) }
    }
    if (showDeleteConfirm && existing != null) ConfirmDeleteDialog("明細を削除しますか？", "${existing.dateText()}  ${existing.note.ifBlank { existing.category }}  ${yen(existing.amount)}", { showDeleteConfirm = false }) { onDelete(existing.id); showDeleteConfirm = false }
}

@Composable private fun TransactionRow(item: Transaction) { Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.note.ifBlank { item.category }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${item.dateText()}  ${item.category}", color = Color.Gray, fontSize = 12.sp) }; Text(yen(item.amount), fontWeight = FontWeight.Bold, color = DeepNavy) } } }
@Composable private fun EmptyMessage(message: String) { Text(message, color = Color.Gray, modifier = Modifier.fillMaxWidth().padding(28.dp)) }

@Composable private fun NoteAiAnalysis(current: List<Transaction>, previous: List<Transaction>, openDetails: (String) -> Unit) {
    val groups = current.filter { it.note.isNotBlank() }.groupBy { normalizeForLearning(it.note) }.filterKeys { it.isNotBlank() }
    val previousTotals = previous.filter { it.note.isNotBlank() }.groupBy { normalizeForLearning(it.note) }.mapValues { it.value.sumOf(Transaction::amount) }
    val ranked = groups.entries.sortedByDescending { it.value.sumOf(Transaction::amount) }
    if (ranked.isEmpty()) { EmptyMessage("備考を登録した明細が増えるとAI分析が表示されます"); return }
    val top = ranked.first()
    val topLabel = top.value.groupingBy { it.note.trim() }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
    val mostFrequent = ranked.maxByOrNull { it.value.size }
    val biggestIncrease = ranked.maxByOrNull { entry -> entry.value.sumOf(Transaction::amount) - previousTotals.getOrDefault(entry.key, 0) }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF0FF)), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text("✦ 端末内AIのまとめ", fontWeight = FontWeight.Bold, color = DeepNavy); Text("支出が最も大きい利用先は「$topLabel」で ${yen(top.value.sumOf { it.amount })} です。", fontSize = 13.sp); mostFrequent?.let { Text("利用回数が最も多いのは「${it.value.first().note}」の ${it.value.size}回です。", fontSize = 13.sp) }; biggestIncrease?.let { val difference = it.value.sumOf(Transaction::amount) - previousTotals.getOrDefault(it.key, 0); Text(if (difference > 0) "前月から最も増えたのは「${it.value.first().note}」で ${yen(difference)} 増えています。" else "前月より増加した利用先は目立ちません。", fontSize = 13.sp) } } }
    Text("備考別ランキング", fontWeight = FontWeight.Bold, color = DeepNavy)
    ranked.take(10).forEach { entry -> val items = entry.value; val amount = items.sumOf { it.amount }; val previousAmount = previousTotals.getOrDefault(entry.key, 0); val label = items.groupingBy { it.note.trim() }.eachCount().maxByOrNull { it.value }?.key.orEmpty(); Card(Modifier.fillMaxWidth().clickable { openDetails(entry.key) }) { Column(Modifier.padding(14.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(yen(amount), fontWeight = FontWeight.Bold, color = DeepNavy); Text("  ›", color = WinterBlue) }; Text("${items.size}回・平均 ${yen(amount / items.size)}・前月比 ${signedYen(amount - previousAmount)}", color = Color.Gray, fontSize = 12.sp) } } }
}

@Composable private fun TransactionDetailDialog(title: String, rows: List<Transaction>, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Column { Text("${rows.size}件・合計 ${yen(rows.sumOf { it.amount })}", color = DeepNavy, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { items(rows, key = { it.id }) { item -> Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = if (item.recurringId != null) 3.dp else 0.dp), border = if (item.recurringId != null) BorderStroke(1.dp, Color(0xFF89A9FF)) else null) { Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.note.ifBlank { item.category }, fontWeight = FontWeight.SemiBold); Text("${item.dateText()}  ${item.category}${if (item.recurringId != null) "  固定費" else ""}", fontSize = 12.sp, color = Color.Gray) }; Text(yen(item.amount), fontWeight = FontWeight.Bold) } } } } } }, confirmButton = { TextButton(onClick = dismiss) { Text("閉じる") } })
}

private fun signedYen(value: Int): String = when { value > 0 -> "+${yen(value)}"; value < 0 -> "-${yen(-value)}"; else -> "±¥0" }
private fun List<Transaction>.forMonth(month: YearMonth) = filter { YearMonth.from(Instant.ofEpochMilli(it.usedAt).atZone(ZoneId.systemDefault())) == month }.sortedBy { it.usedAt }
private fun Transaction.dateText() = Instant.ofEpochMilli(usedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("M/d"))
private fun yen(value: Int): String = "¥${NumberFormat.getIntegerInstance().format(value)}"
private fun intervalLabel(months: Int): String = when (months) { 24 -> "2年ごと"; 12 -> "1年ごと"; 6 -> "6か月ごと"; 3 -> "3か月ごと"; else -> "1か月ごと" }

@Composable private fun DateAmountActionDialog(title: String, requireAmount: Boolean, initialDate: String, initialAmount: String, dismiss: () -> Unit, confirm: (LocalDate, Int) -> Unit) {
    var dateText by remember { mutableStateOf(initialDate) }; var amountText by remember { mutableStateOf(initialAmount) }; val date = runCatching { LocalDate.parse(dateText) }.getOrNull(); val amount = amountText.toIntOrNull()
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(dateText, { dateText = it }, label = { Text("適用日（例：2026-08-10）") }); if (requireAmount) OutlinedTextField(amountText, { amountText = it.filter(Char::isDigit) }, label = { Text("新しい金額") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) } }, confirmButton = { Button(enabled = date != null && (!requireAmount || amount != null), onClick = { confirm(date!!, amount ?: 0) }) { Text("適用") } }, dismissButton = { TextButton(onClick = dismiss) { Text("キャンセル") } })
}

@Composable private fun ConfirmDeleteDialog(title: String, description: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(description, fontWeight = FontWeight.SemiBold); Text("この操作は取り消せません。", color = Color.Gray, fontSize = 13.sp) } }, confirmButton = { Button(onClick = confirm, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD62F55))) { Text("削除する") } }, dismissButton = { TextButton(onClick = dismiss) { Text("キャンセル") } })
}

private fun categorySuggestions(note: String, sourceId: String, history: List<Transaction>, categories: List<String>): List<String> {
    val input = normalizeForLearning(note)
    if (input.isBlank()) return emptyList()
    val scores = mutableMapOf<String, Double>()
    history.asSequence().filter { it.note.isNotBlank() && it.category in categories }.forEach { transaction ->
        val past = normalizeForLearning(transaction.note)
        val similarity = when {
            input == past -> 10.0
            input.length >= 2 && past.length >= 2 && (input.contains(past) || past.contains(input)) -> 4.0
            else -> bigramSimilarity(input, past)
        }
        if (similarity >= 0.25) {
            val sourceBonus = if (transaction.paymentSourceId == sourceId) 1.35 else 1.0
            scores[transaction.category] = scores.getOrDefault(transaction.category, 0.0) + similarity * sourceBonus
        }
    }
    return scores.entries.sortedByDescending { it.value }.take(3).map { it.key }
}

private fun normalizeForLearning(value: String): String = value.lowercase().filterNot { it.isWhitespace() || it in "・･-ー_/()（）" }

private fun bigramSimilarity(first: String, second: String): Double {
    if (first.length < 2 || second.length < 2) return 0.0
    val left = first.windowed(2).toSet()
    val right = second.windowed(2).toSet()
    return left.intersect(right).size.toDouble() / left.union(right).size.coerceAtLeast(1)
}

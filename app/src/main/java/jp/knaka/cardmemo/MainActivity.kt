package jp.knaka.cardmemo

import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val WinterBlue = Color(0xFF173B8F)
private val IceBlue = Color(0xFFF3F7FF)
private val DeepNavy = Color(0xFF102A67)
private val Cyan = Color(0xFF087A63)
private val NeutralGray = Color(0xFFE4E9F0)
private val AppTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp),
)
private val PieColors = listOf(Color(0xFF173B8F), Color(0xFF2F63D8), Color(0xFF0079B8), Color(0xFF318495), Color(0xFF3E7C78), Color(0xFF4D82C8), Color(0xFF2455A6), Color(0xFF66958B))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val japaneseLocale = java.util.Locale.JAPAN
        java.util.Locale.setDefault(japaneseLocale)
        resources.configuration.setLocale(japaneseLocale)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = WinterBlue, onPrimary = Color.White, secondary = Cyan, onSecondary = Color.White, secondaryContainer = Color(0xFFCDEEDF), onSecondaryContainer = Color(0xFF0B604D), tertiary = Color(0xFF2A9175), tertiaryContainer = Color(0xFFD8F2E8), error = Color(0xFF8A3655), errorContainer = Color(0xFFF0E5E9), onErrorContainer = Color(0xFF4E2535), background = IceBlue, surface = Color.White, surfaceVariant = NeutralGray, surfaceContainer = Color.White, surfaceContainerLow = Color.White, surfaceContainerHigh = Color.White, surfaceContainerHighest = Color.White, surfaceDim = NeutralGray, outlineVariant = Color(0xFFD5D8DE)), typography = AppTypography) {
                CardMemoApp()
            }
        }
    }
}

private enum class AppTab(val label: String) { DETAILS("明細"), ANALYSIS("分析"), SETTINGS("設定") }
private enum class SettingsPage { MENU, CATEGORIES, NOTES, SOURCES, BUDGET, RECURRING, EXPORT, IMPORTED_FILES, IMPORT, RECONCILE, LOCK }

@Composable
private fun CardMemoApp(vm: MainViewModel = viewModel()) {
    val transactions by vm.transactions.collectAsState()
    val categories by vm.categories.collectAsState()
    val notesBySource by vm.notesBySource.collectAsState()
    val recurring by vm.recurringExpenses.collectAsState()
    val budget by vm.monthlyBudget.collectAsState()
    val defaultBudget by vm.defaultMonthlyBudget.collectAsState()
    val paymentSources by vm.paymentSources.collectAsState()
    val lockedMonths by vm.lockedMonths.collectAsState()
    val reconciliationProgress by vm.reconciliationProgress.collectAsState()
    val importedStatements by vm.importedStatements.collectAsState()
    var selectedSourceId by remember { mutableStateOf("rakuten") }
    LaunchedEffect(paymentSources) { if (paymentSources.none { it.id == selectedSourceId }) selectedSourceId = paymentSources.firstOrNull()?.id.orEmpty() }
    var tab by remember { mutableStateOf(AppTab.DETAILS) }
    var showEntry by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<Transaction?>(null) }
    var detailsTool by remember { mutableStateOf<Pair<YearMonth, String>?>(null) }
    var detailsHomeKey by remember { mutableIntStateOf(0) }
    var analysisHomeKey by remember { mutableIntStateOf(0) }
    var settingsHomeKey by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = IceBlue,
        topBar = { Surface(color = DeepNavy) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { androidx.compose.foundation.Image(painter = androidx.compose.ui.res.painterResource(jp.knaka.cardmemo.R.drawable.recofi_monogram_icon), contentDescription = "RecoFi", modifier = Modifier.size(36.dp)); Spacer(Modifier.width(10.dp)); Text("RecoFi", modifier = Modifier.alignByBaseline().offset(y = 6.dp), color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, letterSpacing = .3.sp, fontSize = 25.sp); Spacer(Modifier.width(11.dp)); Text("Record your Finance", modifier = Modifier.alignByBaseline().offset(y = 6.dp), color = Color(0xFFBFD0FF), fontFamily = FontFamily.SansSerif, letterSpacing = .45.sp, fontSize = 10.sp) } } },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                AppTab.entries.forEach { item -> NavigationBarItem(selected = tab == item, onClick = { tab = item; when (item) { AppTab.DETAILS -> { detailsTool = null; detailsHomeKey++ }; AppTab.ANALYSIS -> analysisHomeKey++; AppTab.SETTINGS -> settingsHomeKey++ } }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF087A63), selectedTextColor = Color(0xFF202124), unselectedTextColor = Color(0xFF202124), indicatorColor = Color(0xFFCDEEDF)), icon = { Text(when(item) { AppTab.DETAILS -> "≡"; AppTab.ANALYSIS -> "◕"; AppTab.SETTINGS -> "⚙" }, fontSize = 20.sp) }, label = { Text(item.label) }) }
            }
        },
        floatingActionButton = { if (tab != AppTab.SETTINGS) FloatingActionButton(onClick = { showEntry = true }, containerColor = WinterBlue) { Text("＋", color = Color.White, fontSize = 26.sp) } }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                AppTab.DETAILS -> key(detailsHomeKey) { if (detailsTool == null) DetailsScreen(transactions, paymentSources, selectedSourceId, { selectedSourceId = it }, vm::ensureRecurringFor, vm::toggleConfirmed, { editItem = it }, lockedMonths, { month, source -> detailsTool = month to source }, vm::setMonthLocked) else Column(Modifier.fillMaxSize().padding(16.dp)) { TextButton(onClick = { detailsTool = null }) { Text("‹ 明細に戻る") }; Box(Modifier.weight(1f)) { DataTransferSettings(SettingsPage.RECONCILE, transactions, paymentSources, importedStatements, lockedMonths, vm::isImportedFile, vm::recordImportedFile, vm::saveImportedStatement, vm::addSuggestedTransactions, vm::setReconciliationProgress, vm::confirmTransactions, vm::linkImportedStatement, vm::deleteImportedStatement, detailsTool!!.first, detailsTool!!.second) } } }
                AppTab.ANALYSIS -> key(analysisHomeKey) { AnalysisScreen(transactions, budget, defaultBudget, vm::ensureRecurringFor) }
                AppTab.SETTINGS -> key(settingsHomeKey) { SettingsScreen(categories, notesBySource, recurring, budget, defaultBudget, paymentSources, transactions, importedStatements, lockedMonths, reconciliationProgress, vm) }
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
        OutlinedButton(onClick = previous, colors = ButtonDefaults.outlinedButtonColors(containerColor = IceBlue, contentColor = WinterBlue), border = BorderStroke(1.dp, WinterBlue)) { Text("‹", fontWeight = FontWeight.Bold) }
        Text(month.format(DateTimeFormatter.ofPattern("yyyy年 M月")), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DeepNavy)
        OutlinedButton(onClick = next, colors = ButtonDefaults.outlinedButtonColors(containerColor = IceBlue, contentColor = WinterBlue), border = BorderStroke(1.dp, WinterBlue)) { Text("›", fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun PaymentSourcePicker(sources: List<PaymentSource>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = sources.firstOrNull { it.id == selectedId } ?: sources.firstOrNull()
    Box { OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFCDEEDF), contentColor = Color(0xFF087A63)), border = BorderStroke(1.dp, Color(0xFF087A63))) { Text(selected?.name ?: "支払方法", fontWeight = FontWeight.SemiBold) }; DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Color(0xFFF4F7F6)) { sources.forEach { source -> DropdownMenuItem(text = { Text(source.name) }, onClick = { onSelect(source.id); expanded = false }) } } }
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

@Composable private fun DetailsScreen(all: List<Transaction>, sources: List<PaymentSource>, sourceId: String, selectSource: (String) -> Unit, ensure: (YearMonth) -> Unit, toggle: (Long) -> Unit, edit: (Transaction) -> Unit, lockedMonths: Set<String>, openTransfer: (YearMonth, String) -> Unit, setLocked: (YearMonth, Boolean) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var onlyOpen by remember { mutableStateOf(false) }
    LaunchedEffect(month) { ensure(month) }
    val monthRows = all.forMonth(month).filter { it.paymentSourceId == sourceId }
    val rows = if (onlyOpen) monthRows.filterNot { it.confirmed } else monthRows
    var lockedWarning by remember { mutableStateOf(false) }
    var lockFailed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PaymentSourcePicker(sources, sourceId, selectSource)
        MonthHeader(month, { month = month.minusMonths(1) }, { month = month.plusMonths(1) })
        Card(colors = CardDefaults.cardColors(containerColor = WinterBlue), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("この月の利用額", color = Color.White.copy(.78f)); Text(yen(monthRows.sumOf { it.amount }), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold); Text("${monthRows.size}件 ・ 未確認 ${monthRows.count { !it.confirmed }}件", color = Color.White.copy(.82f)) }; OutlinedButton(onClick = { openTransfer(month, sourceId) }, border = BorderStroke(1.dp, Color.White.copy(.38f)), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(.10f), contentColor = Color.White), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text("明細照合  ›", fontSize = 11.sp, fontWeight = FontWeight.Bold) } } }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Switch(onlyOpen, { onlyOpen = it }); Spacer(Modifier.width(8.dp)); Text("未確認のみ"); Spacer(Modifier.weight(1f)); val isLocked = month.toString() in lockedMonths; TextButton(onClick = { if (isLocked) setLocked(month, false) else if (all.forMonth(month).isNotEmpty() && all.forMonth(month).all { it.confirmed }) setLocked(month, true) else lockFailed = true }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(if (isLocked) "🔒 確定済み" else "月を確定", color = Color.Gray, fontSize = 11.sp) } }
        Text("${rows.size}件  ${yen(rows.sumOf { it.amount })}", color = DeepNavy, fontWeight = FontWeight.Bold)
        if (rows.isEmpty()) EmptyMessage("該当する明細はありません") else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth().clickable { edit(item) }, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = if (item.recurringId != null) 4.dp else 1.dp), border = when { item.suggested -> BorderStroke(2.dp, Color(0xFFE14964)); item.recurringId != null -> BorderStroke(1.dp, Color(0xFF89A9FF)); else -> null }) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val itemLocked = month.toString() in lockedMonths || item.reconciledMonth in lockedMonths
                    Checkbox(item.confirmed, { if (itemLocked) lockedWarning = true else toggle(item.id) })
                    Column(Modifier.weight(1f)) { Text(item.note.ifBlank { item.category }, fontWeight = FontWeight.SemiBold, maxLines = 1); Text("${item.dateText()}  ${item.category}${if(item.recurringId != null) "  固定費" else ""}${if(item.suggested) "  推測" else ""}", fontSize = 12.sp, color = if(item.suggested) Color(0xFFE14964) else Color.Gray) }
                    Text(yen(item.amount), fontWeight = FontWeight.Bold)
                } }
            }
        }
    }
    if (lockedWarning) CuteWarningDialog("この月はロック中だよ 🔒", "確定した明細を守るため、手動での消込変更もお休み中です。変更したいときは『確定済み』を押して解除してください。") { lockedWarning = false }
    if (lockFailed) CuteWarningDialog("まだ確定できません", "未確認の明細が残っています。すべての明細を確認してから、もう一度『月を確定』を押してください。") { lockFailed = false }
}

@Composable private fun AnalysisScreen(all: List<Transaction>, budgets: Map<String, Int>, defaultBudget: Int, ensure: (YearMonth) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val budget = budgets[month.toString()] ?: defaultBudget
    var analysisMode by remember { mutableIntStateOf(0) }
    var detailCategory by remember { mutableStateOf<String?>(null) }
    var detailNote by remember { mutableStateOf<String?>(null) }
    var detailDate by remember { mutableStateOf<LocalDate?>(null) }
    val fixedOnly = analysisMode == 1
    val calendarMode = analysisMode == 2
    val noteMode = analysisMode == 3
    LaunchedEffect(month) { ensure(month) }
    val monthRows = all.forMonth(month)
    val analysisRows = if (fixedOnly) monthRows.filter { it.recurringId != null } else monthRows
    val totals = analysisRows.groupBy { it.category }.mapValues { it.value.sumOf(Transaction::amount) }.toList().sortedByDescending { it.second }
    val total = totals.sumOf { it.second }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TabRow(selectedTabIndex = analysisMode, containerColor = Color.Transparent) { Tab(selected = analysisMode == 0, onClick = { analysisMode = 0 }, text = { Text("全体", maxLines = 1, fontSize = 12.sp) }); Tab(selected = analysisMode == 1, onClick = { analysisMode = 1 }, text = { Text("固定費", maxLines = 1, fontSize = 12.sp) }); Tab(selected = analysisMode == 2, onClick = { analysisMode = 2 }, text = { Text("カレンダー", maxLines = 1, fontSize = 10.sp) }); Tab(selected = analysisMode == 3, onClick = { analysisMode = 3 }, text = { Text("備考", maxLines = 1, fontSize = 12.sp) }) }
        MonthHeader(month, { month = month.minusMonths(1) }, { month = month.plusMonths(1) })
        Text(when { fixedOnly -> "固定費の分析"; noteMode -> "備考を軸にしたAI分析"; calendarMode -> "支出カレンダー"; else -> "項目別支出" }, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy)
        if (calendarMode) {
            SpendingCalendar(month, monthRows) { detailDate = it }
        } else if (noteMode) {
            NoteAiAnalysis(analysisRows, all.forMonth(month.minusMonths(1))) { detailNote = it }
        } else {
        if (budget <= 0) { Card(colors = CardDefaults.cardColors(containerColor = NeutralGray)) { Text("設定から毎月の予算を登録すると、使用状況を比較できます。", modifier = Modifier.padding(16.dp), color = DeepNavy) } }
        if (total == 0 && budget <= 0) EmptyMessage("分析できる明細がありません") else {
            Box(Modifier.fillMaxWidth().height(245.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(220.dp)) { val denominator = maxOf(budget, total, 1); drawArc(Color(0xFFE3E6ED), -90f, 360f, useCenter = false, style = Stroke(42f, cap = StrokeCap.Butt)); var start = -90f; totals.forEachIndexed { index, entry -> val remaining = (360f - (start + 90f)).coerceAtLeast(0f); val sweep = (entry.second.toFloat() / denominator * 360f).coerceAtMost(remaining); if (sweep > 0) drawArc(PieColors[index % PieColors.size], start, sweep, useCenter = false, style = Stroke(42f, cap = StrokeCap.Butt)); start += sweep } }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { val overBudget = budget > 0 && total > budget; val emphasizeOver = overBudget && !fixedOnly; if (overBudget) Text("予算オーバー", color = Color(0xFFFF1738), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold); Text("現在の支出", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Normal); Text(yen(total), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (emphasizeOver) Color(0xFFFF1738) else DeepNavy); if (budget > 0) { Text("予算比 ${total * 100 / budget}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (emphasizeOver) Color(0xFFFF1738) else DeepNavy); Text("予算 ${yen(budget)}", fontSize = 11.sp, color = Color(0xFF202124)) } }
            }
            totals.forEachIndexed { index, (category, amount) -> Card(Modifier.fillMaxWidth().clickable { detailCategory = category }, colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(14.dp).background(PieColors[index % PieColors.size], RoundedCornerShape(3.dp))); Spacer(Modifier.width(10.dp)); Text(category, Modifier.weight(1f)); Text("${if (fixedOnly && budget > 0) amount * 100 / budget else if(total > 0) amount * 100 / total else 0}%", fontWeight = FontWeight.SemiBold, color = if (fixedOnly) DeepNavy else Color(0xFF202124)); Spacer(Modifier.width(8.dp)); Text(yen(amount), fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(6.dp)); Text("›", color = WinterBlue) } } }
        }
        }
    }
    detailCategory?.let { category -> TransactionDetailDialog("$category の明細", analysisRows.filter { it.category == category }, { detailCategory = null }) }
    detailNote?.let { normalized -> TransactionDetailDialog("備考別の明細", analysisRows.filter { normalizeForLearning(it.note) == normalized }, { detailNote = null }) }
    detailDate?.let { date -> TransactionDetailDialog("${date.monthValue}月${date.dayOfMonth}日の明細", monthRows.filter { Instant.ofEpochMilli(it.usedAt).atZone(ZoneId.systemDefault()).toLocalDate() == date }, { detailDate = null }) }
}

@Composable private fun SpendingCalendar(month: YearMonth, rows: List<Transaction>, openDay: (LocalDate) -> Unit) {
    val daily = rows.groupBy { Instant.ofEpochMilli(it.usedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.mapValues { it.value.sumOf(Transaction::amount) }
    val maximum = daily.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val leading = month.atDay(1).dayOfWeek.value % 7
    val cells = List<LocalDate?>(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    val padded = cells + List((7 - cells.size % 7) % 7) { null }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth()) { listOf("日", "月", "火", "水", "木", "金", "土").forEachIndexed { index, label -> Text(label, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = if (index == 0) Color(0xFF315CA8) else if (index == 6) Color(0xFF087A63) else Color.Gray, fontSize = 11.sp, fontWeight = if (index == 0 || index == 6) FontWeight.Bold else FontWeight.Normal) } }
            padded.chunked(7).forEach { week -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { week.forEach { date -> val amount = date?.let { daily[it] } ?: 0; val intensity = if (amount > 0) (.18f + .72f * amount / maximum).coerceIn(.18f, .90f) else 0f; Box(Modifier.weight(1f).height(70.dp).background(if (amount > 0) WinterBlue.copy(alpha = intensity) else Color(0xFFF4F6FA), RoundedCornerShape(10.dp)).clickable(enabled = date != null && amount > 0) { date?.let(openDay) }.padding(6.dp)) { if (date != null) { Text(date.dayOfMonth.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (intensity > .42f) Color.White else DeepNavy); if (amount > 0) Text("¥${NumberFormat.getIntegerInstance().format(amount)}", modifier = Modifier.align(Alignment.BottomCenter), fontSize = 9.sp, maxLines = 1, color = if (intensity > .42f) Color.White else DeepNavy) } } } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(12.dp).background(Color(0xFFE8ECF5), RoundedCornerShape(3.dp))); Text("  支出なし　", color = Color.Gray, fontSize = 11.sp); Box(Modifier.size(12.dp).background(WinterBlue.copy(.25f), RoundedCornerShape(3.dp))); Text("  少ない　", color = Color.Gray, fontSize = 11.sp); Box(Modifier.size(12.dp).background(WinterBlue.copy(.90f), RoundedCornerShape(3.dp))); Text("  多い", color = Color.Gray, fontSize = 11.sp) }
        }
    }
}

@Composable private fun SettingsScreen(categories: List<String>, notesBySource: Map<String, List<String>>, recurring: List<RecurringExpense>, budgets: Map<String, Int>, defaultBudget: Int, sources: List<PaymentSource>, transactions: List<Transaction>, importedStatements: List<ImportedStatement>, lockedMonths: Set<String>, reconciliationProgress: Map<String, Pair<Int, Int>>, vm: MainViewModel) {
    var page by remember { mutableStateOf(SettingsPage.MENU) }
    val budget = budgets[YearMonth.now().toString()] ?: defaultBudget
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (page != SettingsPage.MENU) TextButton(onClick = { page = SettingsPage.MENU }) { Text("‹ 設定に戻る") }
        when (page) {
            SettingsPage.MENU -> { Text("設定", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = DeepNavy); Spacer(Modifier.height(12.dp)); SettingsMenuItem("月間予算", if (budget > 0) "現在 ${yen(budget)}" else "毎月の支出目標を登録") { page = SettingsPage.BUDGET }; SettingsMenuItem("固定費プラン", "サブスクや定期支払いを管理") { page = SettingsPage.RECURRING }; SettingsMenuItem("支払方法", "カードなどの支払先を管理") { page = SettingsPage.SOURCES }; SettingsMenuItem("支出カテゴリ", "外食・食料品などの分類を管理") { page = SettingsPage.CATEGORIES }; SettingsMenuItem("備考テンプレート", "入力時に使う定型文を管理") { page = SettingsPage.NOTES }; SettingsMenuItem("取込ファイル管理", "保存済みのカード明細ファイルを確認") { page = SettingsPage.IMPORTED_FILES }; SettingsMenuItem("データエクスポート", "月ごとの明細をCSVで保存") { page = SettingsPage.EXPORT } }
            SettingsPage.SOURCES -> PaymentSourceSettings(sources, vm::addPaymentSource, vm::deletePaymentSource)
            SettingsPage.CATEGORIES -> StringSettings("支出カテゴリ", "新しいカテゴリ", categories, vm::addCategory, vm::deleteCategory, { vm.moveCategory(it, -1) }, { vm.moveCategory(it, 1) })
            SettingsPage.NOTES -> NoteSettings(sources, notesBySource, vm::addFrequentNote, vm::deleteFrequentNote, { source, value -> vm.moveFrequentNote(source, value, -1) }, { source, value -> vm.moveFrequentNote(source, value, 1) })
            SettingsPage.BUDGET -> BudgetSettings(budgets, defaultBudget, vm::setDefaultMonthlyBudget, vm::setMonthlyBudget)
            SettingsPage.RECURRING -> RecurringSettings(categories, sources, recurring, vm::saveRecurringExpense, vm::deleteRecurringExpense, vm::duplicateRecurringExpense, vm::endRecurringExpense, vm::reviseRecurringExpense)
            SettingsPage.LOCK -> MonthLockSettings(transactions, lockedMonths, reconciliationProgress, vm::setMonthLocked)
            SettingsPage.EXPORT -> DataTransferSettings(SettingsPage.EXPORT, transactions, sources, importedStatements, lockedMonths, vm::isImportedFile, vm::recordImportedFile, vm::saveImportedStatement, vm::addSuggestedTransactions, vm::setReconciliationProgress, vm::confirmTransactions)
            SettingsPage.IMPORTED_FILES -> ImportedFileManagement(importedStatements, sources, vm::deleteImportedStatement)
            SettingsPage.IMPORT, SettingsPage.RECONCILE -> Unit
        }
    }
}

@Composable private fun ImportedFileManagement(importedStatements: List<ImportedStatement>, sources: List<PaymentSource>, deleteImported: (String) -> Unit) {
    Text("取込ファイル管理", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy)
    Text("端末に保存されているカード明細ファイルと紐づけ先を確認できます。", color = Color.Gray, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    if (importedStatements.isEmpty()) EmptyMessage("保存済みの明細ファイルはありません")
    importedStatements.sortedWith(compareByDescending<ImportedStatement> { it.statementMonth }.thenBy { it.fileName }).forEach { statement ->
        val sourceName = sources.firstOrNull { it.id == statement.paymentSourceId }?.name
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(statement.fileName, fontWeight = FontWeight.SemiBold, color = DeepNavy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("対象月：${statement.statementMonth.replace('-', '年')}月　・　${statement.entries.size}件", color = Color.Gray, fontSize = 12.sp)
                Text(if (sourceName == null) "紐づけ：未設定" else "紐づけ：${sourceName}", color = if (sourceName == null) Color(0xFF16856B) else DeepNavy, fontSize = 12.sp)
                TextButton(onClick = { deleteImported(statement.fileHash) }, modifier = Modifier.align(Alignment.End), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("削除", color = Color(0xFFC43E55)) }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable private fun DataTransferSettings(mode: SettingsPage, transactions: List<Transaction>, sources: List<PaymentSource>, importedStatements: List<ImportedStatement>, lockedMonths: Set<String>, isImportedFile: (String) -> Boolean, recordImportedFile: (String) -> Unit, saveImported: (ImportedStatement) -> Unit, addSuggestions: (List<CardStatementEntry>, String, YearMonth) -> Unit, setProgress: (YearMonth, String, Int, Int) -> Unit, confirmMatches: (Set<Long>, YearMonth) -> Unit, linkImported: (String, YearMonth, String) -> Boolean = { _, _, _ -> false }, deleteImported: (String) -> Unit = {}, fixedMonth: YearMonth? = null, fixedSourceId: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var month by remember(fixedMonth) { mutableStateOf(fixedMonth ?: YearMonth.now()) }
    var sourceId by remember(sources, fixedSourceId) { mutableStateOf(fixedSourceId ?: sources.firstOrNull { it.id == "rakuten" }?.id ?: sources.firstOrNull()?.id.orEmpty()) }
    var exportSourceId by remember { mutableStateOf<String?>(null) }
    var exportExpanded by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<StatementMatch>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var duplicateName by remember { mutableStateOf<String?>(null) }
    val isLocked = month.toString() in lockedMonths
    val linkedForTarget = if (fixedMonth != null && fixedSourceId != null) importedStatements.lastOrNull { it.statementMonth == fixedMonth.toString() && it.paymentSourceId == fixedSourceId } else null
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let {
            runCatching { context.contentResolver.openOutputStream(it)?.use { output -> StatementTools.writeMonthlyCsv(output, month, transactions, sources.associate { source -> source.id to source.name }, exportSourceId) } }
                .onSuccess { status = "${month.year}年${month.monthValue}月のCSVを保存しました" }
                .onFailure { error -> status = "CSV保存に失敗しました：${error.localizedMessage.orEmpty()}" }
        }
    }
    val statementLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val fileName = StatementTools.displayName(context, it)
            val hash = runCatching { StatementTools.sha256(context, it) }.getOrElse { error -> status = "ファイル確認に失敗しました：${error.localizedMessage.orEmpty()}"; return@let }
            if (isImportedFile(hash)) duplicateName = fileName else {
                val provisionalMonth = Regex("(20\\d{2})[^0-9]?(0?[1-9]|1[0-2])").find(fileName)?.let { found -> YearMonth.of(found.groupValues[1].toInt(), found.groupValues[2].toInt()) } ?: YearMonth.now()
                loading = true; status = "カード明細を端末内で読み込んでいます…"; matches = emptyList()
                scope.launch {
                    runCatching { StatementTools.readStatementFile(context, it, provisionalMonth) }
                        .onSuccess { entries ->
                            if (entries.isEmpty()) status = "明細行を読み取れませんでした。対象月とファイル形式をご確認ください"
                            else {
                                val inferredMonth = StatementTools.inferStatementMonth(context, it, entries)
                                val statementHash = StatementTools.statementFingerprint(entries)
                                if (isImportedFile(statementHash)) { matches = emptyList(); duplicateName = "$fileName（同じ明細内容）"; status = "同じ内容の明細は取込済みです" }
                                else {
                                    val targetSource = fixedSourceId.orEmpty()
                                    val targetMonth = fixedMonth ?: inferredMonth
                                    val occupied = targetSource.isNotBlank() && importedStatements.any { it.statementMonth == targetMonth.toString() && it.paymentSourceId == targetSource }
                                    if (occupied) status = "この月・カードにはすでにファイルが紐づいています。先に現在のファイルを削除してください"
                                    else { saveImported(ImportedStatement(if (targetSource.isBlank()) inferredMonth.toString() else targetMonth.toString(), targetSource, fileName, hash, entries)); if (targetSource.isNotBlank()) setProgress(targetMonth, targetSource, entries.size, 0); recordImportedFile(hash); recordImportedFile(statementHash); status = if (targetSource.isBlank()) "${fileName} をファイル一覧へ保存しました" else "${fileName} をこの月・カードへ紐づけました" }
                                }
                            }
                        }
                        .onFailure { error -> status = "明細解析に失敗しました：${error.localizedMessage.orEmpty()}" }
                    loading = false
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(when (mode) { SettingsPage.EXPORT -> "エクスポート"; SettingsPage.IMPORT -> "カード明細の取込"; else -> "明細の取込・自動消込" }, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy)
        if (fixedMonth != null) Text("${fixedMonth.year}年${fixedMonth.monthValue}月・${sources.firstOrNull { it.id == sourceId }?.name.orEmpty()}", color = Color.Gray, fontSize = 13.sp)
        if (mode != SettingsPage.IMPORT && fixedMonth == null) Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("対象月", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { OutlinedButton(onClick = { month = month.minusMonths(1); matches = emptyList() }) { Text("‹") }; Text("${month.year}年 ${month.monthValue}月", fontWeight = FontWeight.Bold, color = DeepNavy); OutlinedButton(onClick = { month = month.plusMonths(1); matches = emptyList() }) { Text("›") } }
            if (mode == SettingsPage.EXPORT) {
                Box { OutlinedButton(onClick = { exportExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(exportSourceId?.let { id -> sources.firstOrNull { it.id == id }?.name } ?: "すべての支払方法") }; DropdownMenu(exportExpanded, { exportExpanded = false }) { DropdownMenuItem({ Text("すべての支払方法") }, { exportSourceId = null; exportExpanded = false }); sources.forEach { source -> DropdownMenuItem({ Text(source.name) }, { exportSourceId = source.id; exportExpanded = false }) } } }
                val suffix = exportSourceId?.let { id -> sources.firstOrNull { it.id == id }?.name?.replace(Regex("[^\\p{L}\\p{N}]+"), "_") } ?: "all"
                Button(onClick = { csvLauncher.launch("RecoFi_${month}_$suffix.csv") }, modifier = Modifier.fillMaxWidth()) { Text("この条件でCSVを保存") }
            }
        } }
        if (mode == SettingsPage.IMPORT || (mode == SettingsPage.RECONCILE && fixedMonth != null)) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF0FF))) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("カード明細ファイルを保存", fontWeight = FontWeight.Bold, color = DeepNavy)
            Text("ファイル名と明細内容から対象月を自動判定します。ここでは消込を行わず、あとで自動消込に利用できる形で端末内に保存します。", color = Color.Gray, fontSize = 13.sp)
            Button(enabled = !loading && (fixedMonth == null || linkedForTarget == null), onClick = { statementLauncher.launch(arrayOf("application/pdf", "text/csv", "text/comma-separated-values", "text/*")) }, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "読込中…" else if (fixedMonth != null && linkedForTarget != null) "この月には取込済み" else "カード明細のPDF／CSVを選択") }
            Text("現在の対応カード\n・楽天カード\n・ビューカード", color = Color(0xFF53658F), fontSize = 12.sp)
        } }
        if (mode == SettingsPage.IMPORT) {
            Text("保存済みファイル", fontWeight = FontWeight.Bold, color = DeepNavy)
            if (importedStatements.isEmpty()) EmptyMessage("保存済みの明細ファイルはありません")
            importedStatements.sortedByDescending { it.statementMonth }.forEach { statement ->
                val linkedSource = sources.firstOrNull { it.id == statement.paymentSourceId }
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(statement.fileName, fontWeight = FontWeight.SemiBold, color = DeepNavy); Text("推定月：${statement.statementMonth.replace('-', '年')}月 ・ ${statement.entries.size}件", color = Color.Gray, fontSize = 12.sp); Text(if (linkedSource == null) "未紐づけ" else "紐づけ先：${statement.statementMonth.replace('-', '年')}月・${linkedSource.name}", color = if (linkedSource == null) Color(0xFF16856B) else Color(0xFF315CA8), fontSize = 12.sp); TextButton(onClick = { deleteImported(statement.fileHash) }, modifier = Modifier.align(Alignment.End), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("削除", color = Color(0xFFC43E55)) } } }
            }
            Text("ここでの取込はファイルの保存のみです。明細画面で対象の月・カードを開き、ファイルを紐づけてください。", color = Color.Gray, fontSize = 12.sp)
        }
        if (mode == SettingsPage.RECONCILE) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF0FF))) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("保存済みのカード明細と照合", fontWeight = FontWeight.Bold, color = DeepNavy)
            val imported = importedStatements.lastOrNull { it.statementMonth == month.toString() && it.paymentSourceId == sourceId }
            if (imported != null) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(imported.fileName, fontWeight = FontWeight.SemiBold, color = DeepNavy); Text("${imported.entries.size}件・この月に紐づけ済み", color = Color.Gray, fontSize = 12.sp) }; TextButton(onClick = { deleteImported(imported.fileHash) }) { Text("削除", color = Color(0xFFC43E55)) } } }
            else { Text("この月に紐づいたファイルはありません", color = Color.Gray, fontSize = 13.sp); importedStatements.filter { it.paymentSourceId.isBlank() }.sortedByDescending { it.statementMonth == month.toString() }.take(5).forEach { stored -> OutlinedButton(onClick = { if (linkImported(stored.fileHash, month, sourceId)) status = "${stored.fileName} をこの月・カードへ紐づけました" else status = "この月にはすでに別のファイルが紐づいています" }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { Text(stored.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("推定 ${stored.statementMonth}・${stored.entries.size}件", fontSize = 10.sp, color = Color.Gray) } } } }
            Button(enabled = imported != null && !isLocked, onClick = {
                imported?.let { statement -> val result = StatementTools.match(statement.entries, transactions, sourceId); matches = result; val missing = result.filter { it.transactionId == null }.map { it.statement }; addSuggestions(missing, sourceId, month); setProgress(month, sourceId, statement.entries.size, statement.entries.size); status = "${result.count { it.transactionId != null }}件を照合し、${missing.size}件を推測明細として追加しました" }
            }, modifier = Modifier.fillMaxWidth()) { Text("自動消込を実行") }
            if (isLocked) Text("この月はロック済みです", color = Color(0xFFE14964), fontSize = 13.sp)
        } }
        if (status.isNotBlank()) Text(status, color = DeepNavy, fontWeight = FontWeight.SemiBold)
        if (matches.isNotEmpty()) {
            val matchedIds = matches.mapNotNull { it.transactionId }.toSet()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("照合結果", Modifier.weight(1f), fontWeight = FontWeight.Bold); Button(enabled = matchedIds.isNotEmpty() && !isLocked, onClick = { confirmMatches(matchedIds, month); status = "${matchedIds.size}件を確認済みにしました" }) { Text("候補を消込") } }
            matches.forEach { match ->
                val transaction = transactions.firstOrNull { it.id == match.transactionId }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (transaction != null) Color(0xFFE9F7F1) else Color(0xFFFFF3F5)), border = if (transaction == null) BorderStroke(1.dp, Color(0xFFE14964)) else null) { Column(Modifier.padding(12.dp)) { Row { Text("${match.statement.date.monthValue}/${match.statement.date.dayOfMonth}", color = Color.Gray); Spacer(Modifier.width(10.dp)); Text(match.statement.merchant, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text(yen(match.statement.amount), fontWeight = FontWeight.Bold) }; Text(if (transaction != null) "候補：${transaction.note.ifBlank { transaction.category }}（一致度 ${match.score}%）" else "推測明細として追加しました", color = if (transaction != null) Color(0xFF187A55) else Color(0xFFD62F55), fontSize = 12.sp) } }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
    duplicateName?.let { name -> AlertDialog(onDismissRequest = { duplicateName = null }, title = { Text("同じファイルは取込済みです") }, text = { Text("「$name」は以前に読み込まれています。重複した消込を防ぐため、今回は処理しませんでした。") }, confirmButton = { Button(onClick = { duplicateName = null }) { Text("確認") } }) }
}

@Composable private fun MonthLockSettings(transactions: List<Transaction>, lockedMonths: Set<String>, progress: Map<String, Pair<Int, Int>>, setLocked: (YearMonth, Boolean) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var warning by remember { mutableStateOf<String?>(null) }
    var confirmLock by remember { mutableStateOf(false) }
    val isLocked = month.toString() in lockedMonths
    val monthProgress = progress.filterKeys { it.startsWith("${month}|") }.values
    val imported = monthProgress.sumOf { it.first }
    val matched = monthProgress.sumOf { it.second }
    val confirmed = transactions.count { it.reconciledMonth == month.toString() && it.confirmed }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("月次ロック", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy)
        Text("消込が完了した月を確定し、自動・手動の変更から守ります。", color = Color.Gray)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { OutlinedButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }; Text("${month.year}年 ${month.monthValue}月", fontWeight = FontWeight.Bold, color = DeepNavy); OutlinedButton(onClick = { month = month.plusMonths(1) }) { Text("›") } }
            if (imported > 0) Text("取込 $imported 件 ・ 候補 $matched 件 ・ 消込済み $confirmed 件", color = Color(0xFF53658F)) else Text("この月はまだカード明細を取り込んでいません", color = Color.Gray)
            if (isLocked) { Text("🔒 ロック中", fontWeight = FontWeight.Bold, color = DeepNavy); OutlinedButton(onClick = { setLocked(month, false) }, modifier = Modifier.fillMaxWidth()) { Text("ロックを解除") } }
            else Button(onClick = {
                warning = when { imported == 0 -> "明細画面の自動消込から、先にカード明細を読み込んでね。"; matched < imported -> "まだ ${imported - matched} 件、登録明細との組み合わせが見つかっていないよ。"; confirmed < matched -> "あと ${matched - confirmed} 件の消込が残っているよ。"; else -> null }
                if (warning == null) confirmLock = true
            }, modifier = Modifier.fillMaxWidth()) { Text("この月を確定してロック") }
        } }
    }
    warning?.let { CuteWarningDialog("まだロックできないよ 🌷", "$it\nぜんぶ確認できたら、もう一度試してね。") { warning = null } }
    if (confirmLock) AlertDialog(onDismissRequest = { confirmLock = false }, title = { Text("${month.year}年${month.monthValue}月をロックしますか？ 🔐") }, text = { Text("ロック中は、自動消込と明細画面の手動消込を変更できません。") }, confirmButton = { Button(onClick = { setLocked(month, true); confirmLock = false }) { Text("ロックする") } }, dismissButton = { TextButton(onClick = { confirmLock = false }) { Text("キャンセル") } })
}

@Composable private fun PaymentSourceSettings(sources: List<PaymentSource>, add: (String) -> Unit, delete: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("支払方法の設定", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy); Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(name, { name = it }, label = { Text("カード名・支払方法名") }, modifier = Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); Button(enabled = name.trim().isNotEmpty(), onClick = { add(name); name = "" }) { Text("追加") } }; LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(sources, key = { it.id }) { source -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(source.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); TextButton(onClick = { delete(source.id) }) { Text("削除") } } } } } }
}

@Composable private fun NoteSettings(sources: List<PaymentSource>, notesBySource: Map<String, List<String>>, add: (String, String) -> Unit, delete: (String, String) -> Unit, moveUp: (String, String) -> Unit, moveDown: (String, String) -> Unit) {
    var sourceId by remember(sources) { mutableStateOf(sources.firstOrNull()?.id.orEmpty()) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("備考の設定", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy); PaymentSourcePicker(sources, sourceId) { sourceId = it }; Box(Modifier.weight(1f)) { StringSettings("${sources.firstOrNull { it.id == sourceId }?.name.orEmpty()}の備考", "よく使う備考", notesBySource[sourceId].orEmpty(), { add(sourceId, it) }, { delete(sourceId, it) }, { moveUp(sourceId, it) }, { moveDown(sourceId, it) }) } }
}

@Composable private fun BudgetSettings(budgets: Map<String, Int>, defaultBudget: Int, saveDefault: (Int) -> Unit, save: (YearMonth, Int) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val override = budgets[month.toString()]
    var value by remember(month, override) { mutableStateOf(override?.toString().orEmpty()) }
    var commonValue by remember(defaultBudget) { mutableStateOf(if (defaultBudget > 0) defaultBudget.toString() else "") }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text("予算設定", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy); Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("毎月共通の予算", fontWeight = FontWeight.Bold, color = DeepNavy); Text("月別設定がない月には、この金額を自動で適用します。", color = Color.Gray, fontSize = 12.sp); OutlinedTextField(commonValue, { commonValue = it.filter(Char::isDigit) }, label = { Text("共通予算額") }, prefix = { Text("¥") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()); Button(onClick = { saveDefault(commonValue.toIntOrNull() ?: 0) }, enabled = commonValue.toIntOrNull() != null, modifier = Modifier.fillMaxWidth()) { Text("共通予算を保存") } } }; Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("月別の予算", fontWeight = FontWeight.Bold, color = DeepNavy); Text("特定の月だけ、共通予算とは違う金額にできます。", color = Color.Gray, fontSize = 12.sp); MonthHeader(month, { month = month.minusMonths(1) }, { month = month.plusMonths(1) }); OutlinedTextField(value, { value = it.filter(Char::isDigit) }, label = { Text("${month.monthValue}月の予算額") }, placeholder = { if (defaultBudget > 0) Text("共通予算 ${yen(defaultBudget)}") }, prefix = { Text("¥") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()); Button(onClick = { save(month, value.toIntOrNull() ?: 0) }, enabled = value.toIntOrNull() != null, modifier = Modifier.fillMaxWidth()) { Text("この月だけ上書き") }; if (override != null) TextButton(onClick = { save(month, 0); value = "" }, modifier = Modifier.fillMaxWidth()) { Text("月別設定を解除して共通予算に戻す") } } } }
}

@Composable private fun SettingsMenuItem(title: String, description: String, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, color = DeepNavy); Text(description, color = Color.Gray, fontSize = 13.sp) }; Text("›", color = WinterBlue, fontSize = 28.sp) } } }

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
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (existing == null) "固定費を追加" else "固定費を編集") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { PaymentSourcePicker(sources, sourceId) { sourceId = it }; Box { OutlinedButton(onClick = { categoryExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(category.ifBlank { "項目を選択" }) }; DropdownMenu(categoryExpanded, { categoryExpanded = false }) { categories.forEach { DropdownMenuItem({ Text(it) }, { category = it; categoryExpanded = false }) } } }; OutlinedTextField(contractDate, { contractDate = it }, label = { Text("契約日（例：2026-08-09）") }); Box { OutlinedButton(onClick = { intervalExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("支払い間隔：${intervalLabel(interval)}") }; DropdownMenu(intervalExpanded, { intervalExpanded = false }, modifier = Modifier.heightIn(max = 320.dp)) { (1..120).forEach { candidate -> DropdownMenuItem({ Text(intervalLabel(candidate)) }, { interval = candidate; intervalExpanded = false }) } } }; Box { OutlinedButton(onClick = { dayExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("請求日：${day}日") }; DropdownMenu(dayExpanded, { dayExpanded = false }, modifier = Modifier.heightIn(max = 280.dp)) { (1..31).forEach { candidate -> DropdownMenuItem({ Text("${candidate}日") }, { day = candidate; dayExpanded = false }) } } }; OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("基本料金") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); OutlinedTextField(note, { note = it }, label = { Text("備考（例：動画配信サービス）") }); if (existing != null) { HorizontalDivider(); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { showEnd = true }, modifier = Modifier.weight(1f)) { Text("契約終了") }; OutlinedButton(onClick = { showRevision = true }, modifier = Modifier.weight(1f)) { Text("料金改定") } }; existing.endDate?.let { Text("終了日：${it.replace('-', '/')}", color = Color.Gray, fontSize = 12.sp) }; if (existing.priceRevisions.isNotEmpty()) Text("料金改定 ${existing.priceRevisions.size}件", color = Color.Gray, fontSize = 12.sp) } } }, confirmButton = { Button(enabled = amount.toIntOrNull() != null && category in categories && parsedDate != null && sourceId.isNotBlank(), onClick = { save(existing?.id, amount.toInt(), day, category, note, parsedDate!!, sourceId, interval) }) { Text("保存") } }, dismissButton = { TextButton(onClick = dismiss) { Text("キャンセル") } })
    if (showEnd && existing != null) DateAmountActionDialog("契約終了日", false, existing.endDate ?: LocalDate.now().toString(), "", { showEnd = false }) { date, _ -> endContract(existing.id, date); showEnd = false }
    if (showRevision && existing != null) DateAmountActionDialog("料金改定", true, LocalDate.now().toString(), existing.priceRevisions.lastOrNull()?.amount?.toString() ?: existing.amount.toString(), { showRevision = false }) { date, revised -> revisePrice(existing.id, date, revised); showRevision = false }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable private fun EntryDialog(categories: List<String>, notesBySource: Map<String, List<String>>, sources: List<PaymentSource>, history: List<Transaction>, defaultSourceId: String, existing: Transaction?, onDismiss: () -> Unit, onDelete: (Long) -> Unit, save: (Int, String, String, LocalDate, String) -> Unit) {
    var amount by remember { mutableStateOf(existing?.amount?.toString().orEmpty()) }; var note by remember { mutableStateOf(existing?.note.orEmpty()) }; var category by remember { mutableStateOf(existing?.category ?: categories.firstOrNull().orEmpty()) }; var expanded by remember { mutableStateOf(false) }; var date by remember(existing) { mutableStateOf(existing?.let { Instant.ofEpochMilli(it.usedAt).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now()) }; var showDatePicker by remember { mutableStateOf(false) }; var showDeleteConfirm by remember { mutableStateOf(false) }; var sourceId by remember(existing, defaultSourceId) { mutableStateOf(existing?.paymentSourceId ?: defaultSourceId) }
    val notes = notesBySource[sourceId].orEmpty()
    val aiSuggestions = remember(note, sourceId, history, categories) { categorySuggestions(note, sourceId, history, categories) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White, title = { Text(if(existing == null) "利用内容を追加" else "明細を編集") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { PaymentSourcePicker(sources, sourceId) { sourceId = it }; Box { OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text(category.ifBlank { "項目を選択" }) }; DropdownMenu(expanded, { expanded = false }) { categories.forEach { DropdownMenuItem({ Text(it) }, { category = it; expanded = false }) } } }; OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) { Text("利用日：${date.format(DateTimeFormatter.ofPattern("yyyy/M/d"))}　変更") }; OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("金額") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); OutlinedTextField(note, { note = it }, label = { Text("備考") }); if (aiSuggestions.isNotEmpty()) { Text("✦ 端末内AIの項目候補", fontSize = 12.sp, color = WinterBlue, fontWeight = FontWeight.Bold); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { aiSuggestions.forEach { candidate -> SuggestionChip(onClick = { category = candidate }, label = { Text(candidate) }, colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFFE3EBFF))) } } }; if (notes.isNotEmpty()) { Text("設定済みの備考", fontSize = 12.sp, color = Color.Gray); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { notes.forEach { SuggestionChip(onClick = { note = it }, label = { Text(it) }) } } }; if (existing != null) { HorizontalDivider(); TextButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.fillMaxWidth()) { Text("この明細を削除", color = Color(0xFFD62F55)) } } } }, confirmButton = { Button(enabled = amount.toIntOrNull() != null && category.isNotBlank() && sourceId.isNotBlank(), onClick = { save(amount.toInt(), category, note, date, sourceId) }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } })
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
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text("✦ 端末内AIのまとめ", fontWeight = FontWeight.Bold, color = DeepNavy); Text("支出が最も大きい利用先は「$topLabel」で ${yen(top.value.sumOf { it.amount })} です。", fontSize = 13.sp); mostFrequent?.let { Text("利用回数が最も多いのは「${it.value.first().note}」の ${it.value.size}回です。", fontSize = 13.sp) }; biggestIncrease?.let { val difference = it.value.sumOf(Transaction::amount) - previousTotals.getOrDefault(it.key, 0); Text(if (difference > 0) "前月から最も増えたのは「${it.value.first().note}」で ${yen(difference)} 増えています。" else "前月より増加した利用先は目立ちません。", fontSize = 13.sp) } } }
    Text("備考別ランキング", fontWeight = FontWeight.Bold, color = DeepNavy)
    ranked.take(10).forEach { entry -> val items = entry.value; val amount = items.sumOf { it.amount }; val previousAmount = previousTotals.getOrDefault(entry.key, 0); val label = items.groupingBy { it.note.trim() }.eachCount().maxByOrNull { it.value }?.key.orEmpty(); Card(Modifier.fillMaxWidth().clickable { openDetails(entry.key) }, colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(14.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(yen(amount), fontWeight = FontWeight.Bold, color = DeepNavy); Text("  ›", color = WinterBlue) }; Text("${items.size}回・平均 ${yen(amount / items.size)}・前月比 ${signedYen(amount - previousAmount)}", color = Color.Gray, fontSize = 12.sp) } } }
}

@Composable private fun TransactionDetailDialog(title: String, rows: List<Transaction>, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Column { Text("${rows.size}件・合計 ${yen(rows.sumOf { it.amount })}", color = DeepNavy, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { items(rows, key = { it.id }) { item -> Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = if (item.recurringId != null) 3.dp else 0.dp), border = if (item.recurringId != null) BorderStroke(1.dp, Color(0xFF89A9FF)) else null) { Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.note.ifBlank { item.category }, fontWeight = FontWeight.SemiBold); Text("${item.dateText()}  ${item.category}${if (item.recurringId != null) "  固定費" else ""}", fontSize = 12.sp, color = Color.Gray) }; Text(yen(item.amount), fontWeight = FontWeight.Bold) } } } } } }, confirmButton = { TextButton(onClick = dismiss) { Text("閉じる") } })
}

private fun signedYen(value: Int): String = when { value > 0 -> "+${yen(value)}"; value < 0 -> "-${yen(-value)}"; else -> "±¥0" }
private fun List<Transaction>.forMonth(month: YearMonth) = filter { YearMonth.from(Instant.ofEpochMilli(it.usedAt).atZone(ZoneId.systemDefault())) == month }.sortedBy { it.usedAt }
private fun Transaction.dateText() = Instant.ofEpochMilli(usedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("M/d"))
private fun yen(value: Int): String = "¥${NumberFormat.getIntegerInstance().format(value)}"
private fun intervalLabel(months: Int): String = if (months >= 12 && months % 12 == 0) "${months / 12}年ごと" else "${months}か月ごと"

@Composable private fun DateAmountActionDialog(title: String, requireAmount: Boolean, initialDate: String, initialAmount: String, dismiss: () -> Unit, confirm: (LocalDate, Int) -> Unit) {
    var dateText by remember { mutableStateOf(initialDate) }; var amountText by remember { mutableStateOf(initialAmount) }; val date = runCatching { LocalDate.parse(dateText) }.getOrNull(); val amount = amountText.toIntOrNull()
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(dateText, { dateText = it }, label = { Text("適用日（例：2026-08-10）") }); if (requireAmount) OutlinedTextField(amountText, { amountText = it.filter(Char::isDigit) }, label = { Text("新しい金額") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) } }, confirmButton = { Button(enabled = date != null && (!requireAmount || amount != null), onClick = { confirm(date!!, amount ?: 0) }) { Text("適用") } }, dismissButton = { TextButton(onClick = dismiss) { Text("キャンセル") } })
}

@Composable private fun ConfirmDeleteDialog(title: String, description: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(description, fontWeight = FontWeight.SemiBold); Text("この操作は取り消せません。", color = Color.Gray, fontSize = 13.sp) } }, confirmButton = { Button(onClick = confirm, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD62F55))) { Text("削除する") } }, dismissButton = { TextButton(onClick = dismiss) { Text("キャンセル") } })
}

@Composable private fun CuteWarningDialog(title: String, message: String, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        containerColor = Color(0xFFF3F7FF),
        icon = { Text("❉", color = Color(0xFF4D82C8), fontSize = 29.sp) },
        title = { Text(title, color = DeepNavy, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = Color(0xFF4D5E78)) },
        confirmButton = { Button(onClick = dismiss, colors = ButtonDefaults.buttonColors(containerColor = WinterBlue)) { Text("確認") } },
        shape = RoundedCornerShape(22.dp),
    )
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

Warning: truncated output (original token count: 29389)
Total output lines: 834

package jp.knaka.cardmemo

import android.os.Bundle
import android.app.Activity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.zIndex
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
import kotlin.math.abs

internal val WinterBlue = Color(0xFF173B8F)
internal val IceBlue = Color(0xFFF3F7FF)
internal val DeepNavy = Color(0xFF102A67)
internal val Cyan = Color(0xFF087A63)
internal val NeutralGray = Color(0xFFE4E9F0)
private val PaymentSourceColors = linkedMapOf("BLUE" to Color(0xFF315CA8), "GREEN" to Color(0xFF087A63), "RED" to Color(0xFFB23A48), "ORANGE" to Color(0xFFB86118), "PURPLE" to Color(0xFF7651A8), "GRAY" to Color(0xFF58636F))
private fun paymentSourceColor(key:String)=PaymentSourceColors[key]?:PaymentSourceColors.getValue("GREEN")
internal val AppTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp),
)
private val PieColors = listOf(Color(0xFF173B8F), Color(0xFF2F63D8), Color(0xFF0079B8), Color(0xFF318495), Color(0xFF3E7C78), Color(0xFF4D82C8), Color(0xFF2455A6), Color(0xFF66958B))

open class RecoFiActivityHost : ComponentActivity() {
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchMoved = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                touchMoved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (abs(event.x - touchDownX) > slop || abs(event.y - touchDownY) > slop) touchMoved = true
            }
            MotionEvent.ACTION_UP -> if (!touchMoved) {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            }
            MotionEvent.ACTION_CANCEL -> touchMoved = true
        }
        return super.dispatchTouchEvent(event)
    }

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

internal enum class AppTab(val label: String) { DETAILS("明細"), ANALYSIS("分析"), SETTINGS("設定") }
private enum class SettingsPage { MENU, CATEGORIES, MERCHANTS, DESCRIPTIONS, SOURCES, BUDGET, RECURRING, EXPORT, BACKUP, IMPORTED_FILES, RECONCILE, LOCK }

@Composable
internal fun CardMemoApp(vm: MainViewModel = viewModel()) {
    val transactions by vm.transactions.collectAsState()
    val categories by vm.categories.collectAsState()
    val merchantTemplates by vm.merchantTemplates.collectAsState()
    val descriptionTemplates by vm.descriptionTemplates.collectAsState()
    val recurring by vm.recurringExpenses.collectAsState()
    val budget by vm.monthlyBudget.collectAsState()
    val defaultBudget by vm.defaultMonthlyBudget.collectAsState()
    val paymentSources by vm.paymentSources.collectAsState()
    val defaultPaymentSourceId by vm.defaultPaymentSourceId.collectAsState()
    val lockedMonths by vm.lockedMonths.collectAsState()
    val reconciliationProgress by vm.reconciliationProgress.collectAsState()
    val confirmedTransactionIds by vm.confirmedTransactionIds.collectAsState()
    val suggestedTransactionIds by vm.suggestedTransactionIds.collectAsState()
    val importedStatements by vm.importedStatements.collectAsState()
    var selectedSourceId by remember { mutableStateOf("") }
    LaunchedEffect(paymentSources, defaultPaymentSourceId) { if (selectedSourceId.isBlank() || paymentSources.none { it.id == selectedSourceId }) selectedSourceId = resolveInitialPaymentSourceId(paymentSources,defaultPaymentSourceId) }
    var tab by remember { mutableStateOf(AppTab.DETAILS) }
    var showEntry by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<Transaction?>(null) }
    var detailsTool by remember { mutableStateOf<Pair<YearMonth, String>?>(null) }
    var detailsMonth by remember { mutableStateOf(YearMonth.now()) }
    var detailsHomeKey by remember { mutableIntStateOf(0) }
    var analysisHomeKey by remember { mutableIntStateOf(0) }
    var settingsHomeKey by remember { mutableIntStateOf(0) }
    var requestedSettingsPage by remember { mutableStateOf<SettingsPage?>(null) }

    Scaffold(
        containerColor = IceBlue,
        topBar = { Surface(color = DeepNavy,modifier=Modifier.statusBarsPadding()) { Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).clickable(role=androidx.compose.ui.semantics.Role.Button,onClickLabel="当月の明細へ戻る"){detailsMonth=YearMonth.now();detailsTool=null;tab=AppTab.DETAILS;detailsHomeKey++},contentAlignment=Alignment.Center){androidx.compose.foundation.Image(painter = androidx.compose.ui.res.painterResource(jp.knaka.cardmemo.R.drawable.recofi_monogram_icon), contentDescription = "当月の明細へ戻る", modifier = Modifier.size(36.dp))}; Spacer(Modifier.width(10.dp)); Text("RecoFi", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, letterSpacing = .3.sp, fontSize = 25.sp) } } },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                AppTab.entries.forEach { item -> NavigationBarItem(selected = tab == item, onClick = { tab = item; when (item) { AppTab.DETAILS -> { detailsMonth=YearMonth.now();detailsTool = null; detailsHomeKey++ }; AppTab.ANALYSIS -> analysisHomeKey++; AppTab.SETTINGS -> { requestedSettingsPage = null; settingsHomeKey++ } } }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF087A63), selectedTextColor = Color(0xFF202124), unselectedTextColor = Color(0xFF202124), indicatorColor = Color(0xFFCDEEDF)), icon = { Icon(when(item){AppTab.DETAILS->Icons.Outlined.ReceiptLong;AppTab.ANALYSIS->Icons.Outlined.Assessment;AppTab.SETTINGS->Icons.Outlined.Settings},contentDescription=item.label,modifier=Modifier.size(25.dp)) }, label = { Text(item.label) }) }
            }
        },
        floatingActionButton = { if (tab != AppTab.SETTINGS) FloatingActionButton(onClick = { showEntry = true }, containerColor = WinterBlue) { Text("＋", color = Color.White, fontSize = 26.sp) } }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                AppTab.DETAILS -> key(detailsHomeKey) { if (detailsTool == null) DetailsScreen(transactions, paymentSources, selectedSourceId, { selectedSourceId = it }, vm::ensureRecurringFor, vm::toggleConfirmed, { editItem = it }, lockedMonths, reconciliationProgress, confirmedTransactionIds, suggestedTransactionIds, detailsMonth, { month, source -> detailsMonth=month;detailsTool = month to source }, vm::setMonthLocked, vm::lockBlockers) { showEntry = true } else Column(Modifier.fillMaxSize().padding(16.dp)) { TextButton(onClick = { detailsTool = null }) { Text("‹ 戻る") }; Box(Modifier.weight(1f)) { DataTransferSettings(SettingsPage.RECONCILE, transactions, paymentSources, importedStatements, lockedMonths, vm::isImportedFile, vm::recordImportedFile, vm::saveImportedStatement, vm::addSuggestedTransactions, vm::setReconciliationProgress, vm::confirmTransactions, vm::linkImportedStatement, vm::deleteImportedStatement, detailsTool!!.first, detailsTool!!.second,vm) } } }
                AppTab.ANALYSIS -> key(analysisHomeKey) { AnalysisScreen(transactions, budget, defaultBudget, categories, merchantTemplates, descriptionTemplates, paymentSources, recurring, selectedSourceId, vm::ensureRecurringFor, vm::addTransaction, vm::deleteTransaction, vm::linkTransactionToRecurring, {showEntry=true}) { requestedSettingsPage=SettingsPage.BUDGET;tab=AppTab.SETTINGS;settingsHomeKey++ } }
                AppTab.SETTINGS -> key(settingsHomeKey) { SettingsScreen(categories, merchantTemplates, descriptionTemplates, recurring, budget, defaultBudget, paymentSources, defaultPaymentSourceId, transactions, importedStatements, lockedMonths, reconciliationProgress, requestedSettingsPage, vm) }
            }
        }
    }
    if (showEntry || editItem != null) {
        EntryDialog(categories, merchantTemplates, descriptionTemplates, paymentSources, selectedSourceId, editItem, {requestedSettingsPage=SettingsPage.MERCHANTS;showEntry=false;tab=AppTab.SETTINGS;settingsHomeKey++}, {requestedSettingsPage=SettingsPage.DESCRIPTIONS;showEntry=false;tab=AppTab.SETTINGS;settingsHomeKey++}, recurringExpenses=recurring,linkRecurring=vm::linkTransactionToRecurring,onDismiss = { showEntry = false; editItem = null }, onDelete = { id -> vm.deleteTransaction(id); showEntry = false; editItem = null }) { amount, category, merchant, description, date, sourceId ->
            vm.addTransaction(amount, category, merchant, description, date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), sourceId, editItem?.id)
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
    val accent=paymentSourceColor(selected?.colorKey.orEmpty())
    Box { OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(containerColor = accent.copy(alpha=.12f), contentColor = accent), border = BorderStroke(1.dp, accent)) { Text(selected?.name ?: "支払方法", fontWeight = FontWeight.SemiBold) }; DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Color(0xFFF4F7F6)) { sources.forEach { source -> DropdownMenuItem(text = { Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(12.dp).background(paymentSourceColor(source.colorKey),RoundedCornerShape(50)));Spacer(Modifier.width(8.dp));Text(source.name)} }, onClick = { onSelect(source.id); expanded = false }) } } }
}

@Composable private fun DetailsScreen(all: List<Transaction>, sources: List<PaymentSource>, sourceId: String, selectSource: (String) -> Unit, ensure: (YearMonth) -> Unit, toggle: (Long) -> Unit, edit: (Transaction) -> Unit, lockedMonths: Set<String>, reconciliationProgress: Map<String, ReconciliationProgress>, confirmedIds:Set<Long>,suggestedIds:Set<Long>, initialMonth:YearMonth, openTransfer: (YearMonth, String) -> Unit, setLocked: (YearMonth, Boolean) -> Unit, lockBlockers: (YearMonth) -> List<LockBlocker>, addExpense: () -> Unit) {
    var month by remember(initialMonth) { mutableStateOf(initialMonth) }
    var onlyOpen by remember { mutableStateOf(false) }
    LaunchedEffect(month) { ensure(month) }
    val monthRows = all.forMonth(month).filter { it.paymentSourceId == sourceId }
    val rows = if (onlyOpen) monthRows.filterNot { it.id in confirmedIds } else monthRows
    var lockedWarning by remember { mutableStateOf(false) }
    var lockFailed by remember { mutableStateOf(false) }
    var blockerMessage by remember { mutableStateOf("") }
    var confirmUnlock by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var showMonthlySummary by remember(month, sourceId) { mutableStateOf(true) }
    val fastFlingConnection = remember {
        object : NestedScrollConnection {
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y < -3500f) showMonthlySummary = false
                return Velocity.Zero
            }
        }
    }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) showMonthlySummary = true
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PaymentSourcePicker(sources, sourceId, selectSource)
        MonthHeader(month, { month = month.minusMonths(1) }, { month = month.plusMonths(1) })
        if (showMonthlySummary) Card(colors = CardDefaults.cardColors(containerColor = WinterBlue), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("この月の利用額", color = Color.White.copy(.78f)); Text(yen(monthRows.sumOf { it.amount }), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold); Text("${monthRows.size}件 ・ 要確認 ${monthRows.count { it.id !in confirmedIds }}件", color = Color.White.copy(.82f)) }; OutlinedButton(onClick = { openTransfer(month, sourceId) }, border = BorderStroke(1.dp, Color.White.copy(.38f)), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(.10f), contentColor = Color.White), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text("カード明細を取込・照合  ›", fontSize = 11.sp, fontWeight = FontWeight.Bold) } } }
        val progress=reconciliationProgress["$month|$sourceId"]
        if(progress!=null&&progress.matched>progress.confirmed)EmptyStateCard("確認待ちの候補があります","候補を確認すると、この月を確定できます","候補を確認"){openTransfer(month,sourceId)}
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Switch(onlyOpen, { onlyOpen = it }); Spacer(Modifier.width(8.dp)); Text("未確認のみ"); Spacer(Modifier.weight(1f)); val isLocked = month.toString() in lockedMonths; TextButton(onClick = { if (isLocked) confirmUnlock = true else { val blockers=lockBlockers(month); if(blockers.isEmpty())setLocked(month,true) else { blockerMessage=blockers.joinToString("\n"){it.message};lockFailed=true } } }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(if (isLocked) "🔒 確定済み" else "月を確定", color = Color.Gray, fontSize = 11.sp) } }
        Text("${rows.size}件  ${yen(rows.sumOf { it.amount })}", color = DeepNavy, fontWeight = FontWeight.Bold)
        if (rows.isEmpty()) EmptyStateCard("まだ支出がありません", "まずは支出を1件記録しましょう", "最初の支出を記録", addExpense) else LazyColumn(Modifier.nestedScroll(fastFlingConnection),state=listState,verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth().clickable { if(month.toString() in lockedMonths)lockedWarning=true else edit(item) }, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = if (item.recurringId != null) 4.dp else 1.dp), border = when { item.id in suggestedIds -> BorderStroke(2.dp, Color(0xFFE14964)); item.recurringId != null -> BorderStroke(1.dp, Color(0xFF89A9FF)); else -> null }) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val itemLocked = month.toString() in lockedMonths
                    if(item.id in confirmedIds||item.id in suggestedIds)Checkbox(item.id in confirmedIds, { if (itemLocked) lockedWarning = true else toggle(item.id) }) else Spacer(Modifier.width(48.dp))
                    Column(Modifier.weight(1f)) { Text(transactionPrimaryText(item), fontWeight = FontWeight.SemiBold, maxLines = 1); Text("${transactionSecondaryText(item)}${if(item.recurringId != null) "  固定費" else ""}${if(item.id in suggestedIds) "  要確認" else ""}", fontSize = 12.sp, color = if(item.id in suggestedIds) Color(0xFFE14964) else Color.Gray,maxLines=1,overflow=TextOverflow.Ellipsis) }
                    Text(yen(item.amount), fontWeight = FontWeight.Bold)
                } }
            }
        }
    }
    if (lockedWarning) CuteWarningDialog("この月はロック中だよ 🔒", "確定した明細を守るため、手動での消込変更もお休み中です。変更したいときは『確定済み』を押して解除してください。") { lockedWarning = false }
    if (lockFailed) CuteWarningDialog("まだ確定できません", blockerMessage.ifBlank { "未確認の明細が残っています。" }) { lockFailed = false }
    if(confirmUnlock) AlertDialog(onDismissRequest={confirmUnlock=false},title={Text("月次ロックを解除しますか？")},text={Text("解除すると、この月の明細や消込状態を再び変更できるようになります。")},confirmButton={Button(onClick={setLocked(month,false);confirmUnlock=false}){Text("解除する")}},dismissButton={TextButton(onClick={confirmUnlock=false}){Text("キャンセル")}})
}

@Composable private fun AnalysisScreen(all: List<Transaction>, budgets: Map<String, Long>, defaultBudget: Long, categories:List<String>, merchants:List<String>, descriptions:List<String>, sources:List<PaymentSource>, recurringExpenses:List<RecurringExpense>, defaultSourceId:String, ensure: (YearMonth) -> Unit, saveTransaction:(Long,String,String,String,Long,String,Long?)->Unit, deleteTransaction:(Long)->Unit, linkRecurring:(Long,Long?)->Unit, recordExpense:()->Unit, setBudget:()->Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val budget = budgets[month.toString()] ?: defaultBudget
    var analysisMode by remember { mutableIntStateOf(0) }
    var annual by remember { mutableStateOf(false) }
    var detailCategory by remember { mutableStateOf<String?>(null) }
    var detailMerchant by remember { mutableStateOf<String?>(null) }
    var detailDate by remember { mutableStateOf<LocalDate?>(null) }
    var detailTrendMonth by remember { mutableStateOf<YearMonth?>(null) }
    var detailTrendRows by remember { mutableStateOf<List<Transaction>?>(null) }
    var editAnalysisTransaction by remember { mutableStateOf<Transaction?>(null) }
    val fixedOnly = analysisMode == 1
    val calendarMode = analysisMode == 2
    val merchantMode = analysisMode == 3
    val supportsAnnual = !calendarMode
    LaunchedEffect(month) { ensure(month) }
    val analysis = remember(all, month, fixedOnly) { SpendingAnalysis.calculate(all, month, fixedOnly) }
    val monthRows = analysis.monthRows
    val analysisRows = remember(all, analysis, month, annual, supportsAnnual, fixedOnly) { if (annual && supportsAnnual) all.forYear(month.year).let { rows -> if(fixedOnly) rows.filter { it.recurringId != null } else rows } else analysis.analysisRows }
    val previousRows = remember(all, analysis, month, annual, supportsAnnual, fixedOnly) { if (annual && supportsAnnual) all.forYear(month.year - 1).let { rows -> if(fixedOnly) rows.filter { it.recurringId != null } else rows } else analysis.previousMonthRows.let { rows -> if(fixedOnly) rows.filter { it.recurringId != null } else rows } }
    val totals = remember(analysisRows) { analysisRows.groupBy { it.category }.mapValues { it.value.sumOf(Transaction::amount) }.toList().sortedByDescending { it.second } }
    val total = totals.sumOf { it.second }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TabRow(selectedTabIndex = analysisMode, containerColor = Color.Transparent) { Tab(selected = analysisMode == 0, onClick = { analysisMode = 0 }, text = { Text("全体", maxLines = 1, fontSize = 12.sp) }); Tab(selected = analysisMode == 1, onClick = { analysisMode = 1 }, text = { Text("固定費", maxLines = 1, fontSize = 12.sp) }); Tab(selected = analysisMode == 2, onClick = { analysisMode = 2 }, text = { Text("カレンダー", maxLines = 1, fontSize = 10.sp) }); Tab(selected = analysisMode == 3, onClick = { analysisMode = 3 }, text = { Text("取引先", maxLines = 1, fontSize = 12.sp) }) }
        if (supportsAnnual) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { FilterChip(selected = !annual, onClick = { annual = false }, label = { Text("月間") }); Spacer(Modifier.width(8.dp)); FilterChip(selected = annual, onClick = { annual = true }, label = { Text("年間") }) }
        if (annual && supportsAnnual) YearHeader(month.year, { month = month.minusYears(1) }, { month = month.plusYears(1) }) else MonthHeader(month, { month = month.minusMonths(1) }, { month = month.plusMonths(1) })
        Text(when { fixedOnly -> if(annual) "${month.year}年の固定費分析" else "固定費の分析"; merchantMode -> if (annual) "${month.year}年の取引先分析" else "取引先を軸にした分析"; calendarMode -> "支出カレンダー"; annual -> "${month.year}年のカテゴリ別支出"; else -> "カテゴリ別支出" }, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy)
        if (calendarMode) {
            SpendingCalendar(month, monthRows) { detailDate = it }
        } else if (merchantMode) {
            MerchantAnalysis(analysisRows, previousRows, annual) { detailMerchant = it }
        } else {
        if (!annual && budget <= 0) EmptyStateCard("月間予算が未設定です","予算を登録すると、使用状況を比較できます","月間予算を設定",setBudget)
        if (total == 0L && (annual || budget <= 0L)) EmptyStateCard("分析データがありません","支出を記録すると分析が表示されます","支出を記録",recordExpense) else {
            Box(Modifier.fillMaxWidth().height(245.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(220.dp)) { val denominator = if (annual) maxOf(total, 1L) else maxOf(budget, total, 1L); drawArc(Color(0xFFE3E6ED), -90f, 360f, useCenter = false, style = Stroke(42f, cap = StrokeCap.Butt)); var start = -90f; totals.forEachIndexed { index, entry -> val remaining = (360f - (start + 90f)).coerceAtLeast(0f); val sweep = (entry.second.toFloat() / denominator * 360f).coerceAtMost(remaining); if (sweep > 0) drawArc(PieColors[index % PieColors.size], start, sweep, useCenter = false, style = Stroke(42f, cap = StrokeCap.Butt)); start += sweep } }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { val overBudget = !annual && budget > 0 && total > budget; val emphasizeOver = overBudget && !fixedOnly; if (overBudget) Text("予算オーバー", color = Color(0xFFFF1738), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold); Text(if (annual) "年間支出" else "現在の支出", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Normal); Text(yen(total), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (emphasizeOver) Color(0xFFFF1738) else DeepNavy); if (!annual && budget > 0) { Text("予算比 ${total * 100 / budget}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (emphasizeOver) Color(0xFFFF1738) else DeepNavy); Text("予算 ${yen(budget)}", fontSize = 11.sp, color = Color(0xFF202124)) } }
            }
            totals.forEachIndexed { index, (category, amount) -> Card(Modifier.fillMaxWidth().clickable { detailCategory = category }, colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(14.dp).background(PieColors[index % PieColors.size], RoundedCornerShape(3.dp))); Spacer(Modifier.width(10.dp)); Text(category, Modifier.weight(1f)); Text("${if (fixedOnly && budget > 0) amount * 100 / budget else if(total > 0) amount * 100 / total else 0}%", fontWeight = FontWeight.SemiBold, color = if (fixedOnly) DeepNavy else Color(0xFF202124)); Spacer(Modifier.width(8.dp)); Text(yen(amount), fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(6.dp)); Text("›", color = WinterBlue) } } }
        }
        }
    }
    if(editAnalysisTransaction==null&&detailTrendMonth==null)detailCategory?.let { category -> val rows = analysisRows.filter { it.category == category }; TransactionDetailDialog(if(annual)"$category の月次推移" else "$category の明細", rows, { detailCategory = null }, if (annual) month.year else null, { selected -> detailTrendRows = rows.forMonth(selected); detailTrendMonth = selected }, { editAnalysisTransaction=it }, if(annual)"‹ 分析に戻る" else "閉じる") }
    if(editAnalysisTransaction==null&&detailTrendMonth==null)detailMerchant?.let { normalized -> val otherKeys=merchantKeysOutsideTop(analysisRows,50);val rows = if(normalized==OTHER_MERCHANT_KEY)analysisRows.filter{normalizeForLearning(it.merchant) in otherKeys}else analysisRows.filter { normalizeForLearning(it.merchant) == normalized }; TransactionDetailDialog(if(normalized==OTHER_MERCHANT_KEY)"その他の取引先${if(annual)"の月次推移" else ""}" else if (annual) "${rows.firstOrNull()?.merchant.orEmpty()} の月次推移" else "取引先別の明細", rows, { detailMerchant = null }, if (annual) month.year else null, { selected -> detailTrendRows = rows.forMonth(selected); detailTrendMonth = selected }, { editAnalysisTransaction=it }, if(annual)"‹ 分析に戻る" else "閉じる") }
    if(editAnalysisTransaction==null)detailDate?.let { date -> TransactionDetailDialog("${date.monthValue}月${date.dayOfMonth}日の明細", monthRows.filter { Instant.ofEpochMilli(it.usedAt).atZone(ZoneId.systemDefault()).toLocalDate() == date }, { detailDate = null }, editTransaction={editAnalysisTransaction=it}) }
    if(editAnalysisTransaction==null)detailTrendMonth?.let { selected -> TransactionDetailDialog("${selected.year}年${selected.monthValue}月の明細", detailTrendRows ?: all.forMonth(selected), { detailTrendMonth = null; detailTrendRows = null }, editTransaction={editAnalysisTransaction=it}, backLabel="‹ 月次推移に戻る") }
    editAnalysisTransaction?.let { edit -> EntryDialog(categories,merchants,descriptions,sources,edit.paymentSourceId.ifBlank{defaultSourceId},edit,{}, {},recurringExpenses=recurringExpenses,linkRecurring=linkRecurring,onDismiss={editAnalysisTransaction=null},onDelete={deleteTransaction(it);editAnalysisTransaction=null}){amount,category,merchant,description,date,sourceId->saveTransaction(amount,category,merchant,description,date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),sourceId,edit.id);editAnalysisTransaction=null} }
}

@Composable private fun YearHeader(year:Int,previous:()->Unit,next:()->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){OutlinedButton(onClick=previous){Text("‹")};Text("${year}年",fontSize=20.sp,fontWeight=FontWeight.Bold,color=DeepNavy);OutlinedButton(onClick=next){Text("›")}}}

@Composable private fun MonthlySpendingTrend(year: Int, rows: List<Transaction>, openMonth: (YearMonth) -> Unit) {
    val monthlyTotals = remember(rows, year) {
        (1..12).map { monthValue ->
            val target = YearMonth.of(year, monthValue)
            target to rows.filter { YearMonth.from(Instant.ofEpochMilli(it.usedAt).atZone(ZoneId.systemDefault())) == target }.sumOf(Transaction::amount)
        }
    }
    val yearTotal = monthlyTotals.sumOf { it.second }
    val elapsedMonths = if (year == LocalDate.now().year) LocalDate.now().monthValue else 12
    val average = if (elapsedMonths > 0) yearTotal / elapsedMonths else 0L
    val maximum = monthlyTotals.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("年間合計", color = Color.Gray, fontSize = 12.sp); Text(yen(yearTotal), color = DeepNavy, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                Column(horizontalAlignment = Alignment.End) { Text("月平均", color = Color.Gray, fontSize = 12.sp); Text(yen(average), color = DeepNavy, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(4.dp))
            monthlyTotals.forEach { (target, amount) ->
                Row(Modifier.fillMaxWidth().clickable { openMonth(target) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${target.monthValue}月", modifier = Modifier.width(34.dp), fontSize = 12.sp, color = Color(0xFF202124))
                    Box(Modifier.weight(1f).height(14.dp).background(Color(0xFFE3E6ED), RoundedCornerShape(7.dp))) {
                        Box(Modifier.fillMaxWidth((amount.toFloat() / maximum).coerceIn(0f, 1f)).fillMaxHeight().background(WinterBlue, RoundedCornerShape(7.dp)))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(yen(amount), modifier = Modifier.width(82.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DeepNavy)
                    Text("›", color = WinterBlue)
                }
            }
            Text("各月を押すと明細を確認できます", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable private fun SpendingCalendar(month: YearMonth, rows: List<Transaction>, openDay: (LocalDate) -> Unit) {
    val daily = rows.groupBy { Instant.ofEpochMilli(it.usedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.mapValues { it.value.sumOf(Transaction::amount) }
    val maximum = daily.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
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

@Composable private fun SettingsScreen(categories: List<String>, merchants: List<String>, descriptions: List<String>, recurring: List<RecurringExpense>, budgets: Map<String, Long>, defaultBudget: Long, sources: List<PaymentSource>, defaultSourceId:String?, transactions: List<Transaction>, importedStatements: List<ImportedStatement>, lockedMonths: Set<String>, reconciliationProgress: Map<String, ReconciliationProgress>, initialPage:SettingsPage?, vm: MainViewModel) {
    var page by remember(initialPage) { mutableStateOf(initialPage?:SettingsPage.MENU) }
    val menuListState = rememberLazyListState()
    val budget = budgets[YearMonth.now().toString()] ?: defaultBudget
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (page != SettingsPage.MENU) TextButton(onClick = { page = SettingsPage.MENU }) { Text("‹ 設定に戻る") }
        when (page) {
            SettingsPage.MENU -> LazyColumn(state = menuListState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("設定", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = DeepNavy); SettingsSectionTitle("入力設定") }
                item { SettingsMenuItem("支出カテゴリ", "外食・食料品などの分類を管理") { page = SettingsPage.CATEGORIES } }
                item { SettingsMenuItem("よく使う取引先", "入力時に使う取引先を管理") { page = SettingsPage.MERCHANTS } }
                item { SettingsMenuItem("よく使う内容", "入力時に使う内容を管理") { page = SettingsPage.DESCRIPTIONS } }
                item { SettingsMenuItem("支払方法", "カードなどの支払先を管理") { page = SettingsPage.SOURCES } }
                item { SettingsSectionTitle("家計管理") }
                item { SettingsMenuItem("月間予算", if (budget > 0) "現在 ${yen(budget)}" else "毎月の支出目標を登録") { page = SettingsPage.BUDGET } }
                item { SettingsMenuItem("固定費プラン", "サブスクや定期支払いを管理") { page = SettingsPage.RECURRING } }
                item { SettingsMenuItem("月次ロック", "確定済みの月を保護") { page = SettingsPage.LOCK } }
                item { SettingsSectionTitle("カード明細・消込") }
                item { SettingsMenuItem("取込ファイル管理", "保存済みのカード明細ファイルを確認") { page = SettingsPage.IMPORTED_FILES } }
                item { SettingsSectionTitle("データ管理") }
                item { SettingsMenuItem("バックアップと復元", "すべてのデータを安全に保存・復元") { page = SettingsPage.BACKUP } }
                item { SettingsMenuItem("CSVエクスポート", "月ごとの明細をCSVで保存") { page = SettingsPage.EXPORT } }
                item {
                    Text(
                        "RecoFi v${BuildConfig.VERSION_NAME}",
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
       …9389 tokens truncated…nToString("\n") { it.message }
                if (warning == null) confirmLock = true
            }, modifier = Modifier.fillMaxWidth()) { Text("この月を確定してロック") }
        } }
    }
    warning?.let { CuteWarningDialog("まだロックできないよ 🌷", "$it\nぜんぶ確認できたら、もう一度試してね。") { warning = null } }
    if (confirmLock) AlertDialog(onDismissRequest = { confirmLock = false }, title = { Text("${month.year}年${month.monthValue}月をロックしますか？ 🔐") }, text = { Text("ロック中は、自動消込と明細画面の手動消込を変更できません。") }, confirmButton = { Button(onClick = { setLocked(month, true); confirmLock = false }) { Text("ロックする") } }, dismissButton = { TextButton(onClick = { confirmLock = false }) { Text("キャンセル") } })
    if (confirmUnlock) AlertDialog(onDismissRequest = { confirmUnlock = false }, title = { Text("月次ロックを解除しますか？") }, text = { Text("解除すると、この月の家計データを再び変更できるようになります。") }, confirmButton = { Button(onClick = { setLocked(month, false); confirmUnlock = false }) { Text("解除する") } }, dismissButton = { TextButton(onClick = { confirmUnlock = false }) { Text("キャンセル") } })
}

@Composable private fun PaymentSourceSettings(sources: List<PaymentSource>, defaultSourceId:String?, setDefault:(String)->Unit, add: (String, PaymentSourceType) -> Unit, edit: (String, String, PaymentSourceType, String?) -> Unit, delete: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PaymentSourceType.CREDIT_CARD) }
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("支払方法の設定", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy)
        OutlinedTextField(name, { name = it }, label = { Text("カード名・支払方法名") }, modifier = Modifier.fillMaxWidth())
        Box { OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text("種類：${paymentSourceTypeLabel(type)}") }; DropdownMenu(expanded, { expanded = false }) { PaymentSourceType.entries.forEach { candidate -> DropdownMenuItem({ Text(paymentSourceTypeLabel(candidate)) }, { type = candidate; expanded = false }) } } }
        Button(enabled = name.trim().isNotEmpty(), onClick = { add(name, type); name = "" }, modifier = Modifier.fillMaxWidth()) { Text("追加") }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(sources, key = { it.id }) { source -> var itemExpanded by remember(source.id) { mutableStateOf(false) }; Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) { Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(14.dp).background(paymentSourceColor(source.colorKey),RoundedCornerShape(50)));Spacer(Modifier.width(8.dp));Text(source.name, fontWeight = FontWeight.SemiBold,modifier=Modifier.weight(1f));if(source.id==defaultSourceId)Text("デフォルト",color=WinterBlue,fontSize=12.sp,fontWeight=FontWeight.Bold)}; Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { TextButton({ itemExpanded = true }) { Text("種類：${paymentSourceTypeLabel(source.type)}") }; DropdownMenu(itemExpanded, { itemExpanded = false }) { PaymentSourceType.entries.forEach { candidate -> DropdownMenuItem({ Text(paymentSourceTypeLabel(candidate)) }, { edit(source.id, source.name, candidate, null); itemExpanded = false }) } } };if(source.id!=defaultSourceId)TextButton(onClick={setDefault(source.id)}){Text("デフォルトに設定")}; TextButton(onClick = { delete(source.id) }) { Text("削除") } };Text("表示色",fontSize=12.sp,color=Color.Gray);Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){PaymentSourceColors.forEach{(key,color)->Box(Modifier.size(30.dp).background(color,RoundedCornerShape(50)).then(if(source.colorKey==key)Modifier.border(3.dp,Color.White,RoundedCornerShape(50)).border(1.dp,DeepNavy,RoundedCornerShape(50)) else Modifier).clickable{edit(source.id,source.name,source.type,key)})}} } } } }
    }
}

private fun paymentSourceTypeLabel(type: PaymentSourceType) = when (type) { PaymentSourceType.CREDIT_CARD -> "クレジットカード"; PaymentSourceType.CASH -> "現金"; PaymentSourceType.OTHER -> "その他" }

@Composable private fun BudgetSettings(budgets: Map<String, Long>, defaultBudget: Long, saveDefault: (Long) -> Unit, save: (YearMonth, Long) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val override = budgets[month.toString()]
    var value by remember(month, override) { mutableStateOf(override?.toString().orEmpty()) }
    var commonValue by remember(defaultBudget) { mutableStateOf(if (defaultBudget > 0) defaultBudget.toString() else "") }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text("予算設定", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy); Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("毎月共通の予算", fontWeight = FontWeight.Bold, color = DeepNavy); Text("月別設定がない月には、この金額を自動で適用します。", color = Color.Gray, fontSize = 12.sp); OutlinedTextField(commonValue, { commonValue = it.filter(Char::isDigit) }, label = { Text("共通予算額") }, prefix = { Text("¥") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()); Button(onClick = { saveDefault(commonValue.toLongOrNull() ?: 0L) }, enabled = commonValue.toLongOrNull() != null, modifier = Modifier.fillMaxWidth()) { Text("共通予算を保存") } } }; Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("月別の予算", fontWeight = FontWeight.Bold, color = DeepNavy); Text("特定の月だけ、共通予算とは違う金額にできます。", color = Color.Gray, fontSize = 12.sp); MonthHeader(month, { month = month.minusMonths(1) }, { month = month.plusMonths(1) }); OutlinedTextField(value, { value = it.filter(Char::isDigit) }, label = { Text("${month.monthValue}月の予算額") }, placeholder = { if (defaultBudget > 0) Text("共通予算 ${yen(defaultBudget)}") }, prefix = { Text("¥") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()); Button(onClick = { save(month, value.toLongOrNull() ?: 0L) }, enabled = value.toLongOrNull() != null, modifier = Modifier.fillMaxWidth()) { Text("この月だけ上書き") }; if (override != null) TextButton(onClick = { save(month, 0L); value = "" }, modifier = Modifier.fillMaxWidth()) { Text("月別設定を解除して共通予算に戻す") } } } }
}

@Composable private fun SettingsMenuItem(title: String, description: String, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, color = DeepNavy); Text(description, color = Color.Gray, fontSize = 13.sp) }; Text("›", color = WinterBlue, fontSize = 28.sp) } } }

@Composable private fun StringSettings(title: String, hint: String, values: List<String>, add: (String) -> Unit, delete: (String) -> Unit, saveOrder:(List<String>)->Unit,rename:(String,String)->String?) {
    var value by remember { mutableStateOf("") };var editing by remember{mutableStateOf<String?>(null)};var editValue by remember{mutableStateOf("")};var editError by remember{mutableStateOf<String?>(null)}
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) { Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy, modifier = Modifier.weight(1f)); Text("${values.size}/$USER_MANAGED_LIST_LIMIT", color = if (values.size >= USER_MANAGED_LIST_LIMIT) Color(0xFFD62F55) else Color.Gray) }
        Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value, { value = it }, label = { Text(hint) }, modifier = Modifier.weight(1f), enabled = values.size < USER_MANAGED_LIST_LIMIT); Spacer(Modifier.width(8.dp)); Button(enabled = values.size < USER_MANAGED_LIST_LIMIT && value.trim().isNotEmpty(), onClick = { add(value); value = "" }) { Text("追加") } }
        if (values.size >= USER_MANAGED_LIST_LIMIT) Text("登録できるのは${USER_MANAGED_LIST_LIMIT}件までです。", color = Color(0xFFD62F55), fontSize = 13.sp)
        Text("項目を長押しして、そのまま上下に動かすと並び替えられます", color = Color.Gray, fontSize = 12.sp)
        ReorderableStringList(values, Modifier.weight(1f), saveOrder) { item ->
            Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item, Modifier.weight(1f).clickable { editing=item;editValue=item;editError=null })
                TextButton(onClick={editing=item;editValue=item;editError=null}){Text("編集")}
                TextButton(onClick = { delete(item) }) { Text("削除") }
            }
        }
    }
    editing?.let{old->AlertDialog(onDismissRequest={editing=null},title={Text("名称を編集")},text={Column{OutlinedTextField(editValue,{editValue=it;editError=null},label={Text(title)},singleLine=true);editError?.let{Text(it,color=Color(0xFFD62F55),fontSize=13.sp)}}},confirmButton={Button(enabled=editValue.trim().isNotEmpty()&&editValue.trim()!=old,onClick={val error=rename(old,editValue);if(error==null)editing=null else editError=error}){Text("変更")}},dismissButton={TextButton(onClick={editing=null}){Text("キャンセル")}})}
}

@Composable private fun ReorderableStringList(values: List<String>, modifier: Modifier = Modifier, saveOrder: (List<String>) -> Unit, content: @Composable (String) -> Unit) {
    var localValues by remember { mutableStateOf(values) }
    val listState = rememberLazyListState()
    var draggingItem by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val moveThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 54.dp.toPx() }
    LaunchedEffect(values) { localValues = values }

    LazyColumn(modifier, state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        itemsIndexed(localValues, key = { _, item -> item }) { _, item ->
            val isDragging = draggingItem == item
            Card(
                Modifier.fillMaxWidth().zIndex(if(isDragging)1f else 0f).graphicsLayer { translationY=if(isDragging)dragOffset else 0f; shadowElevation=if(isDragging)16f else 0f; scaleX=if(isDragging)1.02f else 1f; scaleY=if(isDragging)1.02f else 1f }
                    .pointerInput(item,localValues) { detectDragGesturesAfterLongPress(
                        onDragStart={draggingItem=item;dragOffset=0f},
                        onDragCancel={draggingItem=null;dragOffset=0f},
                        onDragEnd={draggingItem=null;dragOffset=0f;saveOrder(localValues)},
                        onDrag={change,amount->change.consume();dragOffset+=amount.y;val current=localValues.indexOf(item);val direction=when{dragOffset>moveThreshold->1;dragOffset< -moveThreshold->-1;else->0};val target=current+direction;if(direction!=0&&target in localValues.indices){localValues=moveItem(localValues,current,target);dragOffset-=direction*moveThreshold}}
                    ) },
                elevation = CardDefaults.cardElevation(defaultElevation = if(isDragging)8.dp else 1.dp),
                colors = CardDefaults.cardColors(containerColor = if(isDragging)Color(0xFFF3F7FF) else Color.White)
            ) {
                Box(Modifier.fillMaxWidth()) { content(item) }
            }
        }
    }
}

@Composable private fun RecurringSettings(categories: List<String>, sources: List<PaymentSource>, defaultSourceId:String?, rows: List<RecurringExpense>, save: (Long?, Long, Int, String, String, String, LocalDate, String, Int) -> Unit, delete: (Long) -> Unit, duplicate: (Long) -> Unit, endContract: (Long, LocalDate) -> Unit, revisePrice: (Long, LocalDate, Long) -> Unit, reorder:(List<RecurringExpense>)->Unit) {
    var showDialog by remember { mutableStateOf(false) }; var editing by remember { mutableStateOf<RecurringExpense?>(null) }; var showArchive by remember { mutableStateOf(false) }; var deleteCandidate by remember { mutableStateOf<RecurringExpense?>(null) }
    val today = LocalDate.now(); val visibleRows = rows.filter { item -> val ended = item.endDate?.let { LocalDate.parse(it).isBefore(today) } == true; ended == showArchive }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("固定費の設定", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepNavy); Text(if (showArchive) "終了済みの固定費" else "長押しして上下に動かすと並べ替えられます", color = Color.Gray, fontSize = 13.sp) }; if (!showArchive) Button(onClick = { editing = null; showDialog = true }) { Text("追加") } }
        TabRow(selectedTabIndex = if (showArchive) 1 else 0, containerColor = Color.Transparent) { Tab(selected = !showArchive, onClick = { showArchive = false }, text = { Text("契約中") }); Tab(selected = showArchive, onClick = { showArchive = true }, text = { Text("アーカイブ") }) }
        if (visibleRows.isEmpty()) { if(showArchive)EmptyMessage("アーカイブはありません") else EmptyStateCard("固定費はまだありません","サブスクや定期支払いを登録できます","固定費を追加"){editing=null;showDialog=true} } else ReorderableRecurringList(visibleRows,Modifier.weight(1f),{ordered->val hidden=rows.filter{candidate->ordered.none{it.id==candidate.id}};reorder(if(showArchive)hidden+ordered else ordered+hidden)},{editing=it;showDialog=true},duplicate,{deleteCandidate=it})
    }
    if (showDialog) RecurringDialog(categories, sources, defaultSourceId, editing, { showDialog = false; editing = null }, { id, date -> endContract(id, date); editing = editing?.copy(endDate = date.toString()) }, { id, date, amount -> revisePrice(id, date, amount); editing = editing?.copy(priceRevisions = editing!!.priceRevisions + PriceRevision(date.toString(), amount)) }) { id, amount, day, category, merchant, description, contractDate, sourceId, interval -> save(id, amount, day, category, merchant, description, contractDate, sourceId, interval); showDialog = false; editing = null }
    deleteCandidate?.let { item -> ConfirmDeleteDialog("固定費を削除しますか？", "${item.merchant.ifBlank { item.category }}\nこの固定費から自動作成された明細も削除されます。", { deleteCandidate = null }) { delete(item.id); deleteCandidate = null } }
}

@Composable private fun ReorderableRecurringList(values:List<RecurringExpense>,modifier:Modifier=Modifier,saveOrder:(List<RecurringExpense>)->Unit,edit:(RecurringExpense)->Unit,duplicate:(Long)->Unit,delete:(RecurringExpense)->Unit){
    var localValues by remember{mutableStateOf(values)};var draggingId by remember{mutableStateOf<Long?>(null)};var dragOffset by remember{mutableFloatStateOf(0f)};val moveThreshold=with(androidx.compose.ui.platform.LocalDensity.current){92.dp.toPx()}
    LaunchedEffect(values){localValues=values}
    LazyColumn(modifier,verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=20.dp)){
        itemsIndexed(localValues,key={_,item->item.id}){_,item->val isDragging=draggingId==item.id
            Card(Modifier.fillMaxWidth().zIndex(if(isDragging)1f else 0f).graphicsLayer{translationY=if(isDragging)dragOffset else 0f;shadowElevation=if(isDragging)16f else 0f;scaleX=if(isDragging)1.02f else 1f;scaleY=if(isDragging)1.02f else 1f}.pointerInput(item.id){detectDragGesturesAfterLongPress(onDragStart={draggingId=item.id;dragOffset=0f},onDragCancel={draggingId=null;dragOffset=0f},onDragEnd={draggingId=null;dragOffset=0f;saveOrder(localValues)},onDrag={change,amount->change.consume();dragOffset+=amount.y;val current=localValues.indexOfFirst{it.id==item.id};val direction=when{dragOffset>moveThreshold->1;dragOffset< -moveThreshold->-1;else->0};val target=current+direction;if(direction!=0&&target in localValues.indices){localValues=moveItem(localValues,current,target);dragOffset-=direction*moveThreshold}})}.clickable{edit(item)},elevation=CardDefaults.cardElevation(defaultElevation=if(isDragging)8.dp else 1.dp),colors=CardDefaults.cardColors(containerColor=if(isDragging)Color(0xFFF3F7FF)else Color.White)){
                Column(Modifier.padding(14.dp)){Text(item.merchant.ifBlank{item.description.ifBlank{item.category}},fontWeight=FontWeight.Bold);if(item.description.isNotBlank())Text(item.description,color=Color.Gray,fontSize=12.sp);Text("${item.contractDate.replace('-', '/')}契約 ・ ${intervalLabel(item.intervalMonths)}・${recurringPaymentDayLabel(item.intervalMonths,item.paymentDay)}",color=Color.Gray,fontSize=12.sp);Text("${item.category}  ${yen(item.priceRevisions.lastOrNull()?.amount?:item.amount)}${item.endDate?.let{" ・ ${it.replace('-', '/')}終了"}.orEmpty()}",color=Color.Gray,fontSize=13.sp);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick={duplicate(item.id)}){Text("複製")};TextButton(onClick={delete(item)}){Text("削除")}}}
            }
        }
    }
}

@Composable private fun RecurringDialog(categories: List<String>, sources: List<PaymentSource>, defaultSourceId:String?, existing: RecurringExpense?, dismiss: () -> Unit, endContract: (Long, LocalDate) -> Unit, revisePrice: (Long, LocalDate, Long) -> Unit, save: (Long?, Long, Int, String, String, String, LocalDate, String, Int) -> Unit) {
    var amount by remember(existing) { mutableStateOf(existing?.amount?.toString().orEmpty()) }; var day by remember(existing) { mutableIntStateOf(existing?.paymentDay ?: 1) }; var merchant by remember(existing) { mutableStateOf(existing?.merchant.orEmpty()) }; var description by remember(existing) { mutableStateOf(existing?.description.orEmpty()) }; var contractDate by remember(existing) { mutableStateOf(existing?.contractDate?.let(LocalDate::parse) ?: LocalDate.now()) }; var category by remember(existing, categories) { mutableStateOf(existing?.category ?: categories.firstOrNull().orEmpty()) }; var categoryExpanded by remember { mutableStateOf(false) }; var dayExpanded by remember { mutableStateOf(false) }; var intervalExpanded by remember { mutableStateOf(false) }; var interval by remember(existing) { mutableIntStateOf(existing?.intervalMonths ?: 1) }; var sourceId by remember(existing, sources, defaultSourceId) { mutableStateOf(existing?.paymentSourceId ?: defaultSourceId?.takeIf { id->sources.any{it.id==id} } ?: sources.firstOrNull()?.id.orEmpty()) }; var showEnd by remember { mutableStateOf(false) }; var showRevision by remember { mutableStateOf(false) }
    AlertDialog(modifier=Modifier.imePadding(),onDismissRequest = dismiss, title = { Text(if (existing == null) "固定費を追加" else "固定費を編集") }, text = { LazyColumn(Modifier.fillMaxWidth().heightIn(max=520.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) { item { PaymentSourcePicker(sources, sourceId) { sourceId = it } }; item { Box { OutlinedButton(onClick = { categoryExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(category.ifBlank { "支出カテゴリを選択" }) }; DropdownMenu(categoryExpanded, { categoryExpanded = false }) { categories.forEach { DropdownMenuItem({ Text(it) }, { category = it; categoryExpanded = false }) } } } }; item { DatePickerField("契約日",contractDate){contractDate=it} }; item { Box { OutlinedButton(onClick = { intervalExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("支払い間隔：${intervalLabel(interval)}") }; DropdownMenu(intervalExpanded, { intervalExpanded = false }, modifier = Modifier.heightIn(max = 320.dp)) { (1..120).forEach { candidate -> DropdownMenuItem({ Text(intervalLabel(candidate)) }, { interval = candidate; intervalExpanded = false }) } } } }; item { Box { OutlinedButton(onClick = { dayExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("支払日：${recurringPaymentDayLabel(interval,day)}") }; DropdownMenu(dayExpanded, { dayExpanded = false }, modifier = Modifier.heightIn(max = 280.dp)) { (1..31).forEach { candidate -> DropdownMenuItem({ Text(recurringPaymentDayLabel(interval,candidate)) }, { day = candidate; dayExpanded = false }) } } } }; item { OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text(RECURRING_AMOUNT_LABEL) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }; item { OutlinedTextField(merchant, { merchant = it }, label = { Text("取引先") }, modifier = Modifier.fillMaxWidth()) }; item { OutlinedTextField(description, { description = it }, label = { Text("内容") }, modifier = Modifier.fillMaxWidth()) }; if (existing != null) item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { HorizontalDivider(); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { showEnd = true }, modifier = Modifier.weight(1f)) { Text("契約終了") }; OutlinedButton(onClick = { showRevision = true }, modifier = Modifier.weight(1f)) { Text("料金改定") } } } } } }, confirmButton = { Button(enabled = amount.toLongOrNull() != null && category in categories && sourceId.isNotBlank(), onClick = { save(existing?.id, amount.toLong(), day, category, merchant, description, contractDate, sourceId, interval) }) { Text("保存") } }, dismissButton = { TextButton(onClick = dismiss) { Text("キャンセル") } })
    if (showEnd && existing != null) DateAmountActionDialog("契約終了日", false, existing.endDate ?: LocalDate.now().toString(), "", { showEnd = false }) { date, _ -> endContract(existing.id, date); showEnd = false }
    if (showRevision && existing != null) DateAmountActionDialog("料金改定", true, LocalDate.now().toString(), existing.priceRevisions.lastOrNull()?.amount?.toString() ?: existing.amount.toString(), { showRevision = false }) { date, revised -> revisePrice(existing.id, date, revised); showRevision = false }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable private fun EntryDialog(categories: List<String>, merchants: List<String>, descriptions: List<String>, sources: List<PaymentSource>, defaultSourceId: String, existing: Transaction?, openMerchantSettings:()->Unit,openDescriptionSettings:()->Unit,recurringExpenses:List<RecurringExpense> = emptyList(),linkRecurring:(Long,Long?)->Unit={_,_->},allowDelete:Boolean=true,onDismiss: () -> Unit, onDelete: (Long) -> Unit, save: (Long, String, String, String, LocalDate, String) -> Unit) {
    val suggestions = emptyList<String>()
    var amount by remember(existing) { mutableStateOf(existing?.amount?.toString().orEmpty()) }; var merchant by remember(existing) { mutableStateOf(existing?.merchant.orEmpty()) }; var description by remember(existing) { mutableStateOf(existing?.description.orEmpty()) }; var category by remember(existing, categories) { mutableStateOf(existing?.category ?: categories.firstOrNull().orEmpty()) }; var expanded by remember { mutableStateOf(false) }; var date by remember(existing) { mutableStateOf(existing?.let { Instant.ofEpochMilli(it.usedAt).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now()) }; var showDatePicker by remember { mutableStateOf(false) }; var showDeleteConfirm by remember { mutableStateOf(false) }; var sourceId by remember(existing, defaultSourceId) { mutableStateOf(existing?.paymentSourceId ?: defaultSourceId) }
    var showHelp by remember { mutableStateOf(existing == null) };var recurringExpanded by remember{mutableStateOf(false)};var selectedRecurringId by remember(existing){mutableStateOf(existing?.recurringId)}
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White, title = { Row(verticalAlignment=Alignment.CenterVertically){Text(if(existing == null) "支出を記録" else "明細を編集",modifier=Modifier.weight(1f));TextButton(onClick={showHelp=!showHelp}){Text("？")}} }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { if(showHelp)EntryFieldHelp(); PaymentSourcePicker(sources, sourceId) { sourceId = it }; Box { OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text(category.ifBlank { "支出カテゴリを選択" }) }; DropdownMenu(expanded, { expanded = false }) { categories.forEach { DropdownMenuItem({ Text(it) }, { category = it; expanded = false }) } } }; if(existing!=null){Box{OutlinedButton(onClick={recurringExpanded=true},modifier=Modifier.fillMaxWidth(),border=if(selectedRecurringId!=null)BorderStroke(1.dp,Color(0xFF89A9FF))else null){Text(selectedRecurringId?.let{id->recurringExpenses.firstOrNull{it.id==id}?.let{"固定費：${it.merchant.ifBlank{it.description.ifBlank{it.category}}}"}}?:"固定費に紐づけない")};DropdownMenu(recurringExpanded,{recurringExpanded=false}){DropdownMenuItem({Text("固定費に紐づけない")},{selectedRecurringId=null;recurringExpanded=false});recurringExpenses.forEach{expense->DropdownMenuItem({Text(expense.merchant.ifBlank{expense.description.ifBlank{expense.category}})},{selectedRecurringId=expense.id;recurringExpanded=false})}}}}; OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) { Text("利用日：${date.format(DateTimeFormatter.ofPattern("yyyy/M/d"))}　変更") }; OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("金額") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); OutlinedTextField(merchant, { merchant = it }, label = { Text("取引先") }); if(merchants.isEmpty())OutlinedButton(onClick=openMerchantSettings,modifier=Modifier.fillMaxWidth()){Text("よく使う取引先を登録")}else TemplateChips("よく使う取引先",merchants){merchant=it}; OutlinedTextField(description, { description = it }, label = { Text("内容") }); if(descriptions.isEmpty())OutlinedButton(onClick=openDescriptionSettings,modifier=Modifier.fillMaxWidth()){Text("よく使う内容を登録")}else TemplateChips("よく使う内容",descriptions){description=it}; if (suggestions.isNotEmpty()) { Text("✦ 履歴からのカテゴリ候補", fontSize = 12.sp, color = WinterBlue, fontWeight = FontWeight.Bold); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { suggestions.forEach { candidate -> SuggestionChip(onClick = { category = candidate }, label = { Text(candidate) }, colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFFE3EBFF))) } } }; if (existing != null && allowDelete) { HorizontalDivider(); TextButton(onClick = { showDeleteConfirm = true }, modifier=Modifier.fillMaxWidth()) { Text("この明細を削除", color = Color(0xFFD62F55)) } } } }, confirmButton = { Button(enabled = amount.toLongOrNull() != null && category.isNotBlank() && sourceId.isNotBlank(), onClick = { save(amount.toLong(), category, merchant, description, date, sourceId);existing?.let{linkRecurring(it.id,selectedRecurringId)} }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } })
    if (showDatePicker) { val pickerState = rememberDatePickerState(initialSelectedDateMillis = datePickerMillis(date)); DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { pickerState.selectedDateMillis?.let { date = dateFromPickerMillis(it) }; showDatePicker = false }) { Text("決定") } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") } }) { DatePicker(state = pickerState) } }
    if (showDeleteConfirm && existing != null) ConfirmDeleteDialog("明細を削除しますか？", "${existing.dateText()}  ${existing.merchant.ifBlank { existing.description.ifBlank { existing.category } }}  ${yen(existing.amount)}", { showDeleteConfirm = false }) { onDelete(existing.id); showDeleteConfirm = false }
}

@Composable private fun TransactionRow(item: Transaction) { Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(transactionPrimaryText(item), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(transactionSecondaryText(item), color = Color.Gray, fontSize = 12.sp,maxLines=1,overflow=TextOverflow.Ellipsis) }; Text(yen(item.amount), fontWeight = FontWeight.Bold, color = DeepNavy) } } }
internal fun transactionPrimaryText(item:Transaction)=item.description.ifBlank{item.merchant.ifBlank{item.category}}
internal fun transactionSecondaryText(item:Transaction)=listOf(item.merchant.takeIf{item.description.isNotBlank()&&it.isNotBlank()},item.dateText(),item.category).filterNotNull().joinToString("  ")
internal fun visibleTemplateValues(values:List<String>,expanded:Boolean,limit:Int=6)=if(expanded)values else values.take(limit)
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun TemplateChips(title:String,values:List<String>,select:(String)->Unit){
    if(values.isEmpty())return
    var expanded by remember(values){mutableStateOf(false)}
    Text(title,color=Color.Gray,fontSize=12.sp)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){visibleTemplateValues(values,expanded).forEach{value->SuggestionChip(onClick={select(value);expanded=false},label={Text(value,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=150.dp))})}}
    if(values.size>6)TextButton(onClick={expanded=!expanded},contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)){Text(if(expanded)"閉じる" else "すべて表示",fontSize=12.sp)}
}
@Composable private fun EntryFieldHelp(){Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFF3F6FA)),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){Text("入力項目について",fontWeight=FontWeight.Bold,color=DeepNavy,fontSize=13.sp);Text("取引先：お店やサービス名（例：西友）",fontSize=12.sp);Text("内容：買ったものや用途（例：食料品）",fontSize=12.sp);Text("支出カテゴリ：家計簿での分類（例：食費）",fontSize=12.sp)}}}
@Composable private fun EmptyStateCard(title:String,description:String,actionLabel:String?=null,onAction:(()->Unit)?=null){Card(colors=CardDefaults.cardColors(containerColor=Color.White),modifier=Modifier.fillMaxWidth()){Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,fontWeight=FontWeight.Bold,color=DeepNavy);Text(description,color=Color.Gray,fontSize=13.sp);if(actionLabel!=null&&onAction!=null)Button(onClick=onAction){Text(actionLabel)}}}}
@Composable private fun EmptyMessage(message: String) { Text(message, color = Color.Gray, modifier = Modifier.fillMaxWidth().padding(28.dp)) }

@Composable private fun MerchantAnalysis(current: List<Transaction>, previous: List<Transaction>, annual:Boolean=false, openDetails: (String) -> Unit) {
    val analysis = remember(current, previous) { MerchantSpendingAnalysis.calculate(current, previous) }
    val ranked = analysis.ranking
    if (ranked.isEmpty()) { EmptyMessage("取引先を登録した明細が増えると分析が表示されます"); return }
    Text("取引先別ランキング", fontWeight = FontWeight.Bold, color = DeepNavy)
    val limit=if(annual)50 else ranked.size
    ranked.take(limit).forEach { entry -> Card(Modifier.fillMaxWidth().clickable { openDetails(entry.key) }, colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(14.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(entry.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(yen(entry.amount), fontWeight = FontWeight.Bold, color = DeepNavy); Text("  ›", color = WinterBlue) }; Text("${entry.count}回・平均 ${yen(entry.average)}・${if(annual)"前年比" else "前月比"} ${signedYen(entry.changeFromPrevious)}", color = Color.Gray, fontSize = 12.sp) } } }
    if(annual&&ranked.size>limit){val keys=ranked.drop(limit).map{it.key}.toSet();val rows=current.filter{normalizeForLearning(it.merchant) in keys};val prior=previous.filter{normalizeForLearning(it.merchant) in keys}.sumOf{it.amount};val amount=rows.sumOf{it.amount};Card(Modifier.fillMaxWidth().clickable{openDetails(OTHER_MERCHANT_KEY)},colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("その他",Modifier.weight(1f),fontWeight=FontWeight.SemiBold);Text(yen(amount),fontWeight=FontWeight.Bold,color=DeepNavy);Text("  ›",color=WinterBlue)};Text("${rows.size}回・平均 ${yen(if(rows.isEmpty())0 else amount/rows.size)}・前年比 ${signedYen(amount-prior)}",color=Color.Gray,fontSize=12.sp)}}}
}

@Composable private fun TransactionDetailDialog(title: String, rows: List<Transaction>, dismiss: () -> Unit, trendYear: Int? = null, openMonth: (YearMonth) -> Unit = {}, editTransaction:(Transaction)->Unit={}, backLabel:String="閉じる") {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text("${rows.size}件・合計 ${yen(rows.sumOf { it.amount })}", color = DeepNavy, fontWeight = FontWeight.Bold); if (trendYear != null) { MonthlySpendingTrend(trendYear, rows, openMonth) } else rows.forEach { item -> Card(Modifier.fillMaxWidth().clickable{editTransaction(item)},colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = if (item.recurringId != null) 3.dp else 0.dp), border = if (item.recurringId != null) BorderStroke(1.dp, Color(0xFF89A9FF)) else null) { Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.merchant.ifBlank { item.category }, fontWeight = FontWeight.SemiBold); Text("${item.dateText()}  ${item.category}${if (item.recurringId != null) "  固定費" else ""}", fontSize = 12.sp, color = Color.Gray) }; Text(yen(item.amount), fontWeight = FontWeight.Bold);Text("  ›",color=WinterBlue) } } } } }, confirmButton = { TextButton(onClick = dismiss) { Text(backLabel) } })
}

private const val OTHER_MERCHANT_KEY="__other_merchants__"
private fun merchantKeysOutsideTop(rows:List<Transaction>,limit:Int)=MerchantSpendingAnalysis.calculate(rows,emptyList()).ranking.drop(limit).map{it.key}.toSet()

private fun signedYen(value: Long): String = when { value > 0 -> "+${yen(value)}"; value < 0 -> "-${yen(-value)}"; else -> "±¥0" }
internal const val RECURRING_AMOUNT_LABEL = "金額"
private fun formatBackupTime(epochMillis: Long): String = java.time.Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy/M/d HH:mm"))
private fun List<Transaction>.forMonth(month: YearMonth) = filter { YearMonth.from(Instant.ofEpochMilli(it.usedAt).atZone(ZoneId.systemDefault())) == month }.sortedBy { it.usedAt }
private fun List<Transaction>.forYear(year:Int) = filter { Instant.ofEpochMilli(it.usedAt).atZone(ZoneId.systemDefault()).year == year }.sortedBy { it.usedAt }
private fun Transaction.dateText() = Instant.ofEpochMilli(usedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("M/d"))
private fun yen(value: Long): String = "¥${NumberFormat.getIntegerInstance().format(value)}"
private fun intervalLabel(months: Int): String = if (months >= 12 && months % 12 == 0) "${months / 12}年ごと" else "${months}か月ごと"

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DateAmountActionDialog(title: String, requireAmount: Boolean, initialDate: String, initialAmount: String, dismiss: () -> Unit, confirm: (LocalDate, Long) -> Unit) {
    var date by remember { mutableStateOf(LocalDate.parse(initialDate)) }; var amountText by remember { mutableStateOf(initialAmount) }; val amount = amountText.toLongOrNull()
    AlertDialog(modifier=Modifier.imePadding(),onDismissRequest = dismiss, title = { Text(title) }, text = { LazyColumn(Modifier.fillMaxWidth().heightIn(max=420.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) { item { DatePickerField("適用日",date){date=it} }; if (requireAmount) item { OutlinedTextField(amountText, { amountText = it.filter(Char::isDigit) }, label = { Text("新しい金額") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) } } }, confirmButton = { Button(enabled = !requireAmount || amount != null, onClick = { confirm(date, amount ?: 0L) }) { Text("適用") } }, dismissButton = { TextButton(onClick = dismiss) { Text("キャンセル") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DatePickerField(label:String,value:LocalDate,onChange:(LocalDate)->Unit){
    var open by remember{mutableStateOf(false)}
    OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()){Text("$label：${value.format(DateTimeFormatter.ofPattern("yyyy/M/d"))}")}
    if(open){val state=rememberDatePickerState(initialSelectedDateMillis=datePickerMillis(value));DatePickerDialog(onDismissRequest={open=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{onChange(dateFromPickerMillis(it))};open=false}){Text("決定")}},dismissButton={TextButton(onClick={open=false}){Text("キャンセル")}}){DatePicker(state)}}
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

private fun normalizeForLearning(value: String): String = MerchantSpendingAnalysis.normalize(value)


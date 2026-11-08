package jp.knaka.cardmemo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object StatementTools {
    data class ReadResult(val entries: List<CardStatementEntry>, val declaredTotal: Long? = null)

    suspend fun readStatementFile(context: Context, uri: Uri, targetMonth: YearMonth): ReadResult {
        val name = displayName(context, uri).lowercase()
        return if (name.endsWith(".csv")) ReadResult(readCsv(context, uri, targetMonth)) else readRakutenPdf(context, uri, targetMonth)
    }

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0).orEmpty()
        }
        return uri.lastPathSegment.orEmpty()
    }

    fun inferStatementMonth(context: Context, uri: Uri, entries: List<CardStatementEntry>): YearMonth {
        Regex("(20\\d{2})[^0-9]?(0?[1-9]|1[0-2])").find(displayName(context, uri))?.let { found -> return YearMonth.of(found.groupValues[1].toInt(), found.groupValues[2].toInt()) }
        return entries.maxByOrNull { it.date }?.let { YearMonth.from(it.date) } ?: YearMonth.now()
    }

    fun sha256(context: Context, uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun statementFingerprint(entries: List<CardStatementEntry>): String {
        return StatementFingerprint.calculate(entries)
    }

    private fun readCsv(context: Context, uri: Uri, targetMonth: YearMonth): List<CardStatementEntry> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return emptyList()
        val utf8 = runCatching { StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF") }.getOrNull()
        val text = utf8 ?: bytes.toString(Charset.forName("MS932"))
        return StatementCsvParser.parse(text, targetMonth)
    }
    suspend fun readRakutenPdf(context: Context, uri: Uri, targetMonth: YearMonth): ReadResult {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return ReadResult(emptyList())
        val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        return descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                suspend fun recognize(scale: Int): ReadResult {
                    val totalCandidates = mutableListOf<Long>()
                    val rows = buildList {
                    for (pageIndex in 0 until renderer.pageCount) {
                        renderer.openPage(pageIndex).use { page ->
                            val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitResult()
                            val billingZone = findBillingZone(result)
                            extractRakutenSummaryAmounts(result.text).forEach(totalCandidates::add)
                            // A whole A4 page is large enough that ML Kit can silently omit small
                            // lines even at a high render scale.  Re-read it in overlapping bands;
                            // the overlap prevents a row on a band boundary from being lost.
                            if (billingZone != null) {
                                val bandHeight = 1_200
                                val overlap = 180
                                var top = 0
                                while (top < bitmap.height) {
                                    val bottom = minOf(top + bandHeight, bitmap.height)
                                    val band = Bitmap.createBitmap(bitmap.width, bottom - top, Bitmap.Config.ARGB_8888)
                                    android.graphics.Canvas(band).drawBitmap(
                                        bitmap,
                                        Rect(0, top, bitmap.width, bottom),
                                        Rect(0, 0, band.width, band.height),
                                        null
                                    )
                                    val bandResult = recognizer.process(InputImage.fromBitmap(band, 0)).awaitResult()
                                    val coreTop = if (top == 0) 0 else overlap / 2
                                    val coreBottom = if (bottom == bitmap.height) band.height else band.height - overlap / 2
                                    val dataTop = maxOf(coreTop, billingZone.headerCenterY + 10 - top)
                                    addAll(parseRows(bandResult, targetMonth, billingZone, dataTop, coreBottom))
                                    extractRakutenSummaryAmounts(bandResult.text).forEach(totalCandidates::add)
                                    band.recycle()
                                    if (bottom == bitmap.height) break
                                    top = bottom - overlap
                                }
                            } else {
                                addAll(parseRows(result, targetMonth, null))
                                val bandHeight = 1_200
                                val overlap = 180
                                var top = 0
                                while (top < bitmap.height) {
                                    val bottom = minOf(top + bandHeight, bitmap.height)
                                    val band = Bitmap.createBitmap(bitmap.width, bottom - top, Bitmap.Config.ARGB_8888)
                                    android.graphics.Canvas(band).drawBitmap(
                                        bitmap,
                                        Rect(0, top, bitmap.width, bottom),
                                        Rect(0, 0, band.width, band.height),
                                        null
                                    )
                                    val bandResult = recognizer.process(InputImage.fromBitmap(band, 0)).awaitResult()
                                    extractRakutenSummaryAmounts(bandResult.text).forEach(totalCandidates::add)
                                    band.recycle()
                                    if (bottom == bitmap.height) break
                                    top = bottom - overlap
                                }
                            }
                            parseRakutenDeclaredTotal(result)?.let(totalCandidates::add)
                            parseRakutenDeclaredTotal(result.text)?.let(totalCandidates::add)
                            if (billingZone == null) parseRakutenSummaryTotal(result.text)?.let(totalCandidates::add)
                            bitmap.recycle()
                        }
                    }
                    }.distinctBy { Triple(it.date, it.amount, normalize(it.merchant)) }
                    val declaredTotal = totalCandidates.filterNot { it in 2_000L..2_099L }.maxOrNull()
                    return ReadResult(rows, declaredTotal)
                }
                val first = recognize(4)
                if (first.declaredTotal != null && first.entries.sumOf { it.amount } == first.declaredTotal) return@use first
                val second = recognize(3)
                val knownTotal = first.declaredTotal ?: second.declaredTotal
                if (knownTotal != null && second.entries.sumOf { it.amount } == knownTotal) return@use second.copy(declaredTotal = knownTotal)
                val third = recognize(2)
                val candidates = listOf(first, second, third)
                val total = knownTotal ?: third.declaredTotal
                val selected = if (total != null) {
                    candidates.minByOrNull { candidate -> kotlin.math.abs(candidate.entries.sumOf { it.amount } - total) }!!
                } else {
                    candidates.maxWithOrNull(compareBy<ReadResult> { it.entries.sumOf { row -> row.amount } }.thenBy { it.entries.size })!!
                }
                selected.copy(declaredTotal = total ?: selected.entries.sumOf { it.amount })
            }
        }.also { recognizer.close() }
    }

    internal fun parseRakutenDeclaredTotal(text: String): Long? {
        val compact = text.replace(Regex("[\\s　]+"), " ")
        val labeled = listOf("明細中の利用金額合計", "利用金額合計").firstNotNullOfOrNull { label ->
            val tail = compact.substringAfter(label, "").take(120)
            val commaAmounts = Regex("[0-9]{1,3}(?:,[0-9]{3})+").findAll(tail)
                .mapNotNull { it.value.replace(",", "").toLongOrNull() }.toList()
            val candidates = commaAmounts.ifEmpty {
                Regex("(?<![0-9])[0-9]{4,}(?![0-9])").findAll(tail).mapNotNull { it.value.toLongOrNull() }.toList()
            }
            candidates.asSequence()
                .filter { it >= 1_000 }
                .maxOrNull()
        }
        if (labeled != null) return labeled
        if (!compact.contains("お支払予定金額の明細") && !compact.contains("支払予定金額の明細")) return null
        return Regex("(?<![0-9])([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,})(?![0-9])").findAll(compact)
            .mapNotNull { it.groupValues[1].replace(",", "").toLongOrNull() }.filter { it >= 1_000 }
            .groupingBy { it }.eachCount().maxWithOrNull(compareBy<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })?.key
    }

    internal fun parseRakutenSummaryTotal(text: String): Long? {
        val amounts = extractRakutenSummaryAmounts(text)
        return amounts.groupingBy { it }.eachCount().entries
            .filter { it.value >= 2 }
            .maxWithOrNull(compareBy<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })?.key
    }

    private fun extractRakutenSummaryAmounts(text: String): List<Long> =
        Regex("[0-9]{1,3}(?:[,，.．][0-9]{3})+").findAll(text)
            .mapNotNull { it.value.replace(Regex("[,，.．]"), "").toLongOrNull() }
            .filter { it >= 1_000 && it !in 2_000L..2_099L }
            .toList()

    private fun parseRakutenDeclaredTotal(text: Text): Long? {
        data class LocatedLine(val text: String, val left: Int, val right: Int, val centerY: Int, val height: Int)
        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            LocatedLine(line.text.replace(Regex("[\\s　]+"), ""), box.left, box.right, box.centerY(), box.height())
        }
        val anchor = lines.firstOrNull { it.text.contains("明細中の利用金額合計") }
            ?: lines.firstOrNull { it.text.contains("明細中") && it.text.contains("利用金額合計") }
            ?: return null
        parseMoney(anchor.text.substringAfter("合計", ""))?.takeIf { it >= 1_000 && it !in 2_000L..2_099L }?.let { return it }
        return lines.asSequence()
            .filter { it.left >= anchor.right - 12 && kotlin.math.abs(it.centerY - anchor.centerY) <= maxOf(anchor.height, it.height) }
            .mapNotNull { candidate -> parseMoney(candidate.text)?.takeIf { it >= 1_000 && it !in 2_000L..2_099L }?.let { candidate to it } }
            .minByOrNull { (candidate, _) -> kotlin.math.abs(candidate.centerY - anchor.centerY) * 10_000 + kotlin.math.abs(candidate.left - anchor.right) }
            ?.second
    }

    private data class BillingZone(val left: Int, val right: Int, val headerCenterY: Int)

    private fun findBillingZone(text: Text): BillingZone? {
        data class HeaderPiece(val text: String, val left: Int, val centerY: Int)
        val pieces = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { element ->
            val box = element.boundingBox ?: return@mapNotNull null
            HeaderPiece(element.text.trim(), box.left, box.centerY())
        }.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<HeaderPiece>>()
        pieces.forEach { piece ->
            val row = rows.lastOrNull()
            if (row == null || kotlin.math.abs(row.map { it.centerY }.average() - piece.centerY) > 24) rows += mutableListOf(piece) else row += piece
        }
        val header = rows.firstOrNull { row -> row.joinToString("") { it.text }.replace(" ", "").contains("当月請求額") } ?: return null
        val sorted = header.sortedBy { it.left }
        val index = sorted.indexOfLast { it.text.replace(" ", "").contains("当月請求額") || it.text.contains("請求額") }
        if (index < 0) return null
        val billing = sorted[index].let { found -> sorted.getOrNull(index - 1)?.takeIf { found.text.contains("請求額") && it.text.contains("当月") } ?: found }
        val previous = sorted.lastOrNull { it.left < billing.left - 8 }?.left
        val next = sorted.firstOrNull { it.left > billing.left + 8 }?.left
        return BillingZone(
            previous?.let { (it + billing.left) / 2 } ?: billing.left - 18,
            next?.let { (billing.left + it) / 2 } ?: Int.MAX_VALUE,
            header.map { it.centerY }.average().toInt()
        )
    }

    private fun parseRows(
        text: Text,
        month: YearMonth,
        billingZone: BillingZone? = findBillingZone(text),
        minCenterY: Int = Int.MIN_VALUE,
        maxCenterYExclusive: Int = Int.MAX_VALUE
    ): List<CardStatementEntry> {
        data class Piece(val text: String, val left: Int, val centerY: Int)
        val pieces = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { element ->
            val box = element.boundingBox ?: return@mapNotNull null
            Piece(element.text.trim(), box.left, box.centerY())
        }.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<Piece>>()
        pieces.forEach { piece ->
            val row = rows.lastOrNull()
            if (row == null || kotlin.math.abs(row.map { it.centerY }.average() - piece.centerY) > 24) rows += mutableListOf(piece)
            else row += piece
        }
        val zone = billingZone ?: return emptyList()
        return rows.mapNotNull { row ->
            val rowCenterY = row.map { it.centerY }.average()
            if (rowCenterY < minCenterY || rowCenterY >= maxCenterYExclusive) return@mapNotNull null
            val sorted = row.sortedBy { it.left }
            val amount = sorted.firstOrNull { it.left >= zone.left && it.left < zone.right && parseMoney(it.text) != null }?.text?.let(::parseMoney) ?: return@mapNotNull null
            parseRow(sorted.joinToString(" ") { it.text }, month, amount)
        }
    }

    private fun parseRow(raw: String, month: YearMonth, billingAmount: Long): CardStatementEntry? {
        val dateMatch = Regex("(?<!\\d)(?:(\\d{2,4})[./年\\s-]+)?(\\d{1,2})[./月\\s-]+(\\d{1,2})(?:日)?").find(raw) ?: return null
        val parsedYear = dateMatch.groupValues[1].toIntOrNull()
        val year = when { parsedYear == null -> month.year; parsedYear < 100 -> 2000 + parsedYear; else -> parsedYear }
        val monthNumber = dateMatch.groupValues[2].toIntOrNull() ?: return null
        val day = dateMatch.groupValues[3].toIntOrNull() ?: return null
        val date = runCatching { LocalDate.of(year, monthNumber, day) }.getOrNull() ?: return null
        val tail = raw.substring(dateMatch.range.last + 1)
        if (billingAmount <= 0) return null
        val firstNumber = Regex("(?<![\\d,])[0-9][0-9,]*(?![\\d,])").find(tail)
        val merchant = tail.substring(0, firstNumber?.range?.first ?: tail.length)
            .replace(Regex("\\s+"), " ").trim(' ', '・', '-')
        if (merchant.isBlank()) return null
        return CardStatementEntry(date, billingAmount, merchant, raw)
    }

    private fun parseMoney(value: String): Long? = value.replace(Regex("[^0-9-]"), "").toLongOrNull()?.let { kotlin.math.abs(it) }

    fun match(entries: List<CardStatementEntry>, transactions: List<Transaction>, sourceId: String): List<StatementMatch> {
        return ReconciliationMatcher.match(entries, transactions, sourceId)
    }

    fun writeMonthlyCsv(output: OutputStream, month: YearMonth, transactions: List<Transaction>, sourceNames: Map<String, String>, sourceId: String? = null) {
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val header = "利用日,支払方法,支出カテゴリ,金額,取引先,内容,固定費"
        val rows = transactions.filter {
            YearMonth.from(Instant.ofEpochMilli(it.usedAt).atZone(ZoneId.systemDefault())) == month
        }.filter { sourceId == null || it.paymentSourceId == sourceId }.sortedBy { it.usedAt }.map { item ->
            val date = Instant.ofEpochMilli(item.usedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
            listOf(date, sourceNames[item.paymentSourceId].orEmpty(), item.category, item.amount.toString(), item.merchant, item.description,
                if (item.recurringId != null) "はい" else "いいえ")
                .joinToString(",") { csvCell(it) }
        }
        output.writer(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.write((listOf(header) + rows).joinToString("\r\n"))
        }
    }

    private fun csvCell(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
        "\"${value.replace("\"", "\"\"")}\"" else value
    private fun normalize(value: String) = value.lowercase().filter { it.isLetterOrDigit() }
    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }
}


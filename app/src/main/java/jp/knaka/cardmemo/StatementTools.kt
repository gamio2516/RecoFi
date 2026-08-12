package jp.knaka.cardmemo

import android.content.Context
import android.graphics.Bitmap
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
    suspend fun readStatementFile(context: Context, uri: Uri, targetMonth: YearMonth): List<CardStatementEntry> {
        val name = displayName(context, uri).lowercase()
        return if (name.endsWith(".csv")) readCsv(context, uri, targetMonth) else readRakutenPdf(context, uri, targetMonth)
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
    suspend fun readRakutenPdf(context: Context, uri: Uri, targetMonth: YearMonth): List<CardStatementEntry> {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
        val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        return descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                buildList {
                    for (pageIndex in 0 until renderer.pageCount) {
                        renderer.openPage(pageIndex).use { page ->
                            val scale = 2
                            val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitResult()
                            addAll(parseRows(result, targetMonth))
                            bitmap.recycle()
                        }
                    }
                }.distinctBy { Triple(it.date, it.amount, normalize(it.merchant)) }
            }
        }.also { recognizer.close() }
    }

    private fun parseRows(text: Text, month: YearMonth): List<CardStatementEntry> {
        data class Piece(val text: String, val left: Int, val centerY: Int)
        val pieces = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            Piece(line.text.trim(), box.left, box.centerY())
        }.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<Piece>>()
        pieces.forEach { piece ->
            val row = rows.lastOrNull()
            if (row == null || kotlin.math.abs(row.map { it.centerY }.average() - piece.centerY) > 24) rows += mutableListOf(piece)
            else row += piece
        }
        return rows.mapNotNull { row -> parseRow(row.sortedBy { it.left }.joinToString(" ") { it.text }, month) }
    }

    private fun parseRow(raw: String, month: YearMonth): CardStatementEntry? {
        val dateMatch = Regex("(?<!\\d)(?:(\\d{2,4})[./年\\s-]+)?(\\d{1,2})[./月\\s-]+(\\d{1,2})(?:日)?").find(raw) ?: return null
        val parsedYear = dateMatch.groupValues[1].toIntOrNull()
        val year = when { parsedYear == null -> month.year; parsedYear < 100 -> 2000 + parsedYear; else -> parsedYear }
        val monthNumber = dateMatch.groupValues[2].toIntOrNull() ?: return null
        val day = dateMatch.groupValues[3].toIntOrNull() ?: return null
        val date = runCatching { LocalDate.of(year, monthNumber, day) }.getOrNull() ?: return null
        val tail = raw.substring(dateMatch.range.last + 1)
        val amountMatches = Regex("(?<![\\d,])([0-9][0-9,]{1,})(?:円)?(?![\\d,])").findAll(tail).toList()
        val amountMatch = amountMatches.firstOrNull() ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toIntOrNull() ?: return null
        if (amount <= 0) return null
        val merchant = tail.substring(0, amountMatch.range.first)
            .replace(Regex("\\s+"), " ").trim(' ', '・', '-')
        if (merchant.isBlank()) return null
        return CardStatementEntry(date, amount, merchant, raw)
    }

    fun match(entries: List<CardStatementEntry>, transactions: List<Transaction>, sourceId: String): List<StatementMatch> {
        return ReconciliationMatcher.match(entries, transactions, sourceId)
    }

    fun writeMonthlyCsv(output: OutputStream, month: YearMonth, transactions: List<Transaction>, sourceNames: Map<String, String>, sourceId: String? = null) {
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val header = "利用日,支払方法,項目,金額,備考,固定費,確認済み"
        val rows = transactions.filter {
            YearMonth.from(Instant.ofEpochMilli(it.usedAt).atZone(ZoneId.systemDefault())) == month
        }.filter { sourceId == null || it.paymentSourceId == sourceId }.sortedBy { it.usedAt }.map { item ->
            val date = Instant.ofEpochMilli(item.usedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
            listOf(date, sourceNames[item.paymentSourceId].orEmpty(), item.category, item.amount.toString(), item.note,
                if (item.recurringId != null) "はい" else "いいえ", if (item.confirmed) "はい" else "いいえ")
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

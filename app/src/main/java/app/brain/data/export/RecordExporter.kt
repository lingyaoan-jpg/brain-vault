package app.brain.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import app.brain.data.db.dao.RecordCategoryWithCategory
import app.brain.data.db.entity.CommentEntity
import app.brain.data.db.entity.RecordEntity
import app.brain.ui.common.dimensionLabel
import app.brain.ui.common.displayTitle
import app.brain.ui.common.formatTime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportFormat { TEXT, WORD, IMAGE }

data class ExportedFile(val uri: Uri, val mime: String, val fileName: String)

data class BatchExportEntry(
    val record: RecordEntity,
    val categories: List<RecordCategoryWithCategory>,
    val comments: List<CommentEntity>,
)

/** 单条记录导出：纯文本 / Word / 图片，生成后通过系统分享面板发送。 */
@Singleton
class RecordExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun export(
        record: RecordEntity,
        categories: List<RecordCategoryWithCategory>,
        comments: List<CommentEntity>,
        format: ExportFormat,
        includeMeta: Boolean,
    ): ExportedFile {
        val title = displayTitle(record)
        val body = buildBody(record, categories, comments, includeMeta)
        return when (format) {
            ExportFormat.TEXT -> shareFile(
                fileName = safeName(title) + ".txt",
                bytes = body.toByteArray(Charsets.UTF_8),
                mime = "text/plain",
            )
            ExportFormat.WORD -> shareFile(
                fileName = safeName(title) + ".docx",
                bytes = buildDocx(title, body),
                mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            )
            ExportFormat.IMAGE -> shareFile(
                fileName = safeName(title) + ".png",
                bytes = buildImage(title, body),
                mime = "image/png",
            )
        }
    }


    /** 批量导出：多条记录合并为一个 txt 或 docx 文件。 */
    fun exportBatch(
        entries: List<BatchExportEntry>,
        format: ExportFormat,
        includeMeta: Boolean,
    ): ExportedFile {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "脑内收容所导出-" + stamp
        val combined = entries.joinToString("\n\n==========\n\n") { entry ->
            buildBody(entry.record, entry.categories, entry.comments, includeMeta)
        }
        return when (format) {
            ExportFormat.TEXT -> shareFile(name + ".txt", combined.toByteArray(Charsets.UTF_8), "text/plain")
            ExportFormat.WORD -> shareFile(
                name + ".docx",
                buildDocx("脑内收容所导出", combined),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            )
            ExportFormat.IMAGE -> shareFile(name + ".png", buildImage("脑内收容所导出", combined), "image/png")
        }
    }
    private fun buildBody(
        record: RecordEntity,
        categories: List<RecordCategoryWithCategory>,
        comments: List<CommentEntity>,
        includeMeta: Boolean,
    ): String {
        val sb = StringBuilder()
        record.title?.takeIf { it.isNotBlank() }?.let { sb.append(it).append("\n\n") }
        if (includeMeta) {
            sb.append("时间：").append(formatTime(record.createdAt)).append("\n")
            if (categories.isNotEmpty()) {
                val dims = LinkedHashMap<String, MutableList<String>>()
                categories.forEach { dims.getOrPut(it.dimension) { mutableListOf() }.add(it.name) }
                sb.append(dims.map { (d, names) -> dimensionLabel(d) + "：" + names.joinToString("、") }.joinToString("；")).append("\n")
            }
            sb.append("\n")
        }
        sb.append(record.content).append("\n")
        if (includeMeta && comments.isNotEmpty()) {
            sb.append("\n—— 评论/批注 ——\n")
            comments.forEach { c -> sb.append("[").append(formatTime(c.createdAt)).append("] ").append(c.content).append("\n") }
        }
        return sb.toString().trim()
    }

    private fun buildDocx(title: String, body: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(CONTENT_TYPES.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(RELS.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml(title, body).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun documentXml(title: String, body: String): String {
        fun para(text: String, bold: Boolean): String {
            val rpr = if (bold) "<w:rPr><w:b/></w:rPr>" else ""
            return "<w:p><w:pPr>" + rpr + "</w:pPr><w:r>" + rpr + "<w:t xml:space=\"preserve\">" + escapeXml(text) + "</w:t></w:r></w:p>"
        }
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>")
        if (title.isNotBlank()) sb.append(para(title, bold = true))
        body.lines().forEach { sb.append(para(it, bold = false)) }
        sb.append("</w:body></w:document>")
        return sb.toString()
    }

    private fun buildImage(title: String, body: String): ByteArray {
        val width = 1080
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 44f
            typeface = Typeface.DEFAULT_BOLD
            color = Color.rgb(33, 29, 40)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 32f
            color = Color.rgb(50, 48, 56)
        }
        val lines = mutableListOf<Pair<String, Paint>>()
        if (title.isNotBlank()) lines.add(title to titlePaint)
        body.lines().forEach { raw ->
            if (raw.isBlank()) {
                lines.add("" to bodyPaint)
                return@forEach
            }
            val maxWidth = (width - 80).toFloat()
            var pending = ""
            for (ch in raw) {
                val test = pending + ch
                if (bodyPaint.measureText(test) > maxWidth && pending.isNotEmpty()) {
                    lines.add(pending to bodyPaint)
                    pending = ch.toString()
                } else {
                    pending = test
                }
            }
            if (pending.isNotEmpty()) lines.add(pending to bodyPaint)
        }
        val lineHeight = 46f
        val height = 120 + (lines.size * lineHeight.toInt()) + 80
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        var y = 80f
        for ((text, paint) in lines) {
            canvas.drawText(text, 40f, y, paint)
            y += lineHeight
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun shareFile(fileName: String, bytes: ByteArray, mime: String): ExportedFile {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        return ExportedFile(uri, mime, fileName)
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\n\r\t]"), "_").ifBlank { "记录" }.take(40)

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private companion object {
        const val CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "</Types>"
        const val RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
            "</Relationships>"
    }
}

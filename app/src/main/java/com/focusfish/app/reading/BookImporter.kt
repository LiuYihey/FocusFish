package com.focusfish.app.reading

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 书籍导入器
 *
 * 支持 txt / epub / pdf 三种格式，统一转成「分页纯文本」缓存文件。
 *
 * 分页规则：按段落边界切分，每页约 [CHARS_PER_PAGE] 字。
 * 理由：大肥鱼「一张一张慢慢读」，页是它的进度单位；
 * 而给 LLM 的上下文也需要切片，切太碎读不出上下文，
 * 切太粗一次塞太多会超 token —— 1200 字/页是平衡点。
 */
class BookImporter(private val context: Context) {

    companion object {
        /** 每页字符数 */
        const val CHARS_PER_PAGE = 1200

        /** 缓存目录名 */
        private const val CACHE_DIR = "books"
    }

    /**
     * 导入结果
     */
    data class ImportResult(
        val title: String,
        val author: String?,
        val sourceUri: String,
        val fileName: String,
        val fileType: String,
        val contentPath: String,
        val totalPages: Int
    )

    /**
     * 从 URI 导入书籍
     */
    fun import(uri: Uri): ImportResult {
        val fileName = queryDisplayName(uri) ?: "未命名"
        val fileType = fileName.substringAfterLast('.', "txt").uppercase()

        val rawText = when (fileType) {
            "EPUB" -> com.focusfish.app.reading.EpubTextExtractor.extract(context, uri)
            "PDF" -> com.focusfish.app.reading.PdfTextExtractor.extract(context, uri)
            else -> readPlainText(context, uri)
        }

        require(rawText.isNotBlank()) { "这本书里没有读到任何文字" }

        val pages = paginate(rawText)
        require(pages.isNotEmpty()) { "这本书里没有读到任何文字" }

        val cacheDir = File(context.filesDir, CACHE_DIR).apply { mkdirs() }
        val outFile = File(cacheDir, "${System.currentTimeMillis()}_$fileName.txt")
        outFile.writeText(pages.joinToString("\n\n<<<PAGE>>>\n\n"))

        return ImportResult(
            title = fileName.substringBeforeLast('.'),
            author = null,
            sourceUri = uri.toString(),
            fileName = fileName,
            fileType = fileType,
            contentPath = outFile.absolutePath,
            totalPages = pages.size
        )
    }

    /**
     * 读取纯文本
     */
    private fun readPlainText(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: ""
    }

    /**
     * 分页：按段落边界切分，尽量不把句子劈开
     */
    private fun paginate(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isEmpty()) return emptyList()

        val paragraphs = normalized.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
        val pages = mutableListOf<String>()
        val current = StringBuilder()

        for (paragraph in paragraphs) {
            // 单段超长（PDF 常见）：按 CHARS_PER_PAGE 硬切
            if (paragraph.length > CHARS_PER_PAGE) {
                if (current.isNotEmpty()) {
                    pages.add(current.toString())
                    current.clear()
                }
                paragraph.chunked(CHARS_PER_PAGE).forEach { pages.add(it) }
                continue
            }

            if (current.length + paragraph.length > CHARS_PER_PAGE) {
                pages.add(current.toString())
                current.clear()
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(paragraph)
        }
        if (current.isNotEmpty()) pages.add(current.toString())
        return pages
    }

    /**
     * 查询文件显示名
     */
    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }
}

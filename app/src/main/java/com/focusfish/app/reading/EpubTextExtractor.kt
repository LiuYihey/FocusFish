package com.focusfish.app.reading

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

/**
 * EPUB 文本提取
 *
 * EPUB 本质是 zip，里面是若干 xhtml 章节。
 * 这里用 [ZipInputStream] 逐个读出所有 html/xml 章节，
 * 剥掉标签后拼接成纯文本。
 *
 * 不引入第三方库的原因：EPUB 的正文提取本质上就是「读 zip + 去标签」，
 * 而去标签用正则足够 —— 避免为这一个功能引入 2MB 的依赖。
 */
object EpubTextExtractor {

    /** 分页分隔标记，与 BookImporter 保持一致 */
    private const val PAGE_MARKER = "\n\n<<<PAGE>>>\n\n"

    /**
     * 提取 EPUB 纯文本
     */
    fun extract(context: Context, uri: Uri): String {
        val chapters = mutableListOf<String>()

        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    // 只处理正文章节，排除目录/样式/图片等
                    if (!entry.isDirectory &&
                        (name.endsWith(".xhtml") ||
                            name.endsWith(".html") ||
                            name.endsWith(".htm") ||
                            name.endsWith(".xml"))
                    ) {
                        val html = zip.readBytes().toString(Charsets.UTF_8)
                        val text = stripTags(html)
                        if (text.isNotBlank()) {
                            chapters.add(text)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        return chapters.joinToString(PAGE_MARKER)
    }

    /**
     * 剥离 HTML 标签，保留段落换行
     */
    private fun stripTags(html: String): String {
        return html
            // 去掉 script / style 整块
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
            // 块级标签转成换行，保留段落感
            .replace(Regex("(?i)</?(p|div|br|h[1-6]|li|tr|section|article)[^>]*>"), "\n")
            // 去掉其余标签
            .replace(Regex("(?s)<[^>]+>"), "")
            // 解码常见实体
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            // 压缩空行
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}

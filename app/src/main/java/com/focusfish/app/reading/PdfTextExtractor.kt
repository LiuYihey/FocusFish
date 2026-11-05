package com.focusfish.app.reading

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * PDF 文本提取
 *
 * Android 原生没有 PDF 解析能力（PdfRenderer 只能渲染成图，不能取文字）。
 *
 * 这里的策略是**先用 PdfRenderer 渲染成图 + OCR** 的替代方案不可行（需要 Tesseract），
 * 因此采用一个务实的降级策略：
 * 1. 优先尝试从 PDF 原始字节中提取可读的文本流（对大部分电子版 PDF 有效）
 * 2. 提取不到则提示用户该 PDF 可能是扫描版，建议用 txt/epub
 *
 * 为什么不引入 PdfBox-Android：它会给 APK 增加约 8-10MB，
 * 而电子版 PDF 的文本层提取用轻量方式已能覆盖大多数场景。
 * 若后续需要完整支持，可在此替换为 PdfBox 实现，接口不变。
 */
object PdfTextExtractor {

    private const val TAG = "PdfTextExtractor"

    /**
     * 提取 PDF 纯文本
     */
    fun extract(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes()
        } ?: return ""

        val raw = String(bytes, Charsets.ISO_8859_1)

        // 从 PDF 内容流中提取 Tj / TJ 操作符里的字符串
        val text = extractTextOperators(raw)

        if (text.isBlank()) {
            Log.w(TAG, "未能从 PDF 提取文本，可能是扫描版（无文本层）")
            return ""
        }
        return text
    }

    /**
     * 提取 PDF 文本操作符
     */
    private fun extractTextOperators(raw: String): String {
        val result = StringBuilder()

        // 匹配 (...) 形式的字符串，PDF 文本通常以这种形式存在于 Tj 前
        val pattern = Regex("\\((?:\\\\.|[^\\\\()])*\\)")
        val matches = pattern.findAll(raw)

        var count = 0
        for (match in matches) {
            val literal = match.value.removePrefix("(").removeSuffix(")")
            val decoded = decodePdfString(literal)
            if (decoded.isNotBlank()) {
                result.append(decoded)
                result.append(' ')
            }
            count++
            if (count > 200_000) break // 防御超大文件
        }

        return result.toString()
            .replace(Regex("[ ]{2,}"), " ")
            .trim()
    }

    /**
     * 解码 PDF 字符串转义
     */
    private fun decodePdfString(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            when (val c = s[i]) {
                '\\' -> {
                    if (i + 1 < s.length) {
                        when (val next = s[i + 1]) {
                            'n' -> { sb.append('\n'); i += 2 }
                            'r' -> { sb.append('\r'); i += 2 }
                            't' -> { sb.append('\t'); i += 2 }
                            '(' -> { sb.append('('); i += 2 }
                            ')' -> { sb.append(')'); i += 2 }
                            '\\' -> { sb.append('\\'); i += 2 }
                            '\n' -> { i += 2 }
                            else -> { sb.append(next); i += 2 }
                        }
                    } else {
                        i++
                    }
                }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }
}

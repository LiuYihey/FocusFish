package com.focusfish.app.reading

import com.focusfish.app.data.local.dao.BookDao
import com.focusfish.app.data.local.entity.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 阅读引擎
 *
 * 大肥鱼的「陪读」机制：
 * - 用户专注时，它按固定节奏一页一页慢慢读
 * - 读到有感触的页，攒下一条笔记
 * - 用户专注结束，它把笔记汇总成一段感悟说出来
 *
 * 节奏设计（[PAGES_PER_INTERVAL] × [INTERVAL_MINUTES]）：
 * 每 3 分钟读 1 页 → 一小时读 20 页 → 一本 300 页的书要读 15 小时。
 * 这个速度足够慢，慢到「它真的在读」这件事是可信的。
 */
@Singleton
class ReadingEngine @Inject constructor(
    private val bookDao: BookDao
) {

    companion object {
        /** 每隔多少分钟读一页 */
        const val INTERVAL_MINUTES = 3

        /** 分页分隔标记 */
        private const val PAGE_MARKER = "<<<PAGE>>>"

        /** 一次最多读多少页（防止一次专注结束后狂读） */
        const val MAX_PAGES_PER_SESSION = 5
    }

    /**
     * 读取一页内容
     *
     * @return 该页文本，若无书或已读完返回 null
     */
    suspend fun readNextPage(): PageRead? = withContext(Dispatchers.IO) {
        val book = bookDao.getCurrentReading() ?: return@withContext null
        val pages = loadPages(book) ?: return@withContext null

        val nextIndex = book.currentPage
        if (nextIndex >= pages.size) {
            bookDao.updateProgress(book.id, nextIndex, true)
            return@withContext null
        }

        val content = pages[nextIndex]
        val isFinished = nextIndex + 1 >= pages.size
        bookDao.updateProgress(book.id, nextIndex + 1, isFinished)

        PageRead(
            bookId = book.id,
            bookTitle = book.title,
            pageNumber = nextIndex + 1,
            totalPages = pages.size,
            content = content,
            isBookFinished = isFinished
        )
    }

    /**
     * 批量读几页（专注结束时一次多读一点）
     */
    suspend fun readPages(count: Int): List<PageRead> {
        val reads = mutableListOf<PageRead>()
        val limit = count.coerceAtMost(MAX_PAGES_PER_SESSION)
        while (reads.size < limit) {
            val page = readNextPage() ?: break
            reads.add(page)
        }
        return reads
    }

    /**
     * 加载书籍的所有页面
     */
    private fun loadPages(book: BookEntity): List<String>? {
        val path = book.contentPath ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching {
            file.readText(Charsets.UTF_8).split(PAGE_MARKER)
        }.getOrNull()
    }

    /**
     * 当前正在读的书
     */
    suspend fun currentBook(): BookEntity? = bookDao.getCurrentReading()

    /**
     * 所有已导入的书
     */
    fun observeBooks() = bookDao.observeAll()

    /**
     * 导入书籍
     */
    suspend fun importBook(result: BookImporter.ImportResult): Long {
        val id = bookDao.insert(
            BookEntity(
                title = result.title,
                author = result.author,
                sourceUri = result.sourceUri,
                fileName = result.fileName,
                fileType = result.fileType,
                contentPath = result.contentPath,
                totalPages = result.totalPages,
                currentPage = 0
            )
        )
        return id
    }

    /**
     * 删除书籍
     */
    suspend fun deleteBook(id: Long) = bookDao.delete(id)

    /**
     * 读取的一页
     */
    data class PageRead(
        val bookId: Long,
        val bookTitle: String,
        val pageNumber: Int,
        val totalPages: Int,
        val content: String,
        val isBookFinished: Boolean
    )
}

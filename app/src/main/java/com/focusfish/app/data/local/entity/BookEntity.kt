package com.focusfish.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 书籍实体
 *
 * 用户导入给大肥鱼阅读的书籍 / 论文（txt / epub / pdf）。
 * 专注期间大肥鱼会「一张一张慢慢读」，读完在对话里给出感悟。
 *
 * 文本内容不入库（避免大文件撑爆 Room），只存原始 URI 与分页后的缓存文件路径。
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 书名 */
    val title: String,

    /** 作者（可空） */
    val author: String? = null,

    /** 原始文件 URI（SAF） */
    val sourceUri: String,

    /** 原始文件名 */
    val fileName: String,

    /** 文件类型：TXT / EPUB / PDF */
    val fileType: String = "TXT",

    /** 分页后的纯文本缓存路径（app filesDir 下） */
    val contentPath: String? = null,

    /** 总页数 */
    val totalPages: Int = 0,

    /** 已读到的页码（0 表示还没读） */
    val currentPage: Int = 0,

    /** 是否读完 */
    val isFinished: Boolean = false,

    /** 导入时间 */
    val createdAt: Long = System.currentTimeMillis()
)

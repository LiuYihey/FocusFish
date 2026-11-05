package com.focusfish.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusfish.app.data.local.entity.BookEntity
import com.focusfish.app.reading.BookImporter
import com.focusfish.app.reading.ReadingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 书籍管理 UI 状态
 */
data class BooksUiState(
    val isImporting: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

/**
 * 书籍管理 ViewModel
 *
 * 用户在这里导入 txt / epub / pdf 给大肥鱼读。
 * 导入后大肥鱼会在你专注时一页一页慢慢读。
 */
@HiltViewModel
class BooksViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val readingEngine: ReadingEngine
) : ViewModel() {

    /** 全部书籍 */
    val books: StateFlow<List<BookEntity>> = readingEngine.observeBooks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow(BooksUiState())
    val uiState: StateFlow<BooksUiState> = _uiState.asStateFlow()

    /**
     * 导入书籍
     */
    fun importBook(uri: Uri) {
        _uiState.update { it.copy(isImporting = true, errorMessage = null, message = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    BookImporter(context).import(uri)
                }
                readingEngine.importBook(result)
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        message = "《${result.title}》已入库，共 ${result.totalPages} 页"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = e.message ?: "导入失败，试试 txt 或 epub 格式？"
                    )
                }
            }
        }
    }

    /**
     * 删除书籍
     */
    fun deleteBook(id: Long) {
        viewModelScope.launch {
            readingEngine.deleteBook(id)
            _uiState.update { it.copy(message = "已删除", errorMessage = null) }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null, errorMessage = null) }
}

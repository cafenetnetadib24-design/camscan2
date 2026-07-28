package com.example.ui.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.DocumentEntity
import com.example.data.local.DocumentPageEntity
import com.example.data.repository.DocumentRepository
import com.example.util.GeminiAiAssistant
import com.example.util.ScanFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import android.graphics.PointF

data class EditUiState(
    val document: DocumentEntity? = null,
    val pages: List<DocumentPageEntity> = emptyList(),
    val currentPageIndex: Int = 0,
    val selectedFilter: ScanFilter = ScanFilter.MAGIC_COLOR,
    val cropRect: RectF = RectF(0f, 0f, 1f, 1f),
    val topLeft: PointF = PointF(0f, 0f),
    val topRight: PointF = PointF(1f, 0f),
    val bottomRight: PointF = PointF(1f, 1f),
    val bottomLeft: PointF = PointF(0f, 1f),
    val rotationDegrees: Int = 0,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val isProcessing: Boolean = false,
    val currentOcrText: String = "",
    val aiSummaryText: String = "",
    val translatedText: String = "",
    val isTranslating: Boolean = false,
    val targetLanguage: String = "Persian",
    val qaQuestion: String = "",
    val qaAnswer: String = "",
    val isQaLoading: Boolean = false,
    val isOcrModalVisible: Boolean = false,
    val isAiSummaryLoading: Boolean = false
)

class EditViewModel(
    private val repository: DocumentRepository,
    val documentId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState

    init {
        loadDocument()
    }

    private fun loadDocument() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            val doc = repository.getDocumentById(documentId)
            val pages = repository.getPagesListForDocument(documentId)

            if (doc != null && pages.isNotEmpty()) {
                val firstPage = pages.first()
                val currentCrop = RectF(firstPage.cropLeft, firstPage.cropTop, firstPage.cropRight, firstPage.cropBottom)
                val tl = PointF(firstPage.cropLeft, firstPage.cropTop)
                val tr = PointF(firstPage.cropRight, firstPage.cropTop)
                val br = PointF(firstPage.cropRight, firstPage.cropBottom)
                val bl = PointF(firstPage.cropLeft, firstPage.cropBottom)

                _uiState.value = _uiState.value.copy(
                    document = doc,
                    pages = pages,
                    currentPageIndex = 0,
                    selectedFilter = try { ScanFilter.valueOf(firstPage.filterType) } catch (e: Exception) { ScanFilter.BLACK_WHITE },
                    cropRect = currentCrop,
                    topLeft = tl,
                    topRight = tr,
                    bottomRight = br,
                    bottomLeft = bl,
                    rotationDegrees = firstPage.rotationDegrees,
                    brightness = firstPage.brightness,
                    contrast = firstPage.contrast,
                    currentOcrText = firstPage.ocrText,
                    isProcessing = false
                )
                if (currentCrop.left == 0f && currentCrop.top == 0f && currentCrop.right == 1f && currentCrop.bottom == 1f) {
                    autoDetectEdges()
                }
            } else {
                _uiState.value = _uiState.value.copy(isProcessing = false)
            }
        }
    }

    fun selectPageIndex(index: Int) {
        val pages = _uiState.value.pages
        if (index in pages.indices) {
            val page = pages[index]
            val tl = PointF(page.cropLeft, page.cropTop)
            val tr = PointF(page.cropRight, page.cropTop)
            val br = PointF(page.cropRight, page.cropBottom)
            val bl = PointF(page.cropLeft, page.cropBottom)

            _uiState.value = _uiState.value.copy(
                currentPageIndex = index,
                selectedFilter = try { ScanFilter.valueOf(page.filterType) } catch (e: Exception) { ScanFilter.MAGIC_COLOR },
                cropRect = RectF(page.cropLeft, page.cropTop, page.cropRight, page.cropBottom),
                topLeft = tl,
                topRight = tr,
                bottomRight = br,
                bottomLeft = bl,
                rotationDegrees = page.rotationDegrees,
                brightness = page.brightness,
                contrast = page.contrast,
                currentOcrText = page.ocrText
            )
        }
    }

    fun onFilterSelected(filter: ScanFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
        applyChangesToCurrentPage()
    }

    fun rotateCurrentPage(clockwise: Boolean) {
        val newRotation = if (clockwise) {
            (_uiState.value.rotationDegrees + 90) % 360
        } else {
            (_uiState.value.rotationDegrees - 90 + 360) % 360
        }
        _uiState.value = _uiState.value.copy(rotationDegrees = newRotation)
        applyChangesToCurrentPage()
    }

    fun autoDetectEdges() {
        val pages = _uiState.value.pages
        val index = _uiState.value.currentPageIndex
        if (index !in pages.indices) return

        val page = pages[index]
        val originalBitmap = com.example.util.ImageFilterUtils.loadBitmapFromFile(page.originalImagePath)
            ?: com.example.util.ImageFilterUtils.loadBitmapFromFile(page.imagePath)
            ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            val bounds = com.example.util.DocumentEdgeDetector.detectDocumentBoundariesWithVision(originalBitmap)
            _uiState.value = _uiState.value.copy(
                cropRect = bounds.rect,
                topLeft = bounds.topLeft,
                topRight = bounds.topRight,
                bottomRight = bounds.bottomRight,
                bottomLeft = bounds.bottomLeft
            )
            applyChangesToCurrentPage()
        }
    }

    fun resetCropToFull() {
        _uiState.value = _uiState.value.copy(
            cropRect = RectF(0f, 0f, 1f, 1f),
            topLeft = PointF(0f, 0f),
            topRight = PointF(1f, 0f),
            bottomRight = PointF(1f, 1f),
            bottomLeft = PointF(0f, 1f)
        )
        applyChangesToCurrentPage()
    }

    fun updateCropQuad(tl: PointF, tr: PointF, br: PointF, bl: PointF) {
        val minX = minOf(tl.x, bl.x)
        val minY = minOf(tl.y, tr.y)
        val maxX = maxOf(tr.x, br.x)
        val maxY = maxOf(bl.y, br.y)
        _uiState.value = _uiState.value.copy(
            cropRect = RectF(minX, minY, maxX, maxY),
            topLeft = tl,
            topRight = tr,
            bottomRight = br,
            bottomLeft = bl
        )
    }

    fun applyCropAndSave(onComplete: () -> Unit = {}) {
        applyChangesToCurrentPage(onComplete)
    }

    fun updateBrightnessContrast(brightness: Float, contrast: Float) {
        _uiState.value = _uiState.value.copy(brightness = brightness, contrast = contrast)
        applyChangesToCurrentPage()
    }

    private fun applyChangesToCurrentPage(onComplete: () -> Unit = {}) {
        val pages = _uiState.value.pages
        val index = _uiState.value.currentPageIndex
        if (index !in pages.indices) return

        val page = pages[index]
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            val updatedPage = repository.updatePageFilterAndAdjustments(
                page = page,
                filter = _uiState.value.selectedFilter,
                cropRect = _uiState.value.cropRect,
                rotationDegrees = _uiState.value.rotationDegrees,
                brightness = _uiState.value.brightness,
                contrast = _uiState.value.contrast,
                topLeft = _uiState.value.topLeft,
                topRight = _uiState.value.topRight,
                bottomRight = _uiState.value.bottomRight,
                bottomLeft = _uiState.value.bottomLeft
            )

            val updatedPages = pages.toMutableList().apply { set(index, updatedPage) }
            _uiState.value = _uiState.value.copy(
                pages = updatedPages,
                currentOcrText = updatedPage.ocrText,
                isProcessing = false
            )
            onComplete()
        }
    }

    fun toggleOcrModal(show: Boolean) {
        _uiState.value = _uiState.value.copy(isOcrModalVisible = show)
        if (show && _uiState.value.aiSummaryText.isBlank() && _uiState.value.currentOcrText.isNotBlank()) {
            fetchAiSummary()
        }
    }

    fun fetchAiSummary() {
        val text = _uiState.value.currentOcrText
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAiSummaryLoading = true)
            val summary = GeminiAiAssistant.summarizeDocument(text)
            _uiState.value = _uiState.value.copy(
                aiSummaryText = summary,
                isAiSummaryLoading = false
            )
        }
    }

    fun translateDocument(targetLanguage: String) {
        val text = _uiState.value.currentOcrText
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTranslating = true, targetLanguage = targetLanguage)
            val translated = GeminiAiAssistant.translateText(text, targetLanguage)
            _uiState.value = _uiState.value.copy(
                translatedText = translated,
                isTranslating = false
            )
        }
    }

    fun askDocumentQuestion(question: String) {
        val text = _uiState.value.currentOcrText
        if (text.isBlank() || question.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isQaLoading = true, qaQuestion = question)
            val answer = GeminiAiAssistant.askQuestionAboutDocument(text, question)
            _uiState.value = _uiState.value.copy(
                qaAnswer = answer,
                isQaLoading = false
            )
        }
    }

    fun deleteCurrentPage() {
        val pages = _uiState.value.pages
        val index = _uiState.value.currentPageIndex
        if (index in pages.indices) {
            val page = pages[index]
            viewModelScope.launch {
                repository.deletePage(page.id, documentId)
                loadDocument()
            }
        }
    }

    class Factory(private val context: Context, private val documentId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = AppDatabase.getDatabase(context)
            val repo = DocumentRepository(context, db.documentDao(), db.folderDao())
            return EditViewModel(repo, documentId) as T
        }
    }
}

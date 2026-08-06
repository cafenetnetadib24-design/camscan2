package com.example.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.repository.DocumentRepository
import com.example.util.ScanFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import android.graphics.PointF
import com.example.util.DocumentEdgeDetector

enum class FlashMode {
    OFF, ON, TORCH, AUTO
}

data class CameraUiState(
    val flashMode: FlashMode = FlashMode.ON,
    val isAutoCapture: Boolean = false,
    val isMultiPage: Boolean = false,
    val isColorScan: Boolean = false,
    val scanFilter: ScanFilter = ScanFilter.BLACK_WHITE,
    val targetFolderId: Long? = null,
    val capturedBitmaps: List<Bitmap> = emptyList(),
    val pendingCropBitmap: Bitmap? = null,
    val cropTopLeft: PointF = PointF(0.05f, 0.05f),
    val cropTopRight: PointF = PointF(0.95f, 0.05f),
    val cropBottomRight: PointF = PointF(0.95f, 0.95f),
    val cropBottomLeft: PointF = PointF(0.05f, 0.95f),
    val cropRotationDegrees: Int = 0,
    val isDocumentDetected: Boolean = true,
    val focusCondition: FocusCondition = FocusCondition.SHARP,
    val lightingCondition: LightingCondition = LightingCondition.GOOD,
    val sharpnessScore: Float = 12.0f,
    val avgBrightness: Float = 120.0f,
    val isProcessing: Boolean = false,
    val showPagePreviewTray: Boolean = false,
    val toastMessage: String? = null,
    val newlySavedDocId: Long? = null
)

class CameraViewModel(private val repository: DocumentRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState

    fun setFolderId(folderId: Long?) {
        _uiState.value = _uiState.value.copy(targetFolderId = folderId)
    }

    fun setScanMode(isColorScan: Boolean) {
        val defaultFilter = if (isColorScan) ScanFilter.ORIGINAL else ScanFilter.BLACK_WHITE
        _uiState.value = _uiState.value.copy(
            isColorScan = isColorScan,
            scanFilter = defaultFilter
        )
    }

    fun setScanFilter(filter: ScanFilter) {
        _uiState.value = _uiState.value.copy(
            scanFilter = filter,
            isColorScan = (filter != ScanFilter.BLACK_WHITE && filter != ScanFilter.GRAYSCALE)
        )
    }

    fun updateFrameMetrics(
        focus: FocusCondition,
        lighting: LightingCondition,
        score: Float,
        brightness: Float,
        isDetected: Boolean
    ) {
        _uiState.value = _uiState.value.copy(
            focusCondition = focus,
            lightingCondition = lighting,
            sharpnessScore = score,
            avgBrightness = brightness,
            isDocumentDetected = isDetected
        )
    }

    fun clearNewlySavedDocId() {
        _uiState.value = _uiState.value.copy(newlySavedDocId = null)
    }

    fun turnOffFlash() {
        _uiState.value = _uiState.value.copy(flashMode = FlashMode.OFF)
    }

    fun toggleFlashMode() {
        val nextFlash = when (_uiState.value.flashMode) {
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.TORCH
            FlashMode.TORCH -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.OFF
        }
        _uiState.value = _uiState.value.copy(flashMode = nextFlash)
    }

    fun toggleAutoCapture() {
        _uiState.value = _uiState.value.copy(isAutoCapture = !_uiState.value.isAutoCapture)
    }

    fun toggleMultiPage() {
        _uiState.value = _uiState.value.copy(isMultiPage = !_uiState.value.isMultiPage)
    }

    fun togglePagePreviewTray(show: Boolean) {
        _uiState.value = _uiState.value.copy(showPagePreviewTray = show)
    }

    fun clearToastMessage() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    fun addInstantCapturedBitmap(bitmap: Bitmap): Int {
        val currentList = _uiState.value.capturedBitmaps.toMutableList()
        currentList.add(bitmap)
        val newIndex = currentList.size - 1
        val count = currentList.size
        _uiState.value = _uiState.value.copy(
            capturedBitmaps = currentList,
            toastMessage = "برگه $count با موفقیت ثبت شد"
        )
        return newIndex
    }

    fun addCapturedImage(imageProxy: ImageProxy, targetIndex: Int? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val bitmap = imageProxyToBitmap(imageProxy, maxDimension = 1600)
            imageProxy.close()

            if (bitmap != null) {
                val state = _uiState.value
                val fullFrameRect = android.graphics.RectF(0f, 0f, 1f, 1f)
                val processedBitmap = com.example.util.ImageFilterUtils.applyFilterAndAdjustments(
                    sourceBitmap = bitmap,
                    filter = state.scanFilter,
                    rotationDegrees = 0,
                    cropRect = fullFrameRect,
                    topLeft = android.graphics.PointF(0f, 0f),
                    topRight = android.graphics.PointF(1f, 0f),
                    bottomRight = android.graphics.PointF(1f, 1f),
                    bottomLeft = android.graphics.PointF(0f, 1f)
                )

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val currentList = _uiState.value.capturedBitmaps.toMutableList()
                    if (targetIndex != null && targetIndex in currentList.indices) {
                        currentList[targetIndex] = processedBitmap
                    } else {
                        currentList.add(processedBitmap)
                    }
                    val count = currentList.size
                    _uiState.value = _uiState.value.copy(
                        capturedBitmaps = currentList,
                        toastMessage = "برگه $count با موفقیت ثبت شد"
                    )
                }
            }
        }
    }

    fun updateCropQuad(tl: PointF, tr: PointF, br: PointF, bl: PointF) {
        _uiState.value = _uiState.value.copy(
            cropTopLeft = tl,
            cropTopRight = tr,
            cropBottomRight = br,
            cropBottomLeft = bl
        )
    }

    fun rotatePendingCropBitmap() {
        val current = _uiState.value.pendingCropBitmap ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isProcessing = true)
            }
            val matrix = Matrix().apply { postRotate(90f) }
            val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
            val bounds = DocumentEdgeDetector.detectDocumentBoundariesWithVision(rotated)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    pendingCropBitmap = rotated,
                    cropTopLeft = bounds.topLeft,
                    cropTopRight = bounds.topRight,
                    cropBottomRight = bounds.bottomRight,
                    cropBottomLeft = bounds.bottomLeft,
                    isProcessing = false
                )
            }
        }
    }

    fun autoDetectCropQuad() {
        val bitmap = _uiState.value.pendingCropBitmap ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isProcessing = true)
            }
            val bounds = DocumentEdgeDetector.detectDocumentBoundariesWithVision(bitmap)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    cropTopLeft = bounds.topLeft,
                    cropTopRight = bounds.topRight,
                    cropBottomRight = bounds.bottomRight,
                    cropBottomLeft = bounds.bottomLeft,
                    isProcessing = false
                )
            }
        }
    }

    fun resetCropQuad() {
        _uiState.value = _uiState.value.copy(
            cropTopLeft = PointF(0f, 0f),
            cropTopRight = PointF(1f, 0f),
            cropBottomRight = PointF(1f, 1f),
            cropBottomLeft = PointF(0f, 1f)
        )
    }

    fun cancelPendingCrop() {
        _uiState.value = _uiState.value.copy(pendingCropBitmap = null)
    }

    fun confirmCropAndProceed() {
        val rawBitmap = _uiState.value.pendingCropBitmap ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isProcessing = true)
            }
            val state = _uiState.value
            val croppedBitmap = com.example.util.ImageFilterUtils.applyFilterAndAdjustments(
                sourceBitmap = rawBitmap,
                filter = state.scanFilter,
                rotationDegrees = state.cropRotationDegrees,
                topLeft = state.cropTopLeft,
                topRight = state.cropTopRight,
                bottomRight = state.cropBottomRight,
                bottomLeft = state.cropBottomLeft
            )

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val updatedList = state.capturedBitmaps + croppedBitmap
                val count = updatedList.size
                _uiState.value = _uiState.value.copy(
                    capturedBitmaps = updatedList,
                    pendingCropBitmap = null,
                    toastMessage = "برگه $count با موفقیت ثبت و برش شد"
                )

                if (!state.isMultiPage) {
                    saveDocumentAndFinish()
                } else {
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                }
            }
        }
    }

    fun saveDocumentAndFinish(onComplete: (Long) -> Unit = {}) {
        val bitmaps = _uiState.value.capturedBitmaps
        if (bitmaps.isEmpty()) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isProcessing = true)

            val docId = repository.createDocument(
                title = null,
                folderId = _uiState.value.targetFolderId,
                capturedBitmaps = bitmaps,
                filter = _uiState.value.scanFilter
            )

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    newlySavedDocId = docId
                )
                onComplete(docId)
            }
        }
    }

    fun addImportedBitmaps(bitmaps: List<Bitmap>) {
        if (bitmaps.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            val updatedList = _uiState.value.capturedBitmaps + bitmaps
            _uiState.value = _uiState.value.copy(
                capturedBitmaps = updatedList,
                isProcessing = false,
                toastMessage = "${bitmaps.size} برگه اضافه شد"
            )
            if (!_uiState.value.isMultiPage) {
                saveDocumentAndFinish()
            }
        }
    }

    fun addImportedUris(context: Context, uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            val bitmaps = uris.mapNotNull { uri ->
                com.example.util.ImageFilterUtils.loadSafeBitmapFromUri(context, uri)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (bitmaps.isNotEmpty()) {
                    val firstBmp = bitmaps.first()
                    val bounds = DocumentEdgeDetector.detectDocumentBoundariesWithVision(firstBmp)
                    
                    // Add remaining bitmaps if any directly to captured list
                    val remainingBitmaps = bitmaps.drop(1)
                    val currentCaptured = _uiState.value.capturedBitmaps + remainingBitmaps

                    _uiState.value = _uiState.value.copy(
                        capturedBitmaps = currentCaptured,
                        pendingCropBitmap = firstBmp,
                        cropTopLeft = bounds.topLeft,
                        cropTopRight = bounds.topRight,
                        cropBottomRight = bounds.bottomRight,
                        cropBottomLeft = bounds.bottomLeft,
                        cropRotationDegrees = 0,
                        isProcessing = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                }
            }
        }
    }

    fun removeCapturedPage(index: Int) {
        val current = _uiState.value.capturedBitmaps.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            val hideTray = current.isEmpty()
            _uiState.value = _uiState.value.copy(
                capturedBitmaps = current,
                showPagePreviewTray = if (hideTray) false else _uiState.value.showPagePreviewTray
            )
        }
    }

    fun clearAllPages() {
        _uiState.value = _uiState.value.copy(
            capturedBitmaps = emptyList(),
            showPagePreviewTray = false
        )
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy, maxDimension: Int = 2048): Bitmap? {
        return try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

            var sampleSize = 1
            val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            if (maxDim > maxDimension) {
                sampleSize = maxDim / maxDimension
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = AppDatabase.getDatabase(context)
            val repo = DocumentRepository(context, db.documentDao(), db.folderDao())
            return CameraViewModel(repo) as T
        }
    }
}

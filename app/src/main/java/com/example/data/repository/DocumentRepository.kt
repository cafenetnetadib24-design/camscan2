package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.data.local.DocumentDao
import com.example.data.local.DocumentEntity
import com.example.data.local.DocumentPageEntity
import com.example.data.local.FolderDao
import com.example.data.local.FolderEntity
import com.example.util.GeminiAiAssistant
import com.example.util.ImageFilterUtils
import com.example.util.OcrEngine
import com.example.util.PdfGenerator
import com.example.util.PdfQuality
import com.example.util.ScanFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DocumentRepository(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val folderDao: FolderDao
) {

    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    val activeFolders: Flow<List<FolderEntity>> = folderDao.getActiveFolders()
    val archivedFolders: Flow<List<FolderEntity>> = folderDao.getArchivedFolders()
    val allFolders: Flow<List<FolderEntity>> = folderDao.getAllFolders()
    val favoriteDocuments: Flow<List<DocumentEntity>> = documentDao.getFavoriteDocuments()
    val trashDocuments: Flow<List<DocumentEntity>> = documentDao.getTrashDocuments()

    fun getDocumentsInFolder(folderId: Long): Flow<List<DocumentEntity>> =
        documentDao.getDocumentsInFolder(folderId)

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> =
        documentDao.searchDocuments(query)

    suspend fun getDocumentById(id: Long): DocumentEntity? =
        documentDao.getDocumentById(id)

    fun getPagesForDocument(documentId: Long): Flow<List<DocumentPageEntity>> =
        documentDao.getPagesForDocument(documentId)

    suspend fun getPagesListForDocument(documentId: Long): List<DocumentPageEntity> =
        documentDao.getPagesListForDocument(documentId)

    /**
     * Creates a new document from captured page bitmap images.
     */
    suspend fun createDocument(
        title: String?,
        folderId: Long?,
        capturedBitmaps: List<Bitmap>,
        filter: ScanFilter = ScanFilter.MAGIC_COLOR
    ): Long = withContext(Dispatchers.IO) {
        if (capturedBitmaps.isEmpty()) return@withContext 0L

        val docTitle = if (!title.isNullOrBlank()) {
            title
        } else {
            val sdf = java.text.SimpleDateFormat("yyyy_MM_dd - HH_mm_ss", java.util.Locale("fa"))
            "سند ${sdf.format(java.util.Date())}"
        }

        val pagePaths = mutableListOf<String>()
        val origPaths = mutableListOf<String>()
        val fullFrameRect = android.graphics.RectF(0f, 0f, 1f, 1f)

        capturedBitmaps.forEach { bitmap ->
            val origPath = ImageFilterUtils.saveBitmapToAppStorage(context, bitmap, "orig")
            origPaths.add(origPath)

            val processedBitmap = ImageFilterUtils.applyFilterAndAdjustments(
                sourceBitmap = bitmap,
                filter = filter,
                cropRect = fullFrameRect,
                topLeft = android.graphics.PointF(0f, 0f),
                topRight = android.graphics.PointF(1f, 0f),
                bottomRight = android.graphics.PointF(1f, 1f),
                bottomLeft = android.graphics.PointF(0f, 1f)
            )
            val procPath = ImageFilterUtils.saveBitmapToAppStorage(context, processedBitmap, "proc")
            pagePaths.add(procPath)
        }

        val firstProcPath = pagePaths.firstOrNull() ?: ""

        val docEntity = DocumentEntity(
            title = docTitle,
            folderId = folderId,
            pageCount = capturedBitmaps.size,
            thumbnailPath = firstProcPath,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val docId = documentDao.insertDocument(docEntity)

        pagePaths.forEachIndexed { index, path ->
            val origP = origPaths.getOrNull(index) ?: path
            documentDao.insertPage(
                DocumentPageEntity(
                    documentId = docId,
                    pageIndex = index,
                    imagePath = path,
                    originalImagePath = origP,
                    filterType = filter.name,
                    cropLeft = 0f,
                    cropTop = 0f,
                    cropRight = 1f,
                    cropBottom = 1f,
                    ocrText = ""
                )
            )
        }

        // Asynchronously run OCR in background without delaying document creation or page rendering
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val firstPage = documentDao.getPagesListForDocument(docId).firstOrNull()
                if (firstPage != null && java.io.File(firstPage.imagePath).exists()) {
                    val bmp = BitmapFactory.decodeFile(firstPage.imagePath)
                    if (bmp != null) {
                        val ocrText = kotlinx.coroutines.withTimeoutOrNull(2500) {
                            OcrEngine.recognizeTextFromBitmap(bmp)
                        } ?: ""
                        if (ocrText.isNotEmpty()) {
                            documentDao.updatePage(firstPage.copy(ocrText = ocrText))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        docId
    }

    suspend fun updatePageFilterAndAdjustments(
        page: DocumentPageEntity,
        filter: ScanFilter,
        cropRect: android.graphics.RectF,
        rotationDegrees: Int,
        brightness: Float,
        contrast: Float,
        saturation: Float = 1f,
        warmth: Float = 0f,
        sharpness: Float = 1f,
        topLeft: android.graphics.PointF = android.graphics.PointF(cropRect.left, cropRect.top),
        topRight: android.graphics.PointF = android.graphics.PointF(cropRect.right, cropRect.top),
        bottomRight: android.graphics.PointF = android.graphics.PointF(cropRect.right, cropRect.bottom),
        bottomLeft: android.graphics.PointF = android.graphics.PointF(cropRect.left, cropRect.bottom)
    ): DocumentPageEntity = withContext(Dispatchers.IO) {
        val origPathToKeep = if (page.originalImagePath.isNotBlank() && File(page.originalImagePath).exists()) {
            page.originalImagePath
        } else {
            page.imagePath
        }

        val origBitmap = BitmapFactory.decodeFile(origPathToKeep)
            ?: BitmapFactory.decodeFile(page.imagePath)

        if (origBitmap != null) {
            val newBitmap = ImageFilterUtils.applyFilterAndAdjustments(
                sourceBitmap = origBitmap,
                filter = filter,
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                warmth = warmth,
                sharpness = sharpness,
                rotationDegrees = rotationDegrees,
                cropRect = cropRect,
                topLeft = topLeft,
                topRight = topRight,
                bottomRight = bottomRight,
                bottomLeft = bottomLeft
            )

            val newImagePath = ImageFilterUtils.saveBitmapToAppStorage(context, newBitmap, "proc")
            val newOcrText = OcrEngine.recognizeTextFromBitmap(newBitmap)

            // Clean up previous image file if it exists, is different, and is not the original file
            try {
                if (page.imagePath.isNotBlank() && page.imagePath != newImagePath && page.imagePath != origPathToKeep && File(page.imagePath).exists()) {
                    File(page.imagePath).delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val updatedPage = page.copy(
                imagePath = newImagePath,
                originalImagePath = origPathToKeep,
                filterType = filter.name,
                cropLeft = cropRect.left,
                cropTop = cropRect.top,
                cropRight = cropRect.right,
                cropBottom = cropRect.bottom,
                rotationDegrees = rotationDegrees,
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                warmth = warmth,
                sharpness = sharpness,
                ocrText = newOcrText
            )

            documentDao.updatePage(updatedPage)

            // Update document thumbnail if this is page 0
            if (page.pageIndex == 0) {
                val doc = documentDao.getDocumentById(page.documentId)
                if (doc != null) {
                    documentDao.updateDocument(doc.copy(thumbnailPath = newImagePath, updatedAt = System.currentTimeMillis()))
                }
            }

            updatedPage
        } else {
            page
        }
    }

    suspend fun updateDocumentTitle(documentId: Long, newTitle: String) {
        val doc = documentDao.getDocumentById(documentId) ?: return
        documentDao.updateDocument(doc.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
    }

    suspend fun isTitleDuplicate(newTitle: String, currentDocId: Long): Boolean {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return false
        return documentDao.countDocumentsWithTitle(trimmed, currentDocId) > 0
    }

    suspend fun setFavorite(documentId: Long, isFavorite: Boolean) {
        documentDao.setFavorite(documentId, isFavorite)
    }

    suspend fun moveDocumentsToFolder(docIds: List<Long>, folderId: Long?) {
        documentDao.moveDocumentsToFolder(docIds, folderId)
    }

    suspend fun moveToTrash(documentId: Long) {
        documentDao.moveToTrash(documentId)
    }

    suspend fun moveDocumentsToTrash(documentIds: List<Long>) {
        documentDao.moveMultipleToTrash(documentIds)
    }

    suspend fun restoreFromTrash(documentId: Long) {
        documentDao.restoreFromTrash(documentId)
    }

    suspend fun restoreMultipleFromTrash(documentIds: List<Long>) {
        documentDao.restoreMultipleFromTrash(documentIds)
    }

    suspend fun permanentlyDeleteDocument(documentId: Long) = withContext(Dispatchers.IO) {
        val pages = documentDao.getPagesListForDocument(documentId)
        pages.forEach { page ->
            File(page.imagePath).delete()
            File(page.originalImagePath).delete()
        }
        documentDao.deleteDocumentById(documentId)
    }

    suspend fun permanentlyDeleteDocuments(documentIds: List<Long>) = withContext(Dispatchers.IO) {
        documentIds.forEach { permanentlyDeleteDocument(it) }
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        val trashDocs = documentDao.getAllTrashDocumentsList()
        trashDocs.forEach { doc ->
            val pages = documentDao.getPagesListForDocument(doc.id)
            pages.forEach { page ->
                File(page.imagePath).delete()
                File(page.originalImagePath).delete()
            }
        }
        documentDao.emptyTrash()
    }

    suspend fun cleanExpiredTrash() = withContext(Dispatchers.IO) {
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000L
        val cutoffTimestamp = System.currentTimeMillis() - thirtyDaysInMillis
        val expiredDocs = documentDao.getExpiredTrashDocuments(cutoffTimestamp)
        expiredDocs.forEach { doc ->
            permanentlyDeleteDocument(doc.id)
        }
    }

    suspend fun deleteDocument(documentId: Long) {
        moveToTrash(documentId)
    }

    suspend fun deleteDocuments(documentIds: List<Long>) {
        moveDocumentsToTrash(documentIds)
    }

    suspend fun deletePage(pageId: Long, documentId: Long) = withContext(Dispatchers.IO) {
        documentDao.deletePageById(pageId)
        val remainingPages = documentDao.getPagesListForDocument(documentId)
        if (remainingPages.isEmpty()) {
            deleteDocument(documentId)
        } else {
            val updatedDoc = documentDao.getDocumentById(documentId)?.copy(
                pageCount = remainingPages.size,
                thumbnailPath = remainingPages.first().imagePath,
                updatedAt = System.currentTimeMillis()
            )
            if (updatedDoc != null) {
                documentDao.updateDocument(updatedDoc)
            }
        }
    }

    suspend fun createFolder(name: String, colorHex: String = "#0052CC"): Long {
        return folderDao.insertFolder(FolderEntity(name = name, colorHex = colorHex))
    }

    suspend fun renameFolder(folderId: Long, newName: String, colorHex: String = "#0052CC") {
        folderDao.renameFolder(folderId, newName, colorHex)
    }

    suspend fun setFolderArchived(folderId: Long, isArchived: Boolean) {
        folderDao.setFolderArchived(folderId, isArchived)
    }

    suspend fun deleteFolder(folderId: Long) = withContext(Dispatchers.IO) {
        documentDao.clearFolderForDocuments(folderId)
        folderDao.deleteFolderById(folderId)
    }

    suspend fun exportDocumentPdf(
        documentId: Long,
        quality: PdfQuality = PdfQuality.HIGH,
        watermarkText: String? = null,
        addPageNumbers: Boolean = true,
        password: String? = null
    ): File? = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentById(documentId) ?: return@withContext null
        val pages = documentDao.getPagesListForDocument(documentId)
        val imagePaths = pages.map { it.imagePath }
        PdfGenerator.generatePdfFromPagePaths(
            context = context,
            pageImagePaths = imagePaths,
            pdfTitle = doc.title,
            quality = quality,
            watermarkText = watermarkText,
            addPageNumbers = addPageNumbers,
            password = password
        )
    }
}

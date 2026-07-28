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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

        val pagePaths = mutableListOf<String>()
        val origPaths = mutableListOf<String>()
        val detectedRects = mutableListOf<android.graphics.RectF>()
        var firstPageOcrText = ""

        capturedBitmaps.forEachIndexed { idx, bitmap ->
            // Save raw original image
            val origPath = ImageFilterUtils.saveBitmapToAppStorage(context, bitmap, "orig")
            origPaths.add(origPath)
            
            // Edge detection to identify document boundaries
            val edgeBounds = com.example.util.DocumentEdgeDetector.detectDocumentBoundaries(bitmap)
            detectedRects.add(edgeBounds.rect)

            // Process filter with edge detection crop bounds
            val processedBitmap = ImageFilterUtils.applyFilterAndAdjustments(
                sourceBitmap = bitmap,
                filter = filter,
                cropRect = edgeBounds.rect
            )
            val procPath = ImageFilterUtils.saveBitmapToAppStorage(context, processedBitmap, "proc")
            pagePaths.add(procPath)

            if (idx == 0) {
                firstPageOcrText = OcrEngine.recognizeTextFromBitmap(processedBitmap)
            }
        }

        val docTitle = if (!title.isNullOrBlank()) {
            title
        } else {
            val sdf = java.text.SimpleDateFormat("yyyy_MM_dd - HH_mm_ss", java.util.Locale("fa"))
            "سند ${sdf.format(java.util.Date())}"
        }

        val thumbnailPath = pagePaths.firstOrNull() ?: ""

        val docEntity = DocumentEntity(
            title = docTitle,
            folderId = folderId,
            pageCount = capturedBitmaps.size,
            thumbnailPath = thumbnailPath,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val docId = documentDao.insertDocument(docEntity)

        // Insert pages with edge detected bounds
        pagePaths.forEachIndexed { index, path ->
            val origP = origPaths.getOrNull(index) ?: path
            val rect = detectedRects.getOrElse(index) { android.graphics.RectF(0f, 0f, 1f, 1f) }
            documentDao.insertPage(
                DocumentPageEntity(
                    documentId = docId,
                    pageIndex = index,
                    imagePath = path,
                    originalImagePath = origP,
                    filterType = filter.name,
                    cropLeft = rect.left,
                    cropTop = rect.top,
                    cropRight = rect.right,
                    cropBottom = rect.bottom,
                    ocrText = if (index == 0) firstPageOcrText else ""
                )
            )
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
        topLeft: android.graphics.PointF = android.graphics.PointF(cropRect.left, cropRect.top),
        topRight: android.graphics.PointF = android.graphics.PointF(cropRect.right, cropRect.top),
        bottomRight: android.graphics.PointF = android.graphics.PointF(cropRect.right, cropRect.bottom),
        bottomLeft: android.graphics.PointF = android.graphics.PointF(cropRect.left, cropRect.bottom)
    ): DocumentPageEntity = withContext(Dispatchers.IO) {
        val origBitmap = BitmapFactory.decodeFile(page.originalImagePath)
            ?: BitmapFactory.decodeFile(page.imagePath)

        if (origBitmap != null) {
            val newBitmap = ImageFilterUtils.applyFilterAndAdjustments(
                sourceBitmap = origBitmap,
                filter = filter,
                brightness = brightness,
                contrast = contrast,
                rotationDegrees = rotationDegrees,
                cropRect = cropRect,
                topLeft = topLeft,
                topRight = topRight,
                bottomRight = bottomRight,
                bottomLeft = bottomLeft
            )

            val newImagePath = ImageFilterUtils.saveBitmapToAppStorage(context, newBitmap, "proc")
            val newOcrText = OcrEngine.recognizeTextFromBitmap(newBitmap)

            val updatedPage = page.copy(
                imagePath = newImagePath,
                filterType = filter.name,
                cropLeft = cropRect.left,
                cropTop = cropRect.top,
                cropRight = cropRect.right,
                cropBottom = cropRect.bottom,
                rotationDegrees = rotationDegrees,
                brightness = brightness,
                contrast = contrast,
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

    suspend fun deleteDocument(documentId: Long) = withContext(Dispatchers.IO) {
        val pages = documentDao.getPagesListForDocument(documentId)
        pages.forEach { page ->
            File(page.imagePath).delete()
            File(page.originalImagePath).delete()
        }
        documentDao.deleteDocumentById(documentId)
    }

    suspend fun deleteDocuments(documentIds: List<Long>) = withContext(Dispatchers.IO) {
        documentIds.forEach { deleteDocument(it) }
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
        addPageNumbers: Boolean = true
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
            addPageNumbers = addPageNumbers
        )
    }
}

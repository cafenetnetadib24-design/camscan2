package com.example.ui.home

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.DocumentEntity
import com.example.data.local.FolderEntity
import com.example.data.repository.DocumentRepository
import com.example.util.ScanFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class SortOrder {
    DATE_DESC,
    DATE_ASC,
    NAME_ASC,
    NAME_DESC,
    PAGE_COUNT_DESC
}

data class HomeUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val selectedFolderId: Long? = null, // null = All, -1 = Favorites
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val isGridMode: Boolean = true,
    val selectedDocIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
    val shareFile: File? = null
)

class HomeViewModel(private val repository: DocumentRepository) : ViewModel() {

    private val _selectedFolderId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)
    private val _isGridMode = MutableStateFlow(true)
    private val _selectedDocIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isLoading = MutableStateFlow(false)
    private val _shareFile = MutableStateFlow<File?>(null)

    val shareFile: StateFlow<File?> = _shareFile

    init {
        viewModelScope.launch {
            repository.cleanExpiredTrash()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _documents = combine(
        _selectedFolderId,
        _searchQuery
    ) { folderId, query ->
        Pair(folderId, query)
    }.flatMapLatest { (folderId, query) ->
        when {
            query.isNotBlank() -> repository.searchDocuments(query)
            folderId == -1L -> repository.favoriteDocuments
            folderId == -2L -> repository.trashDocuments
            folderId != null -> repository.getDocumentsInFolder(folderId)
            else -> repository.allDocuments
        }
    }

    private data class UiPreferences(
        val sortOrder: SortOrder = SortOrder.DATE_DESC,
        val isGridMode: Boolean = true,
        val selectedDocIds: Set<Long> = emptySet(),
        val isLoading: Boolean = false
    )

    private val _uiPrefs = combine(
        _sortOrder,
        _isGridMode,
        _selectedDocIds,
        _isLoading
    ) { sort, isGrid, selectedIds, loading ->
        UiPreferences(sort, isGrid, selectedIds, loading)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        _documents,
        repository.allFolders,
        _selectedFolderId,
        _searchQuery,
        _uiPrefs
    ) { docs, folders, folderId, query, prefs ->
        val sortedDocs = when (prefs.sortOrder) {
            SortOrder.DATE_DESC -> docs.sortedByDescending { it.updatedAt }
            SortOrder.DATE_ASC -> docs.sortedBy { it.updatedAt }
            SortOrder.NAME_ASC -> docs.sortedBy { it.title.lowercase() }
            SortOrder.NAME_DESC -> docs.sortedByDescending { it.title.lowercase() }
            SortOrder.PAGE_COUNT_DESC -> docs.sortedByDescending { it.pageCount }
        }

        HomeUiState(
            documents = sortedDocs,
            folders = folders,
            selectedFolderId = folderId,
            searchQuery = query,
            sortOrder = prefs.sortOrder,
            isGridMode = prefs.isGridMode,
            selectedDocIds = prefs.selectedDocIds,
            isLoading = prefs.isLoading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun onSelectFolder(folderId: Long?) {
        _selectedFolderId.value = folderId
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSortOrderChange(sort: SortOrder) {
        _sortOrder.value = sort
    }

    fun toggleGridMode() {
        _isGridMode.value = !_isGridMode.value
    }

    fun toggleDocumentSelection(docId: Long) {
        val current = _selectedDocIds.value.toMutableSet()
        if (current.contains(docId)) {
            current.remove(docId)
        } else {
            current.add(docId)
        }
        _selectedDocIds.value = current
    }

    fun clearSelection() {
        _selectedDocIds.value = emptySet()
    }

    fun toggleFavorite(documentId: Long, currentFavorite: Boolean) {
        viewModelScope.launch {
            repository.setFavorite(documentId, !currentFavorite)
        }
    }

    fun renameDocument(documentId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.updateDocumentTitle(documentId, newTitle)
        }
    }

    fun deleteDocument(documentId: Long) {
        viewModelScope.launch {
            repository.deleteDocument(documentId)
        }
    }

    fun deleteSelectedDocuments() {
        viewModelScope.launch {
            repository.deleteDocuments(_selectedDocIds.value.toList())
            clearSelection()
        }
    }

    fun restoreDocument(documentId: Long) {
        viewModelScope.launch {
            repository.restoreFromTrash(documentId)
        }
    }

    fun restoreSelectedDocuments() {
        viewModelScope.launch {
            repository.restoreMultipleFromTrash(_selectedDocIds.value.toList())
            clearSelection()
        }
    }

    fun permanentlyDeleteDocument(documentId: Long) {
        viewModelScope.launch {
            repository.permanentlyDeleteDocument(documentId)
        }
    }

    fun permanentlyDeleteSelectedDocuments() {
        viewModelScope.launch {
            repository.permanentlyDeleteDocuments(_selectedDocIds.value.toList())
            clearSelection()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
            clearSelection()
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.createFolder(name)
        }
    }

    fun renameFolder(folderId: Long, newName: String) {
        viewModelScope.launch {
            repository.renameFolder(folderId, newName)
        }
    }

    fun archiveFolder(folderId: Long, isArchived: Boolean) {
        viewModelScope.launch {
            repository.setFolderArchived(folderId, isArchived)
            if (_selectedFolderId.value == folderId && isArchived) {
                _selectedFolderId.value = null
            }
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            if (_selectedFolderId.value == folderId) {
                _selectedFolderId.value = null
            }
        }
    }

    fun moveDocumentsToFolder(docIds: List<Long>, folderId: Long?) {
        viewModelScope.launch {
            repository.moveDocumentsToFolder(docIds, folderId)
            clearSelection()
        }
    }

    fun moveSelectedDocumentsToFolder(folderId: Long?) {
        moveDocumentsToFolder(_selectedDocIds.value.toList(), folderId)
    }

    fun importBitmapsFromGallery(bitmaps: List<Bitmap>, onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val newDocId = repository.createDocument(
                title = null,
                folderId = _selectedFolderId.value?.takeIf { it > 0 },
                capturedBitmaps = bitmaps,
                filter = ScanFilter.ORIGINAL
            )
            _isLoading.value = false
            onComplete(newDocId)
        }
    }

    fun importUrisFromGallery(context: Context, uris: List<android.net.Uri>, onComplete: (Long) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isLoading.value = true
            val bitmaps = uris.mapNotNull { uri ->
                com.example.util.ImageFilterUtils.loadSafeBitmapFromUri(context, uri)
            }
            if (bitmaps.isNotEmpty()) {
                val newDocId = repository.createDocument(
                    title = null,
                    folderId = _selectedFolderId.value?.takeIf { it > 0 },
                    capturedBitmaps = bitmaps,
                    filter = ScanFilter.ORIGINAL
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _isLoading.value = false
                    onComplete(newDocId)
                }
            } else {
                _isLoading.value = false
            }
        }
    }

    fun exportAndSharePdf(documentId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            val file = repository.exportDocumentPdf(documentId)
            _isLoading.value = false
            if (file != null) {
                _shareFile.value = file
            }
        }
    }

    fun saveDocumentToGallery(context: Context, documentId: Long, onResult: (Boolean, Int) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val doc = repository.getDocumentById(documentId)
            val pages = repository.getPagesListForDocument(documentId)
            val paths = pages.map { it.imagePath }
            val count = com.example.util.GalleryExporter.saveImagePathsToGallery(
                context = context,
                imagePaths = paths,
                baseName = doc?.title ?: "Scan"
            )
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(count > 0, count)
            }
        }
    }

    fun saveSelectedDocumentsToGallery(context: Context, onResult: (Boolean, Int) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var totalCount = 0
            val docIds = _selectedDocIds.value.toList()
            docIds.forEach { docId ->
                val doc = repository.getDocumentById(docId)
                val pages = repository.getPagesListForDocument(docId)
                val paths = pages.map { it.imagePath }
                totalCount += com.example.util.GalleryExporter.saveImagePathsToGallery(
                    context = context,
                    imagePaths = paths,
                    baseName = doc?.title ?: "Scan"
                )
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                clearSelection()
                onResult(totalCount > 0, totalCount)
            }
        }
    }

    fun clearShareFile() {
        _shareFile.value = null
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = AppDatabase.getDatabase(context)
            val repo = DocumentRepository(context, db.documentDao(), db.folderDao())
            return HomeViewModel(repo) as T
        }
    }
}

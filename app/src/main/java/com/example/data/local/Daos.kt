package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE folderId = :folderId AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun getDocumentsInFolder(folderId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isFavorite = 1 AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun getFavoriteDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("SELECT COUNT(*) FROM documents WHERE LOWER(TRIM(title)) = LOWER(TRIM(:title)) AND id != :excludeId AND deletedAt IS NULL")
    suspend fun countDocumentsWithTitle(title: String, excludeId: Long): Int

    @Query("SELECT DISTINCT d.* FROM documents d LEFT JOIN document_pages p ON d.id = p.documentId WHERE d.deletedAt IS NULL AND (d.title LIKE '%' || :query || '%' OR d.tags LIKE '%' || :query || '%' OR p.ocrText LIKE '%' || :query || '%') ORDER BY d.updatedAt DESC")
    fun searchDocuments(query: String): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun moveToTrash(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET deletedAt = :deletedAt WHERE id IN (:ids)")
    suspend fun moveMultipleToTrash(ids: List<Long>, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)

    @Query("UPDATE documents SET deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restoreMultipleFromTrash(ids: List<Long>)

    @Query("SELECT * FROM documents WHERE deletedAt IS NOT NULL AND deletedAt < :cutoffTimestamp")
    suspend fun getExpiredTrashDocuments(cutoffTimestamp: Long): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE deletedAt IS NOT NULL")
    suspend fun getAllTrashDocumentsList(): List<DocumentEntity>

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("DELETE FROM documents WHERE id IN (:ids)")
    suspend fun deleteDocumentsByIds(ids: List<Long>)

    @Query("DELETE FROM documents WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("UPDATE documents SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun moveDocumentsToFolder(ids: List<Long>, folderId: Long?)

    @Query("UPDATE documents SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolderForDocuments(folderId: Long)

    @Query("UPDATE documents SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    // Pages
    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    fun getPagesForDocument(documentId: Long): Flow<List<DocumentPageEntity>>

    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getPagesListForDocument(documentId: Long): List<DocumentPageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: DocumentPageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<DocumentPageEntity>): List<Long>

    @Update
    suspend fun updatePage(page: DocumentPageEntity)

    @Query("DELETE FROM document_pages WHERE id = :pageId")
    suspend fun deletePageById(pageId: Long)

    @Query("DELETE FROM document_pages WHERE documentId = :documentId")
    suspend fun deletePagesByDocumentId(documentId: Long)
}

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders WHERE isArchived = 0 ORDER BY name ASC")
    fun getActiveFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE isArchived = 1 ORDER BY name ASC")
    fun getArchivedFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Long): FolderEntity?

    @Query("UPDATE folders SET name = :name, colorHex = :colorHex WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String, colorHex: String)

    @Query("UPDATE folders SET isArchived = :isArchived WHERE id = :id")
    suspend fun setFolderArchived(id: Long, isArchived: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: Long)
}

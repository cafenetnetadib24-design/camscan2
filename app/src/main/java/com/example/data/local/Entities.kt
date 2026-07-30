package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders"
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val colorHex: String = "#0052CC",
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)

@Entity(
    tableName = "documents",
    indices = [Index(value = ["folderId"])]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val folderId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pageCount: Int = 1,
    val thumbnailPath: String = "",
    val isFavorite: Boolean = false,
    val tags: String = "", // Comma-separated tags
    val deletedAt: Long? = null // Timestamp when moved to trash, null if active
)

@Entity(
    tableName = "document_pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentId"])]
)
data class DocumentPageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val imagePath: String,
    val originalImagePath: String,
    val filterType: String = "MAGIC_COLOR", // ORIGINAL, MAGIC_COLOR, BLACK_WHITE, GRAYSCALE
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val rotationDegrees: Int = 0,
    val brightness: Float = 0f, // -1f to 1f
    val contrast: Float = 1f,   // 0.5f to 2f
    val saturation: Float = 1f, // 0f to 2f
    val warmth: Float = 0f,     // -0.5f to 0.5f
    val sharpness: Float = 1f,  // 0.5f to 2f
    val ocrText: String = ""
)

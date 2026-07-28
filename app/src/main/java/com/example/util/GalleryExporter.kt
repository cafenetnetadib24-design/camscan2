package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object GalleryExporter {

    suspend fun saveImagePathsToGallery(
        context: Context,
        imagePaths: List<String>,
        baseName: String = "Scan"
    ): Int = withContext(Dispatchers.IO) {
        var savedCount = 0
        val sanitizedBase = baseName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "Scan" }

        imagePaths.forEachIndexed { index, path ->
            if (path.isNotBlank() && File(path).exists()) {
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap != null) {
                    val fileName = "${sanitizedBase}_page_${index + 1}_${System.currentTimeMillis()}.jpg"
                    if (saveBitmapToGallery(context, bitmap, fileName)) {
                        savedCount++
                    }
                    bitmap.recycle()
                }
            }
        }
        savedCount
    }

    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        fileName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/DocScanner")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    true
                } else false
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val docDir = File(imagesDir, "DocScanner").apply { if (!exists()) mkdirs() }
                val imageFile = File(docDir, fileName)
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

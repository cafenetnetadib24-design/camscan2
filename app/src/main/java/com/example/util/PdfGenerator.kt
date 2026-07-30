package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

enum class PdfQuality(val scaleRatio: Float, val compressQuality: Int) {
    HIGH(1.0f, 95),
    MEDIUM(0.75f, 85),
    LOW(0.5f, 70)
}

object PdfGenerator {

    // Standard A4 dimensions in points (72 points = 1 inch, A4 = 595 x 842 points)
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842

    private fun decodeSampledBitmapFromFile(path: String, maxDimension: Int = 1800): Bitmap? {
        val file = File(path)
        if (!file.exists() || !file.canRead() || file.length() == 0L) return null
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            
            val maxDim = maxOf(options.outWidth, options.outHeight)
            var sampleSize = 1
            if (maxDim > maxDimension) {
                sampleSize = maxDim / maxDimension
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(path, decodeOptions)
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    fun generatePdfFromPagePaths(
        context: Context,
        pageImagePaths: List<String>,
        pdfTitle: String,
        quality: PdfQuality = PdfQuality.HIGH,
        watermarkText: String? = null,
        addPageNumbers: Boolean = true,
        password: String? = null
    ): File? {
        val validPaths = pageImagePaths.filter { File(it).exists() && File(it).length() > 0 }
        if (validPaths.isEmpty()) return null

        val pdfDocument = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.GRAY
            textSize = 10f
            textAlign = Paint.Align.CENTER
        }
        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(50, 180, 180, 180)
            textSize = 42f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        try {
            val totalPages = validPaths.size
            var validPageCount = 0

            validPaths.forEachIndexed { index, path ->
                val maxDim = when (quality) {
                    PdfQuality.HIGH -> 2048
                    PdfQuality.MEDIUM -> 1440
                    PdfQuality.LOW -> 1024
                }
                val originalBitmap = decodeSampledBitmapFromFile(path, maxDimension = maxDim) ?: return@forEachIndexed

                try {
                    // Scale for selected quality if needed
                    val scaledWidth = (originalBitmap.width * quality.scaleRatio).toInt().coerceAtLeast(100)
                    val scaledHeight = (originalBitmap.height * quality.scaleRatio).toInt().coerceAtLeast(100)
                    
                    val workingBitmap = if (quality != PdfQuality.HIGH && (scaledWidth != originalBitmap.width || scaledHeight != originalBitmap.height)) {
                        Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
                    } else {
                        originalBitmap
                    }

                    // Page dimension matches A4 standard
                    val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, validPageCount + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    // Calculate aspect fill / fit rect on A4 page with margins
                    val margin = 20
                    val targetWidth = A4_WIDTH - (margin * 2)
                    val targetHeight = A4_HEIGHT - (margin * 2)

                    val imgRatio = workingBitmap.width.toFloat() / workingBitmap.height.toFloat().coerceAtLeast(1f)
                    val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()

                    val drawRect = if (imgRatio > targetRatio) {
                        val drawH = (targetWidth / imgRatio).toInt()
                        val top = margin + (targetHeight - drawH) / 2
                        Rect(margin, top, margin + targetWidth, top + drawH)
                    } else {
                        val drawW = (targetHeight * imgRatio).toInt()
                        val left = margin + (targetWidth - drawW) / 2
                        Rect(left, margin, left + drawW, margin + targetHeight)
                    }

                    canvas.drawBitmap(workingBitmap, null, drawRect, paint)

                    // Optional Watermark
                    if (!watermarkText.isNullOrBlank()) {
                        canvas.save()
                        canvas.rotate(-35f, A4_WIDTH / 2f, A4_HEIGHT / 2f)
                        canvas.drawText(watermarkText.uppercase(), A4_WIDTH / 2f, A4_HEIGHT / 2f, watermarkPaint)
                        canvas.restore()
                    }

                    // Optional Page Numbers
                    if (addPageNumbers) {
                        val pageNumStr = "Page ${validPageCount + 1} of $totalPages"
                        canvas.drawText(pageNumStr, A4_WIDTH / 2f, A4_HEIGHT - 12f, textPaint)
                    }

                    pdfDocument.finishPage(page)
                    validPageCount++

                    if (workingBitmap != originalBitmap) {
                        workingBitmap.recycle()
                    }
                    originalBitmap.recycle()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (validPageCount == 0) {
                return null
            }

            // Save PDF to internal files directory under 'exported_pdfs'
            val pdfDir = File(context.filesDir, "exported_pdfs").apply { if (!exists()) mkdirs() }
            val sanitizedTitle = pdfTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Scan_Document" }
            val pdfFile = File(pdfDir, "${sanitizedTitle}_${System.currentTimeMillis()}.pdf")

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }

            if (!password.isNullOrBlank()) {
                val encFile = File(pdfDir, "enc_${sanitizedTitle}_${System.currentTimeMillis()}.pdf")
                val success = PdfEncryptor.encryptPdfFile(pdfFile, encFile, password.trim())
                if (success && encFile.exists() && encFile.length() > 0) {
                    pdfFile.delete()
                    return encFile
                }
            }

            return pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try {
                pdfDocument.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getShareUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun sharePdfFile(context: Context, pdfFile: File) {
        try {
            if (!pdfFile.exists() || pdfFile.length() == 0L) {
                android.widget.Toast.makeText(context, "فایل PDF یافت نشد یا خالی است", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            val uri = getShareUriForFile(context, pdfFile)
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newRawUri("PDF Document", uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = android.content.Intent.createChooser(shareIntent, "اشتراک‌گذاری فایل PDF").apply {
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Grant URI permission to all potential targets
            val resInfoList = context.packageManager.queryIntentActivities(chooser, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(
                context,
                "خطا در اشتراک‌گذاری: ${e.localizedMessage ?: "برنامه‌ای برای ارسال PDF یافت نشد"}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    fun savePdfToDeviceStorage(context: Context, pdfFile: File, title: String): Boolean {
        return try {
            val sanitized = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Document" }
            val fileName = "${sanitized}_${System.currentTimeMillis()}.pdf"

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/DocScanner")
                }
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        pdfFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    true
                } else false
            } else {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val docDir = File(downloadsDir, "DocScanner").apply { if (!exists()) mkdirs() }
                val targetFile = File(docDir, fileName)
                pdfFile.copyTo(targetFile, overwrite = true)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

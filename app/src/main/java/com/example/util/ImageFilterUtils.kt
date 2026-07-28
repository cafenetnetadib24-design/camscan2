package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

enum class ScanFilter {
    ORIGINAL,
    MAGIC_COLOR,
    BLACK_WHITE,
    GRAYSCALE,
    INVERTED
}

object ImageFilterUtils {

    fun loadBitmapFromFile(filePath: String): Bitmap? {
        if (filePath.isEmpty() || !File(filePath).exists()) return null
        return try {
            BitmapFactory.decodeFile(filePath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadSafeBitmapFromUri(context: Context, uri: android.net.Uri, maxDimension: Int = 2048): Bitmap? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    val size = info.size
                    val maxDim = maxOf(size.width, size.height)
                    if (maxDim > maxDimension) {
                        val sample = maxDim / maxDimension
                        decoder.setTargetSampleSize(sample.coerceAtLeast(1))
                    }
                }
            } else {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()

                var sampleSize = 1
                val maxDim = maxOf(options.outWidth, options.outHeight)
                if (maxDim > maxDimension) {
                    sampleSize = maxDim / maxDimension
                }

                val decodeStream = context.contentResolver.openInputStream(uri) ?: return null
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize.coerceAtLeast(1)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bmp = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
                decodeStream.close()
                bmp
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun applyFilterAndAdjustments(
        sourceBitmap: Bitmap,
        filter: ScanFilter,
        brightness: Float = 0f, // -1f to 1f
        contrast: Float = 1f,   // 0.5f to 2f
        rotationDegrees: Int = 0,
        cropRect: RectF = RectF(0f, 0f, 1f, 1f),
        topLeft: android.graphics.PointF = android.graphics.PointF(cropRect.left, cropRect.top),
        topRight: android.graphics.PointF = android.graphics.PointF(cropRect.right, cropRect.top),
        bottomRight: android.graphics.PointF = android.graphics.PointF(cropRect.right, cropRect.bottom),
        bottomLeft: android.graphics.PointF = android.graphics.PointF(cropRect.left, cropRect.bottom)
    ): Bitmap {
        // Step 1: Rotate base bitmap to match active rotation state
        val baseBitmap = if (rotationDegrees % 360 != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, matrix, true)
        } else {
            sourceBitmap
        }

        // Step 2: 4-Point Homography Perspective Warp Transformation
        var workingBitmap = DocumentEdgeDetector.applyPerspectiveCorrection(
            baseBitmap, topLeft, topRight, bottomRight, bottomLeft
        )

        // Step 3: Color Filter & Brightness/Contrast
        val result = Bitmap.createBitmap(
            workingBitmap.width,
            workingBitmap.height,
            workingBitmap.config ?: Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val cm = ColorMatrix()

        when (filter) {
            ScanFilter.ORIGINAL -> {
                cm.reset()
            }
            ScanFilter.MAGIC_COLOR -> {
                // Boost contrast & saturation to make scanned text pop
                val satMatrix = ColorMatrix().apply { setSaturation(1.25f) }
                val magicContrastMatrix = createContrastBrightnessMatrix(contrast = 1.35f, brightness = 15f)
                satMatrix.postConcat(magicContrastMatrix)
                cm.set(satMatrix)
            }
            ScanFilter.GRAYSCALE -> {
                cm.setSaturation(0f)
            }
            ScanFilter.INVERTED -> {
                val invertedBW = applyHighQualityInvertedFilter(workingBitmap)
                if (brightness != 0f || contrast != 1f) {
                    val userCm = createContrastBrightnessMatrix(contrast, brightness * 100f)
                    paint.colorFilter = ColorMatrixColorFilter(userCm)
                    canvas.drawBitmap(invertedBW, 0f, 0f, paint)
                } else {
                    canvas.drawBitmap(invertedBW, 0f, 0f, paint)
                }
                return result
            }
            ScanFilter.BLACK_WHITE -> {
                // High precision engineered document scanner binarization filter (Bradley-Wellner adaptive thresholding)
                val printReadyBW = applyHighQualityPrintBWFilter(workingBitmap)
                if (brightness != 0f || contrast != 1f) {
                    val userCm = createContrastBrightnessMatrix(contrast, brightness * 100f)
                    paint.colorFilter = ColorMatrixColorFilter(userCm)
                    canvas.drawBitmap(printReadyBW, 0f, 0f, paint)
                } else {
                    canvas.drawBitmap(printReadyBW, 0f, 0f, paint)
                }
                return result
            }
        }

        // Apply user explicit brightness & contrast on top
        if (brightness != 0f || contrast != 1f) {
            val userCm = createContrastBrightnessMatrix(contrast, brightness * 100f)
            cm.postConcat(userCm)
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(workingBitmap, 0f, 0f, paint)

        return result
    }

    /**
     * High precision adaptive document binarization & contrast enhancement engine.
     * Uses local adaptive thresholding (Bradley-Wellner) to eliminate page shadows,
     * background noise, and paper discoloration, producing ultra-high contrast,
     * crisp dark text on 100% pure white background for professional document printing.
     */
    fun applyHighQualityPrintBWFilter(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = IntArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000
        }

        val windowSize = (width.coerceAtLeast(height) / 18).coerceAtLeast(8)
        val halfWindow = windowSize / 2

        val integral = LongArray(width * height)
        for (y in 0 until height) {
            var sum = 0L
            val yOffset = y * width
            for (x in 0 until width) {
                sum += gray[yOffset + x]
                if (y == 0) {
                    integral[x] = sum
                } else {
                    integral[yOffset + x] = integral[(y - 1) * width + x] + sum
                }
            }
        }

        val resultPixels = IntArray(width * height)
        val t = 0.11f // Threshold factor for document text detection

        for (y in 0 until height) {
            val y1 = (y - halfWindow).coerceAtLeast(0)
            val y2 = (y + halfWindow).coerceAtMost(height - 1)
            val y1Offset = y1 * width
            val y2Offset = y2 * width
            val curYOffset = y * width

            for (x in 0 until width) {
                val x1 = (x - halfWindow).coerceAtLeast(0)
                val x2 = (x + halfWindow).coerceAtMost(width - 1)

                val count = (x2 - x1 + 1) * (y2 - y1 + 1)

                val sum = integral[y2Offset + x2] -
                        (if (x1 > 0) integral[y2Offset + (x1 - 1)] else 0L) -
                        (if (y1 > 0) integral[(y1 - 1) * width + x2] else 0L) +
                        (if (x1 > 0 && y1 > 0) integral[(y1 - 1) * width + (x1 - 1)] else 0L)

                val mean = (sum / count).toInt()
                val pixelVal = gray[curYOffset + x]

                // Check if local pixel is darker than local mean background
                if (pixelVal < mean * (1.0f - t)) {
                    // Deep dark ink with smooth contrast curve for max legibility
                    val darknessRatio = (pixelVal.toFloat() / (mean.toFloat().coerceAtLeast(1f))).coerceIn(0f, 1f)
                    val ink = (darknessRatio * 45f).toInt().coerceIn(0, 45)
                    resultPixels[curYOffset + x] = (0xFF shl 24) or (ink shl 16) or (ink shl 8) or ink
                } else {
                    // Pure white background suitable for crisp printing
                    resultPixels[curYOffset + x] = 0xFFFFFFFF.toInt()
                }
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * High precision inverted document binarization engine.
     * Takes dark background or negative documents (or inverted light documents)
     * and guarantees white background and dark/black text.
     */
    fun applyHighQualityInvertedFilter(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val lum = IntArray(width * height)
        var totalLum = 0L
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val l = (r * 299 + g * 587 + b * 114) / 1000
            lum[i] = l
            totalLum += l
        }
        val avgLum = totalLum / pixels.size.coerceAtLeast(1)

        val gray = IntArray(width * height)
        if (avgLum < 128) {
            // Dark background document (e.g. white text on black background/screen): invert luminance
            for (i in pixels.indices) {
                gray[i] = 255 - lum[i]
            }
        } else {
            // Light background document: invert luminance so background becomes light in mapped space
            for (i in pixels.indices) {
                gray[i] = 255 - lum[i]
            }
        }

        val windowSize = (width.coerceAtLeast(height) / 18).coerceAtLeast(8)
        val halfWindow = windowSize / 2

        val integral = LongArray(width * height)
        for (y in 0 until height) {
            var sum = 0L
            val yOffset = y * width
            for (x in 0 until width) {
                sum += gray[yOffset + x]
                if (y == 0) {
                    integral[x] = sum
                } else {
                    integral[yOffset + x] = integral[(y - 1) * width + x] + sum
                }
            }
        }

        val resultPixels = IntArray(width * height)
        val t = 0.11f

        for (y in 0 until height) {
            val y1 = (y - halfWindow).coerceAtLeast(0)
            val y2 = (y + halfWindow).coerceAtMost(height - 1)
            val y1Offset = y1 * width
            val y2Offset = y2 * width
            val curYOffset = y * width

            for (x in 0 until width) {
                val x1 = (x - halfWindow).coerceAtLeast(0)
                val x2 = (x + halfWindow).coerceAtMost(width - 1)

                val count = (x2 - x1 + 1) * (y2 - y1 + 1)

                val sum = integral[y2Offset + x2] -
                        (if (x1 > 0) integral[y2Offset + (x1 - 1)] else 0L) -
                        (if (y1 > 0) integral[(y1 - 1) * width + x2] else 0L) +
                        (if (x1 > 0 && y1 > 0) integral[(y1 - 1) * width + (x1 - 1)] else 0L)

                val mean = (sum / count).toInt()
                val pixelVal = gray[curYOffset + x]

                if (avgLum < 128) {
                    if (pixelVal < mean * (1.0f - t)) {
                        val darknessRatio = (pixelVal.toFloat() / (mean.toFloat().coerceAtLeast(1f))).coerceIn(0f, 1f)
                        val ink = (darknessRatio * 45f).toInt().coerceIn(0, 45)
                        resultPixels[curYOffset + x] = (0xFF shl 24) or (ink shl 16) or (ink shl 8) or ink
                    } else {
                        resultPixels[curYOffset + x] = 0xFFFFFFFF.toInt()
                    }
                } else {
                    if (pixelVal > mean * (1.0f + t)) {
                        val ink = 15
                        resultPixels[curYOffset + x] = (0xFF shl 24) or (ink shl 16) or (ink shl 8) or ink
                    } else {
                        resultPixels[curYOffset + x] = 0xFFFFFFFF.toInt()
                    }
                }
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun cropBitmapNormalized(bitmap: Bitmap, rect: RectF): Bitmap {
        val left = (rect.left.coerceIn(0f, 1f) * bitmap.width).toInt()
        val top = (rect.top.coerceIn(0f, 1f) * bitmap.height).toInt()
        val right = (rect.right.coerceIn(0f, 1f) * bitmap.width).toInt()
        val bottom = (rect.bottom.coerceIn(0f, 1f) * bitmap.height).toInt()

        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)

        val safeWidth = width.coerceAtMost(bitmap.width - left)
        val safeHeight = height.coerceAtMost(bitmap.height - top)

        return Bitmap.createBitmap(bitmap, left, top, safeWidth, safeHeight)
    }

    private fun createContrastBrightnessMatrix(contrast: Float, brightness: Float): ColorMatrix {
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f + brightness

        val matrix = floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
        return ColorMatrix(matrix)
    }

    fun saveBitmapToAppStorage(context: Context, bitmap: Bitmap, prefix: String = "doc_page"): String {
        val dir = File(context.filesDir, "scanned_pages").apply { if (!exists()) mkdirs() }
        val fileName = "${prefix}_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
        val file = File(dir, fileName)

        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return file.absolutePath
    }
}

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
    DESKTOP_COLOR,
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

    fun createAdjustmentColorMatrix(
        contrast: Float = 1f,
        brightness: Float = 0f, // -1f to 1f
        saturation: Float = 1f, // 0f to 2f
        warmth: Float = 0f,     // -0.5f to 0.5f
        sharpness: Float = 1f   // 0.5f to 2f
    ): ColorMatrix {
        val matrix = ColorMatrix()

        // 1. Saturation
        if (saturation != 1f) {
            matrix.setSaturation(saturation)
        }

        // 2. Contrast & Brightness
        if (contrast != 1f || brightness != 0f) {
            val cCm = createContrastBrightnessMatrix(contrast, brightness * 100f)
            matrix.postConcat(cCm)
        }

        // 3. Warmth (Temperature)
        if (warmth != 0f) {
            val rScale = (1f + warmth * 0.25f).coerceIn(0.5f, 1.8f)
            val gScale = (1f + warmth * 0.08f).coerceIn(0.5f, 1.8f)
            val bScale = (1f - warmth * 0.25f).coerceIn(0.5f, 1.8f)
            val warmthCm = ColorMatrix(
                floatArrayOf(
                    rScale, 0f, 0f, 0f, 0f,
                    0f, gScale, 0f, 0f, 0f,
                    0f, 0f, bScale, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            matrix.postConcat(warmthCm)
        }

        // 4. Sharpness / Clarity (Midtone Boost)
        if (sharpness != 1f) {
            val sCm = createContrastBrightnessMatrix(sharpness, 0f)
            matrix.postConcat(sCm)
        }

        return matrix
    }

    fun applyFilterAndAdjustments(
        sourceBitmap: Bitmap,
        filter: ScanFilter,
        brightness: Float = 0f, // -1f to 1f
        contrast: Float = 1f,   // 0.5f to 2f
        saturation: Float = 1f, // 0f to 2f
        warmth: Float = 0f,     // -0.5f to 0.5f
        sharpness: Float = 1f,  // 0.5f to 2f
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

        // Check if points represent full frame corners
        val isFullFrame = (
            topLeft.x <= 0.02f && topLeft.y <= 0.02f &&
            topRight.x >= 0.98f && topRight.y <= 0.02f &&
            bottomRight.x >= 0.98f && bottomRight.y >= 0.98f &&
            bottomLeft.x <= 0.02f && bottomLeft.y >= 0.98f
        )

        // Step 2: 4-Point Homography Perspective Warp Transformation
        var workingBitmap = if (isFullFrame) {
            baseBitmap
        } else {
            DocumentEdgeDetector.applyPerspectiveCorrection(
                baseBitmap, topLeft, topRight, bottomRight, bottomLeft
            )
        }

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
            ScanFilter.DESKTOP_COLOR -> {
                val desktopColor = applyHighPrecisionColorScanFilter(workingBitmap)
                val userCm = createAdjustmentColorMatrix(contrast, brightness, saturation, warmth, sharpness)
                paint.colorFilter = ColorMatrixColorFilter(userCm)
                canvas.drawBitmap(desktopColor, 0f, 0f, paint)
                return result
            }
            ScanFilter.GRAYSCALE -> {
                cm.setSaturation(0f)
            }
            ScanFilter.INVERTED -> {
                val invertedBW = applyHighQualityInvertedFilter(workingBitmap)
                val userCm = createAdjustmentColorMatrix(contrast, brightness, saturation, warmth, sharpness)
                paint.colorFilter = ColorMatrixColorFilter(userCm)
                canvas.drawBitmap(invertedBW, 0f, 0f, paint)
                return result
            }
            ScanFilter.BLACK_WHITE -> {
                val printReadyBW = applyHighQualityPrintBWFilter(workingBitmap)
                val userCm = createAdjustmentColorMatrix(contrast, brightness, saturation, warmth, sharpness)
                paint.colorFilter = ColorMatrixColorFilter(userCm)
                canvas.drawBitmap(printReadyBW, 0f, 0f, paint)
                return result
            }
        }

        // Apply user explicit adjustments on top
        val userCm = createAdjustmentColorMatrix(contrast, brightness, saturation, warmth, sharpness)
        cm.postConcat(userCm)

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
        return try {
            val origW = src.width
            val origH = src.height

            val maxDim = 1200
            val scaleFactor = if (maxOf(origW, origH) > maxDim) {
                maxDim.toFloat() / maxOf(origW, origH).toFloat()
            } else 1.0f

            val width = (origW * scaleFactor).toInt().coerceAtLeast(1)
            val height = (origH * scaleFactor).toInt().coerceAtLeast(1)

            val procBitmap = if (scaleFactor < 1.0f) {
                Bitmap.createScaledBitmap(src, width, height, true)
            } else {
                src
            }

            val pixels = IntArray(width * height)
            procBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

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

                    if (pixelVal < mean * (1.0f - t)) {
                        val darknessRatio = (pixelVal.toFloat() / (mean.toFloat().coerceAtLeast(1f))).coerceIn(0f, 1f)
                        val ink = (darknessRatio * 45f).toInt().coerceIn(0, 45)
                        resultPixels[curYOffset + x] = (0xFF shl 24) or (ink shl 16) or (ink shl 8) or ink
                    } else {
                        resultPixels[curYOffset + x] = 0xFFFFFFFF.toInt()
                    }
                }
            }

            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            result.setPixels(resultPixels, 0, width, 0, 0, width, height)
            if (procBitmap != src) {
                procBitmap.recycle()
            }

            if (width != origW || height != origH) {
                val scaledBack = Bitmap.createScaledBitmap(result, origW, origH, true)
                result.recycle()
                scaledBack
            } else {
                result
            }
        } catch (e: Exception) {
            e.printStackTrace()
            src
        }
    }

    /**
     * High precision inverted document binarization engine.
     * Takes dark background or negative documents (or inverted light documents)
     * and guarantees white background and dark/black text.
     */
    fun applyHighQualityInvertedFilter(src: Bitmap): Bitmap {
        return try {
            val origW = src.width
            val origH = src.height

            val maxDim = 1200
            val scaleFactor = if (maxOf(origW, origH) > maxDim) {
                maxDim.toFloat() / maxOf(origW, origH).toFloat()
            } else 1.0f

            val width = (origW * scaleFactor).toInt().coerceAtLeast(1)
            val height = (origH * scaleFactor).toInt().coerceAtLeast(1)

            val procBitmap = if (scaleFactor < 1.0f) {
                Bitmap.createScaledBitmap(src, width, height, true)
            } else {
                src
            }

            val pixels = IntArray(width * height)
            procBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

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
            for (i in pixels.indices) {
                gray[i] = 255 - lum[i]
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
            if (procBitmap != src) {
                procBitmap.recycle()
            }

            if (width != origW || height != origH) {
                val scaledBack = Bitmap.createScaledBitmap(result, origW, origH, true)
                result.recycle()
                scaledBack
            } else {
                result
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Safe fallback: invert luminance using ColorMatrix without crash
            val res = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
            val canvas = Canvas(res)
            val paint = Paint()
            val cm = ColorMatrix().apply {
                setSaturation(0f)
                postConcat(ColorMatrix(floatArrayOf(
                    -1f,  0f,  0f, 0f, 255f,
                     0f, -1f,  0f, 0f, 255f,
                     0f,  0f, -1f, 0f, 255f,
                     0f,  0f,  0f, 1f,   0f
                )))
            }
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(src, 0f, 0f, paint)
            res
        }
    }

    /**
     * High-precision Desktop Flatbed Scanner Color Filter engine (اسکنر رنگی رومیزی).
     * Removes paper shadows and uneven illumination while preserving vibrant colors,
     * stamps, signatures, and photos with zero bleaching or overexposure.
     */
    fun applyHighPrecisionColorScanFilter(src: Bitmap): Bitmap {
        return try {
            val origW = src.width
            val origH = src.height

            // Max processing dimension for speed and stability
            val maxProcessingDim = 1600
            val scaleFactor = if (maxOf(origW, origH) > maxProcessingDim) {
                maxProcessingDim.toFloat() / maxOf(origW, origH).toFloat()
            } else 1.0f

            val width = (origW * scaleFactor).toInt().coerceAtLeast(1)
            val height = (origH * scaleFactor).toInt().coerceAtLeast(1)

            val procBitmap = if (scaleFactor < 1.0f) {
                Bitmap.createScaledBitmap(src, width, height, true)
            } else {
                src
            }

            // Downsampled illumination grid to estimate ambient paper lighting
            val gridW = (width / 16).coerceIn(32, 128)
            val gridH = (height / 16).coerceIn(32, 128)

            val small = Bitmap.createScaledBitmap(procBitmap, gridW, gridH, true)
            val smallPixels = IntArray(gridW * gridH)
            small.getPixels(smallPixels, 0, gridW, 0, 0, gridW, gridH)
            if (small != procBitmap && small != src) {
                small.recycle()
            }

            // Spatial 5x5 box blur on grid for smooth background illumination map
            val blurR = IntArray(gridW * gridH)
            val blurG = IntArray(gridW * gridH)
            val blurB = IntArray(gridW * gridH)

            val rRadius = 2
            for (y in 0 until gridH) {
                val yMin = (y - rRadius).coerceAtLeast(0)
                val yMax = (y + rRadius).coerceAtMost(gridH - 1)
                for (x in 0 until gridW) {
                    val xMin = (x - rRadius).coerceAtLeast(0)
                    val xMax = (x + rRadius).coerceAtMost(gridW - 1)

                    var sumR = 0
                    var sumG = 0
                    var sumB = 0
                    var count = 0

                    for (ny in yMin..yMax) {
                        val rowOffset = ny * gridW
                        for (nx in xMin..xMax) {
                            val p = smallPixels[rowOffset + nx]
                            sumR += (p shr 16) and 0xFF
                            sumG += (p shr 8) and 0xFF
                            sumB += p and 0xFF
                            count++
                        }
                    }

                    val idx = y * gridW + x
                    blurR[idx] = (sumR / count).coerceAtLeast(40)
                    blurG[idx] = (sumG / count).coerceAtLeast(40)
                    blurB[idx] = (sumB / count).coerceAtLeast(40)
                }
            }

            val srcPixels = IntArray(width * height)
            procBitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)
            val outPixels = IntArray(width * height)

            val scaleX = gridW.toFloat() / width.toFloat()
            val scaleY = gridH.toFloat() / height.toFloat()

            for (y in 0 until height) {
                val gy = (y * scaleY).toInt().coerceIn(0, gridH - 1)
                val gOffset = gy * gridW
                val yOffset = y * width

                for (x in 0 until width) {
                    val gx = (x * scaleX).toInt().coerceIn(0, gridW - 1)
                    val gIdx = gOffset + gx

                    val bgR = blurR[gIdx]
                    val bgG = blurG[gIdx]
                    val bgB = blurB[gIdx]

                    val curIdx = yOffset + x
                    val p = srcPixels[curIdx]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF

                    val luma = r * 0.299f + g * 0.587f + b * 0.114f
                    val maxC = maxOf(r, maxOf(g, b))
                    val minC = minOf(r, minOf(g, b))
                    val chroma = maxC - minC

                    val bgLuma = bgR * 0.299f + bgG * 0.587f + bgB * 0.114f

                    // Illumination gain factor capped gently between 1.0 and 1.30 to avoid overexposure
                    val gain = (245f / bgLuma.coerceAtLeast(100f)).coerceIn(1.0f, 1.30f)

                    var outR: Float
                    var outG: Float
                    var outB: Float

                    if (chroma < 26 && luma > 155f && luma >= bgLuma * 0.75f) {
                        // Paper background area (low saturation & bright)
                        val bgRatio = ((luma - 155f) / 90f).coerceIn(0f, 1f)
                        val rNorm = r * gain
                        val gNorm = g * gain
                        val bNorm = b * gain

                        outR = rNorm + (252f - rNorm) * bgRatio
                        outG = gNorm + (252f - gNorm) * bgRatio
                        outB = bNorm + (252f - bNorm) * bgRatio
                    } else if (luma < 120f) {
                        // Dark text / ink / line drawing
                        val contrast = 1.25f
                        val rNorm = r * gain
                        val gNorm = g * gain
                        val bNorm = b * gain

                        outR = (((rNorm / 255f - 0.45f) * contrast + 0.45f) * 255f)
                        outG = (((gNorm / 255f - 0.45f) * contrast + 0.45f) * 255f)
                        outB = (((bNorm / 255f - 0.45f) * contrast + 0.45f) * 255f)
                    } else {
                        // Colored content (photos, colorful logos, stamps, signatures)
                        val rNorm = (r * gain).coerceIn(0f, 255f)
                        val gNorm = (g * gain).coerceIn(0f, 255f)
                        val bNorm = (b * gain).coerceIn(0f, 255f)

                        val normAvg = (rNorm + gNorm + bNorm) / 3f
                        val satFactor = 1.25f
                        val satR = normAvg + (rNorm - normAvg) * satFactor
                        val satG = normAvg + (gNorm - normAvg) * satFactor
                        val satB = normAvg + (bNorm - normAvg) * satFactor

                        val contrastFactor = 1.15f
                        outR = ((satR / 255f - 0.5f) * contrastFactor + 0.5f) * 255f
                        outG = ((satG / 255f - 0.5f) * contrastFactor + 0.5f) * 255f
                        outB = ((satB / 255f - 0.5f) * contrastFactor + 0.5f) * 255f
                    }

                    val finalR = outR.toInt().coerceIn(0, 255)
                    val finalG = outG.toInt().coerceIn(0, 255)
                    val finalB = outB.toInt().coerceIn(0, 255)

                    outPixels[curIdx] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                }
            }

            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            if (procBitmap != src) {
                procBitmap.recycle()
            }

            if (width != origW || height != origH) {
                val scaledBack = Bitmap.createScaledBitmap(result, origW, origH, true)
                result.recycle()
                scaledBack
            } else {
                result
            }
        } catch (e: Exception) {
            e.printStackTrace()
            src
        }
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

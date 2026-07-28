package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Result of document boundary detection containing normalized quadrilateral corner coordinates (0.0f .. 1.0f).
 */
data class DetectedDocumentBounds(
    val rect: RectF = RectF(0.05f, 0.05f, 0.95f, 0.95f),
    val topLeft: PointF = PointF(0.05f, 0.05f),
    val topRight: PointF = PointF(0.95f, 0.05f),
    val bottomRight: PointF = PointF(0.95f, 0.95f),
    val bottomLeft: PointF = PointF(0.05f, 0.95f),
    val confidence: Float = 0.85f,
    val isDetected: Boolean = true
)

object DocumentEdgeDetector {

    /**
     * Precision document edge and corner detector using multi-stage Sobel gradient analysis,
     * Gaussian noise filtering, and quadrant convex corner estimation.
     */
    fun detectDocumentBoundaries(
        sourceBitmap: Bitmap,
        maxProcessingDimension: Int = 400
    ): DetectedDocumentBounds {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        val fallbackBounds = DetectedDocumentBounds(
            rect = RectF(0.05f, 0.05f, 0.95f, 0.95f),
            topLeft = PointF(0.05f, 0.05f),
            topRight = PointF(0.95f, 0.05f),
            bottomRight = PointF(0.95f, 0.95f),
            bottomLeft = PointF(0.05f, 0.95f),
            confidence = 0.5f,
            isDetected = false
        )

        if (width <= 0 || height <= 0) {
            return fallbackBounds
        }

        val maxDim = max(width, height)
        val scale = if (maxDim > maxProcessingDimension) {
            maxProcessingDimension.toFloat() / maxDim.toFloat()
        } else {
            1.0f
        }

        val procWidth = (width * scale).toInt().coerceAtLeast(20)
        val procHeight = (height * scale).toInt().coerceAtLeast(20)

        val scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, procWidth, procHeight, true)
        val pixels = IntArray(procWidth * procHeight)
        scaledBitmap.getPixels(pixels, 0, procWidth, 0, 0, procWidth, procHeight)
        if (scaledBitmap != sourceBitmap) {
            scaledBitmap.recycle()
        }

        // Convert to grayscale with standard Rec.601 weights
        val gray = FloatArray(procWidth * procHeight)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // Apply 3x3 Gaussian Blur filter to smooth text/textures inside paper
        val blurred = FloatArray(procWidth * procHeight)
        val kernel = floatArrayOf(
            1f/16f, 2f/16f, 1f/16f,
            2f/16f, 4f/16f, 2f/16f,
            1f/16f, 2f/16f, 1f/16f
        )
        for (y in 1 until procHeight - 1) {
            for (x in 1 until procWidth - 1) {
                var sum = 0f
                var kIdx = 0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        sum += gray[(y + ky) * procWidth + (x + kx)] * kernel[kIdx++]
                    }
                }
                blurred[y * procWidth + x] = sum
            }
        }

        // Sobel Gradient Magnitude Calculation
        val edges = FloatArray(procWidth * procHeight)
        var maxEdgeValue = 0f
        var totalEdgeSum = 0f
        var edgeCount = 0

        for (y in 1 until procHeight - 1) {
            for (x in 1 until procWidth - 1) {
                val idx = y * procWidth + x

                val gx = (
                    -1f * blurred[idx - procWidth - 1] + 1f * blurred[idx - procWidth + 1]
                    -2f * blurred[idx - 1]             + 2f * blurred[idx + 1]
                    -1f * blurred[idx + procWidth - 1] + 1f * blurred[idx + procWidth + 1]
                )

                val gy = (
                    -1f * blurred[idx - procWidth - 1] - 2f * blurred[idx - procWidth] - 1f * blurred[idx - procWidth + 1]
                    +1f * blurred[idx + procWidth - 1] + 2f * blurred[idx + procWidth] + 1f * blurred[idx + procWidth + 1]
                )

                val mag = hypot(gx, gy)
                edges[idx] = mag
                if (mag > maxEdgeValue) {
                    maxEdgeValue = mag
                }
                totalEdgeSum += mag
                edgeCount++
            }
        }

        if (maxEdgeValue == 0f || edgeCount == 0) {
            return fallbackBounds
        }

        val avgEdgeValue = totalEdgeSum / edgeCount
        val edgeThreshold = maxOf(maxEdgeValue * 0.15f, avgEdgeValue * 1.8f)

        // Ignore 2% outer border pixels to prevent camera frame border noise
        val borderX = (procWidth * 0.02f).toInt().coerceAtLeast(1)
        val borderY = (procHeight * 0.02f).toInt().coerceAtLeast(1)

        val centerX = procWidth / 2f
        val centerY = procHeight / 2f

        // Collect edge candidate points for each quadrant
        class PointWithMetric(val x: Int, val y: Int, val metric: Float)

        val tlPoints = mutableListOf<PointWithMetric>()
        val trPoints = mutableListOf<PointWithMetric>()
        val brPoints = mutableListOf<PointWithMetric>()
        val blPoints = mutableListOf<PointWithMetric>()

        for (y in borderY until procHeight - borderY) {
            for (x in borderX until procWidth - borderX) {
                val idx = y * procWidth + x
                if (edges[idx] >= edgeThreshold) {
                    val isLeft = x <= centerX
                    val isTop = y <= centerY

                    if (isLeft && isTop) {
                        // TL quadrant: minimize projection distance to (0,0)
                        val metric = x.toFloat() + y.toFloat()
                        tlPoints.add(PointWithMetric(x, y, metric))
                    } else if (!isLeft && isTop) {
                        // TR quadrant: minimize projection distance to (W,0)
                        val metric = (procWidth - x).toFloat() + y.toFloat()
                        trPoints.add(PointWithMetric(x, y, metric))
                    } else if (!isLeft && !isTop) {
                        // BR quadrant: minimize projection distance to (W,H)
                        val metric = (procWidth - x).toFloat() + (procHeight - y).toFloat()
                        brPoints.add(PointWithMetric(x, y, metric))
                    } else {
                        // BL quadrant: minimize projection distance to (0,H)
                        val metric = x.toFloat() + (procHeight - y).toFloat()
                        blPoints.add(PointWithMetric(x, y, metric))
                    }
                }
            }
        }

        val minPointsPerQuadrant = 5
        if (tlPoints.size < minPointsPerQuadrant || trPoints.size < minPointsPerQuadrant ||
            brPoints.size < minPointsPerQuadrant || blPoints.size < minPointsPerQuadrant) {
            return fallbackBounds
        }

        // Sort by metric to find outer corner candidates
        tlPoints.sortBy { it.metric }
        trPoints.sortBy { it.metric }
        brPoints.sortBy { it.metric }
        blPoints.sortBy { it.metric }

        // Take average of top 10 best corner candidate points in each quadrant for noise resistance
        fun calculateRobustCorner(points: List<PointWithMetric>): PointF {
            val sampleSize = minOf(10, points.size)
            var sumX = 0f
            var sumY = 0f
            for (i in 0 until sampleSize) {
                sumX += points[i].x
                sumY += points[i].y
            }
            return PointF(sumX / sampleSize, sumY / sampleSize)
        }

        val rawTL = calculateRobustCorner(tlPoints)
        val rawTR = calculateRobustCorner(trPoints)
        val rawBR = calculateRobustCorner(brPoints)
        val rawBL = calculateRobustCorner(blPoints)

        // Convert to normalized coordinates (0.0f .. 1.0f)
        val normTL = PointF((rawTL.x / procWidth).coerceIn(0.02f, 0.48f), (rawTL.y / procHeight).coerceIn(0.02f, 0.48f))
        val normTR = PointF((rawTR.x / procWidth).coerceIn(0.52f, 0.98f), (rawTR.y / procHeight).coerceIn(0.02f, 0.48f))
        val normBR = PointF((rawBR.x / procWidth).coerceIn(0.52f, 0.98f), (rawBR.y / procHeight).coerceIn(0.52f, 0.98f))
        val normBL = PointF((rawBL.x / procWidth).coerceIn(0.02f, 0.48f), (rawBL.y / procHeight).coerceIn(0.52f, 0.98f))

        // Validate sanity of detected quad shape
        val quadWidthTop = normTR.x - normTL.x
        val quadWidthBottom = normBR.x - normBL.x
        val quadHeightLeft = normBL.y - normTL.y
        val quadHeightRight = normBR.y - normTR.y

        if (quadWidthTop < 0.2f || quadWidthBottom < 0.2f || quadHeightLeft < 0.2f || quadHeightRight < 0.2f) {
            return fallbackBounds
        }

        val minX = minOf(normTL.x, normBL.x)
        val minY = minOf(normTL.y, normTR.y)
        val maxX = maxOf(normTR.x, normBR.x)
        val maxY = maxOf(normBL.y, normBR.y)
        val rect = RectF(minX, minY, maxX, maxY)

        return DetectedDocumentBounds(
            rect = rect,
            topLeft = normTL,
            topRight = normTR,
            bottomRight = normBR,
            bottomLeft = normBL,
            confidence = 0.95f,
            isDetected = true
        )
    }

    /**
     * Executes 4-point homography perspective warp transformation (setPolyToPoly)
     * to transform a 3D tilted camera shot into a perfectly flat 2D orthographic document.
     */
    fun applyPerspectiveCorrection(
        sourceBitmap: Bitmap,
        topLeft: PointF,
        topRight: PointF,
        bottomRight: PointF,
        bottomLeft: PointF
    ): Bitmap {
        val w = sourceBitmap.width.toFloat()
        val h = sourceBitmap.height.toFloat()

        val srcPoints = floatArrayOf(
            (topLeft.x.coerceIn(0f, 1f) * w), (topLeft.y.coerceIn(0f, 1f) * h),
            (topRight.x.coerceIn(0f, 1f) * w), (topRight.y.coerceIn(0f, 1f) * h),
            (bottomRight.x.coerceIn(0f, 1f) * w), (bottomRight.y.coerceIn(0f, 1f) * h),
            (bottomLeft.x.coerceIn(0f, 1f) * w), (bottomLeft.y.coerceIn(0f, 1f) * h)
        )

        val topW = hypot(srcPoints[2] - srcPoints[0], srcPoints[3] - srcPoints[1])
        val bottomW = hypot(srcPoints[4] - srcPoints[6], srcPoints[5] - srcPoints[7])
        val targetWidth = maxOf(topW, bottomW).coerceAtLeast(100f)

        val leftH = hypot(srcPoints[6] - srcPoints[0], srcPoints[7] - srcPoints[1])
        val rightH = hypot(srcPoints[4] - srcPoints[2], srcPoints[5] - srcPoints[3])
        val targetHeight = maxOf(leftH, rightH).coerceAtLeast(100f)

        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth, 0f,
            targetWidth, targetHeight,
            0f, targetHeight
        )

        val matrix = Matrix()
        val polySuccess = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        if (!polySuccess) {
            val minX = minOf(srcPoints[0], srcPoints[6]).toInt().coerceIn(0, sourceBitmap.width - 1)
            val minY = minOf(srcPoints[1], srcPoints[3]).toInt().coerceIn(0, sourceBitmap.height - 1)
            val maxX = maxOf(srcPoints[2], srcPoints[4]).toInt().coerceIn(minX + 1, sourceBitmap.width)
            val maxY = maxOf(srcPoints[5], srcPoints[7]).toInt().coerceIn(minY + 1, sourceBitmap.height)
            return Bitmap.createBitmap(sourceBitmap, minX, minY, maxX - minX, maxY - minY)
        }

        val outputBitmap = Bitmap.createBitmap(targetWidth.toInt(), targetHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(sourceBitmap, matrix, paint)

        return outputBitmap
    }

    /**
     * Automatically crops document based on edge detection results.
     */
    fun cropToDetectedEdges(sourceBitmap: Bitmap, bounds: DetectedDocumentBounds): Bitmap {
        return applyPerspectiveCorrection(
            sourceBitmap,
            bounds.topLeft,
            bounds.topRight,
            bounds.bottomRight,
            bounds.bottomLeft
        )
    }
}


package com.example.ui.camera

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

enum class FocusCondition {
    SHARP,
    BLURRY
}

enum class LightingCondition {
    GOOD,
    DARK,
    GLARE
}

class CameraFrameAnalyzer(
    private val onFrameAnalyzed: (
        focus: FocusCondition,
        lighting: LightingCondition,
        sharpnessScore: Float,
        avgBrightness: Float,
        isDetected: Boolean
    ) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalysisTime = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        // Throttle frame analysis to ~12-15 FPS to ensure smooth main thread rendering
        if (currentTime - lastAnalysisTime < 70) {
            imageProxy.close()
            return
        }
        lastAnalysisTime = currentTime

        try {
            val planes = imageProxy.planes
            if (planes.isEmpty()) {
                imageProxy.close()
                return
            }

            val yBuffer = planes[0].buffer
            val width = imageProxy.width
            val height = imageProxy.height
            val rowStride = planes[0].rowStride
            val pixelStride = planes[0].pixelStride

            val bytes = ByteArray(yBuffer.remaining())
            yBuffer.get(bytes)

            // Sampling step across frame for rapid analysis
            val step = 10
            var totalLuminance = 0L
            var sampleCount = 0
            var highGradientSum = 0L

            var y = 0
            while (y < height - step) {
                var x = 0
                while (x < width - step) {
                    val idx1 = y * rowStride + x * pixelStride
                    val idxRight = y * rowStride + (x + step) * pixelStride
                    val idxDown = (y + step) * rowStride + x * pixelStride

                    if (idx1 < bytes.size && idxRight < bytes.size && idxDown < bytes.size) {
                        val lum = bytes[idx1].toInt() and 0xFF
                        totalLuminance += lum

                        val lumRight = bytes[idxRight].toInt() and 0xFF
                        val lumDown = bytes[idxDown].toInt() and 0xFF

                        val diffX = kotlin.math.abs(lum - lumRight)
                        val diffY = kotlin.math.abs(lum - lumDown)

                        val diffTotal = diffX + diffY
                        if (diffTotal > 10) {
                            highGradientSum += diffTotal
                        }

                        sampleCount++
                    }
                    x += step
                }
                y += step
            }

            if (sampleCount > 0) {
                val avgBrightness = totalLuminance.toFloat() / sampleCount
                val sharpnessScore = highGradientSum.toFloat() / sampleCount

                val focus = if (sharpnessScore >= 9.5f) FocusCondition.SHARP else FocusCondition.BLURRY
                val lighting = when {
                    avgBrightness < 55f -> LightingCondition.DARK
                    avgBrightness > 215f -> LightingCondition.GLARE
                    else -> LightingCondition.GOOD
                }

                val isDetected = avgBrightness in 35f..230f && sharpnessScore >= 5.0f

                onFrameAnalyzed(focus, lighting, sharpnessScore, avgBrightness, isDetected)
            }
        } catch (e: Exception) {
            // Ignore temporary buffer or camera frame read issues
        } finally {
            imageProxy.close()
        }
    }
}

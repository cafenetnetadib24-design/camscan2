package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private enum class CropHandle {
    NONE,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    EDGE_LEFT, EDGE_TOP, EDGE_RIGHT, EDGE_BOTTOM,
    CENTER_QUAD
}

@Composable
fun InteractiveCropView(
    bitmap: Bitmap,
    topLeft: PointF,
    topRight: PointF,
    bottomRight: PointF,
    bottomLeft: PointF,
    onCropQuadChanged: (tl: PointF, tr: PointF, br: PointF, bl: PointF) -> Unit,
    modifier: Modifier = Modifier
) {
    var tl by remember(topLeft) { mutableStateOf(PointF(topLeft.x, topLeft.y)) }
    var tr by remember(topRight) { mutableStateOf(PointF(topRight.x, topRight.y)) }
    var br by remember(bottomRight) { mutableStateOf(PointF(bottomRight.x, bottomRight.y)) }
    var bl by remember(bottomLeft) { mutableStateOf(PointF(bottomLeft.x, bottomLeft.y)) }

    var activeHandle by remember { mutableStateOf(CropHandle.NONE) }

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(bitmap) {
                val touchRadius = 48.dp.toPx()

                detectDragGestures(
                    onDragStart = { startOffset ->
                        val canvasW = size.width.toFloat()
                        val canvasH = size.height.toFloat()
                        if (canvasW <= 0f || canvasH <= 0f) return@detectDragGestures

                        val imgW = bitmap.width.toFloat()
                        val imgH = bitmap.height.toFloat()
                        val scale = minOf(canvasW / imgW, canvasH / imgH)
                        val drawW = imgW * scale
                        val drawH = imgH * scale
                        val offsetX = (canvasW - drawW) / 2f
                        val offsetY = (canvasH - drawH) / 2f

                        val pTL = Offset(offsetX + (tl.x * drawW), offsetY + (tl.y * drawH))
                        val pTR = Offset(offsetX + (tr.x * drawW), offsetY + (tr.y * drawH))
                        val pBR = Offset(offsetX + (br.x * drawW), offsetY + (br.y * drawH))
                        val pBL = Offset(offsetX + (bl.x * drawW), offsetY + (bl.y * drawH))

                        val touch = Offset(startOffset.x, startOffset.y)

                        val dTL = (pTL - touch).getDistance()
                        val dTR = (pTR - touch).getDistance()
                        val dBR = (pBR - touch).getDistance()
                        val dBL = (pBL - touch).getDistance()

                        when {
                            dTL < touchRadius -> activeHandle = CropHandle.TOP_LEFT
                            dTR < touchRadius -> activeHandle = CropHandle.TOP_RIGHT
                            dBR < touchRadius -> activeHandle = CropHandle.BOTTOM_RIGHT
                            dBL < touchRadius -> activeHandle = CropHandle.BOTTOM_LEFT
                            else -> {
                                val midTop = Offset((pTL.x + pTR.x) / 2f, (pTL.y + pTR.y) / 2f)
                                val midBottom = Offset((pBL.x + pBR.x) / 2f, (pBL.y + pBR.y) / 2f)
                                val midLeft = Offset((pTL.x + pBL.x) / 2f, (pTL.y + pBL.y) / 2f)
                                val midRight = Offset((pTR.x + pBR.x) / 2f, (pTR.y + pBR.y) / 2f)

                                val dTop = (midTop - touch).getDistance()
                                val dBottom = (midBottom - touch).getDistance()
                                val dLeft = (midLeft - touch).getDistance()
                                val dRight = (midRight - touch).getDistance()

                                val minDist = minOf(dTop, dBottom, dLeft, dRight)
                                when {
                                    minDist < touchRadius && minDist == dTop -> activeHandle = CropHandle.EDGE_TOP
                                    minDist < touchRadius && minDist == dBottom -> activeHandle = CropHandle.EDGE_BOTTOM
                                    minDist < touchRadius && minDist == dLeft -> activeHandle = CropHandle.EDGE_LEFT
                                    minDist < touchRadius && minDist == dRight -> activeHandle = CropHandle.EDGE_RIGHT
                                    else -> activeHandle = CropHandle.CENTER_QUAD
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        activeHandle = CropHandle.NONE
                        onCropQuadChanged(tl, tr, br, bl)
                    },
                    onDragCancel = {
                        activeHandle = CropHandle.NONE
                        onCropQuadChanged(tl, tr, br, bl)
                    }
                ) { change, dragAmount ->
                    change.consume()

                    val canvasW = size.width.toFloat()
                    val canvasH = size.height.toFloat()
                    if (canvasW <= 0f || canvasH <= 0f) return@detectDragGestures

                    val imgW = bitmap.width.toFloat()
                    val imgH = bitmap.height.toFloat()
                    val scale = minOf(canvasW / imgW, canvasH / imgH)
                    val drawW = imgW * scale
                    val drawH = imgH * scale

                    if (drawW <= 0f || drawH <= 0f) return@detectDragGestures

                    val dx = dragAmount.x / drawW
                    val dy = dragAmount.y / drawH

                    when (activeHandle) {
                        CropHandle.TOP_LEFT -> {
                            tl = PointF((tl.x + dx).coerceIn(0f, tr.x - 0.05f), (tl.y + dy).coerceIn(0f, bl.y - 0.05f))
                        }
                        CropHandle.TOP_RIGHT -> {
                            tr = PointF((tr.x + dx).coerceIn(tl.x + 0.05f, 1f), (tr.y + dy).coerceIn(0f, br.y - 0.05f))
                        }
                        CropHandle.BOTTOM_RIGHT -> {
                            br = PointF((br.x + dx).coerceIn(bl.x + 0.05f, 1f), (br.y + dy).coerceIn(tr.y + 0.05f, 1f))
                        }
                        CropHandle.BOTTOM_LEFT -> {
                            bl = PointF((bl.x + dx).coerceIn(0f, br.x - 0.05f), (bl.y + dy).coerceIn(tl.y + 0.05f, 1f))
                        }
                        CropHandle.EDGE_TOP -> {
                            tl = PointF(tl.x, (tl.y + dy).coerceIn(0f, bl.y - 0.05f))
                            tr = PointF(tr.x, (tr.y + dy).coerceIn(0f, br.y - 0.05f))
                        }
                        CropHandle.EDGE_BOTTOM -> {
                            bl = PointF(bl.x, (bl.y + dy).coerceIn(tl.y + 0.05f, 1f))
                            br = PointF(br.x, (br.y + dy).coerceIn(tr.y + 0.05f, 1f))
                        }
                        CropHandle.EDGE_LEFT -> {
                            tl = PointF((tl.x + dx).coerceIn(0f, tr.x - 0.05f), tl.y)
                            bl = PointF((bl.x + dx).coerceIn(0f, br.x - 0.05f), bl.y)
                        }
                        CropHandle.EDGE_RIGHT -> {
                            tr = PointF((tr.x + dx).coerceIn(tl.x + 0.05f, 1f), tr.y)
                            br = PointF((br.x + dx).coerceIn(bl.x + 0.05f, 1f), br.y)
                        }
                        CropHandle.CENTER_QUAD -> {
                            val newTlX = (tl.x + dx).coerceIn(0f, 0.9f)
                            val newTlY = (tl.y + dy).coerceIn(0f, 0.9f)
                            val shiftX = newTlX - tl.x
                            val shiftY = newTlY - tl.y

                            if (tr.x + shiftX in 0f..1f && br.x + shiftX in 0f..1f && bl.x + shiftX in 0f..1f &&
                                tr.y + shiftY in 0f..1f && br.y + shiftY in 0f..1f && bl.y + shiftY in 0f..1f) {
                                tl = PointF(tl.x + shiftX, tl.y + shiftY)
                                tr = PointF(tr.x + shiftX, tr.y + shiftY)
                                br = PointF(br.x + shiftX, br.y + shiftY)
                                bl = PointF(bl.x + shiftX, bl.y + shiftY)
                            }
                        }
                        CropHandle.NONE -> {}
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            val imgW = bitmap.width.toFloat()
            val imgH = bitmap.height.toFloat()

            val scale = minOf(canvasW / imgW, canvasH / imgH)
            val drawW = imgW * scale
            val drawH = imgH * scale
            val offsetX = (canvasW - drawW) / 2f
            val offsetY = (canvasH - drawH) / 2f

            drawImage(
                image = imageBitmap,
                dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(drawW.toInt(), drawH.toInt())
            )

            val pTL = Offset(offsetX + (tl.x * drawW), offsetY + (tl.y * drawH))
            val pTR = Offset(offsetX + (tr.x * drawW), offsetY + (tr.y * drawH))
            val pBR = Offset(offsetX + (br.x * drawW), offsetY + (br.y * drawH))
            val pBL = Offset(offsetX + (bl.x * drawW), offsetY + (bl.y * drawH))

            val fullPath = Path().apply {
                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, canvasW, canvasH))
            }

            val quadPath = Path().apply {
                moveTo(pTL.x, pTL.y)
                lineTo(pTR.x, pTR.y)
                lineTo(pBR.x, pBR.y)
                lineTo(pBL.x, pBL.y)
                close()
            }

            val dimPath = Path().apply {
                op(fullPath, quadPath, PathOperation.Difference)
            }

            drawPath(path = dimPath, color = Color(0xAA000000))

            val strokeBlue = Color(0xFF2563EB)
            drawPath(
                path = quadPath,
                color = strokeBlue,
                style = Stroke(width = 2.5f.dp.toPx())
            )

            // Perspective Mesh 3x3 Grid
            val gridColor = Color(0x66FFFFFF)
            for (i in 1..2) {
                val frac = i / 3f
                val topPt = Offset(pTL.x + (pTR.x - pTL.x) * frac, pTL.y + (pTR.y - pTL.y) * frac)
                val botPt = Offset(pBL.x + (pBR.x - pBL.x) * frac, pBL.y + (pBR.y - pBL.y) * frac)
                drawLine(gridColor, topPt, botPt, strokeWidth = 1.dp.toPx())

                val leftPt = Offset(pTL.x + (pBL.x - pTL.x) * frac, pTL.y + (pBL.y - pTL.y) * frac)
                val rightPt = Offset(pTR.x + (pBR.x - pTR.x) * frac, pTR.y + (pBR.y - pTR.y) * frac)
                drawLine(gridColor, leftPt, rightPt, strokeWidth = 1.dp.toPx())
            }

            // Edge Midpoint Pill Handles
            val pillW = 20.dp.toPx()
            val pillH = 6.dp.toPx()

            listOf(
                Offset((pTL.x + pTR.x) / 2f, (pTL.y + pTR.y) / 2f),
                Offset((pTR.x + pBR.x) / 2f, (pTR.y + pBR.y) / 2f),
                Offset((pBL.x + pBR.x) / 2f, (pBL.y + pBR.y) / 2f),
                Offset((pTL.x + pBL.x) / 2f, (pTL.y + pBL.y) / 2f)
            ).forEach { mid ->
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(mid.x - pillW / 2, mid.y - pillH / 2),
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(pillH, pillH)
                )
                drawRoundRect(
                    color = strokeBlue,
                    topLeft = Offset(mid.x - pillW / 2, mid.y - pillH / 2),
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(pillH, pillH),
                    style = Stroke(1.dp.toPx())
                )
            }

            // Corner Precision Control Point Handles
            val handleRadius = 14.dp.toPx()
            val handleInnerRadius = 6.dp.toPx()

            listOf(pTL, pTR, pBR, pBL).forEach { cornerPos ->
                drawCircle(color = Color.White, radius = handleRadius, center = cornerPos)
                drawCircle(color = strokeBlue, radius = handleInnerRadius, center = cornerPos)
            }
        }
    }
}


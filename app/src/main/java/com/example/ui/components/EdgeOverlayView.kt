package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.camera.FocusCondition
import com.example.ui.camera.LightingCondition

@Composable
fun EdgeOverlayView(
    isDocumentDetected: Boolean,
    focusCondition: FocusCondition = FocusCondition.SHARP,
    lightingCondition: LightingCondition = LightingCondition.GOOD,
    customStatusText: String? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanline")
    val scanlineY by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "yPosition"
    )

    val laserGlowPulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserGlowPulse"
    )

    val laserAlpha by animateFloatAsState(
        targetValue = if (isDocumentDetected) 1.0f else 0.5f,
        animationSpec = tween(350),
        label = "laserAlpha"
    )

    // Determine target indicator colors
    val targetColor = when {
        !isDocumentDetected -> Color(0xAAFFFFFF)
        focusCondition == FocusCondition.BLURRY -> Color(0xFFF97316) // Warning Orange
        lightingCondition == LightingCondition.DARK || lightingCondition == LightingCondition.GLARE -> Color(0xFFFACC15) // Warning Amber
        else -> Color(0xFF10B981) // Vibrant Emerald Green
    }

    val animatedBorderColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(300),
        label = "borderColor"
    )

    val fillColor = when {
        !isDocumentDetected -> Color(0x11FFFFFF)
        focusCondition == FocusCondition.BLURRY -> Color(0x22F97316)
        lightingCondition == LightingCondition.DARK || lightingCondition == LightingCondition.GLARE -> Color(0x22FACC15)
        else -> Color(0x2210B981)
    }

    // Determine guidance status text & badge background
    val (statusMessage, statusBgColor) = when {
        customStatusText != null -> Pair(customStatusText, Color(0xCC0F172A))
        !isDocumentDetected -> Pair("سند را داخل کادر قرار دهید", Color(0xCC0F172A))
        focusCondition == FocusCondition.BLURRY -> Pair("⚠️ تصویر تار است - دوربین را ثابت نگه‌دارید", Color(0xEEEA580C))
        lightingCondition == LightingCondition.DARK -> Pair("💡 نور محیط کم است - فلاش را روشن کنید", Color(0xEED97706))
        lightingCondition == LightingCondition.GLARE -> Pair("☀️ بازتاب شدید نور - زاویه دوربین را تغییر دهید", Color(0xEED97706))
        else -> Pair("✓ کیفیت عالی - آماده اسکن خودکار", Color(0xEE059669))
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Document Viewfinder Margin Bounds (Default Scanner Rect)
            val left = w * 0.10f
            val top = h * 0.18f
            val right = w * 0.90f
            val bottom = h * 0.78f
            val rectWidth = right - left
            val rectHeight = bottom - top

            // Fill viewfinder background
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(left, top),
                size = Size(rectWidth, rectHeight),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
            )

            // Dotted/solid border stroke based on readiness
            drawRoundRect(
                color = animatedBorderColor,
                topLeft = Offset(left, top),
                size = Size(rectWidth, rectHeight),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(
                    width = 3.dp.toPx(),
                    pathEffect = if (focusCondition == FocusCondition.BLURRY) {
                        PathEffect.dashPathEffect(floatArrayOf(16f, 16f), 0f)
                    } else {
                        PathEffect.dashPathEffect(floatArrayOf(24f, 12f), 0f)
                    }
                )
            )

            // High precision corner brackets
            val bracketLen = 36.dp.toPx()
            val strokeW = 5.dp.toPx()

            // Top-Left
            drawPath(
                path = Path().apply {
                    moveTo(left, top + bracketLen)
                    lineTo(left, top)
                    lineTo(left + bracketLen, top)
                },
                color = animatedBorderColor,
                style = Stroke(width = strokeW)
            )

            // Top-Right
            drawPath(
                path = Path().apply {
                    moveTo(right - bracketLen, top)
                    lineTo(right, top)
                    lineTo(right, top + bracketLen)
                },
                color = animatedBorderColor,
                style = Stroke(width = strokeW)
            )

            // Bottom-Left
            drawPath(
                path = Path().apply {
                    moveTo(left, bottom - bracketLen)
                    lineTo(left, bottom)
                    lineTo(left + bracketLen, bottom)
                },
                color = animatedBorderColor,
                style = Stroke(width = strokeW)
            )

            // Bottom-Right
            drawPath(
                path = Path().apply {
                    moveTo(right - bracketLen, bottom)
                    lineTo(right, bottom)
                    lineTo(right, bottom - bracketLen)
                },
                color = animatedBorderColor,
                style = Stroke(width = strokeW)
            )

            // High-Tech Laser Scanning Beam Animation
            val currentScanY = top + (rectHeight * scanlineY)
            val laserBeamColor = if (isDocumentDetected) {
                if (focusCondition == FocusCondition.SHARP) Color(0xFF00E5FF) else Color(0xFFF97316)
            } else {
                Color(0xBB38BDF8)
            }

            // 1. Laser Gradient Trail (Aura swept behind laser line)
            val trailHeight = 44.dp.toPx()
            val trailTop = maxOf(top, currentScanY - trailHeight)
            val trailBottom = minOf(bottom, currentScanY + 6.dp.toPx())

            if (trailBottom > trailTop) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            laserBeamColor.copy(alpha = 0.0f),
                            laserBeamColor.copy(alpha = 0.12f * laserAlpha * laserGlowPulse),
                            laserBeamColor.copy(alpha = 0.35f * laserAlpha * laserGlowPulse),
                            Color.Transparent
                        ),
                        startY = trailTop,
                        endY = trailBottom
                    ),
                    topLeft = Offset(left + 8f, trailTop),
                    size = Size(rectWidth - 16f, trailBottom - trailTop)
                )
            }

            // 2. Outer Soft Glowing Line
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        laserBeamColor.copy(alpha = 0.1f * laserAlpha),
                        laserBeamColor.copy(alpha = 0.65f * laserAlpha * laserGlowPulse),
                        laserBeamColor.copy(alpha = 0.1f * laserAlpha)
                    ),
                    startX = left + 8f,
                    endX = right - 8f
                ),
                start = Offset(left + 8f, currentScanY),
                end = Offset(right - 8f, currentScanY),
                strokeWidth = 9.dp.toPx()
            )

            // 3. Central Bright High-Contrast Core Laser Line
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        laserBeamColor.copy(alpha = 0.5f * laserAlpha),
                        Color.White.copy(alpha = 0.95f * laserAlpha),
                        laserBeamColor.copy(alpha = 0.5f * laserAlpha)
                    ),
                    startX = left + 12f,
                    endX = right - 12f
                ),
                start = Offset(left + 12f, currentScanY),
                end = Offset(right - 12f, currentScanY),
                strokeWidth = 3.5f.dp.toPx()
            )

            // 4. Glowing End-Cap Laser Nodes
            val nodeRadius = 5.dp.toPx()
            val glowRadius = 11.dp.toPx()

            // Left Node
            drawCircle(
                color = laserBeamColor.copy(alpha = 0.4f * laserAlpha * laserGlowPulse),
                radius = glowRadius,
                center = Offset(left + 12f, currentScanY)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.95f * laserAlpha),
                radius = nodeRadius,
                center = Offset(left + 12f, currentScanY)
            )

            // Right Node
            drawCircle(
                color = laserBeamColor.copy(alpha = 0.4f * laserAlpha * laserGlowPulse),
                radius = glowRadius,
                center = Offset(right - 12f, currentScanY)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.95f * laserAlpha),
                radius = nodeRadius,
                center = Offset(right - 12f, currentScanY)
            )
        }

        // Top Column: Live Status Guidance + Focus & Lighting Indicator Pills
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 88.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Guidance Pill
            Surface(
                color = statusBgColor,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 6.dp
            ) {
                Text(
                    text = statusMessage,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }

            // Real-Time Focus & Lighting Quality Badges
            Row(
                modifier = Modifier
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live Focus Indicator
                val focusColor = if (focusCondition == FocusCondition.SHARP) Color(0xFF10B981) else Color(0xFFF97316)
                Surface(
                    color = Color(0xDD0F172A),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(1.dp, focusColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (focusCondition == FocusCondition.SHARP) Icons.Default.CenterFocusStrong else Icons.Default.CenterFocusWeak,
                            contentDescription = null,
                            tint = focusColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (focusCondition == FocusCondition.SHARP) "فوکوس: شفاف" else "فوکوس: تار",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Live Lighting Indicator
                val lightColor = when (lightingCondition) {
                    LightingCondition.GOOD -> Color(0xFF10B981)
                    LightingCondition.DARK -> Color(0xFFFACC15)
                    LightingCondition.GLARE -> Color(0xFFF97316)
                }
                val lightText = when (lightingCondition) {
                    LightingCondition.GOOD -> "نور: مناسب"
                    LightingCondition.DARK -> "نور: کم"
                    LightingCondition.GLARE -> "نور: بازتاب زیاد"
                }

                Surface(
                    color = Color(0xDD0F172A),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(1.dp, lightColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (lightingCondition == LightingCondition.GOOD) Icons.Default.Lightbulb else Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = lightColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = lightText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

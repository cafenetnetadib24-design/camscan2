package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.ScanFilter

data class FilterOption(
    val filter: ScanFilter,
    val title: String,
    val icon: ImageVector,
    val colorFilter: ColorFilter?
)

@Composable
fun FilterStrip(
    selectedFilter: ScanFilter,
    onFilterSelected: (ScanFilter) -> Unit,
    modifier: Modifier = Modifier,
    sampleBitmap: Bitmap? = null
) {
    val magicColorMatrix = ColorMatrix(
        floatArrayOf(
            1.25f, 0f, 0f, 0f, 10f,
            0f, 1.25f, 0f, 0f, 10f,
            0f, 0f, 1.25f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    val bwColorMatrix = ColorMatrix(
        floatArrayOf(
            1.5f, 1.5f, 1.5f, 0f, -160f,
            1.5f, 1.5f, 1.5f, 0f, -160f,
            1.5f, 1.5f, 1.5f, 0f, -160f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    val grayscaleColorMatrix = ColorMatrix().apply { setToSaturation(0f) }

    val invertedColorMatrix = ColorMatrix(
        floatArrayOf(
            1.8f, 1.8f, 1.8f, 0f, -140f,
            1.8f, 1.8f, 1.8f, 0f, -140f,
            1.8f, 1.8f, 1.8f, 0f, -140f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    val filterOptions = listOf(
        FilterOption(
            filter = ScanFilter.DESKTOP_COLOR,
            title = "اسکنر رنگی رومیزی",
            icon = Icons.Default.Palette,
            colorFilter = ColorFilter.colorMatrix(magicColorMatrix)
        ),
        FilterOption(
            filter = ScanFilter.MAGIC_COLOR,
            title = "رنگی جادویی",
            icon = Icons.Default.AutoAwesome,
            colorFilter = ColorFilter.colorMatrix(magicColorMatrix)
        ),
        FilterOption(
            filter = ScanFilter.BLACK_WHITE,
            title = "سیاه و سفید",
            icon = Icons.Default.FilterBAndW,
            colorFilter = ColorFilter.colorMatrix(bwColorMatrix)
        ),
        FilterOption(
            filter = ScanFilter.GRAYSCALE,
            title = "خاکستری",
            icon = Icons.Default.ColorLens,
            colorFilter = ColorFilter.colorMatrix(grayscaleColorMatrix)
        ),
        FilterOption(
            filter = ScanFilter.INVERTED,
            title = "خاکستری معکوس",
            icon = Icons.Default.InvertColors,
            colorFilter = ColorFilter.colorMatrix(invertedColorMatrix)
        ),
        FilterOption(
            filter = ScanFilter.ORIGINAL,
            title = "اصلی",
            icon = Icons.Default.Image,
            colorFilter = null
        )
    )

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(filterOptions) { option ->
            val isSelected = option.filter == selectedFilter

            Surface(
                onClick = { onFilterSelected(option.filter) },
                modifier = Modifier.bounceClick(),
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) Color(0xFF2563EB) else Color.White,
                shadowElevation = if (isSelected) 3.dp else 1.dp,
                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (sampleBitmap != null) {
                        Box(
                            modifier = Modifier
                                .size(width = 32.dp, height = 40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .background(Color(0xFFF1F5F9))
                        ) {
                            Image(
                                bitmap = sampleBitmap.asImageBitmap(),
                                contentDescription = option.title,
                                contentScale = ContentScale.Crop,
                                colorFilter = option.colorFilter,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color(0xFFF1F5F9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = option.title,
                                tint = if (isSelected) Color.White else Color(0xFF2563EB),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = option.title,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) Color.White else Color(0xFF1E293B)
                        )
                        if (isSelected) {
                            Text(
                                text = "اعمال شد",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}


package com.example.ui.edit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import com.example.ui.components.bounceClick
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.FilterStrip
import com.example.ui.components.InteractiveCropView
import java.io.File

enum class EditTab {
    CROP_ROTATE, FILTERS, ADJUSTMENTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDocumentScreen(
    viewModel: EditViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSaveExport: (documentId: Long) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Default tab set to FILTERS so user sees the processed document preview first
    var activeTab by remember { mutableStateOf(EditTab.FILTERS) }

    val currentPage = uiState.pages.getOrNull(uiState.currentPageIndex)
    val currentBitmap = remember(currentPage?.imagePath) {
        currentPage?.let { page ->
            if (File(page.imagePath).exists()) {
                BitmapFactory.decodeFile(page.imagePath)
            } else null
        }
    }

    val originalBitmap = remember(currentPage?.originalImagePath, currentPage?.imagePath) {
        currentPage?.let { page ->
            val path = if (page.originalImagePath.isNotEmpty() && File(page.originalImagePath).exists()) {
                page.originalImagePath
            } else {
                page.imagePath
            }
            if (File(path).exists()) {
                BitmapFactory.decodeFile(path)
            } else null
        }
    }

    val rotatedOriginalBitmap = remember(originalBitmap, uiState.rotationDegrees) {
        originalBitmap?.let { bmp ->
            if (uiState.rotationDegrees % 360 != 0) {
                val matrix = android.graphics.Matrix().apply {
                    postRotate(uiState.rotationDegrees.toFloat())
                }
                android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            } else bmp
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.document?.title ?: "ویرایش سند",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "صفحه ${uiState.currentPageIndex + 1} از ${uiState.pages.size}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeTab == EditTab.CROP_ROTATE) {
                            viewModel.applyCropAndSave { onNavigateBack() }
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    // Save / Export PDF
                    IconButton(onClick = {
                        viewModel.applyCropAndSave {
                            onNavigateToSaveExport(viewModel.documentId)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "ذخیره / خروجی",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
            ) {
                // Pages Thumbnail Selector Bar
                if (uiState.pages.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(uiState.pages) { idx, page ->
                            val isSelected = idx == uiState.currentPageIndex
                            val thumbnailBitmap = remember(page.imagePath) {
                                if (File(page.imagePath).exists()) BitmapFactory.decodeFile(page.imagePath) else null
                            }

                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.selectPageIndex(idx) }
                            ) {
                                thumbnailBitmap?.let {
                                    Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = "Page ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(Color(0xCC000000), RoundedCornerShape(topStart = 6.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Active Tab Toolbar Content
                when (activeTab) {
                    EditTab.CROP_ROTATE -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { viewModel.autoDetectEdges() }) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = "تشخیص خودکار حاشیه", tint = Color(0xFF2563EB))
                                            Text("خودکار", fontSize = 10.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    IconButton(onClick = { viewModel.resetCropToFull() }) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.Crop, contentDescription = "تمام صفحه")
                                            Text("بازنشانی", fontSize = 10.sp)
                                        }
                                    }
                                    IconButton(onClick = { viewModel.rotateCurrentPage(true) }) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.RotateRight, contentDescription = "چرخش")
                                            Text("چرخش", fontSize = 10.sp)
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        viewModel.applyCropAndSave {
                                            activeTab = EditTab.FILTERS
                                            Toast.makeText(context, "برش با موفقیت اعمال شد", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.bounceClick(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2563EB)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "تایید برش",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "تایید برش",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    EditTab.FILTERS -> {
                        FilterStrip(
                            selectedFilter = uiState.selectedFilter,
                            onFilterSelected = { viewModel.onFilterSelected(it) },
                            sampleBitmap = rotatedOriginalBitmap ?: currentBitmap,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }

                    EditTab.ADJUSTMENTS -> {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("روشنایی", fontSize = 12.sp, modifier = Modifier.width(80.dp))
                                Slider(
                                    value = uiState.brightness,
                                    onValueChange = { viewModel.updateBrightnessContrast(it, uiState.contrast) },
                                    valueRange = -0.5f..0.5f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("کنتراست", fontSize = 12.sp, modifier = Modifier.width(80.dp))
                                Slider(
                                    value = uiState.contrast,
                                    onValueChange = { viewModel.updateBrightnessContrast(uiState.brightness, it) },
                                    valueRange = 0.5f..2.0f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Main Navigation Bar Tabs
                NavigationBar {
                    NavigationBarItem(
                        selected = activeTab == EditTab.CROP_ROTATE,
                        onClick = { activeTab = EditTab.CROP_ROTATE },
                        icon = { Icon(Icons.Default.Crop, contentDescription = null) },
                        label = { Text("برش و چرخش") }
                    )
                    NavigationBarItem(
                        selected = activeTab == EditTab.FILTERS,
                        onClick = {
                            if (activeTab == EditTab.CROP_ROTATE) {
                                viewModel.applyCropAndSave { activeTab = EditTab.FILTERS }
                            } else {
                                activeTab = EditTab.FILTERS
                            }
                        },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                        label = { Text("فیلترها") }
                    )
                    NavigationBarItem(
                        selected = activeTab == EditTab.ADJUSTMENTS,
                        onClick = {
                            if (activeTab == EditTab.CROP_ROTATE) {
                                viewModel.applyCropAndSave { activeTab = EditTab.ADJUSTMENTS }
                            } else {
                                activeTab = EditTab.ADJUSTMENTS
                            }
                        },
                        icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                        label = { Text("تنظیمات") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            if (activeTab == EditTab.CROP_ROTATE) {
                rotatedOriginalBitmap?.let { bitmap ->
                    InteractiveCropView(
                        bitmap = bitmap,
                        topLeft = uiState.topLeft,
                        topRight = uiState.topRight,
                        bottomRight = uiState.bottomRight,
                        bottomLeft = uiState.bottomLeft,
                        onCropQuadChanged = { tl, tr, br, bl ->
                            viewModel.updateCropQuad(tl, tr, br, bl)
                        }
                    )
                } ?: CircularProgressIndicator(color = Color(0xFF2563EB))
            } else {
                currentBitmap?.let { bitmap ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Document Preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentScale = ContentScale.Fit
                        )

                        // Floating button to edit crop boundaries quickly
                        Surface(
                            onClick = { activeTab = EditTab.CROP_ROTATE },
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xCC1E293B),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Crop,
                                    contentDescription = "تنظیم برش",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تنظیم برش", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } ?: CircularProgressIndicator(color = Color(0xFF2563EB))
            }

            if (uiState.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x88000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF2563EB))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("در حال پردازش سند...", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

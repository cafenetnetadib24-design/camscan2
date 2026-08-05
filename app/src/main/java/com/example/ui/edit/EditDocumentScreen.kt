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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector

enum class EditTab {
    CROP_ROTATE, FILTERS, ADJUSTMENTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDocumentScreen(
    viewModel: EditViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSaveExport: (documentId: Long) -> Unit,
    onNavigateToRescan: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Default tab set to FILTERS so user sees the processed document preview first
    var activeTab by remember { mutableStateOf(EditTab.FILTERS) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveFolderDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val currentPage = uiState.pages.getOrNull(uiState.currentPageIndex)
    val currentBitmap = remember(currentPage?.imagePath, uiState.document?.thumbnailPath) {
        val path = currentPage?.imagePath
            ?: uiState.document?.thumbnailPath?.takeIf { File(it).exists() }
            ?: ""
        if (path.isNotEmpty() && File(path).exists()) {
            BitmapFactory.decodeFile(path)
        } else null
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

    val filteredBaseBitmap = remember(
        originalBitmap,
        uiState.rotationDegrees,
        uiState.topLeft,
        uiState.topRight,
        uiState.bottomRight,
        uiState.bottomLeft,
        uiState.cropRect,
        uiState.selectedFilter
    ) {
        originalBitmap?.let { bmp ->
            com.example.util.ImageFilterUtils.applyFilterAndAdjustments(
                sourceBitmap = bmp,
                filter = uiState.selectedFilter,
                brightness = 0f,
                contrast = 1f,
                saturation = 1f,
                warmth = 0f,
                sharpness = 1f,
                rotationDegrees = uiState.rotationDegrees,
                cropRect = uiState.cropRect,
                topLeft = uiState.topLeft,
                topRight = uiState.topRight,
                bottomRight = uiState.bottomRight,
                bottomLeft = uiState.bottomLeft
            )
        }
    }

    val adjustmentColorMatrix = remember(
        uiState.brightness,
        uiState.contrast,
        uiState.saturation,
        uiState.warmth,
        uiState.sharpness
    ) {
        val androidCm = com.example.util.ImageFilterUtils.createAdjustmentColorMatrix(
            contrast = uiState.contrast,
            brightness = uiState.brightness,
            saturation = uiState.saturation,
            warmth = uiState.warmth,
            sharpness = uiState.sharpness
        )
        ColorMatrix(androidCm.array)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable { showRenameDialog = true }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = uiState.document?.title ?: "ویرایش سند",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "تغییر نام",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
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

                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "منو گزینه‌ها"
                            )
                        }

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("تغییر نام سند") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    showRenameDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("انتقال به پوشه") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DriveFileMove,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    showMoveFolderDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("انتقال به سطل زباله", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
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
                        ColorAdjustmentsPanel(
                            uiState = uiState,
                            viewModel = viewModel
                        )
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
            if (uiState.pages.isEmpty() && currentBitmap == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF2563EB), modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "در حال ذخیره و پردازش سند...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (activeTab == EditTab.CROP_ROTATE) {
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
                val previewBitmap = filteredBaseBitmap ?: currentBitmap
                previewBitmap?.let { bitmap ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Document Preview",
                            colorFilter = ColorFilter.colorMatrix(adjustmentColorMatrix),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentScale = ContentScale.Fit
                        )

                        // Floating action buttons overlay
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = { onNavigateToRescan() },
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xEE2563EB),
                                modifier = Modifier.bounceClick()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "بازگشت به اسکن",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("بازگشت به اسکن", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Surface(
                                onClick = { activeTab = EditTab.CROP_ROTATE },
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xCC1E293B)
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

            // Rename Document Dialog
            if (showRenameDialog) {
                var newTitle by remember { mutableStateOf(uiState.document?.title ?: "") }
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text("تغییر نام سند", fontWeight = FontWeight.Bold) },
                    text = {
                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            label = { Text("نام جدید سند") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newTitle.isNotBlank()) {
                                    viewModel.renameDocument(newTitle.trim())
                                    showRenameDialog = false
                                }
                            }
                        ) {
                            Text("ثبت نام جدید")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = false }) {
                            Text("انصراف")
                        }
                    }
                )
            }

            // Move to Folder Dialog
            if (showMoveFolderDialog) {
                AlertDialog(
                    onDismissRequest = { showMoveFolderDialog = false },
                    title = { Text("انتقال به پوشه", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            TextButton(
                                onClick = {
                                    viewModel.moveDocumentToFolder(null)
                                    showMoveFolderDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("اصلی (بدون پوشه)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            uiState.folders.filter { !it.isArchived }.forEach { folder ->
                                TextButton(
                                    onClick = {
                                        viewModel.moveDocumentToFolder(folder.id)
                                        showMoveFolderDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = Color(0xFF2563EB),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(folder.name, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showMoveFolderDialog = false }) {
                            Text("انصراف")
                        }
                    }
                )
            }

            // Delete Confirm Dialog
            if (showDeleteConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = { Text("انتقال به سطل زباله", fontWeight = FontWeight.Bold) },
                    text = { Text("آیا از انتقال این سند به سطل زباله اطمینان دارید؟") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteDocument {
                                    showDeleteConfirmDialog = false
                                    onNavigateBack()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("انتقال به سطل زباله")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmDialog = false }) {
                            Text("انصراف")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ColorAdjustmentsPanel(
    uiState: EditUiState,
    viewModel: EditViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 210.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "تنظیمات پیشرفته رنگ و تصویر",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            TextButton(
                onClick = { viewModel.resetAdjustments() },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("بازنشانی", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 1. Brightness
        AdjustmentSliderRow(
            icon = Icons.Default.Brightness6,
            label = "روشنایی",
            value = uiState.brightness,
            valueRange = -0.6f..0.6f,
            valueText = "${(uiState.brightness * 100).toInt()}%",
            onValueChange = { viewModel.updateBrightness(it) }
        )

        // 2. Contrast
        AdjustmentSliderRow(
            icon = Icons.Default.Contrast,
            label = "کنتراست",
            value = uiState.contrast,
            valueRange = 0.5f..2.0f,
            valueText = String.format("%.1fx", uiState.contrast),
            onValueChange = { viewModel.updateContrast(it) }
        )

        // 3. Saturation
        AdjustmentSliderRow(
            icon = Icons.Default.Palette,
            label = "اشباع رنگ",
            value = uiState.saturation,
            valueRange = 0.0f..2.0f,
            valueText = String.format("%.1fx", uiState.saturation),
            onValueChange = { viewModel.updateSaturation(it) }
        )

        // 4. Warmth / Temperature
        AdjustmentSliderRow(
            icon = Icons.Default.WbSunny,
            label = "دمای رنگ",
            value = uiState.warmth,
            valueRange = -0.5f..0.5f,
            valueText = if (uiState.warmth > 0.05f) "گرم" else if (uiState.warmth < -0.05f) "سرد" else "طبیعی",
            onValueChange = { viewModel.updateWarmth(it) }
        )

        // 5. Sharpness / Clarity
        AdjustmentSliderRow(
            icon = Icons.Default.HighQuality,
            label = "شفافیت",
            value = uiState.sharpness,
            valueRange = 0.5f..2.0f,
            valueText = String.format("%.1fx", uiState.sharpness),
            onValueChange = { viewModel.updateSharpness(it) }
        )
    }
}

@Composable
private fun AdjustmentSliderRow(
    icon: ImageVector,
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(65.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = valueText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(42.dp)
        )
    }
}

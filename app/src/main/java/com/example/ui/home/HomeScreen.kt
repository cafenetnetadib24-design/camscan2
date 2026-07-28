package com.example.ui.home

import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import com.example.ui.components.bounceClick
import com.example.util.AdManager
import com.example.util.AppExpirationUtils
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DocumentEntity
import com.example.ui.components.DocumentCard
import com.example.ui.components.FloatingScanButton
import com.example.util.PdfGenerator

import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security

import com.example.data.local.FolderEntity
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ButtonDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToCamera: (folderId: Long?) -> Unit,
    onNavigateToEdit: (documentId: Long) -> Unit,
    onNavigateToDetail: (documentId: Long) -> Unit,
    onNavigateToPrivacy: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val shareFile by viewModel.shareFile.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showMoveToFolderDialog by remember { mutableStateOf(false) }
    var moveTargetDocId by remember { mutableStateOf<Long?>(null) }
    var renameDocTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    
    var folderToEditTarget by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToDeleteTarget by remember { mutableStateOf<FolderEntity?>(null) }

    // Ad Popup Dialog States
    var showAdDialog by remember { mutableStateOf(true) }
    var adImageUrl by remember { mutableStateOf(AdManager.DEFAULT_IMAGE_URL) }
    var adTargetUrl by remember { mutableStateOf("https://github.com/cafenetnetadib24-design/english701") }

    LaunchedEffect(Unit) {
        val cached = AdManager.getCachedAdInfo(context)
        adImageUrl = cached.first
        adTargetUrl = cached.second

        val refreshed = AdManager.refreshAdInfoIfNeeded(context)
        adImageUrl = refreshed.first
        adTargetUrl = refreshed.second
    }

    // System share launcher when PDF export is ready
    LaunchedEffect(shareFile) {
        shareFile?.let { file ->
            PdfGenerator.sharePdfFile(context, file)
            viewModel.clearShareFile()
        }
    }

    // Gallery import launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUrisFromGallery(context, uris) { newDocId ->
                onNavigateToEdit(newDocId)
            }
        }
    }

    Scaffold(
        topBar = {
            if (uiState.selectedDocIds.isNotEmpty()) {
                // Multi-select top app bar
                TopAppBar(
                    title = {
                        Text(
                            text = "${uiState.selectedDocIds.size} انتخاب شده",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "بستن")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.saveSelectedDocumentsToGallery(context) { success, count ->
                                if (success) {
                                    Toast.makeText(
                                        context,
                                        "با موفقیت $count تصویر اسناد انتخاب شده در گالری ذخیره شد",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(context, "خطا در ذخیره‌سازی در گالری", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Image, contentDescription = "ذخیره دسته‌ای در گالری")
                        }
                        IconButton(onClick = { showMoveToFolderDialog = true }) {
                            Icon(imageVector = Icons.Default.DriveFileMove, contentDescription = "انتقال")
                        }
                        IconButton(onClick = { viewModel.deleteSelectedDocuments() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "حذف",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                // Frosted Glass Search Header Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "جستجو",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.onSearchQueryChange(it) },
                                placeholder = {
                                    Text(
                                        text = "جستجوی اسناد...",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 14.sp
                                    )
                                },
                                singleLine = true,
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "پاکسازی جستجو", tint = Color(0xFF94A3B8))
                                        }
                                    }
                                },
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            // Privacy Policy Shield Icon Button
                            IconButton(
                                onClick = onNavigateToPrivacy,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "حریم خصوصی و امنیت",
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingScanButton(
                onCameraScanClick = { onNavigateToCamera(uiState.selectedFolderId) },
                onGalleryImportClick = { galleryLauncher.launch("image/*") }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8FAFC))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Category Chips Row (Frosted Pill Tags)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    item {
                        val isSelected = uiState.selectedFolderId == null
                        Surface(
                            onClick = { viewModel.onSelectFolder(null) },
                            modifier = Modifier.bounceClick(),
                            shape = CircleShape,
                            color = if (isSelected) Color(0xFF2563EB) else Color.White,
                            shadowElevation = if (isSelected) 2.dp else 1.dp,
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Text(
                                text = "همه فایل‌ها",
                                color = if (isSelected) Color.White else Color(0xFF475569),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                            )
                        }
                    }

                    item {
                        val isSelected = uiState.selectedFolderId == -1L
                        Surface(
                            onClick = { viewModel.onSelectFolder(-1L) },
                            modifier = Modifier.bounceClick(),
                            shape = CircleShape,
                            color = if (isSelected) Color(0xFF2563EB) else Color.White,
                            shadowElevation = if (isSelected) 2.dp else 1.dp,
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else Color(0xFFFFB800),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "علاقه‌مندی‌ها",
                                    color = if (isSelected) Color.White else Color(0xFF475569),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    items(uiState.folders) { folder ->
                        val isSelected = uiState.selectedFolderId == folder.id
                        Surface(
                            onClick = { viewModel.onSelectFolder(folder.id) },
                            modifier = Modifier.bounceClick(),
                            shape = CircleShape,
                            color = if (isSelected) Color(0xFF2563EB) else Color.White,
                            shadowElevation = if (isSelected) 2.dp else 1.dp,
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (folder.isArchived) Icons.Default.Archive else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else if (folder.isArchived) Color(0xFFD97706) else Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (folder.isArchived) "${folder.name} (بایگانی)" else folder.name,
                                    color = if (isSelected) Color.White else Color(0xFF475569),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    item {
                        Surface(
                            onClick = { showCreateFolderDialog = true },
                            shape = CircleShape,
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "پوشه جدید",
                                    color = Color(0xFF2563EB),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Active Folder Banner Info Header (if folder selected)
                val activeFolder = uiState.folders.find { it.id == uiState.selectedFolderId }
                if (activeFolder != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(Color(0xFFEFF6FF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = activeFolder.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )
                                        if (activeFolder.isArchived) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFFFEF3C7),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "بایگانی شده",
                                                    color = Color(0xFFD97706),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val persianSdf = java.text.SimpleDateFormat("yyyy/MM/dd - HH:mm", java.util.Locale("fa"))
                                        Text(
                                            text = "ایجاد: ${persianSdf.format(java.util.Date(activeFolder.createdAt))}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { folderToEditTarget = activeFolder }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "تغییر نام پوشه",
                                        tint = Color(0xFF2563EB)
                                    )
                                }
                                IconButton(onClick = { viewModel.archiveFolder(activeFolder.id, !activeFolder.isArchived) }) {
                                    Icon(
                                        imageVector = if (activeFolder.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                        contentDescription = if (activeFolder.isArchived) "خروج از بایگانی" else "بایگانی پوشه",
                                        tint = Color(0xFFD97706)
                                    )
                                }
                                IconButton(onClick = { folderToDeleteTarget = activeFolder }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "حذف پوشه",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                // Section Title & View/Sort Options
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اسکن‌های اخیر",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 1.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.toggleGridMode() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isGridMode) Icons.Default.List else Icons.Default.GridView,
                                contentDescription = "تغییر حالت نمایش",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "مرتب‌سازی",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("تاریخ (جدیدترین)") },
                                    onClick = { viewModel.onSortOrderChange(SortOrder.DATE_DESC); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("تاریخ (قدیمی‌ترین)") },
                                    onClick = { viewModel.onSortOrderChange(SortOrder.DATE_ASC); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("نام (الف تا ی)") },
                                    onClick = { viewModel.onSortOrderChange(SortOrder.NAME_ASC); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("تعداد صفحات") },
                                    onClick = { viewModel.onSortOrderChange(SortOrder.PAGE_COUNT_DESC); showSortMenu = false }
                                )
                            }
                        }
                    }
                }

                // Documents Content Area
                if (uiState.documents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (uiState.searchQuery.isNotEmpty()) "هیچ سندی یافت نشد" else "هنوز سندی وجود ندارد",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "برای اسکن اسناد با کیفیت بالا روی دکمه آبی اسکن کلیک کنید.",
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = { onNavigateToCamera(uiState.selectedFolderId) }) {
                                Icon(Icons.Default.DocumentScanner, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("اسکن اولین سند")
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = if (uiState.isGridMode) GridCells.Fixed(2) else GridCells.Fixed(1),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(uiState.documents, key = { it.id }) { doc ->
                            DocumentCard(
                                document = doc,
                                isGrid = uiState.isGridMode,
                                isSelectionMode = uiState.selectedDocIds.isNotEmpty(),
                                isSelected = uiState.selectedDocIds.contains(doc.id),
                                onDocumentClick = { onNavigateToDetail(doc.id) },
                                onDocumentLongClick = { viewModel.toggleDocumentSelection(doc.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(doc.id, doc.isFavorite) },
                                onRename = { renameDocTarget = doc },
                                onMoveToFolder = {
                                    moveTargetDocId = doc.id
                                    showMoveToFolderDialog = true
                                },
                                onSharePdf = { viewModel.exportAndSharePdf(doc.id) },
                                onSaveToGallery = {
                                    viewModel.saveDocumentToGallery(context, doc.id) { success, count ->
                                        if (success) {
                                            android.widget.Toast.makeText(
                                                context,
                                                "تصویر سند با موفقیت در گالری ذخیره شد ($count صفحه)",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "خطا در ذخیره‌سازی در گالری", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onDelete = { viewModel.deleteDocument(doc.id) }
                            )
                        }
                    }
                }
            }

            // Loading overlay
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("در حال پردازش سند...", color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    // Create Folder Dialog
    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("ایجاد پوشه جدید") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("نام پوشه") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            viewModel.createFolder(folderName.trim())
                            showCreateFolderDialog = false
                        }
                    }
                ) {
                    Text("ایجاد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Rename Document Dialog
    renameDocTarget?.let { doc ->
        var newTitle by remember { mutableStateOf(doc.title) }
        AlertDialog(
            onDismissRequest = { renameDocTarget = null },
            title = { Text("تغییر نام سند") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("عنوان") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            viewModel.renameDocument(doc.id, newTitle.trim())
                            renameDocTarget = null
                        }
                    }
                ) {
                    Text("ذخیره")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDocTarget = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Move to Folder Dialog
    if (showMoveToFolderDialog) {
        AlertDialog(
            onDismissRequest = {
                showMoveToFolderDialog = false
                moveTargetDocId = null
            },
            title = { Text("انتقال به پوشه", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            val targetIds = if (uiState.selectedDocIds.isNotEmpty()) {
                                uiState.selectedDocIds.toList()
                            } else {
                                listOfNotNull(moveTargetDocId)
                            }
                            viewModel.moveDocumentsToFolder(targetIds, null)
                            showMoveToFolderDialog = false
                            moveTargetDocId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("همه اسناد (بدون پوشه)", color = MaterialTheme.colorScheme.primary)
                    }
                    uiState.folders.filter { !it.isArchived }.forEach { folder ->
                        TextButton(
                            onClick = {
                                val targetIds = if (uiState.selectedDocIds.isNotEmpty()) {
                                    uiState.selectedDocIds.toList()
                                } else {
                                    listOfNotNull(moveTargetDocId)
                                }
                                viewModel.moveDocumentsToFolder(targetIds, folder.id)
                                showMoveToFolderDialog = false
                                moveTargetDocId = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(folder.name, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showMoveToFolderDialog = false
                    moveTargetDocId = null
                }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Rename Folder Dialog
    folderToEditTarget?.let { folder ->
        var folderName by remember { mutableStateOf(folder.name) }
        AlertDialog(
            onDismissRequest = { folderToEditTarget = null },
            title = { Text("ویرایش / تغییر نام پوشه") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("نام پوشه") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            viewModel.renameFolder(folder.id, folderName.trim())
                            folderToEditTarget = null
                        }
                    }
                ) {
                    Text("ذخیره")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToEditTarget = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Delete Folder Dialog
    folderToDeleteTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDeleteTarget = null },
            title = { Text("حذف پوشه") },
            text = {
                Text("آیا از حذف پوشه «${folder.name}» اطمینان دارید؟ اسناد موجود در این پوشه حذف نخواهند شد و به بخش «همه فایل‌ها» منتقل می‌شوند.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFolder(folder.id)
                        folderToDeleteTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف پوشه")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDeleteTarget = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Ad Banner Popup Dialog on HomeScreen Entry
    if (showAdDialog) {
        Dialog(
            onDismissRequest = { showAdDialog = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Header Row with Title and Close ("X") Icon Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFEFF6FF),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Campaign,
                                        contentDescription = "تبلیغات",
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "پیشنهاد ویژه",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF1E293B)
                            )
                        }

                        // Close (X) button
                        IconButton(
                            onClick = { showAdDialog = false },
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFFF1F5F9), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "بستن",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Ad Image Banner Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF8FAFC))
                            .clickable {
                                if (adTargetUrl.isNotEmpty()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(adTargetUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "امکان بازکردن لینک وجود ندارد", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = adImageUrl,
                            contentDescription = "تصویر تبلیغاتی",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons Row: Open Link + Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (adTargetUrl.isNotEmpty()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(adTargetUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "امکان بازکردن لینک وجود ندارد", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .bounceClick(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("مشاهده لینک تبلیغ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        OutlinedButton(
                            onClick = { showAdDialog = false },
                            modifier = Modifier.bounceClick(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("بستن", color = Color(0xFF64748B), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

}

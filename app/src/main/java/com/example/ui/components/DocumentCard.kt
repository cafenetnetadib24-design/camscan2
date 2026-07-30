package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.DocumentEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.ui.components.bounceClick

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentCard(
    document: DocumentEntity,
    isGrid: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onDocumentClick: () -> Unit,
    onDocumentLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onMoveToFolder: () -> Unit,
    onSharePdf: () -> Unit,
    onSaveToGallery: () -> Unit = {},
    onDelete: () -> Unit,
    onRestore: (() -> Unit)? = null,
    onPermanentlyDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val formattedDate = remember(document.updatedAt) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(document.updatedAt))
    }
    val daysLeft = remember(document.deletedAt) {
        if (document.deletedAt != null) {
            val msInDay = 24 * 60 * 60 * 1000L
            val elapsed = System.currentTimeMillis() - document.deletedAt
            maxOf(0L, 30L - (elapsed / msInDay))
        } else null
    }

    if (isGrid) {
        Surface(
            modifier = modifier
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) onDocumentLongClick() else onDocumentClick()
                    },
                    onLongClick = {
                        if (isSelectionMode) {
                            onDocumentLongClick()
                        } else {
                            showMenu = true
                        }
                    }
                )
                .bounceClick(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Thumbnail container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (document.thumbnailPath.isNotEmpty() && File(document.thumbnailPath).exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(File(document.thumbnailPath))
                                .crossfade(true)
                                .build(),
                            contentDescription = document.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.matchParentSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    // Multi-select overlay check
                    if (isSelectionMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Page count badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${document.pageCount} صفحه",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Favorite star
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(32.dp)
                            .background(Color(0x55000000), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (document.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = "علاقه‌مندی",
                            tint = if (document.isFavorite) Color(0xFFFFB800) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Info section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = document.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (daysLeft != null) "حذف در $daysLeft روز" else formattedDate,
                            fontSize = 11.sp,
                            fontWeight = if (daysLeft != null) FontWeight.Medium else FontWeight.Normal,
                            color = if (daysLeft != null) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DocumentCardDropdownMenu(
                            expanded = showMenu,
                            onDismiss = { showMenu = false },
                            onRename = onRename,
                            onMoveToFolder = onMoveToFolder,
                            onSharePdf = onSharePdf,
                            onSaveToGallery = onSaveToGallery,
                            onSelectMode = onDocumentLongClick,
                            onDelete = onDelete,
                            onRestore = onRestore,
                            onPermanentlyDelete = onPermanentlyDelete
                        )
                    }
                }
            }
        }
    } else {
        // List Layout
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) onDocumentLongClick() else onDocumentClick()
                    },
                    onLongClick = {
                        if (isSelectionMode) {
                            onDocumentLongClick()
                        } else {
                            showMenu = true
                        }
                    }
                )
                .bounceClick(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(24.dp)
                    )
                }

                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (document.thumbnailPath.isNotEmpty() && File(document.thumbnailPath).exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(File(document.thumbnailPath))
                                .crossfade(true)
                                .build(),
                            contentDescription = document.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (daysLeft != null) "حذف در $daysLeft روز  •  ${document.pageCount} صفحه" else "$formattedDate  •  ${document.pageCount} صفحه",
                            fontSize = 12.sp,
                            fontWeight = if (daysLeft != null) FontWeight.Medium else FontWeight.Normal,
                            color = if (daysLeft != null) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (document.deletedAt == null) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (document.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = "علاقه‌مندی",
                            tint = if (document.isFavorite) Color(0xFFFFB800) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "منو",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DocumentCardDropdownMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        onRename = onRename,
                        onMoveToFolder = onMoveToFolder,
                        onSharePdf = onSharePdf,
                        onSaveToGallery = onSaveToGallery,
                        onSelectMode = onDocumentLongClick,
                        onDelete = onDelete,
                        onRestore = onRestore,
                        onPermanentlyDelete = onPermanentlyDelete
                    )
                }
            }
        }
    }
}

@Composable
fun DocumentCardDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onMoveToFolder: () -> Unit,
    onSharePdf: () -> Unit,
    onSaveToGallery: () -> Unit,
    onSelectMode: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onRestore: (() -> Unit)? = null,
    onPermanentlyDelete: (() -> Unit)? = null
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        if (onRestore != null) {
            DropdownMenuItem(
                text = { Text("بازگردانی سند") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { onDismiss(); onRestore() }
            )
            DropdownMenuItem(
                text = { Text("حذف دائمی", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { onDismiss(); (onPermanentlyDelete ?: onDelete)() }
            )
        } else {
            DropdownMenuItem(
                text = { Text("تغییر نام") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { onDismiss(); onRename() }
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
                onClick = { onDismiss(); onMoveToFolder() }
            )
            DropdownMenuItem(
                text = { Text("اشتراک‌گذاری / خروجی PDF") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { onDismiss(); onSharePdf() }
            )
            DropdownMenuItem(
                text = { Text("ذخیره در گالری (JPG)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { onDismiss(); onSaveToGallery() }
            )
            onSelectMode?.let { selectAction ->
                DropdownMenuItem(
                    text = { Text("انتخاب چندتایی") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = { onDismiss(); selectAction() }
                )
            }
            DropdownMenuItem(
                text = { Text("حذف (انتقال به سطل زباله)", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { onDismiss(); onDelete() }
            )
        }
    }
}

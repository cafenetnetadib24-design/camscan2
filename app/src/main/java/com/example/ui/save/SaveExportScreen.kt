package com.example.ui.save

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppDatabase
import com.example.data.local.DocumentEntity
import com.example.data.repository.DocumentRepository
import com.example.util.PdfGenerator
import com.example.util.PdfQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.example.util.GalleryExporter
import com.example.ui.components.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveExportScreen(
    documentId: Long,
    onNavigateHome: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember {
        val db = AppDatabase.getDatabase(context)
        DocumentRepository(context, db.documentDao(), db.folderDao())
    }

    var document by remember { mutableStateOf<DocumentEntity?>(null) }
    var title by remember { mutableStateOf("") }
    var isDuplicateTitle by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf(PdfQuality.HIGH) }
    var watermarkText by remember { mutableStateOf("") }
    var addPageNumbers by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }

    LaunchedEffect(documentId) {
        val doc = repository.getDocumentById(documentId)
        if (doc != null) {
            document = doc
            title = doc.title
        }
    }

    LaunchedEffect(title, documentId) {
        val trimmed = title.trim()
        if (trimmed.isNotEmpty()) {
            isDuplicateTitle = repository.isTitleDuplicate(trimmed, documentId)
        } else {
            isDuplicateTitle = false
        }
    }

    suspend fun validateAndSaveTitle(): Boolean {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            Toast.makeText(context, "نام سند نباید خالی باشد", Toast.LENGTH_SHORT).show()
            return false
        }
        if (repository.isTitleDuplicate(trimmed, documentId)) {
            Toast.makeText(context, "نام سند تکراری است. لطفاً نام دیگری انتخاب کنید", Toast.LENGTH_SHORT).show()
            return false
        }
        repository.updateDocumentTitle(documentId, trimmed)
        return true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ذخیره و خروجی سند", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت", tint = Color.Black)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Return to Document Library Cross (X) Button Row above File Title Card on the Right Side
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEF2F2),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.bounceClick { onNavigateHome() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "بازگشت به کتابخانه اسناد",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "بازگشت به کتابخانه اسناد",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
                }

                // File Title Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("مشخصات فایل", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("نام سند", color = Color.Black, fontWeight = FontWeight.Medium) },
                            singleLine = true,
                            isError = title.trim().isEmpty() || isDuplicateTitle,
                            trailingIcon = {
                                if (title.isNotEmpty()) {
                                    IconButton(onClick = { title = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "پاک کردن نام سند",
                                            tint = Color(0xFF64748B)
                                        )
                                    }
                                }
                            },
                            supportingText = {
                                if (title.trim().isEmpty()) {
                                    Text("نام سند نباید خالی باشد", color = MaterialTheme.colorScheme.error)
                                } else if (isDuplicateTitle) {
                                    Text("نام سند تکراری است. لطفاً نام دیگری انتخاب کنید", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedLabelColor = Color.Black,
                                unfocusedLabelColor = Color.Black,
                                focusedBorderColor = Color(0xFF2563EB),
                                unfocusedBorderColor = Color(0xFF94A3B8),
                                errorBorderColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // PDF Quality Options
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("کیفیت PDF / فشرده‌سازی", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        QualityOptionRow(
                            title = "کیفیت بالا (رزولوشن اصلی)",
                            subtitle = "بهترین کیفیت برای چاپ و بایگانی",
                            selected = selectedQuality == PdfQuality.HIGH,
                            onSelect = { selectedQuality = PdfQuality.HIGH }
                        )

                        QualityOptionRow(
                            title = "کیفیت متوسط (حجم متعادل)",
                            subtitle = "مناسب برای اشتراک‌گذاری سریع در پیام‌رسان‌ها",
                            selected = selectedQuality == PdfQuality.MEDIUM,
                            onSelect = { selectedQuality = PdfQuality.MEDIUM }
                        )

                        QualityOptionRow(
                            title = "کیفیت پایین (فشرده)",
                            subtitle = "حجم بسیار کم برای ارسال با ایمیل",
                            selected = selectedQuality == PdfQuality.LOW,
                            onSelect = { selectedQuality = PdfQuality.LOW }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Primary Action Buttons
                // 1. Export and Share PDF
                Button(
                    onClick = {
                        scope.launch {
                            if (!validateAndSaveTitle()) return@launch
                            isExporting = true
                            val pdfFile = repository.exportDocumentPdf(
                                documentId = documentId,
                                quality = selectedQuality
                            )
                            isExporting = false

                            if (pdfFile != null) {
                                PdfGenerator.sharePdfFile(context, pdfFile)
                            } else {
                                Toast.makeText(context, "خطا در تولید PDF", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .bounceClick(),
                    enabled = !isExporting,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("خروجی و اشتراک‌گذاری PDF", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Direct Save Images to Gallery (JPG)
                Button(
                    onClick = {
                        scope.launch {
                            if (!validateAndSaveTitle()) return@launch
                            isExporting = true
                            val pages = repository.getPagesListForDocument(documentId)
                            val paths = pages.map { it.imagePath }
                            val count = GalleryExporter.saveImagePathsToGallery(
                                context = context,
                                imagePaths = paths,
                                baseName = title.trim()
                            )
                            isExporting = false
                            if (count > 0) {
                                Toast.makeText(
                                    context,
                                    "با موفقیت $count تصویر در گالری ذخیره شد (آلبوم DocScanner)",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(context, "خطا در ذخیره‌سازی تصاویر در گالری", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .bounceClick(),
                    enabled = !isExporting,
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF059669)
                    )
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ذخیره مستقیم تصویر در گالری (JPG)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Save & Return to Document Library
                Button(
                    onClick = {
                        scope.launch {
                            if (validateAndSaveTitle()) {
                                Toast.makeText(context, "سند با موفقیت در کتابخانه ذخیره شد", Toast.LENGTH_SHORT).show()
                                onNavigateHome()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .bounceClick(),
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF97316)
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ذخیره و بازگشت به کتابخانه اسناد", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            if (isExporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

@Composable
fun QualityOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .bounceClick { onSelect() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(subtitle, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B))
        }
    }
}

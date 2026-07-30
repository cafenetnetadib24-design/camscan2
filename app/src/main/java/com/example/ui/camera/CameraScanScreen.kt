package com.example.ui.camera

import android.Manifest
import android.content.Context
import android.util.Log
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.example.ui.components.InteractiveCropView
import com.example.ui.components.EdgeOverlayView
import androidx.compose.foundation.layout.navigationBarsPadding
import com.example.ui.components.bounceClick
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraScanScreen(
    viewModel: CameraViewModel,
    folderId: Long? = null,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (documentId: Long) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var cameraInstance: androidx.camera.core.Camera? by remember { mutableStateOf(null) }

    LaunchedEffect(folderId) {
        if (folderId != null && folderId > 0) {
            viewModel.setFolderId(folderId)
        }
    }

    LaunchedEffect(uiState.newlySavedDocId) {
        uiState.newlySavedDocId?.let { docId ->
            onNavigateToEdit(docId)
            viewModel.clearNewlySavedDocId()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImportedUris(context, uris)
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                cameraProvider.unbindAll()
            } catch (e: Exception) {
                Log.e("CameraScanScreen", "Error unbinding camera on dispose", e)
            }
        }
    }

    LaunchedEffect(uiState.flashMode, imageCapture, cameraInstance) {
        val capture = imageCapture ?: return@LaunchedEffect
        when (uiState.flashMode) {
            FlashMode.OFF -> {
                capture.flashMode = ImageCapture.FLASH_MODE_OFF
                cameraInstance?.cameraControl?.enableTorch(false)
            }
            FlashMode.ON -> {
                capture.flashMode = ImageCapture.FLASH_MODE_ON
                cameraInstance?.cameraControl?.enableTorch(false)
            }
            FlashMode.TORCH -> {
                capture.flashMode = ImageCapture.FLASH_MODE_OFF
                cameraInstance?.cameraControl?.enableTorch(true)
            }
            FlashMode.AUTO -> {
                capture.flashMode = ImageCapture.FLASH_MODE_AUTO
                cameraInstance?.cameraControl?.enableTorch(false)
            }
        }
    }

    // Capture & Fly-to-Thumbnail Animation States
    val coroutineScope = rememberCoroutineScope()
    var currentPreviewView by remember { mutableStateOf<PreviewView?>(null) }
    var shutterFlashVisible by remember { mutableStateOf(false) }
    var flyingBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val flyAnimProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    var thumbnailOffsetInRoot by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var thumbnailSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var rootContainerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var previousCapturedCount by remember { mutableStateOf(uiState.capturedBitmaps.size) }

    LaunchedEffect(shutterFlashVisible) {
        if (shutterFlashVisible) {
            kotlinx.coroutines.delay(80)
            shutterFlashVisible = false
        }
    }

    LaunchedEffect(uiState.capturedBitmaps.size) {
        val currentSize = uiState.capturedBitmaps.size
        if (currentSize > previousCapturedCount && uiState.capturedBitmaps.isNotEmpty()) {
            if (flyingBitmap == null) {
                val newBitmap = uiState.capturedBitmaps.last()
                flyingBitmap = newBitmap
                flyAnimProgress.snapTo(0f)
                flyAnimProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 650,
                        easing = androidx.compose.animation.core.LinearEasing
                    )
                )
                flyingBitmap = null
            }
        }
        previousCapturedCount = currentSize
    }

    ScaffoldCameraContainer(
        hasPermission = cameraPermissionState.status.isGranted,
        onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
        onNavigateBack = onNavigateBack
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onGloballyPositioned { coords -> rootContainerSize = coords.size }
        ) {
            // Camera Preview View
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    currentPreviewView = previewView
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(
                            ContextCompat.getMainExecutor(ctx),
                            CameraFrameAnalyzer { focus, lighting, score, brightness, isDetected ->
                                viewModel.updateFrameMetrics(focus, lighting, score, brightness, isDetected)
                            }
                        )

                        imageCapture = capture

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            val boundCamera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                capture,
                                imageAnalysis
                            )
                            cameraInstance = boundCamera
                        } catch (e: Exception) {
                            Log.e("CameraScanScreen", "Camera binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Live Viewfinder Document Border Edge Overlay with Focus & Lighting Quality Indicators
            EdgeOverlayView(
                isDocumentDetected = uiState.isDocumentDetected,
                focusCondition = uiState.focusCondition,
                lightingCondition = uiState.lightingCondition,
                customStatusText = if (uiState.isAutoCapture && uiState.focusCondition == FocusCondition.SHARP && uiState.lightingCondition == LightingCondition.GOOD) {
                    "برای اسکن خودکار دوربین را ثابت نگه‌دارید..."
                } else null
            )

            // Top Toolbar Controls
            TopCameraToolbar(
                flashMode = uiState.flashMode,
                isAutoCapture = uiState.isAutoCapture,
                isMultiPage = uiState.isMultiPage,
                isColorScan = uiState.isColorScan,
                onToggleFlash = { viewModel.toggleFlashMode() },
                onToggleAuto = { viewModel.toggleAutoCapture() },
                onToggleMultiPage = { viewModel.toggleMultiPage() },
                onToggleColorScan = { viewModel.setScanMode(!uiState.isColorScan) },
                onClose = onNavigateBack,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp)
            )

            // Bottom Shutter & Multi-page Stack Bar
            BottomShutterBar(
                capturedCount = uiState.capturedBitmaps.size,
                lastBitmap = uiState.capturedBitmaps.lastOrNull(),
                isProcessing = uiState.isProcessing,
                flyProgress = flyAnimProgress.value,
                onThumbnailPositioned = { pos, size ->
                    thumbnailOffsetInRoot = pos
                    thumbnailSize = size
                },
                onThumbnailClick = {
                    if (uiState.capturedBitmaps.isNotEmpty()) {
                        viewModel.togglePagePreviewTray(true)
                    }
                },
                onCaptureClick = {
                    val capture = imageCapture ?: return@BottomShutterBar
                    shutterFlashVisible = true

                    when (uiState.flashMode) {
                        FlashMode.ON -> capture.flashMode = ImageCapture.FLASH_MODE_ON
                        FlashMode.OFF -> capture.flashMode = ImageCapture.FLASH_MODE_OFF
                        FlashMode.AUTO -> capture.flashMode = ImageCapture.FLASH_MODE_AUTO
                        FlashMode.TORCH -> capture.flashMode = ImageCapture.FLASH_MODE_OFF
                    }

                    capture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    cameraInstance?.cameraControl?.enableTorch(false)
                                } catch (e: Exception) {
                                    Log.e("CameraScanScreen", "Failed to turn off torch", e)
                                }
                                viewModel.turnOffFlash()
                                viewModel.addCapturedImage(image)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e("CameraScanScreen", "Capture failed: ${exception.message}", exception)
                            }
                        }
                    )
                },
                onProceedClick = {
                    viewModel.saveDocumentAndFinish { docId ->
                        onNavigateToEdit(docId)
                    }
                },
                onGalleryClick = {
                    galleryLauncher.launch("image/*")
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp, start = 20.dp, end = 20.dp)
            )

            // Camera Shutter Flash Overlay Effect
            AnimatedVisibility(
                visible = shutterFlashVisible,
                enter = fadeIn(animationSpec = tween(40)),
                exit = fadeOut(animationSpec = tween(90))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.7f))
                )
            }

            // Flying Thumbnail Animation Overlay (Pops larger than laser frame, then glides into corner square)
            flyingBitmap?.let { bmp ->
                val progress = flyAnimProgress.value
                val startX = rootContainerSize.width / 2f
                val startY = rootContainerSize.height * 0.40f

                val targetX = if (thumbnailOffsetInRoot.x > 0f) {
                    thumbnailOffsetInRoot.x + thumbnailSize.width / 2f
                } else {
                    rootContainerSize.width - 80f
                }
                val targetY = if (thumbnailOffsetInRoot.y > 0f) {
                    thumbnailOffsetInRoot.y + thumbnailSize.height / 2f
                } else {
                    rootContainerSize.height - 100f
                }

                val popThreshold = 0.22f
                val currentX: Float
                val currentY: Float
                val currentScale: Float
                val currentRotation: Float

                if (progress <= popThreshold) {
                    val popFactor = progress / popThreshold
                    // Expands larger than laser frame (up to 1.25x scale)
                    currentScale = 0.95f + 0.30f * popFactor
                    currentX = startX
                    currentY = startY - 15f * popFactor
                    currentRotation = -2f * popFactor
                } else {
                    val flightP = (progress - popThreshold) / (1.0f - popThreshold)
                    val easedFlight = androidx.compose.animation.core.FastOutSlowInEasing.transform(flightP)

                    currentX = startX + (targetX - startX) * easedFlight
                    currentY = (startY - 15f) + (targetY - (startY - 15f)) * easedFlight
                    // Shrinks smoothly from 1.25x down to 0.18x into corner square
                    currentScale = 1.25f - 1.07f * easedFlight
                    currentRotation = -2f - 16f * easedFlight
                }

                val currentAlpha = if (progress > 0.92f) (1f - (progress - 0.92f) / 0.08f) else 1.0f

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = currentX - (rootContainerSize.width / 2f)
                            translationY = currentY - (rootContainerSize.height / 2f)
                            scaleX = currentScale
                            scaleY = currentScale
                            rotationZ = currentRotation
                            alpha = currentAlpha
                        }
                        .size(width = 240.dp, height = 320.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF0F172A))
                        .border(
                            width = if (progress <= popThreshold) 4.dp else 2.dp,
                            color = if (progress <= popThreshold) Color(0xFF10B981) else Color.White,
                            shape = RoundedCornerShape(22.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Continuous Capture Toast Message Overlay
            AnimatedVisibility(
                visible = uiState.toastMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 95.dp)
            ) {
                uiState.toastMessage?.let { msg ->
                    Surface(
                        color = Color(0xEE0F172A),
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(msg, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(2000)
                        viewModel.clearToastMessage()
                    }
                }
            }

            if (uiState.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF))
                }
            }

            // Full-screen Cropping Overlay when a photo is taken
            AnimatedVisibility(
                visible = uiState.pendingCropBitmap != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.fillMaxSize()
            ) {
                uiState.pendingCropBitmap?.let { pendingBitmap ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F172A))
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top Bar for Crop Screen
                            TopAppBar(
                                title = {
                                    Text(
                                        text = "تنظیم و برش کادر سند",
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { viewModel.cancelPendingCrop() }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "انصراف",
                                            tint = Color.White
                                        )
                                    }
                                },
                                actions = {
                                    TextButton(onClick = { viewModel.confirmCropAndProceed() }) {
                                        Text(
                                            text = "تایید و ادامه",
                                            color = Color(0xFF38BDF8),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color(0xFF1E293B)
                                )
                            )

                            // Interactive 4-Corner Crop View
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                InteractiveCropView(
                                    bitmap = pendingBitmap,
                                    topLeft = uiState.cropTopLeft,
                                    topRight = uiState.cropTopRight,
                                    bottomRight = uiState.cropBottomRight,
                                    bottomLeft = uiState.cropBottomLeft,
                                    onCropQuadChanged = { tl, tr, br, bl ->
                                        viewModel.updateCropQuad(tl, tr, br, bl)
                                    }
                                )
                            }

                            // Bottom Controls Toolbar
                            Surface(
                                color = Color(0xFF1E293B),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                        // Auto Detect
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable { viewModel.autoDetectCropQuad() }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = "خودکار",
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("خودکار", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Rotate
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable { viewModel.rotatePendingCropBitmap() }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RotateRight,
                                                contentDescription = "چرخش",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("چرخش", color = Color.White, fontSize = 11.sp)
                                        }

                                        // Full Reset
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable { viewModel.resetCropQuad() }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Crop,
                                                contentDescription = "بازنشانی",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("کامل", color = Color.White, fontSize = 11.sp)
                                        }
                                    }

                                    // Primary CTA Button
                                    Button(
                                        onClick = { viewModel.confirmCropAndProceed() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF2563EB)
                                        ),
                                        shape = RoundedCornerShape(24.dp),
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "تایید و برش",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Tray for Continuous Scanned Pages Review
    if (uiState.showPagePreviewTray) {
        PagePreviewTraySheet(
            capturedBitmaps = uiState.capturedBitmaps,
            onDismiss = { viewModel.togglePagePreviewTray(false) },
            onRemovePage = { idx -> viewModel.removeCapturedPage(idx) },
            onClearAll = { viewModel.clearAllPages() },
            onProceed = {
                viewModel.togglePagePreviewTray(false)
                viewModel.saveDocumentAndFinish { docId ->
                    onNavigateToEdit(docId)
                }
            }
        )
    }
}

@Composable
fun TopCameraToolbar(
    flashMode: FlashMode,
    isAutoCapture: Boolean,
    isMultiPage: Boolean,
    isColorScan: Boolean,
    onToggleFlash: () -> Unit,
    onToggleAuto: () -> Unit,
    onToggleMultiPage: () -> Unit,
    onToggleColorScan: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Navigation & Flash Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = Color(0x990F172A),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت", tint = Color.White)
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0x990F172A),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "اسکنر اسناد",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            Surface(
                onClick = onToggleFlash,
                shape = CircleShape,
                color = Color(0x990F172A),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (flashMode) {
                            FlashMode.OFF -> Icons.Default.FlashOff
                            FlashMode.ON -> Icons.Default.FlashOn
                            FlashMode.TORCH -> Icons.Default.Highlight
                            FlashMode.AUTO -> Icons.Default.FlashAuto
                        },
                        contentDescription = "فلاش",
                        tint = if (flashMode != FlashMode.OFF) Color(0xFF00E5FF) else Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Mode Selector Chips Bar
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xBB0F172A),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color Scan Toggle Chip
                Surface(
                    onClick = onToggleColorScan,
                    shape = RoundedCornerShape(18.dp),
                    color = if (isColorScan) Color(0xFFE11D48) else Color(0x22FFFFFF)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isColorScan) "تصویر رنگی" else "اسناد B/W",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Auto Capture Toggle Chip
                Surface(
                    onClick = onToggleAuto,
                    shape = RoundedCornerShape(18.dp),
                    color = if (isAutoCapture) Color(0xFF2563EB) else Color(0x22FFFFFF)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAutoCapture) "خودکار" else "دستی",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Multi-page Toggle Chip
                Surface(
                    onClick = onToggleMultiPage,
                    shape = RoundedCornerShape(18.dp),
                    color = if (isMultiPage) Color(0xFF059669) else Color(0x22FFFFFF)
                ) {
                    Text(
                        text = if (isMultiPage) "چند صفحه‌ای" else "تک صفحه‌ای",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BottomShutterBar(
    capturedCount: Int,
    lastBitmap: android.graphics.Bitmap?,
    isProcessing: Boolean,
    flyProgress: Float = 0f,
    onThumbnailPositioned: (androidx.compose.ui.geometry.Offset, androidx.compose.ui.unit.IntSize) -> Unit = { _, _ -> },
    onThumbnailClick: () -> Unit,
    onCaptureClick: () -> Unit,
    onProceedClick: () -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbnailBounceScale = if (flyProgress in 0.75f..1.0f) {
        1f + 0.28f * kotlin.math.sin((flyProgress - 0.75f) / 0.25f * kotlin.math.PI.toFloat())
    } else 1f

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = Color(0xCC0F172A),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail Stack Badge
            Box(
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        onThumbnailPositioned(coords.positionInRoot(), coords.size)
                    }
                    .graphicsLayer {
                        scaleX = thumbnailBounceScale
                        scaleY = thumbnailBounceScale
                    }
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x66000000))
                    .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                    .bounceClick { if (capturedCount > 0) onThumbnailClick() },
                contentAlignment = Alignment.Center
            ) {
                if (lastBitmap != null) {
                    Image(
                        bitmap = lastBitmap.asImageBitmap(),
                        contentDescription = "پیش‌نمایش",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(Color(0xFF2563EB), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$capturedCount",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text("0", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Center Big Shutter Button
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2563EB))
                    .bounceClick { if (!isProcessing) onCaptureClick() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }

            // Proceed or Gallery Import Button
            if (capturedCount > 0) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF059669))
                        .bounceClick { if (!isProcessing) onProceedClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "تایید اسکن",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0x44FFFFFF))
                        .bounceClick { if (!isProcessing) onGalleryClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = "انتخاب از گالری",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Text("گالری", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagePreviewTraySheet(
    capturedBitmaps: List<android.graphics.Bitmap>,
    onDismiss: () -> Unit,
    onRemovePage: (Int) -> Unit,
    onClearAll: () -> Unit,
    onProceed: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "برگه‌های اسکن شده (${capturedBitmaps.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (capturedBitmaps.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("پاکسازی همه", color = Color(0xFFFF5252), fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (capturedBitmaps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("هیچ برگه‌ای اسکن نشده است", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(capturedBitmaps) { idx, bitmap ->
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.5.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Page ${idx + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Page Number Badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .background(Color(0xCC000000), RoundedCornerShape(topEnd = 8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "برگه ${idx + 1}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Remove Page Icon
                            IconButton(
                                onClick = { onRemovePage(idx) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(28.dp)
                                    .background(Color(0xDDFF5252), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove page",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Text("افزودن برگه بیشتر", color = Color.White, fontSize = 13.sp)
                }

                Button(
                    onClick = onProceed,
                    enabled = capturedBitmaps.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A86B))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تایید و ساخت سند", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ScaffoldCameraContainer(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNavigateBack: () -> Unit,
    content: @Composable () -> Unit
) {
    if (hasPermission) {
        content()
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "دسترسی به دوربین نیاز است",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "برنامه اسکنر اسناد برای اسکن، تشخیص خودکار حاشیه‌ها و پردازش اسناد به دسترسی دوربین نیاز دارد.",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onRequestPermission) {
                    Text("اعطای دسترسی به دوربین")
                }
            }
        }
    }
}

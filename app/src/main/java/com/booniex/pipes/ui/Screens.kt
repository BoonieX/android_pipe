package com.booniex.pipes.ui

import android.Manifest
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.ExifInterface
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.booniex.pipes.data.BundleSide
import com.booniex.pipes.data.NormPoint
import com.booniex.pipes.data.PipeBox
import com.booniex.pipes.data.ScanSession
import com.booniex.pipes.data.SelectionArea
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class Route {
    data object Home : Route()
    data object Scan : Route()
    data object Result : Route()
    data object Verify : Route()
    data object History : Route()
}

@Composable
fun PipeCounterApp(
    vm: ScanViewModel,
    onRegisterVolumeCapture: ((() -> Boolean)?) -> Unit = {},
) {
    var route by remember { mutableStateOf<Route>(Route.Home) }
    val state by vm.state.collectAsStateWithLifecycle()

    when (route) {
        Route.Home -> HomeScreen(
            onScan = {
                vm.startNewScan()
                route = Route.Scan
            },
            onHistory = {
                vm.refreshHistory()
                route = Route.History
            },
            onExportCsv = vm::exportCsv,
        )
        Route.Scan -> ScanScreen(
            state = state,
            onBack = { route = Route.Home },
            onSide = vm::goToSide,
            onAddFrame = vm::addFrame,
            onRemoveLastFrame = vm::removeLastFrame,
            onRemoveFrame = vm::removeFrame,
            onProcessFrames = vm::processFrames,
            onRedo = vm::redoCurrentSide,
            onRegisterVolumeCapture = onRegisterVolumeCapture,
            onFinish = {
                if (state.hasAnySideDone) {
                    vm.buildResultSession()
                    route = Route.Result
                }
            },
        )
        Route.Result -> ResultScreen(
            state = state,
            onBack = { route = Route.Home },
            onVerify = { route = Route.Verify },
            onSave = {
                vm.saveActive()
                route = Route.Home
            },
            onRedo = {
                vm.startNewScan()
                route = Route.Scan
            },
        )
        Route.Verify -> VerifyScreen(
            session = state.activeSession,
            onBack = { route = Route.Result },
            onConfirm = { ok, note ->
                vm.confirm(ok, note)
                route = Route.Home
            },
            onSelection = vm::saveSelection,
        )
        Route.History -> HistoryScreen(
            sessions = state.history,
            onBack = { route = Route.Home },
            onOpen = { id ->
                vm.loadSession(id)
                route = Route.Result
            },
        )
    }
}

@Composable
private fun HomeScreen(onScan: () -> Unit, onHistory: () -> Unit, onExportCsv: () -> Unit) {
    Scaffold { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("PipeCounter", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Hitung pipa bundle · verifikasi foto", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Text("Mulai Pemindaian Baru")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
                Text("Riwayat")
            }
//            Spacer(Modifier.height(12.dp))
//            OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
//                Text("Export CSV")
//            }
        }
    }
}

/** Status fokus kamera untuk indikator UI (F-23). */
private enum class FocusHint { IDLE, FOCUSING, FOCUSED, FAILED }

private enum class OverlayDirection {
    LEFT,
    RIGHT,
}

@Composable
private fun ScanScreen(
    state: ScanUiState,
    onBack: () -> Unit,
    onSide: (Int) -> Unit,
    onAddFrame: (String) -> Unit,
    onRemoveLastFrame: () -> Unit,
    onRemoveFrame: (Int) -> Unit,
    onProcessFrames: () -> Unit,
    onRedo: () -> Unit,
    onRegisterVolumeCapture: ((() -> Boolean)?) -> Unit,
    onFinish: () -> Unit,
) {
    val ctx = LocalContext.current
    var hasCam by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCam = it
    }
    LaunchedEffect(Unit) {
        if (!hasCam) perm.launch(Manifest.permission.CAMERA)
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var showFrameOverlay by remember { mutableStateOf(true) }
    var overlayDirection by remember { mutableStateOf(OverlayDirection.RIGHT) }
    // Exposure dikunci (index kompensasi konstan) begitu frame pertama sisi diambil (F-19)
    var exposureLock by remember { mutableStateOf<Int?>(null) }
    var captureLocked by remember { mutableStateOf(false) }
    val captureGate = remember { AtomicBoolean(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val latestCamera by rememberUpdatedState(camera)
    val sensorManager = remember {
        ctx.getSystemService(SensorManager::class.java)
    }
    var tilt by remember { mutableStateOf(TiltState()) }

    DisposableEffect(Unit) {
        onDispose {
            latestCamera?.cameraControl?.enableTorch(false)
        }
    }

    DisposableEffect(sensorManager) {
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = if (sensor != null) {
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    tilt = smoothTilt(tilt, rotationVectorToTilt(event.values))
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
        } else {
            null
        }
        if (sensor != null && listener != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            if (listener != null) sensorManager?.unregisterListener(listener)
        }
    }

    // Adaptif: saat belum multi-frame, perbaiki exposure berdasarkan kualitas frame terakhir
    LaunchedEffect(state.lastFrameQuality, camera, exposureLock) {
        val q = state.lastFrameQuality ?: return@LaunchedEffect
        if (exposureLock != null) return@LaunchedEffect // mode multi-frame: jangan ubah
        val cam = camera ?: return@LaunchedEffect
        val info = cam.cameraInfo
        val range = info.exposureState.exposureCompensationRange
        val current = info.exposureState.exposureCompensationIndex
        if (q.isTooDark && current < range.upper) {
            cam.cameraControl.setExposureCompensationIndex(current + 1)
        } else if (q.isTooBright && current > range.lower) {
            cam.cameraControl.setExposureCompensationIndex(current - 1)
        }
    }

    // Lock exposure saat frame pertama sisi tersimpan; lepas saat semua frame diproses
    LaunchedEffect(state.currentFrames.size, camera) {
        exposureLock = if (state.currentFrames.isNotEmpty()) {
            camera?.cameraInfo?.exposureState?.exposureCompensationIndex ?: exposureLock
        } else {
            null
        }
    }

    // F-23: hint fokus kamera — auto-fokus berkala + tap-to-focus manual
    var focusHint by remember { mutableStateOf(FocusHint.IDLE) }
    var tapFocusPos by remember { mutableStateOf<Offset?>(null) }
    val detectingNow by rememberUpdatedState(state.detecting)
    val focusScope = rememberCoroutineScope()

    val captureFrame = {
        if (!hasCam || camera == null || state.detecting || !captureGate.compareAndSet(false, true)) {
            false
        } else {
            captureLocked = true
            // Tombol layar dan volume + memakai jalur capture yang sama.
            focusHint = FocusHint.IDLE
            tapFocusPos = null
            val dir = File(ctx.cacheDir, "scans").also { it.mkdirs() }
            val file = File(dir, "frame_${state.currentSide.name}_${System.currentTimeMillis()}.jpg")
            val opts = ImageCapture.OutputFileOptions.Builder(file).build()
            imageCapture.takePicture(
                opts,
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        captureGate.set(false)
                        mainHandler.post {
                            captureLocked = false
                            onAddFrame(file.absolutePath)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        captureGate.set(false)
                        mainHandler.post { captureLocked = false }
                        // User can retry with the button or volume +.
                    }
                },
            )
            true
        }
    }
    val latestCaptureFrame by rememberUpdatedState(captureFrame)
    DisposableEffect(Unit) {
        onRegisterVolumeCapture { latestCaptureFrame() }
        onDispose { onRegisterVolumeCapture(null) }
    }

    LaunchedEffect(camera, state.detecting) {
        while (camera != null) {
            val cam = camera ?: break
            val pv = previewView
            // Auto-fokus hanya saat belum fokus sukses (IDLE/FAILED); berhenti setelah FOCUSED
            if (!state.detecting && pv != null && focusHint != FocusHint.FOCUSED) {
                focusHint = FocusHint.FOCUSING
                triggerFocus(cam, pv.meteringPointFactory, 0.5f, 0.5f, executor) { ok ->
                    focusHint = if (ok) FocusHint.FOCUSED else FocusHint.FAILED
                }
            }
            delay(1500)
        }
    }
    // Jarak berubah saat ganti sisi → minta fokus ulang
    LaunchedEffect(state.currentSideIndex) {
        focusHint = FocusHint.IDLE
        tapFocusPos = null
        showFrameOverlay = true
        overlayDirection = OverlayDirection.RIGHT
    }

    Scaffold { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Batal") }
                Text("Sisi: ${state.currentSide.label}", fontWeight = FontWeight.SemiBold)
                TextButton(
                    onClick = onFinish,
                    enabled = state.hasAnySideDone && !state.detecting,
                ) { Text("Hasil") }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.scanOrder.forEachIndexed { i, side ->
                    val done = state.sides.containsKey(side)
                    FilterChip(
                        selected = i == state.currentSideIndex,
                        onClick = { onSide(i) },
                        label = {
                            Text(
                                if (done) "${side.label} ✓${state.sides[side]?.count}"
                                else side.label
                            )
                        },
                    )
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (hasCam) {
                    CameraPreview(
                        imageCapture = imageCapture,
                        referencePath = state.currentFrames.lastOrNull(),
                        showReference = showFrameOverlay && !state.detecting,
                        overlayDirection = overlayDirection,
                        onCamera = { camera = it },
                        onPreviewView = { previewView = it },
                        onTap = { norm ->
                            val cam = camera ?: return@CameraPreview
                            val pv = previewView ?: return@CameraPreview
                            if (!detectingNow) {
                                // Tap = tandai titik, reset ke putih, lalu kuning (proses) → hijau/merah (hasil)
                                tapFocusPos = norm
                                focusHint = FocusHint.IDLE
                                focusScope.launch {
                                    delay(120)
                                    if (!detectingNow) {
                                        focusHint = FocusHint.FOCUSING
                                        triggerFocus(cam, pv.meteringPointFactory, norm.x, norm.y, executor) { ok ->
                                            focusHint = if (ok) FocusHint.FOCUSED else FocusHint.FAILED
                                        }
                                    }
                                }
                            }
                        },
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width * 0.8f
                        val h = size.height * 0.55f
                        val left = (size.width - w) / 2
                        val top = (size.height - h) / 2
                        val guideColor = when (focusHint) {
                            FocusHint.IDLE -> Color.White.copy(alpha = 0.85f)
                            FocusHint.FOCUSING -> Color(0xFFFFC107)
                            FocusHint.FOCUSED -> Color(0xFF00E676)
                            FocusHint.FAILED -> Color(0xFFF44336)
                        }
                        drawRect(
                            color = guideColor,
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                            style = Stroke(width = 4f),
                        )
                        tapFocusPos?.let { p ->
                            val f = Offset(p.x * size.width, p.y * size.height)
                            val focusColor = when (focusHint) {
                                FocusHint.IDLE -> Color.White
                                FocusHint.FOCUSING -> Color(0xFFFFC107)
                                FocusHint.FOCUSED -> Color(0xFF00E676)
                                FocusHint.FAILED -> Color(0xFFF44336)
                            }
                            drawCircle(
                                color = focusColor,
                                radius = 28.dp.toPx(),
                                center = f,
                                style = Stroke(width = 3f),
                            )
                            drawCircle(
                                color = focusColor,
                                radius = 4.dp.toPx(),
                                center = f,
                                style = androidx.compose.ui.graphics.drawscope.Fill,
                            )
                        }
                    }
                    if (focusHint != FocusHint.IDLE) {
                        val (label, color) = when (focusHint) {
                            FocusHint.FOCUSING -> "Memfokuskan…" to Color(0xFFFFC107)
                            FocusHint.FOCUSED -> "Fokus OK" to Color(0xFF00E676)
                            FocusHint.FAILED -> "Fokus gagal — ketuk layar" to Color(0xFFF44336)
                            FocusHint.IDLE -> "" to Color.Transparent
                        }
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(label, color = color, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    TiltIndicator(tilt, Modifier.align(Alignment.TopStart).padding(8.dp))
                if (state.currentFrames.isNotEmpty() && !state.detecting) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            TextButton(onClick = { showFrameOverlay = !showFrameOverlay }) {
                                Text("Overlay: ${if (showFrameOverlay) "Nyala" else "Mati"}")
                            }
                            if (showFrameOverlay) {
                                TextButton(
                                    onClick = {
                                        overlayDirection = when (overlayDirection) {
                                            OverlayDirection.LEFT -> OverlayDirection.RIGHT
                                            OverlayDirection.RIGHT -> OverlayDirection.LEFT
                                        }
                                    },
                                ) {
                                    Text(
                                        if (overlayDirection == OverlayDirection.RIGHT) {
                                            "Geser ke kanan  →"
                                        } else {
                                            "←  Geser ke kiri"
                                        },
                                    )
                                }
                            }
                    }
                }
                if (captureLocked) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("Menyimpan foto…", color = Color.White)
                    }
                }
                } else {
                    Text("Izin kamera diperlukan", Modifier.align(Alignment.Center))
                }
                if (state.detecting) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                         Text(
                             when (state.processingStage) {
                                 ProcessingStage.STITCHING -> "Menggabungkan frame…"
                                 ProcessingStage.DETECTING -> "Menghitung pipa…"
                                 ProcessingStage.FALLBACK -> "Stitching gagal, menghitung frame terbaik…"
                                 ProcessingStage.IDLE -> "Memproses…"
                             },
                         )
                        }
                    }
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
            }
            state.sides[state.currentSide]?.let { scan ->
                Text(
                    "Terakhir ${scan.side.label}: ${scan.count} pipa",
                    Modifier.padding(horizontal = 16.dp),
                )
            }
            if (state.currentFrames.isNotEmpty()) {
                Text(
                    "Frame: ${state.currentFrames.size} — geser perlahan, overlap ±30%",
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                state.lastOverlap?.let { overlap ->
                    Text(
                        "Overlap terakhir: ${(overlap.overlapRatio * 100).toInt()}% · ${overlap.message}",
                        Modifier.padding(horizontal = 16.dp),
                        color = if (overlap.accepted) {
                            Color(0xFF2E7D32)
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.currentFrames.size) { index ->
                        val frame = state.currentFrames[index]
                        val bitmap = remember(frame) { decodeReferenceBitmap(frame) }
                        Box(
                            modifier = Modifier
                                .width(76.dp)
                                .height(76.dp),
                        ) {
                            bitmap?.let {
                                androidx.compose.foundation.Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Frame ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Text(
                                "${index + 1}",
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                            TextButton(
                                onClick = { onRemoveFrame(index) },
                                modifier = Modifier.align(Alignment.BottomCenter),
                            ) { Text("Hapus", color = Color.White) }
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        torchOn = !torchOn
                        camera?.cameraControl?.enableTorch(torchOn)
                    },
                    enabled = hasCam && !state.detecting && !captureLocked,
                    modifier = Modifier.weight(1f),
                ) { Text(if (torchOn) "Lampu: Nyala" else "Lampu") }
                OutlinedButton(
                    onClick = onRemoveLastFrame,
                    enabled = state.currentFrames.isNotEmpty() && !state.detecting,
                    modifier = Modifier.weight(1f),
                ) { Text("Batal Frame") }
                Button(
                    onClick = { captureFrame() },
                    enabled = hasCam && !state.detecting && !captureLocked,
                    modifier = Modifier.weight(1f),
                ) { Text("Ambil Foto") }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onRedo,
                    enabled = state.sides.containsKey(state.currentSide) && !state.detecting,
                    modifier = Modifier.weight(1f),
                ) { Text("Ulangi sisi") }
                Button(
                    onClick = onProcessFrames,
                    enabled = state.currentFrames.isNotEmpty() && !state.detecting,
                    modifier = Modifier.weight(2f),
                ) { Text("Gabung & Hitung") }
            }
        }
    }
}

@Composable
private fun TiltIndicator(tilt: TiltState, modifier: Modifier = Modifier) {
    val (label, color) = when {
        !tilt.available -> "Sensor sudut tidak tersedia" to Color.LightGray
        tilt.isTooTilted -> "Atur ke 90° · ${tilt.angle.toInt()}°" to Color(0xFFF44336)
        tilt.isWarning -> "Mendekati 90° · ${tilt.angle.toInt()}°" to Color(0xFFFFC107)
        else -> "Sudut OK · ${tilt.angle.toInt()}°" to Color(0xFF00E676)
    }
    Text(
        label,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun CameraPreview(
    imageCapture: ImageCapture,
    referencePath: String?,
    showReference: Boolean,
    overlayDirection: OverlayDirection,
    onCamera: (Camera) -> Unit,
    onPreviewView: (PreviewView) -> Unit,
    onTap: (Offset) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                onPreviewView(previewView)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    provider.unbindAll()
                    val cam = provider.bindToLifecycle(
                        lifecycleOwner,
                        androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                    )
                    onCamera(cam)
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onTap(Offset(offset.x / size.width, offset.y / size.height))
                    }
                },
        )
        if (showReference && referencePath != null) {
            FrameReferenceOverlay(referencePath, overlayDirection)
        }
    }
}

@Composable
private fun FrameReferenceOverlay(path: String, direction: OverlayDirection) {
    val bitmap = remember(path) { decodeReferenceBitmap(path) }
    bitmap?.let {
        val isRight = direction == OverlayDirection.RIGHT
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .fillMaxHeight()
                    .align(if (isRight) Alignment.CenterStart else Alignment.CenterEnd),
            ) {
                val image = it.asImageBitmap()
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.12f)),
                ) {
                    val edgeWidth = (image.width * 0.35f).toInt().coerceAtLeast(1)
                    val sourceLeft = if (isRight) image.width - edgeWidth else 0
                    drawImage(
                        image = image,
                        srcOffset = androidx.compose.ui.unit.IntOffset(sourceLeft, 0),
                        srcSize = androidx.compose.ui.unit.IntSize(edgeWidth, image.height),
                        dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                        alpha = 0.38f,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = if (isRight) {
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.08f))
                                } else {
                                    listOf(Color.Black.copy(alpha = 0.08f), Color.Transparent)
                                },
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .align(if (isRight) Alignment.CenterEnd else Alignment.CenterStart)
                        .background(Color(0xFFFFC107)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (isRight) "Tepi kanan frame lama\nGeser ke kanan →" else "← Geser ke kiri\nTepi kiri frame lama",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** Decode only enough pixels for the preview, then apply the JPEG EXIF orientation. */
private fun decodeReferenceBitmap(path: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = 1
        while (bounds.outWidth / (inSampleSize * 2) >= 800 &&
            bounds.outHeight / (inSampleSize * 2) >= 800
        ) {
            inSampleSize *= 2
        }
    }
    val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
    val matrix = Matrix()
    when (runCatching { ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        .getOrDefault(ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
    }
    return if (matrix.isIdentity) bitmap else Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true,
    ).also { if (it !== bitmap) bitmap.recycle() }
}

@Composable
private fun ResultScreen(
    state: ScanUiState,
    onBack: () -> Unit,
    onVerify: () -> Unit,
    onSave: () -> Unit,
    onRedo: () -> Unit,
) {
    val session = state.activeSession
    val sides = session?.sides ?: state.sides.values.toList()
    val finalEst = session?.finalEstimate ?: state.finalEstimate

    Scaffold { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Beranda") }
            Text("Hasil Pemindaian", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            sides.forEach { s ->
                Text("${s.side.label}: ${s.count}", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Estimasi final (max): $finalEst batang",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            session?.confirmed?.let {
                Text(if (it) "Status: dikonfirmasi" else "Status: ada masalah${session.note?.let { n -> " — $n" } ?: ""}")
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onVerify, modifier = Modifier.fillMaxWidth(), enabled = sides.isNotEmpty()) {
                Text("Verifikasi (foto + overlay)")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Simpan")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRedo, modifier = Modifier.fillMaxWidth()) {
                Text("Ulangi")
            }
        }
    }
}

@Composable
private fun VerifyScreen(
    session: ScanSession?,
    onBack: () -> Unit,
    onConfirm: (Boolean, String?) -> Unit,
    onSelection: (BundleSide, SelectionArea?) -> Unit,
) {
    if (session == null || session.sides.isEmpty()) {
        Column(Modifier.padding(16.dp)) {
            Text("Tidak ada sesi")
            TextButton(onClick = onBack) { Text("Kembali") }
        }
        return
    }
    var sideIndex by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    var selectionMode by remember { mutableStateOf(false) }
    val side = session.sides[sideIndex.coerceIn(0, session.sides.lastIndex)]

    Scaffold { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) { Text("Kembali") }
                Text("Verifikasi · ${side.side.label} · ${side.count}", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier)
            }
            Row(
                Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                session.sides.forEachIndexed { i, s ->
                    FilterChip(
                        selected = i == sideIndex,
                        onClick = { sideIndex = i },
                        label = { Text(s.side.label) },
                    )
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                key(selectionMode) {
                    StillOverlay(
                        photoPath = side.photoPath,
                        boxes = side.boxes,
                        selection = side.selection,
                        selectionMode = selectionMode,
                        onSelectionComplete = { area ->
                            onSelection(side.side, area)
                        },
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selectionMode) {
                        "Satu jari: seleksi area · dua jari: zoom/geser"
                    } else if (side.selection != null) {
                        "Area terpilih: ${side.selection.count} dari ${side.count} pipa"
                    } else {
                        "Total terdeteksi: ${side.count} pipa"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            selectionMode = !selectionMode
                        },
                    ) { Text(if (selectionMode) "Selesai" else "Seleksi Area") }
                    OutlinedButton(
                        onClick = { onSelection(side.side, null) },
                        enabled = side.selection != null,
                    ) { Text("Reset") }
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Catatan (opsional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { onConfirm(false, note) },
                    modifier = Modifier.weight(1f),
                ) { Text("Laporkan masalah") }
                Button(
                    onClick = { onConfirm(true, note.ifBlank { null }) },
                    modifier = Modifier.weight(1f),
                ) { Text("Konfirmasi") }
            }
        }
    }
}

@Composable
fun StillOverlay(
    photoPath: String,
    boxes: List<PipeBox>,
    selection: SelectionArea? = null,
    selectionMode: Boolean = false,
    onSelectionComplete: (SelectionArea) -> Unit = {},
) {
    val bmp = remember(photoPath) {
        BitmapFactory.decodeFile(photoPath)?.asImageBitmap()
    }
    var scale by remember(photoPath, selectionMode) { mutableFloatStateOf(1f) }
    var offsetX by remember(photoPath, selectionMode) { mutableFloatStateOf(0f) }
    var offsetY by remember(photoPath, selectionMode) { mutableFloatStateOf(0f) }
    // Draft polygon (normalized) sedang digambar; reset saat ganti foto/mode.
    var draft by remember(photoPath, selectionMode) { mutableStateOf<List<NormPoint>>(emptyList()) }

    if (bmp == null) {
        Text("Foto tidak ditemukan", Modifier.padding(16.dp))
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(selectionMode, photoPath) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var transforming = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }

                        if (pressed.size >= 2) {
                            if (!transforming) {
                                transforming = true
                                // Sentuhan kedua berarti pengguna bermaksud zoom, bukan lasso.
                                draft = emptyList()
                            }
                            val oldScale = scale
                            val newScale = (oldScale * event.calculateZoom()).coerceIn(1f, 5f)
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val center = Offset(size.width / 2f, size.height / 2f)
                            scale = newScale
                            offsetX += event.calculatePan().x +
                                (centroid.x - center.x) * (1f - newScale / oldScale)
                            offsetY += event.calculatePan().y +
                                (centroid.y - center.y) * (1f - newScale / oldScale)
                            event.changes.forEach { it.consume() }
                        } else if (selectionMode && pressed.size == 1 && !transforming) {
                            val change = pressed.first()
                            val point = toNormPoint(
                                change.position,
                                bmp,
                                size,
                                scale,
                                offsetX,
                                offsetY,
                            )
                            if (draft.isEmpty()) draft = listOf(point)
                            else if (change.position != change.previousPosition) draft = draft + point
                            change.consume()
                        } else if (pressed.isEmpty()) {
                            if (!transforming && draft.size >= 3) {
                                val count = boxes.count { b ->
                                    pointInPolygon(b.x + b.w / 2f, b.y + b.h / 2f, draft)
                                }
                                onSelectionComplete(SelectionArea(polygon = draft, count = count))
                            }
                            break
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            }
    ) {
        androidx.compose.foundation.Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Canvas(Modifier.fillMaxSize()) {
            // Fit letterbox mapping: ContentScale.Fit
            val bw = bmp.width.toFloat()
            val bh = bmp.height.toFloat()
            val scaleFit = minOf(size.width / bw, size.height / bh)
            val dw = bw * scaleFit
            val dh = bh * scaleFit
            val left = (size.width - dw) / 2f
            val top = (size.height - dh) / 2f

            val activePolygon = if (selectionMode && draft.isNotEmpty()) draft
            else selection?.polygon.orEmpty()
            boxes.forEachIndexed { i, b ->
                val cx = b.x + b.w / 2f
                val cy = b.y + b.h / 2f
                val selected = activePolygon.isNotEmpty() && pointInPolygon(cx, cy, activePolygon)
                val x = left + b.x * dw
                val y = top + b.y * dh
                val w = b.w * dw
                val h = b.h * dh
                drawRect(
                    color = if (selected) Color(0xFFFFEB3B)
                    else Color(0xFF00E676).copy(alpha = if (activePolygon.isNotEmpty()) 0.5f else 1f),
                    topLeft = Offset(x, y),
                    size = Size(w, h),
                    style = Stroke(width = if (selected) 4f else 3f),
                )
            }
            if (activePolygon.isNotEmpty()) {
                val path = Path()
                activePolygon.forEachIndexed { i, p ->
                    val px = left + p.x * dw
                    val py = top + p.y * dh
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                drawPath(path, color = Color.White.copy(alpha = 0.25f))
                drawPath(path, color = Color.White, style = Stroke(width = 3f))
            }
        }
    }
}

/** Konversi offset layar -> koordinat normalized foto (invers ContentScale.Fit). */
private fun toNormPoint(
    off: Offset,
    bmp: androidx.compose.ui.graphics.ImageBitmap,
    viewSize: androidx.compose.ui.unit.IntSize,
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
): NormPoint {
    val center = Offset(viewSize.width / 2f, viewSize.height / 2f)
    val base = Offset(
        x = (off.x - center.x - offsetX) / scale + center.x,
        y = (off.y - center.y - offsetY) / scale + center.y,
    )
    val bw = bmp.width.toFloat()
    val bh = bmp.height.toFloat()
    val scaleFit = minOf(viewSize.width / bw, viewSize.height / bh)
    val dw = bw * scaleFit
    val dh = bh * scaleFit
    val left = (viewSize.width - dw) / 2f
    val top = (viewSize.height - dh) / 2f
    return NormPoint(
        x = ((base.x - left) / dw).coerceIn(0f, 1f),
        y = ((base.y - top) / dh).coerceIn(0f, 1f),
    )
}

/** Ray-casting: titik (x,y) di dalam poligon normalized? */
private fun pointInPolygon(x: Float, y: Float, poly: List<NormPoint>): Boolean {
    if (poly.size < 3) return false
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val xi = poly[i].x
        val yi = poly[i].y
        val xj = poly[j].x
        val yj = poly[j].y
        if ((yi > y) != (yj > y) &&
            x < (xj - xi) * (y - yi) / (yj - yi) + xi
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

/**
 * Picu focus + metering CameraX di titik normalized (0..1) lalu laporkan hasilnya.
 * Aman dipanggil dari auto-fokus berkala maupun tap-to-focus.
 * Catatan: isFocusSuccessful tidak andal di banyak perangkat, jadi AF scan yang
 * selesai tanpa error dianggap sukses; FAILED hanya untuk error nyata.
 */
private fun triggerFocus(
    cam: Camera,
    factory: MeteringPointFactory,
    x: Float,
    y: Float,
    executor: Executor,
    onResult: (Boolean) -> Unit,
) {
    val action = FocusMeteringAction.Builder(factory.createPoint(x, y))
        .setAutoCancelDuration(3000, TimeUnit.MILLISECONDS)
        .build()
    if (!cam.cameraInfo.isFocusMeteringSupported(action)) {
        // Perangkat tanpa AF (fixed focus) — tidak ada proses fokus; anggap siap
        onResult(true)
        return
    }
    val future = try {
        cam.cameraControl.startFocusAndMetering(action)
    } catch (e: Exception) {
        onResult(false)
        return
    }
    future.addListener({
        onResult(true)
    }, executor)
}

@Composable
private fun HistoryScreen(
    sessions: List<ScanSession>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val fmt = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()) }
    Scaffold { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            TextButton(onClick = onBack) { Text("← Beranda") }
            Text(
                "Riwayat",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (sessions.isEmpty()) {
                Text("Belum ada data", Modifier.padding(16.dp))
            } else {
                LazyColumn {
                    items(sessions, key = { it.id }) { s ->
                        OutlinedButton(
                            onClick = { onOpen(s.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(fmt.format(Date(s.timestampMs)), fontWeight = FontWeight.SemiBold)
                                Text("Estimasi: ${s.finalEstimate} · ${s.sides.size} sisi")
                            }
                        }
                    }
                }
            }
        }
    }
}

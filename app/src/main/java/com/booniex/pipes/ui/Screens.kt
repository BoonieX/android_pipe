package com.booniex.pipes.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import com.booniex.pipes.data.PipeBox
import com.booniex.pipes.data.ScanSession
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

sealed class Route {
    data object Home : Route()
    data object Scan : Route()
    data object Result : Route()
    data object Verify : Route()
    data object History : Route()
}

@Composable
fun PipeCounterApp(vm: ScanViewModel) {
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
            onDetected = { path ->
                vm.detectPhoto(path)
            },
            onRedo = vm::redoCurrentSide,
            onFinish = {
                if (state.allRequiredDone) {
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
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                Text("Export CSV")
            }
        }
    }
}

@Composable
private fun ScanScreen(
    state: ScanUiState,
    onBack: () -> Unit,
    onSide: (Int) -> Unit,
    onDetected: (String) -> Unit,
    onRedo: () -> Unit,
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
                    enabled = state.allRequiredDone && !state.detecting,
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
                    CameraPreview(imageCapture = imageCapture)
                    // Guide frame
                    Box(
                        Modifier
                            .fillMaxSize(0.85f)
                            .align(Alignment.Center)
                            .background(Color.Transparent)
                            .then(
                                Modifier.padding(0.dp)
                            )
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width * 0.8f
                        val h = size.height * 0.55f
                        val left = (size.width - w) / 2
                        val top = (size.height - h) / 2
                        drawRect(
                            color = Color.White.copy(alpha = 0.85f),
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                            style = Stroke(width = 4f),
                        )
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
                        CircularProgressIndicator()
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
                    onClick = {
                        val dir = File(ctx.cacheDir, "scans").also { it.mkdirs() }
                        val file = File(dir, "side_${state.currentSide.name}_${System.currentTimeMillis()}.jpg")
                        val opts = ImageCapture.OutputFileOptions.Builder(file).build()
                        imageCapture.takePicture(
                            opts,
                            executor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    onDetected(file.absolutePath)
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    // surfaced via detecting false + no update; user retries
                                }
                            },
                        )
                    },
                    enabled = hasCam && !state.detecting,
                    modifier = Modifier.weight(1f),
                ) { Text("Ambil Foto") }
            }
        }
    }
}

@Composable
private fun CameraPreview(imageCapture: ImageCapture) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
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
                StillOverlay(photoPath = side.photoPath, boxes = side.boxes)
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
fun StillOverlay(photoPath: String, boxes: List<PipeBox>) {
    val bmp = remember(photoPath) {
        BitmapFactory.decodeFile(photoPath)?.asImageBitmap()
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    if (bmp == null) {
        Text("Foto tidak ditemukan", Modifier.padding(16.dp))
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
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
            boxes.forEachIndexed { i, b ->
                val x = left + b.x * dw
                val y = top + b.y * dh
                val w = b.w * dw
                val h = b.h * dh
                drawRect(
                    color = Color(0xFF00E676),
                    topLeft = Offset(x, y),
                    size = Size(w, h),
                    style = Stroke(width = 3f),
                )
            }
        }
    }
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

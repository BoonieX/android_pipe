package com.booniex.pipes.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.booniex.pipes.data.BundleSide
import com.booniex.pipes.data.ScanSession
import com.booniex.pipes.data.SelectionArea
import com.booniex.pipes.data.SideScan
import com.booniex.pipes.data.aggregateMax
import com.booniex.pipes.data.db.ScanRepository
import com.booniex.pipes.detect.FrameQuality
import com.booniex.pipes.detect.NcnnDetector
import com.booniex.pipes.detect.OverlapAnalysis
import com.booniex.pipes.detect.PanoStitcher
import com.booniex.pipes.detect.PipeDetector
import com.booniex.pipes.detect.QualityEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class ProcessingStage {
    IDLE,
    STITCHING,
    DETECTING,
    FALLBACK,
}

data class ScanUiState(
    val currentSideIndex: Int = 0,
    val sides: Map<BundleSide, SideScan> = emptyMap(),
    /** Frame foto mentah per sisi (sebelum stitch/deteksi). */
    val frames: Map<BundleSide, List<String>> = emptyMap(),
    val lastFrameQuality: FrameQuality? = null,
    val lastOverlap: OverlapAnalysis? = null,
    val detecting: Boolean = false,
    val processingStage: ProcessingStage = ProcessingStage.IDLE,
    val error: String? = null,
    val activeSession: ScanSession? = null,
    val history: List<ScanSession> = emptyList(),
) {
    val scanOrder: List<BundleSide> = listOf(BundleSide.LEFT, BundleSide.RIGHT, BundleSide.FRONT)
    val currentSide: BundleSide get() = scanOrder[currentSideIndex.coerceIn(0, scanOrder.lastIndex)]
    val currentFrames: List<String> get() = frames[currentSide].orEmpty()
    /** Hasil dapat dibuka jika minimal satu sisi sudah selesai (sisi lain opsional). */
    val hasAnySideDone: Boolean
        get() = sides.isNotEmpty()
    val finalEstimate: Int get() = aggregateMax(sides.values.toList())
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ScanRepository(app)
    // Real NCNN YOLO; fails loudly if .so / model missing (no silent stub in field builds)
    private val detector: PipeDetector = NcnnDetector(app)

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.allSessions().collect { sessions ->
                _state.update { it.copy(history = sessions) }
            }
        }
    }

    fun startNewScan() {
        _state.update {
            it.copy(
                currentSideIndex = 0,
                sides = emptyMap(),
                frames = emptyMap(),
                lastFrameQuality = null,
                lastOverlap = null,
                detecting = false,
                processingStage = ProcessingStage.IDLE,
                error = null,
                activeSession = null,
            )
        }
    }

    fun goToSide(index: Int) {
        _state.update {
            it.copy(currentSideIndex = index.coerceIn(0, it.scanOrder.lastIndex), error = null)
        }
    }

    /** Tambah frame foto; dievaluasi kualitasnya (F-18). Frame buruk ditolak dengan pesan. */
    fun addFrame(path: String) {
        val side = _state.value.currentSide
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            val quality = withContext(Dispatchers.Default) { QualityEvaluator.evaluate(path) }
            _state.update { it.copy(lastFrameQuality = quality) }
            when {
                quality == null -> rejectFrame(path, "Gagal memproses frame, coba lagi")
                quality.isBlurry -> rejectFrame(path, "Foto blur — tahan ponsel stabil, lalu ambil ulang")
                quality.isTooDark -> rejectFrame(path, "Foto terlalu gelap — nyalakan lampu / ambil ulang")
                quality.isTooBright -> rejectFrame(path, "Foto terlalu terang — jauhi sumber cahaya / ambil ulang")
                else -> _state.update { s ->
                    val cur = s.frames[side].orEmpty()
                    val overlap = if (cur.isEmpty()) {
                        null
                    } else {
                        withContext(Dispatchers.Default) {
                            PanoStitcher.analyzeOverlap(cur.last(), path)
                        }
                    }
                    if (cur.isNotEmpty() && (overlap == null || !overlap.accepted)) {
                        File(path).delete()
                        s.copy(
                            lastOverlap = overlap,
                            error = overlap?.message ?: "Gagal memvalidasi overlap — coba ambil ulang",
                        )
                    } else {
                        s.copy(
                            frames = s.frames + (side to cur + path),
                            lastOverlap = overlap,
                            error = null,
                        )
                    }
                }
            }
        }
    }

    private fun rejectFrame(path: String, message: String) {
        File(path).delete()
        _state.update { it.copy(error = message) }
    }

    /** Hapus frame terakhir dari sisi aktif (F-07). */
    fun removeLastFrame() {
        removeFrame(_state.value.currentFrames.lastIndex)
    }

    /** Hapus frame tertentu agar frame tengah yang salah tidak merusak urutan. */
    fun removeFrame(index: Int) {
        val side = _state.value.currentSide
        _state.update { s ->
            val cur = s.frames[side].orEmpty()
            if (index !in cur.indices) s
            else s.copy(
                frames = s.frames + (side to cur.toMutableList().also { it.removeAt(index) }),
                lastOverlap = null,
                error = null,
            )
        }
    }

    /** Stitch semua frame sisi aktif -> deteksi -> simpan hasil (F-16, F-17). */
    fun processFrames() {
        val side = _state.value.currentSide
        val frames = _state.value.currentFrames
        if (frames.isEmpty()) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    detecting = true,
                    processingStage = ProcessingStage.STITCHING,
                    error = null,
                )
            }
            try {
                val result = withContext(Dispatchers.Default) {
                    val panoFile = File(
                        getApplication<Application>().cacheDir,
                        "pano_${side.name}_${System.currentTimeMillis()}.jpg",
                    )
                    val panoPath = PanoStitcher.stitch(frames, panoFile.absolutePath)
                    if (panoPath != null) {
                        _state.update { it.copy(processingStage = ProcessingStage.DETECTING) }
                        val bmp = BitmapFactory.decodeFile(panoPath) ?: error("Gagal memuat panorama")
                        val luma = QualityEvaluator.evaluate(panoPath)?.luma
                        val enhance = luma != null && luma < 60f
                        val boxes = detector.detect(bmp, enhance)
                        SideScan(side = side, photoPath = panoPath, count = boxes.size, boxes = boxes)
                    } else {
                        // Fallback F-17: stitch gagal -> deteksi per frame, pakai frame dengan count terbanyak
                        _state.update { it.copy(processingStage = ProcessingStage.FALLBACK) }
                        val best = frames.mapNotNull { path ->
                            val bmp = BitmapFactory.decodeFile(path) ?: return@mapNotNull null
                            val luma = QualityEvaluator.evaluate(path)?.luma
                            val boxes = detector.detect(bmp, luma != null && luma < 60f)
                            Triple(path, boxes.size, boxes)
                        }.maxByOrNull { it.second }
                        if (best == null) error("Stitching & deteksi gagal — coba lagi")
                        SideScan(side = side, photoPath = best.first, count = best.second, boxes = best.third)
                    }
                }
                _state.update { s ->
                    val next = s.sides + (side to result)
                    s.copy(
                        sides = next,
                        frames = s.frames - side,
                        lastFrameQuality = null,
                        detecting = false,
                        processingStage = ProcessingStage.IDLE,
                        lastOverlap = null,
                        currentSideIndex = if (s.sides.containsKey(side)) s.currentSideIndex
                        else (s.currentSideIndex + 1).coerceAtMost(s.scanOrder.lastIndex),
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        detecting = false,
                        processingStage = ProcessingStage.IDLE,
                        error = e.message ?: "Deteksi gagal",
                    )
                }
            }
        }
    }

    fun redoCurrentSide() {
        val side = _state.value.currentSide
        _state.update {
            it.copy(sides = it.sides - side, frames = it.frames - side, error = null)
        }
    }

    fun buildResultSession(): ScanSession {
        val s = _state.value
        val session = ScanSession(
            id = UUID.randomUUID().toString(),
            timestampMs = System.currentTimeMillis(),
            sides = s.scanOrder.mapNotNull { s.sides[it] },
            finalEstimate = s.finalEstimate,
        )
        _state.update { it.copy(activeSession = session) }
        return session
    }

    fun saveActive() {
        val session = _state.value.activeSession ?: buildResultSession()
        viewModelScope.launch {
            repo.save(session)
        }
        _state.update { it.copy(activeSession = session) }
    }

    fun loadSession(id: String) {
        viewModelScope.launch {
            val session = repo.get(id) ?: return@launch
            _state.update {
                it.copy(
                    activeSession = session,
                    sides = session.sides.associateBy { side -> side.side },
                )
            }
        }
    }

    fun confirm(ok: Boolean, note: String?) {
        val cur = _state.value.activeSession ?: return
        val updated = cur.copy(confirmed = ok, note = note?.ifBlank { null })
        viewModelScope.launch { repo.save(updated) }
        _state.update { it.copy(activeSession = updated) }
    }

    /** Simpan area seleksi lasso per sisi (F-22). null = hapus seleksi. */
    fun saveSelection(side: BundleSide, selection: SelectionArea?) {
        val cur = _state.value.activeSession ?: return
        val updated = cur.copy(sides = cur.sides.map {
            if (it.side == side) it.copy(selection = selection) else it
        })
        viewModelScope.launch { repo.save(updated) }
        _state.update {
            it.copy(activeSession = updated, sides = it.sides + (side to updated.side(side)!!))
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            repo.allSessions().collect { sessions ->
                _state.update { it.copy(history = sessions) }
            }
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            repo.exportCsv()
        }
    }
}

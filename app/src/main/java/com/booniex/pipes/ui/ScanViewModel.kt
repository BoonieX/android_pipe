package com.booniex.pipes.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.booniex.pipes.data.BundleSide
import com.booniex.pipes.data.ScanSession
import com.booniex.pipes.data.SideScan
import com.booniex.pipes.data.aggregateMax
import com.booniex.pipes.data.db.ScanRepository
import com.booniex.pipes.detect.NcnnDetector
import com.booniex.pipes.detect.PipeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ScanUiState(
    val currentSideIndex: Int = 0,
    val sides: Map<BundleSide, SideScan> = emptyMap(),
    val detecting: Boolean = false,
    val error: String? = null,
    val activeSession: ScanSession? = null,
    val history: List<ScanSession> = emptyList(),
) {
    val scanOrder: List<BundleSide> = listOf(BundleSide.LEFT, BundleSide.RIGHT, BundleSide.FRONT)
    val currentSide: BundleSide get() = scanOrder[currentSideIndex.coerceIn(0, scanOrder.lastIndex)]
    val allRequiredDone: Boolean
        get() = sides.containsKey(BundleSide.LEFT) && sides.containsKey(BundleSide.RIGHT)
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
                detecting = false,
                error = null,
                activeSession = null,
            )
        }
    }

    fun goToSide(index: Int) {
        _state.update { it.copy(currentSideIndex = index.coerceIn(0, it.scanOrder.lastIndex), error = null) }
    }

    fun detectPhoto(path: String) {
        val side = _state.value.currentSide
        viewModelScope.launch {
            _state.update { it.copy(detecting = true, error = null) }
            try {
                val result = withContext(Dispatchers.Default) {
                    val bmp = BitmapFactory.decodeFile(path) ?: error("Gagal memuat foto")
                    val boxes = detector.detect(bmp)
                    SideScan(side = side, photoPath = path, count = boxes.size, boxes = boxes)
                }
                _state.update { s ->
                    val next = s.sides + (side to result)
                    s.copy(
                        sides = next,
                        detecting = false,
                        currentSideIndex = if (s.sides.containsKey(side)) s.currentSideIndex
                        else (s.currentSideIndex + 1).coerceAtMost(s.scanOrder.lastIndex),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(detecting = false, error = e.message ?: "Deteksi gagal") }
            }
        }
    }

    fun redoCurrentSide() {
        val side = _state.value.currentSide
        _state.update { it.copy(sides = it.sides - side, error = null) }
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

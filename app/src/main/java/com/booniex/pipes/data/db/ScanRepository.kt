package com.booniex.pipes.data.db

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.booniex.pipes.data.BundleSide
import com.booniex.pipes.data.PipeBox
import com.booniex.pipes.data.ScanSession
import com.booniex.pipes.data.SideScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanRepository(context: Context) {
    private val context = context.applicationContext
    private val dao = AppDatabase.instance(context).scanDao()

    fun allSessions(): Flow<List<ScanSession>> =
        dao.all().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: String): ScanSession? = dao.get(id)?.toDomain()

    suspend fun save(session: ScanSession) = dao.upsert(session.toEntity())

    suspend fun delete(session: ScanSession) = dao.delete(session.toEntity())

    suspend fun exportCsv(): Uri? = withContext(Dispatchers.IO) {
        val rows = allSessions().first()
        if (rows.isEmpty()) return@withContext null

        val dir = context.getExternalFilesDir(null)
            ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, "PipeCounter_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.csv")
        file.bufferedWriter().use { w ->
            w.appendLine("id,timestamp,side,count,photo,confirmed,note")
            rows.forEach { s ->
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(s.timestampMs))
                s.sides.forEach { side ->
                    w.appendLine(
                        "\"${s.id}\",\"$ts\",\"${side.side.label}\",${side.count},\"${side.photoPath}\",${s.confirmed ?: ""},\"${s.note ?: ""}\""
                    )
                }
            }
        }
        Uri.fromFile(file)
    }
}

// --- Mapping ---

private fun ScanEntity.toDomain(): ScanSession {
    val sidesArr = JSONArray(sidesJson)
    val sides = buildList {
        for (i in 0 until sidesArr.length()) {
            val s = sidesArr.getJSONObject(i)
            val boxesArr = s.getJSONArray("boxes")
            val boxes = buildList {
                for (j in 0 until boxesArr.length()) {
                    val b = boxesArr.getJSONObject(j)
                    add(
                        PipeBox(
                            x = b.getDouble("x").toFloat(),
                            y = b.getDouble("y").toFloat(),
                            w = b.getDouble("w").toFloat(),
                            h = b.getDouble("h").toFloat(),
                            conf = b.optDouble("conf", 1.0).toFloat(),
                        )
                    )
                }
            }
            add(
                SideScan(
                    side = BundleSide.valueOf(s.getString("side")),
                    photoPath = s.getString("photoPath"),
                    count = s.getInt("count"),
                    boxes = boxes,
                )
            )
        }
    }
    return ScanSession(id = id, timestampMs = timestampMs, sides = sides, finalEstimate = finalEstimate, confirmed = confirmed, note = note)
}

private fun ScanSession.toEntity(): ScanEntity {
    val arr = JSONArray()
    sides.forEach { s ->
        arr.put(JSONObject().apply {
            put("side", s.side.name)
            put("photoPath", s.photoPath)
            put("count", s.count)
            put("boxes", JSONArray().also { ba ->
                s.boxes.forEach { b ->
                    ba.put(JSONObject().apply {
                        put("x", b.x.toDouble())
                        put("y", b.y.toDouble())
                        put("w", b.w.toDouble())
                        put("h", b.h.toDouble())
                        put("conf", b.conf.toDouble())
                    })
                }
            })
        })
    }
    return ScanEntity(id = id, timestampMs = timestampMs, finalEstimate = finalEstimate, confirmed = confirmed, note = note, sidesJson = arr.toString())
}

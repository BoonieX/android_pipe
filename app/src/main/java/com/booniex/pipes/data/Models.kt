package com.booniex.pipes.data

import org.json.JSONArray
import org.json.JSONObject

enum class BundleSide(val label: String) {
    LEFT("Kiri"),
    RIGHT("Kanan"),
    FRONT("Depan");
}

/** Normalized box in [0,1] relative to photo width/height. */
data class PipeBox(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val conf: Float = 1f,
)

/** Titik normalized [0,1] relatif terhadap foto (untuk polygon seleksi). */
data class NormPoint(val x: Float, val y: Float)

/** Area seleksi lasso/polygon pada satu sisi + jumlah pipa (centroid) di dalamnya. */
data class SelectionArea(
    val polygon: List<NormPoint>,
    val count: Int,
)

data class SideScan(
    val side: BundleSide,
    val photoPath: String,
    val count: Int,
    val boxes: List<PipeBox>,
    val selection: SelectionArea? = null,
)

data class ScanSession(
    val id: String,
    val timestampMs: Long,
    val sides: List<SideScan>,
    val finalEstimate: Int,
    val confirmed: Boolean? = null,
    val note: String? = null,
) {
    fun side(side: BundleSide): SideScan? = sides.find { it.side == side }
}

fun aggregateMax(sides: List<SideScan>): Int =
    sides.maxOfOrNull { it.count } ?: 0

private fun selectionToJson(sel: SelectionArea): JSONObject = JSONObject().apply {
    put("count", sel.count)
    put("polygon", JSONArray().also { pa ->
        sel.polygon.forEach { p ->
            pa.put(JSONObject().apply {
                put("x", p.x.toDouble())
                put("y", p.y.toDouble())
            })
        }
    })
}

private fun selectionFromJson(o: JSONObject): SelectionArea {
    val polyArr = o.getJSONArray("polygon")
    val polygon = buildList {
        for (i in 0 until polyArr.length()) {
            val p = polyArr.getJSONObject(i)
            add(NormPoint(p.getDouble("x").toFloat(), p.getDouble("y").toFloat()))
        }
    }
    return SelectionArea(polygon = polygon, count = o.getInt("count"))
}

fun ScanSession.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("timestampMs", timestampMs)
    put("finalEstimate", finalEstimate)
    put("confirmed", confirmed)
    put("note", note)
    put("sides", JSONArray().also { arr ->
        sides.forEach { s ->
            arr.put(JSONObject().apply {
                put("side", s.side.name)
                put("photoPath", s.photoPath)
                put("count", s.count)
                s.selection?.let { put("selection", selectionToJson(it)) }
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
    })
}

fun scanSessionFromJson(o: JSONObject): ScanSession {
    val sidesArr = o.getJSONArray("sides")
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
            val selection = if (s.has("selection") && !s.isNull("selection")) {
                selectionFromJson(s.getJSONObject("selection"))
            } else null
            add(
                SideScan(
                    side = BundleSide.valueOf(s.getString("side")),
                    photoPath = s.getString("photoPath"),
                    count = s.getInt("count"),
                    boxes = boxes,
                    selection = selection,
                )
            )
        }
    }
    return ScanSession(
        id = o.getString("id"),
        timestampMs = o.getLong("timestampMs"),
        sides = sides,
        finalEstimate = o.getInt("finalEstimate"),
        confirmed = if (o.isNull("confirmed")) null else o.getBoolean("confirmed"),
        note = if (o.isNull("note")) null else o.getString("note"),
    )
}

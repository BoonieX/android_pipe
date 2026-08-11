package com.booniex.pipes.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.booniex.pipes.data.BundleSide
import com.booniex.pipes.data.NormPoint
import com.booniex.pipes.data.PipeBox
import com.booniex.pipes.data.ScanSession
import com.booniex.pipes.data.SelectionArea
import com.booniex.pipes.data.SideScan
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

// --- Entities ---

@Entity(tableName = "scan_sessions")
data class ScanEntity(
    @PrimaryKey val id: String,
    val timestampMs: Long,
    val finalEstimate: Int,
    val confirmed: Boolean?,
    val note: String?,
    val sidesJson: String,
)

// --- Converters ---

class Converters {
    @TypeConverter
    fun sidesToJson(sides: List<SideScan>): String {
        val arr = JSONArray()
        sides.forEach { s ->
            arr.put(JSONObject().apply {
                put("side", s.side.name)
                put("photoPath", s.photoPath)
                put("count", s.count)
                s.selection?.let { sel ->
                    put("selection", JSONObject().apply {
                        put("count", sel.count)
                        put("polygon", JSONArray().also { pa ->
                            sel.polygon.forEach { p ->
                                pa.put(JSONObject().apply {
                                    put("x", p.x.toDouble())
                                    put("y", p.y.toDouble())
                                })
                            }
                        })
                    })
                }
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
        return arr.toString()
    }

    @TypeConverter
    fun jsonToSides(json: String): List<SideScan> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
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
                    val sel = s.getJSONObject("selection")
                    val polyArr = sel.getJSONArray("polygon")
                    SelectionArea(
                        count = sel.getInt("count"),
                        polygon = buildList {
                            for (k in 0 until polyArr.length()) {
                                val p = polyArr.getJSONObject(k)
                                add(NormPoint(p.getDouble("x").toFloat(), p.getDouble("y").toFloat()))
                            }
                        },
                    )
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
    }
}

// --- DAO ---

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_sessions ORDER BY timestampMs DESC")
    fun all(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scan_sessions WHERE id = :id")
    suspend fun get(id: String): ScanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScanEntity)

    @Delete
    suspend fun delete(entity: ScanEntity)

    @Query("DELETE FROM scan_sessions")
    suspend fun clear()
}

// --- Database ---

@Database(entities = [ScanEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun instance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pipe_counter.db",
                ).build().also { INSTANCE = it }
            }
    }
}

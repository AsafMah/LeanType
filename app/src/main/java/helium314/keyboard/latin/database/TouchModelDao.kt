// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import helium314.keyboard.latin.utils.Log
import java.util.concurrent.Executors

/**
 * Cached access to the learned per-user touch-model table (see docs/ADAPTIVE_TYPING.md).
 *
 * Stores, per (key code, layout, orientation), the running mean landing offset relative to the
 * key center and its variance, plus a sample count. **Content-free**: no characters/words are
 * stored, only aggregate touch geometry. Used to bias tap resolution (KeyDetector) and gesture
 * sweet spots (ProximityInfo) toward where the user actually types.
 *
 * Lives in the shared [Database] ("leantype.db"), so it rides the existing settings
 * backup/restore. Lookups are O(1) from an in-memory cache for the input hot path.
 */
class TouchModelDao private constructor(private val db: Database) {

    /** One key's learned stats. Offsets/variance are in pixels (relative to the key center).
     *  keyWidth/keyHeight are the key's size in px at record time, so a viewer can express the
     *  offset as a fraction of the key (e.g. "18px = 20% toward the lower-left of E"). */
    data class Stat(
        val keyCode: Int,
        val layout: String,
        val orientation: Int,
        var meanDx: Float,
        var meanDy: Float,
        var varDx: Float,
        var varDy: Float,
        var count: Int,
        var updatedAt: Long,
        var keyWidth: Int = 0,
        var keyHeight: Int = 0,
    )

    private val cache = HashMap<String, Stat>()
    // Persist off the input thread: record() runs on every letter tap, so the DB write must not
    // block typing. The cache (source of truth at runtime) is updated synchronously; the disk
    // write is serialized on this single thread (order preserved). Losing the last sample or two
    // on a crash is acceptable for a learning model.
    private val writeExecutor = Executors.newSingleThreadExecutor()

    init {
        db.readableDatabase.query(
            TABLE,
            arrayOf(COLUMN_KEY_CODE, COLUMN_LAYOUT, COLUMN_ORIENTATION, COLUMN_MEAN_DX, COLUMN_MEAN_DY,
                COLUMN_VAR_DX, COLUMN_VAR_DY, COLUMN_COUNT, COLUMN_UPDATED_AT, COLUMN_KEY_WIDTH, COLUMN_KEY_HEIGHT),
            null, null, null, null, null
        ).use {
            while (it.moveToNext()) {
                val s = Stat(it.getInt(0), it.getString(1), it.getInt(2), it.getFloat(3), it.getFloat(4),
                    it.getFloat(5), it.getFloat(6), it.getInt(7), it.getLong(8), it.getInt(9), it.getInt(10))
                cache[key(s.keyCode, s.layout, s.orientation)] = s
            }
        }
    }

    /**
     * Fold a new landing offset (touch position minus key center, in px) into the running model
     * for this key, using an exponential moving average so recent behavior is weighted more and
     * old data decays. Persists immediately. Callers must gate on incognito + the opt-in pref.
     */
    @Synchronized
    fun record(keyCode: Int, layout: String, orientation: Int, dx: Float, dy: Float,
               keyWidth: Int, keyHeight: Int, now: Long, halfLifeMs: Long) {
        val k = key(keyCode, layout, orientation)
        val s = cache[k]
        if (s == null) {
            val ns = Stat(keyCode, layout, orientation, dx, dy, 0f, 0f, 1, now, keyWidth, keyHeight)
            cache[k] = ns
            persistAsync(ns.copy())
            return
        }
        // Forget window: fade the accumulated confidence by wall-clock elapsed BEFORE folding in
        // the new sample, so after a long gap a single tap can't restore full confidence in
        // months-old data — the model rebuilds gradually. (The mean EMA below is unaffected; only
        // the sample count, which gates how much the bias is applied, decays.)
        if (halfLifeMs > 0L && s.updatedAt in 1 until now) {
            s.count = Math.round(s.count * TouchModelManager.decayFactor(now - s.updatedAt, halfLifeMs))
        }
        val a = EMA_ALPHA
        val oldMeanDx = s.meanDx
        val oldMeanDy = s.meanDy
        s.meanDx = (1 - a) * oldMeanDx + a * dx
        s.meanDy = (1 - a) * oldMeanDy + a * dy
        // Exponentially-weighted variance around the pre-update mean.
        s.varDx = (1 - a) * (s.varDx + a * (dx - oldMeanDx) * (dx - oldMeanDx))
        s.varDy = (1 - a) * (s.varDy + a * (dy - oldMeanDy) * (dy - oldMeanDy))
        if (s.count < Int.MAX_VALUE) s.count++
        s.updatedAt = now
        if (keyWidth > 0) s.keyWidth = keyWidth
        if (keyHeight > 0) s.keyHeight = keyHeight
        persistAsync(s.copy())
    }

    private fun persistAsync(snapshot: Stat) {
        try {
            writeExecutor.execute { write(snapshot) }
        } catch (e: Throwable) {
            Log.e(TAG, "touch model write rejected", e)
        }
    }

    /** Learned stats for a key, or null if none recorded yet. */
    @Synchronized
    fun get(keyCode: Int, layout: String, orientation: Int): Stat? =
        cache[key(keyCode, layout, orientation)]

    /** Snapshot of all stats (for the stats page / debugging). */
    @Synchronized
    fun all(): List<Stat> = cache.values.map { it.copy() }

    /** Insert a full stat verbatim (no EMA) — used when restoring from a backup. */
    @Synchronized
    fun restore(s: Stat) {
        cache[key(s.keyCode, s.layout, s.orientation)] = s
        // Route through the same executor as record()/clear() so every DB mutation shares one
        // ordering domain — no synchronous write racing the queued async writes.
        persistAsync(s)
    }

    /** Forget everything (the "reset learned typing model" action). */
    @Synchronized
    fun clear() {
        if (cache.isEmpty()) return
        cache.clear()
        // Delete on the SAME single-thread executor that record() writes on, so any write queued
        // just before Reset is applied first and the delete wins. A synchronous delete here could
        // be overtaken by an in-flight async write that re-inserts a row, silently resurrecting the
        // data the user asked to forget (it would reload into the cache on the next launch).
        try {
            writeExecutor.execute { db.writableDatabase.delete(TABLE, null, null) }
        } catch (e: Throwable) {
            Log.e(TAG, "touch model clear rejected", e)
        }
    }

    private fun write(s: Stat) {
        val cv = ContentValues(11)
        cv.put(COLUMN_KEY_CODE, s.keyCode)
        cv.put(COLUMN_LAYOUT, s.layout)
        cv.put(COLUMN_ORIENTATION, s.orientation)
        cv.put(COLUMN_MEAN_DX, s.meanDx)
        cv.put(COLUMN_MEAN_DY, s.meanDy)
        cv.put(COLUMN_VAR_DX, s.varDx)
        cv.put(COLUMN_VAR_DY, s.varDy)
        cv.put(COLUMN_COUNT, s.count)
        cv.put(COLUMN_UPDATED_AT, s.updatedAt)
        cv.put(COLUMN_KEY_WIDTH, s.keyWidth)
        cv.put(COLUMN_KEY_HEIGHT, s.keyHeight)
        db.writableDatabase.insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    companion object {
        private const val TAG = "TouchModelDao"
        // ~ last 20 samples dominate; tuned later on-device.
        private const val EMA_ALPHA = 0.05f
        /** Below this many samples a key's learned bias should not be applied (confidence gate). */
        const val MIN_CONFIDENT_SAMPLES = 20

        const val TABLE = "TOUCH_MODEL"
        private const val COLUMN_KEY_CODE = "KEY_CODE"
        private const val COLUMN_LAYOUT = "LAYOUT"
        private const val COLUMN_ORIENTATION = "ORIENTATION"
        private const val COLUMN_MEAN_DX = "MEAN_DX"
        private const val COLUMN_MEAN_DY = "MEAN_DY"
        private const val COLUMN_VAR_DX = "VAR_DX"
        private const val COLUMN_VAR_DY = "VAR_DY"
        private const val COLUMN_COUNT = "COUNT"
        private const val COLUMN_UPDATED_AT = "UPDATED_AT"
        const val COLUMN_KEY_WIDTH = "KEY_WIDTH"
        const val COLUMN_KEY_HEIGHT = "KEY_HEIGHT"
        const val CREATE_TABLE = """
            CREATE TABLE $TABLE (
                $COLUMN_KEY_CODE INTEGER NOT NULL,
                $COLUMN_LAYOUT TEXT NOT NULL,
                $COLUMN_ORIENTATION INTEGER NOT NULL,
                $COLUMN_MEAN_DX REAL NOT NULL,
                $COLUMN_MEAN_DY REAL NOT NULL,
                $COLUMN_VAR_DX REAL NOT NULL,
                $COLUMN_VAR_DY REAL NOT NULL,
                $COLUMN_COUNT INTEGER NOT NULL,
                $COLUMN_UPDATED_AT INTEGER NOT NULL,
                $COLUMN_KEY_WIDTH INTEGER NOT NULL DEFAULT 0,
                $COLUMN_KEY_HEIGHT INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY ($COLUMN_KEY_CODE, $COLUMN_LAYOUT, $COLUMN_ORIENTATION)
            )
        """

        private fun key(keyCode: Int, layout: String, orientation: Int) = "$keyCode|$layout|$orientation"

        private var instance: TouchModelDao? = null

        /** Returns the instance, or null if it can't be created (e.g. device locked). */
        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): TouchModelDao? {
            if (instance == null)
                try {
                    instance = TouchModelDao(Database.getInstance(context))
                } catch (e: Throwable) {
                    Log.e(TAG, "can't create TouchModelDao", e)
                }
            return instance
        }
    }
}

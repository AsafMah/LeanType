// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import helium314.keyboard.latin.utils.Log
import java.io.File

class Database private constructor(context: Context, name: String = NAME) : SQLiteOpenHelper(context, name, null, VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(ClipboardDao.CREATE_TABLE)
        db.execSQL(TouchModelDao.CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE CLIPBOARD ADD COLUMN IMAGE_URI TEXT")
        }
        if (oldVersion < 3) {
            db.execSQL(TouchModelDao.CREATE_TABLE)
        }
    }

    companion object {
        private val TAG = Database::class.java.simpleName
        private const val VERSION = 3
        const val NAME = "leantype.db"
        private var instance: Database? = null
        fun getInstance(context: Context): Database {
            if (instance == null)
                instance = Database(context)
            return instance!!
        }

        // needs to be in sync with db version
        fun copyFromDb(file: File, context: Context) {
            if (!file.exists())
                return
            val otherDb = Database(context, file.name)
            val clipDao = ClipboardDao.getInstance(context) // insert to dao because of cache
            if (clipDao == null) {
                Log.e(TAG, "can't transfer clipboard data because ClipboardDao is null")
                return
            }
            val hasImageUri = otherDb.readableDatabase.rawQuery("PRAGMA table_info(CLIPBOARD)", null).use {
                var hasIt = false
                while(it.moveToNext()) {
                    if (it.getString(1) == "IMAGE_URI") hasIt = true
                }
                hasIt
            }
            val query = if (hasImageUri) "SELECT TIMESTAMP, PINNED, TEXT, IMAGE_URI FROM CLIPBOARD" else "SELECT TIMESTAMP, PINNED, TEXT FROM CLIPBOARD"
            otherDb.readableDatabase.rawQuery(query, null)
                .use {
                    clipDao.clear()
                    while (it.moveToNext()) {
                        val imageUri = if (hasImageUri && !it.isNull(3)) it.getString(3) else null
                        clipDao.addClip(it.getLong(0), it.getInt(1) != 0, it.getString(2) ?: "", imageUri)
                    }
                }
            // Touch model (adaptive typing): present only in backups from versions that have it.
            val touchDao = TouchModelDao.getInstance(context)
            if (touchDao != null) {
                val hasTouchModel = otherDb.readableDatabase.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='${TouchModelDao.TABLE}'", null
                ).use { it.moveToNext() }
                if (hasTouchModel) {
                    touchDao.clear()
                    otherDb.readableDatabase.rawQuery(TouchModelDao.SELECT_ALL, null).use {
                        while (it.moveToNext()) {
                            touchDao.restore(TouchModelDao.Stat(
                                it.getInt(0), it.getString(1), it.getInt(2), it.getFloat(3), it.getFloat(4),
                                it.getFloat(5), it.getFloat(6), it.getInt(7), it.getLong(8)))
                        }
                    }
                }
            }
            otherDb.close()
            file.delete()
        }
    }
}

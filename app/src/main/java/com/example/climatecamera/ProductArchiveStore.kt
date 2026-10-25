package com.example.climatecamera

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** SQLite commits are synchronous; a failed insert prevents the destructive network request. */
internal class ProductArchiveStore private constructor(context: Context) : SQLiteOpenHelper(context.applicationContext, "product_archive.db", null, 1) {
    companion object {
        @Volatile private var instance: ProductArchiveStore? = null
        fun get(context: Context): ProductArchiveStore = instance ?: synchronized(this) {
            instance ?: ProductArchiveStore(context.applicationContext).also { instance = it }
        }
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE archive (archive_id TEXT PRIMARY KEY NOT NULL, camera_key TEXT NOT NULL, archived_at INTEGER NOT NULL, product_json TEXT NOT NULL, state TEXT NOT NULL, name TEXT NOT NULL, notes TEXT NOT NULL)")
        db.execSQL("CREATE INDEX archive_camera ON archive(camera_key, archived_at)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Неизвестная версия базы архива; автоматическое удаление запрещено")
    }
    fun insert(entry: ArchivedProduct) {
        val values = ContentValues().apply {
            put("archive_id", entry.archiveId); put("camera_key", entry.cameraKey)
            put("archived_at", entry.archivedAt); put("product_json", ArchiveCodec.encode(entry.product))
            put("state", entry.state.name); put("name", entry.name); put("notes", entry.notes)
        }
        writableDatabase.insertOrThrow("archive", null, values)
    }
    fun list(): List<ArchivedProduct> {
        val result = mutableListOf<ArchivedProduct>()
        readableDatabase.query("archive", null, null, null, null, null, "archived_at DESC, archive_id ASC").use { c ->
            fun text(name: String) = c.getString(c.getColumnIndexOrThrow(name))
            while (c.moveToNext()) result += ArchivedProduct(text("archive_id"), text("camera_key"),
                c.getLong(c.getColumnIndexOrThrow("archived_at")), ArchiveCodec.decode(text("product_json")),
                ArchiveState.valueOf(text("state")), text("name"), text("notes"))
        }
        return result
    }
    fun state(id: String, state: ArchiveState) {
        val changed = writableDatabase.update("archive", ContentValues().apply { put("state", state.name) },
            "archive_id = ?", arrayOf(id))
        check(changed == 1) { "Запись архива не найдена" }
    }
    fun edit(id: String, name: String, notes: String) {
        require(name.isNotBlank() && name.length <= 120 && notes.length <= 4000)
        val changed = writableDatabase.update("archive", ContentValues().apply {
            put("name", name.trim()); put("notes", notes)
        }, "archive_id = ?", arrayOf(id))
        check(changed == 1) { "Запись архива не найдена" }
    }
}

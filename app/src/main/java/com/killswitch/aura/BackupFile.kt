package com.killswitch.aura

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Backups are real files the person chooses where to keep, picked through Android's file picker. No storage permission needed. */
object BackupFile {
    private const val CAP = 2_000_000 // a real backup is a few KB, so anything bigger is not one

    /** aura-alarm-backup-2026-11-02.json */
    fun name(now: Long): String = "aura-alarm-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now)) + ".json"

    /** The backup as readable, indented JSON. Restoring reads the same data as the old compact text. */
    fun text(c: Context): String = JSONObject(Backup.exportAll(c)).toString(2)

    fun write(c: Context, uri: Uri, text: String): Boolean = try {
        c.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
    } catch (e: Exception) { false }

    /** The file as text, or null if it cannot be read or is far too big to be a backup. */
    fun read(c: Context, uri: Uri): String? = try {
        c.contentResolver.openInputStream(uri)?.use { s ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > CAP) return null
            }
            out.toString("UTF-8")
        }
    } catch (e: Exception) { null }
}

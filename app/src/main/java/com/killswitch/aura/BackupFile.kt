package com.killswitch.aura

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Backups are real files the person chooses where to keep, picked through Android's file picker. No storage permission needed. */
object BackupFile {
    private const val CAP = 2_000_000 // a real backup is a few KB, so anything bigger is not one

    /** The extension of an Aura backup. */
    const val EXT = "aura"

    /** The file type Android is told about. It is made up on purpose, so no file app adds .json or .bin behind our back. */
    const val MIME = "application/x-aura-backup"

    /** aura-backup-2026-11-02.aura */
    fun name(now: Long): String = "aura-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now)) + "." + EXT

    /** Everything, in the .aura format. */
    fun bytes(c: Context): ByteArray = AuraFile.encode(Backup.exportAll(c))

    fun write(c: Context, uri: Uri, data: ByteArray): Boolean = try {
        c.contentResolver.openOutputStream(uri, "wt")?.use { it.write(data) } != null
    } catch (e: Exception) { false }

    /** The file's bytes, or null if it cannot be read or is far too big to be a backup. */
    fun read(c: Context, uri: Uri): ByteArray? = try {
        c.contentResolver.openInputStream(uri)?.use { s ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > CAP) return null
            }
            out.toByteArray()
        }
    } catch (e: Exception) { null }
}

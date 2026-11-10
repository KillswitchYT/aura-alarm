package com.killswitch.aura

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The .aura backup file. Small and recognisable: the four letters AURA, a version byte, a checksum of the data, then the data squeezed
 * with gzip. The checksum is how a damaged or half-copied file is told apart from a good one. Plain JSON backups (earlier Aura exports
 * and the web build's) still open.
 */
object AuraFile {
    const val VERSION = 1
    private const val CAP = 2_000_000 // a real backup is a few KB, so anything that unpacks bigger than this is not one
    private val MAGIC = byteArrayOf(0x41, 0x55, 0x52, 0x41) // "AURA"

    sealed class Read {
        /** The backup's JSON. [legacy] is true for a plain JSON file with no .aura wrapper. */
        data class Ok(val json: String, val legacy: Boolean) : Read()
        /** It says it is an Aura backup but cut short or changed, so it cannot be trusted. */
        object Damaged : Read()
        /** Written by a newer Aura than this one. */
        object Newer : Read()
        /** Not an Aura backup at all. */
        object NotABackup : Read()
    }

    fun encode(json: String): ByteArray {
        val raw = json.toByteArray(Charsets.UTF_8)
        val crc = CRC32().also { it.update(raw) }.value
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(VERSION)
        for (shift in intArrayOf(24, 16, 8, 0)) out.write(((crc shr shift) and 0xFF).toInt())
        GZIPOutputStream(out).use { it.write(raw) }
        return out.toByteArray()
    }

    fun decode(b: ByteArray): Read {
        if (b.isEmpty()) return Read.NotABackup
        if (b.size >= 4 && b[0] == MAGIC[0] && b[1] == MAGIC[1] && b[2] == MAGIC[2] && b[3] == MAGIC[3]) {
            if (b.size < 9) return Read.Damaged
            if ((b[4].toInt() and 0xFF) > VERSION) return Read.Newer
            var crc = 0L
            for (i in 5..8) crc = (crc shl 8) or (b[i].toLong() and 0xFF)
            val raw = try {
                val out = ByteArrayOutputStream()
                GZIPInputStream(ByteArrayInputStream(b, 9, b.size - 9)).use { s ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = s.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > CAP) return Read.Damaged
                    }
                }
                out.toByteArray()
            } catch (e: Exception) { return Read.Damaged }
            if (CRC32().also { it.update(raw) }.value != crc) return Read.Damaged
            return Read.Ok(String(raw, Charsets.UTF_8), legacy = false)
        }
        // Not ours. It may still be plain JSON, which is what Aura wrote before this format and what the web build wrote.
        val text = String(b, Charsets.UTF_8).removePrefix("\uFEFF").trimStart()
        return if (text.startsWith("{")) Read.Ok(text, legacy = true) else Read.NotABackup
    }
}

package com.streambd.iptv.utils

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater

/**
 * Wraps an upstream DataSource and transparently unwraps HLS segments that
 * the CDN has disguised as PNG/WebP images (anti-DPI evasion).
 *
 * Confirmed disguise method (verified with curl on tiktokcdn-us.com):
 *   - Segment URL: https://p16-common-sign.tiktokcdn-us.com/…/xxx~tplv-tiktokx-origin.image
 *   - Actual bytes: 512×773 RGB PNG, ~1.1 MB
 *   - Hidden payload: gzipped MPEG-TS stored as RGB pixel data
 *   - Layout:
 *       bytes 0..7   : ASCII "TIKTIKPX" magic
 *       bytes 8..11  : big-endian uint32 = length of gzipped payload
 *       bytes 12..N  : gzip-compressed MPEG-TS stream
 *
 * When ExoPlayer opens a disguised segment, we download the whole PNG,
 * decode its pixel grid (reverse PNG filters), extract the gzip blob,
 * decompress it, and serve the resulting MPEG-TS bytes to ExoPlayer.
 *
 * Non-image URLs (m3u8, direct .ts, .mp4) pass through untouched.
 */
class PngStrippingDataSource(
    private val upstream: DataSource
) : DataSource {

    private var unwrapped: ByteArray? = null
    private var stream: InputStream? = null

    override fun open(dataSpec: DataSpec): Long {
        val url = dataSpec.uri.toString()

        // Fast-path: any URL that isn't a TikTok-style image goes straight
        // through to the network. Keeps m3u8, direct MP4, etc. cheap.
        val isDisguised =
            url.endsWith(".image", ignoreCase = true) ||
            url.contains(".image?", ignoreCase = true) ||
            url.contains("tiktokcdn", ignoreCase = true) ||
            url.contains("tplv-", ignoreCase = true)

        if (!isDisguised) {
            unwrapped = null
            stream = null
            return upstream.open(dataSpec)
        }

        // Ignore Range requests for disguised segments — we always need the
        // whole file. Segments are ~1 MB so this is acceptable.
        val fullSpec = dataSpec.withPosition(0).withLength(DataSpec.LENGTH_UNSET.toLong())

        upstream.open(fullSpec)
        val raw: ByteArray = try {
            readAll(upstream)
        } finally {
            try { upstream.close() } catch (_: Exception) {}
        }

        val payload = try {
            unwrap(raw)
        } catch (_: Exception) {
            raw // fall through — ExoPlayer will surface a normal parse error
        }

        unwrapped = payload
        stream = ByteArrayInputStream(payload).apply {
            if (dataSpec.position > 0) skip(dataSpec.position)
        }
        return (payload.size - dataSpec.position).coerceAtLeast(0L)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val s = stream ?: return upstream.read(buffer, offset, length)
        val n = s.read(buffer, offset, length)
        return n
    }

    override fun close() {
        try { stream?.close() } catch (_: Exception) {}
        stream = null
        unwrapped = null
        try { upstream.close() } catch (_: Exception) {}
    }

    override fun getUri(): Uri? = upstream.uri

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    // ─────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────

    private fun readAll(source: DataSource): ByteArray {
        val out = ByteArrayOutputStream(2 * 1024 * 1024)
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = source.read(buf, 0, buf.size)
            if (n == -1) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun unwrap(bytes: ByteArray): ByteArray {
        if (bytes.size < 16) return bytes

        // PNG?
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            extractPngTail(bytes)?.let { return it }
            extractPngPixels(bytes)?.let { return it }
            return bytes
        }

        // WEBP?
        if (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes.size > 11 &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte()
        ) {
            extractWebpExif(bytes)?.let { return it }
            return bytes
        }

        // Unknown — try raw TS sync scan
        for (i in 0 until bytes.size - 188) {
            if (bytes[i] == 0x47.toByte() && bytes[i + 188] == 0x47.toByte()) {
                return bytes.copyOfRange(i, bytes.size)
            }
        }
        return bytes
    }

    /** WEBP with TS bytes in the EXIF chunk. */
    private fun extractWebpExif(bytes: ByteArray): ByteArray? {
        var off = 12
        while (off + 8 <= bytes.size) {
            val tag = String(bytes, off, 4, Charsets.US_ASCII)
            val n = readU32LE(bytes, off + 4)
            off += 8
            if (n < 0 || off + n > bytes.size) return null
            if (tag == "EXIF") {
                val data = bytes.copyOfRange(off, off + n)
                if (data.size >= 189 &&
                    data[0] == 0x47.toByte() && data[188] == 0x47.toByte()
                ) return data
                return null
            }
            off += n + (n and 1)
        }
        return null
    }

    /** PNG where TS bytes are appended after the IEND chunk. */
    private fun extractPngTail(bytes: ByteArray): ByteArray? {
        var off = 8
        while (off + 8 <= bytes.size) {
            val len = readU32BE(bytes, off)
            if (len < 0 || off + 12 + len > bytes.size) return null
            val type = String(bytes, off + 4, 4, Charsets.US_ASCII)
            off += 12 + len
            if (type == "IEND") {
                if (off + 189 <= bytes.size &&
                    bytes[off] == 0x47.toByte() && bytes[off + 188] == 0x47.toByte()
                ) return bytes.copyOfRange(off, bytes.size)
                return null
            }
        }
        return null
    }

    /**
     * PNG where the TS payload is stored as the raw RGB pixel grid,
     * preceded by the ASCII "TIKTIKPX" magic + big-endian length.
     * This is the method used by the tiktokcdn-us.com segments.
     */
    private fun extractPngPixels(bytes: ByteArray): ByteArray? {
        var off = 8
        var width = 0; var height = 0
        var depth = 0; var colorType = 0; var interlace = 0
        val idat = ByteArrayOutputStream(2 * 1024 * 1024)

        while (off + 8 <= bytes.size) {
            val len = readU32BE(bytes, off)
            if (len < 0 || off + 12 + len > bytes.size) return null
            val type = String(bytes, off + 4, 4, Charsets.US_ASCII)
            val dataOff = off + 8
            when (type) {
                "IHDR" -> {
                    if (len < 13) return null
                    width = readU32BE(bytes, dataOff)
                    height = readU32BE(bytes, dataOff + 4)
                    depth = bytes[dataOff + 8].toInt() and 0xFF
                    colorType = bytes[dataOff + 9].toInt() and 0xFF
                    interlace = bytes[dataOff + 12].toInt() and 0xFF
                }
                "IDAT" -> idat.write(bytes, dataOff, len)
                "IEND" -> break
            }
            off += 12 + len
        }

        if (width <= 0 || height <= 0) return null
        if (depth != 8 || interlace != 0) return null
        if (colorType != 2 && colorType != 6) return null

        val raw = inflateZlib(idat.toByteArray()) ?: return null
        val bpp = if (colorType == 6) 4 else 3
        val stride = width * bpp
        val rgb = ByteArray(width * height * 3)

        var srcOff = 0
        var dstOff = 0
        var prev = ByteArray(stride)

        for (y in 0 until height) {
            if (srcOff + 1 + stride > raw.size) return null
            val filter = raw[srcOff].toInt() and 0xFF
            srcOff++
            val recon = ByteArray(stride)
            for (i in 0 until stride) {
                val a = if (i >= bpp) recon[i - bpp].toInt() and 0xFF else 0
                val b = prev[i].toInt() and 0xFF
                val c = if (i >= bpp) prev[i - bpp].toInt() and 0xFF else 0
                val v = raw[srcOff + i].toInt() and 0xFF
                val out = when (filter) {
                    0 -> v
                    1 -> (v + a) and 0xFF
                    2 -> (v + b) and 0xFF
                    3 -> (v + ((a + b) shr 1)) and 0xFF
                    4 -> (v + paeth(a, b, c)) and 0xFF
                    else -> return null
                }
                recon[i] = out.toByte()
            }
            srcOff += stride

            if (colorType == 2) {
                System.arraycopy(recon, 0, rgb, dstOff, stride)
                dstOff += stride
            } else {
                var i = 0
                while (i < stride) {
                    rgb[dstOff++] = recon[i]
                    rgb[dstOff++] = recon[i + 1]
                    rgb[dstOff++] = recon[i + 2]
                    i += 4
                }
            }
            prev = recon
        }

        if (rgb.size < 12) return null

        // Magic "TIKTIKPX"
        val magic = byteArrayOf(
            'T'.code.toByte(), 'I'.code.toByte(), 'K'.code.toByte(),
            'T'.code.toByte(), 'I'.code.toByte(), 'K'.code.toByte(),
            'P'.code.toByte(), 'X'.code.toByte()
        )
        for (k in magic.indices) if (rgb[k] != magic[k]) return null

        val n = readU32BE(rgb, 8)
        if (n <= 0 || 12 + n > rgb.size) return null
        val gz = rgb.copyOfRange(12, 12 + n)
        if (gz.size < 2 || gz[0] != 0x1F.toByte() || gz[1] != 0x8B.toByte()) return null

        return gunzip(gz)
    }

    private fun inflateZlib(input: ByteArray): ByteArray? = try {
        val inflater = Inflater()
        inflater.setInput(input)
        val out = ByteArrayOutputStream(input.size * 4)
        val buf = ByteArray(16 * 1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && inflater.needsInput()) break
            out.write(buf, 0, n)
        }
        inflater.end()
        out.toByteArray()
    } catch (_: Exception) { null }

    private fun gunzip(input: ByteArray): ByteArray? = try {
        GZIPInputStream(ByteArrayInputStream(input)).use { it.readBytes() }
    } catch (_: Exception) { null }

    private fun readU32BE(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or
        ((b[off + 1].toInt() and 0xFF) shl 16) or
        ((b[off + 2].toInt() and 0xFF) shl 8) or
        (b[off + 3].toInt() and 0xFF)

    private fun readU32LE(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
        ((b[off + 1].toInt() and 0xFF) shl 8) or
        ((b[off + 2].toInt() and 0xFF) shl 16) or
        ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }
}

package com.streambd.iptv.utils

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.ByteArrayOutputStream


@UnstableApi
class PngStrippingDataSource(private val upstream: DataSource) : DataSource {

    private var data: ByteArray? = null
    private var pos = 0
    private var end = 0
    private var passthrough = true
    private var openedUri: Uri? = null

    // passthrough মোডে প্রথম বাইটগুলো পিক করার জন্য
    private var peeked: ByteArray? = null
    private var peekPos = 0
    private var peekEnd = 0

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        openedUri = dataSpec.uri
        resetState()

        // Range রিকুয়েস্ট (offset/length) হলে ছবি-ট্রিকের সম্ভাবনা নেই, সরাসরি পাস
        if (dataSpec.position != 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) {
            passthrough = true
            return upstream.open(dataSpec)
        }

        val length = upstream.open(dataSpec)

        val contentType = upstream.responseHeaders.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value?.firstOrNull()?.lowercase().orEmpty()

        val looksLikeImageByHeader = contentType.startsWith("image/")

        // বড় নন-ইমেজ রেসপন্স (mp4/mkv) এ পিক করার দরকার নেই
        val mayBeImage = looksLikeImageByHeader ||
            contentType.isEmpty() ||
            contentType == "application/octet-stream" ||
            contentType.startsWith("video/mp2t") ||
            contentType.startsWith("text/plain")

        if (!mayBeImage) {
            passthrough = true
            return length
        }

        // প্রথম ১৬ বাইট পিক করে সিগনেচার দেখি
        val head = ByteArray(16)
        var got = 0
        while (got < head.size) {
            val n = upstream.read(head, got, head.size - got)
            if (n == C.RESULT_END_OF_INPUT) break
            got += n
        }

        val isImageBySig = got >= 8 && hasImageSignature(head)

        if (!isImageBySig && !looksLikeImageByHeader) {
            // সাধারণ সেগমেন্ট: পিক করা বাইট আগে ফেরত দিয়ে বাকিটা upstream থেকে
            passthrough = true
            peeked = head
            peekPos = 0
            peekEnd = got
            return if (length == C.LENGTH_UNSET.toLong()) length else length
        }

        // ছবির মতো রেসপন্স: পুরোটা নামাই
        passthrough = false
        val out = ByteArrayOutputStream(if (length > 0) length.toInt().coerceAtMost(MAX_BYTES) else 1 shl 20)
        out.write(head, 0, got)
        val buf = ByteArray(16 * 1024)
        try {
            while (out.size() <= MAX_BYTES) {
                val n = upstream.read(buf, 0, buf.size)
                if (n == C.RESULT_END_OF_INPUT) break
                out.write(buf, 0, n)
            }
        } finally {
            upstream.close()
        }
        val raw = out.toByteArray()

        val start = if (isPng(raw) || hasImageSignature(raw)) findTsStart(raw) else 0
        data = raw
        if (start < 0) {
            Log.w(TAG, "ছবির ভেতরে TS ডেটা পাওয়া যায়নি (size=${raw.size}) $openedUri")
            pos = 0
        } else {
            Log.d(TAG, "stripped: TS offset=$start of ${raw.size}")
            pos = start
        }
        end = raw.size
        return (end - pos).toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0

        if (passthrough) {
            val p = peeked
            if (p != null && peekPos < peekEnd) {
                val n = minOf(length, peekEnd - peekPos)
                System.arraycopy(p, peekPos, buffer, offset, n)
                peekPos += n
                return n
            }
            return upstream.read(buffer, offset, length)
        }

        val d = data ?: return C.RESULT_END_OF_INPUT
        if (pos >= end) return C.RESULT_END_OF_INPUT
        val n = minOf(length, end - pos)
        System.arraycopy(d, pos, buffer, offset, n)
        pos += n
        return n
    }

    override fun getUri(): Uri? = if (passthrough) upstream.uri else openedUri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        // upstream.close() idempotent; stripped মোডে আগেই বন্ধ হয়েছে
        try { upstream.close() } catch (e: Exception) {}
        resetState()
    }

    private fun resetState() {
        data = null
        pos = 0
        end = 0
        peeked = null
        peekPos = 0
        peekEnd = 0
        passthrough = true
    }

    // -------------------------------------------------------------------------

    private fun isPng(b: ByteArray): Boolean =
        b.size > 8 &&
            b[0] == 0x89.toByte() && b[1] == 0x50.toByte() &&
            b[2] == 0x4E.toByte() && b[3] == 0x47.toByte()

    /** PNG, JPEG, GIF, WEBP(RIFF....WEBP) */
    private fun hasImageSignature(b: ByteArray): Boolean {
        if (b.size < 4) return false
        if (isPng(b)) return true
        if (b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()) return true
        if (b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte()) return true
        if (b.size >= 12 && b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() &&
            b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte() &&
            b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte() &&
            b[10] == 'B'.code.toByte() && b[11] == 'P'.code.toByte()
        ) return true
        return false
    }

    /** PNG হলে IEND এর পরে খোঁজে; না মিললে পুরো ফাইলে TS সিঙ্ক প্যাটার্ন (৫×১৮৮) স্ক্যান করে। */
    private fun findTsStart(b: ByteArray): Int {
        if (isPng(b)) {
            var p = 8
            while (p + 12 <= b.size) {
                val len = ((b[p].toInt() and 0xFF) shl 24) or ((b[p + 1].toInt() and 0xFF) shl 16) or
                    ((b[p + 2].toInt() and 0xFF) shl 8) or (b[p + 3].toInt() and 0xFF)
                if (len < 0 || p + 12L + len > b.size) break
                val isIend = b[p + 4] == 'I'.code.toByte() && b[p + 5] == 'E'.code.toByte() &&
                    b[p + 6] == 'N'.code.toByte() && b[p + 7] == 'D'.code.toByte()
                p += 12 + len
                if (isIend) {
                    if (p < b.size && hasSyncRun(b, p)) return p
                    break
                }
            }
        }
        for (i in 0 until b.size - SYNC_RUN * 188) {
            if (b[i] == 0x47.toByte() && hasSyncRun(b, i)) return i
        }
        return -1
    }

    private fun hasSyncRun(b: ByteArray, start: Int): Boolean {
        for (k in 0 until SYNC_RUN) {
            val idx = start + k * 188
            if (idx >= b.size || b[idx] != 0x47.toByte()) return false
        }
        return true
    }

    private companion object {
        const val TAG = "PngStripDS"
        const val SYNC_RUN = 5
        const val MAX_BYTES = 16 * 1024 * 1024
    }
}

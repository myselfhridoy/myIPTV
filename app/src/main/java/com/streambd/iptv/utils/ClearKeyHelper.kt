package com.streambd.iptv.utils

import android.os.Looper
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DrmInitData
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSession
import androidx.media3.exoplayer.drm.DrmSessionEventListener
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@OptIn(UnstableApi::class)
object ClearKeyHelper {

    private val RE_HEX_PAIR = Regex("""([0-9a-fA-F]{32}):([0-9a-fA-F]{32})""")

    data class ClearKeyParsed(
        val jwkBytes: ByteArray,
        val kidBytesList: List<ByteArray>
    )

    /**
     * Parses raw key pair string (e.g. "kid:key" or "kid1:key1,kid2:key2" or JSON) into JWK bytes
     * and a list of 16-byte KIDs.
     */
    fun parseClearKeys(rawKeyPair: String?, licenseServer: String?): ClearKeyParsed? {
        val distinctPairs = mutableMapOf<String, String>() // kidHex -> keyHex

        // 1. Try finding hex pairs in rawKeyPair
        if (!rawKeyPair.isNullOrBlank()) {
            for (m in RE_HEX_PAIR.findAll(rawKeyPair)) {
                distinctPairs[m.groupValues[1].lowercase()] = m.groupValues[2].lowercase()
            }
        }

        // 2. Try licenseServer if no pairs found or if it contains JSON / hex pairs
        if (!licenseServer.isNullOrBlank()) {
            val trimmed = licenseServer.trim()
            if (trimmed.startsWith("{")) {
                try {
                    val jsonObj = JSONObject(trimmed)
                    if (jsonObj.has("keys")) {
                        val arr = jsonObj.getJSONArray("keys")
                        val kidList = mutableListOf<ByteArray>()
                        for (i in 0 until arr.length()) {
                            val kObj = arr.getJSONObject(i)
                            val kidB64 = kObj.optString("kid")
                            if (kidB64.isNotEmpty()) {
                                try {
                                    val b = Base64.decode(kidB64, Base64.URL_SAFE or Base64.NO_PADDING)
                                    if (b.size == 16) kidList.add(b)
                                } catch (_: Exception) {}
                            }
                        }
                        if (kidList.isNotEmpty()) {
                            return ClearKeyParsed(trimmed.toByteArray(Charsets.UTF_8), kidList)
                        }
                    } else {
                        // Key-value mapping: {"kidHex": "keyHex"}
                        jsonObj.keys().forEach { k ->
                            val v = jsonObj.optString(k)
                            if (k.length == 32 && v.length == 32) {
                                distinctPairs[k.lowercase()] = v.lowercase()
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Also check for hex pairs embedded in licenseServer
            if (distinctPairs.isEmpty()) {
                for (m in RE_HEX_PAIR.findAll(trimmed)) {
                    distinctPairs[m.groupValues[1].lowercase()] = m.groupValues[2].lowercase()
                }
            }
        }

        if (distinctPairs.isEmpty()) return null

        val keysArray = JSONArray()
        val kidBytesList = mutableListOf<ByteArray>()

        for ((kidHex, keyHex) in distinctPairs) {
            try {
                val kidBytes = kidHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val keyBytes = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                if (kidBytes.size != 16 || keyBytes.size != 16) continue

                val kidB64 = Base64.encodeToString(
                    kidBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                )
                val keyB64 = Base64.encodeToString(
                    keyBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                )

                val keyObj = JSONObject().apply {
                    put("kty", "oct")
                    put("k", keyB64)
                    put("kid", kidB64)
                }
                keysArray.put(keyObj)
                kidBytesList.add(kidBytes)
            } catch (_: Exception) {}
        }

        if (kidBytesList.isEmpty()) return null

        val jwkJson = JSONObject().apply {
            put("keys", keysArray)
            put("type", "temporary")
        }

        return ClearKeyParsed(
            jwkBytes = jwkJson.toString().toByteArray(Charsets.UTF_8),
            kidBytesList = kidBytesList
        )
    }

    /**
     * Builds a standard ISO/IEC 23001-7 v1 PSSH box containing the given KIDs.
     */
    fun buildPsshBox(systemUuid: UUID, kidBytesList: List<ByteArray>): ByteArray {
        val numKids = kidBytesList.size
        val boxSize = 36 + 16 * numKids
        val buffer = ByteBuffer.allocate(boxSize).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(boxSize)
        buffer.put("pssh".toByteArray(Charsets.US_ASCII))
        buffer.putInt(0x01000000) // version 1, flags 0
        buffer.putLong(systemUuid.mostSignificantBits)
        buffer.putLong(systemUuid.leastSignificantBits)
        buffer.putInt(numKids)
        for (kid in kidBytesList) {
            buffer.put(kid)
        }
        buffer.putInt(0) // DataSize = 0
        return buffer.array()
    }

    /**
     * Builds synthetic [DrmInitData] for ClearKey and Common PSSH so ExoPlayer can bridge
     * Widevine/PlayReady-only manifests to Android ClearKey CDM.
     */
    fun buildSyntheticDrmInitData(kidBytesList: List<ByteArray>): DrmInitData {
        val schemes = mutableListOf<DrmInitData.SchemeData>()
        // 1. Combined PSSH box containing all KIDs
        val allCkPssh = buildPsshBox(C.CLEARKEY_UUID, kidBytesList)
        val allCmPssh = buildPsshBox(C.COMMON_PSSH_UUID, kidBytesList)
        schemes.add(DrmInitData.SchemeData(C.CLEARKEY_UUID, "video/mp4", allCkPssh))
        schemes.add(DrmInitData.SchemeData(C.COMMON_PSSH_UUID, "video/mp4", allCmPssh))

        // 2. If multiple KIDs, also add per-KID PSSH boxes so single-key decoders match
        if (kidBytesList.size > 1) {
            for (kid in kidBytesList) {
                val ckPssh = buildPsshBox(C.CLEARKEY_UUID, listOf(kid))
                val cmPssh = buildPsshBox(C.COMMON_PSSH_UUID, listOf(kid))
                schemes.add(DrmInitData.SchemeData(C.CLEARKEY_UUID, "video/mp4", ckPssh))
                schemes.add(DrmInitData.SchemeData(C.COMMON_PSSH_UUID, "video/mp4", cmPssh))
            }
        }
        return DrmInitData(schemes)
    }

    /**
     * Creates a [DrmSessionManager] that injects ClearKey scheme data into formats that
     * only specify Widevine or PlayReady in their MPD/HLS manifests.
     */
    fun createClearKeyDrmSessionManager(
        jwkBytes: ByteArray,
        kidBytesList: List<ByteArray>
    ): DrmSessionManager {
        val localCallback = LocalMediaDrmCallback(jwkBytes)
        val defaultDrmSessionManager = DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setMultiSession(true)
            .build(localCallback)

        val syntheticDrmInitData = buildSyntheticDrmInitData(kidBytesList)
        return ClearKeyDelegatingDrmSessionManager(defaultDrmSessionManager, syntheticDrmInitData)
    }

    private class ClearKeyDelegatingDrmSessionManager(
        private val delegate: DrmSessionManager,
        private val syntheticDrmInitData: DrmInitData
    ) : DrmSessionManager {

        override fun setPlayer(playbackLooper: Looper, playerId: PlayerId) {
            delegate.setPlayer(playbackLooper, playerId)
        }

        override fun prepare() {
            delegate.prepare()
        }

        override fun release() {
            delegate.release()
        }

        override fun preacquireSession(
            eventDispatcher: DrmSessionEventListener.EventDispatcher?,
            format: Format
        ): DrmSessionManager.DrmSessionReference {
            return delegate.preacquireSession(eventDispatcher, patchFormat(format))
        }

        override fun acquireSession(
            eventDispatcher: DrmSessionEventListener.EventDispatcher?,
            format: Format
        ): DrmSession? {
            return delegate.acquireSession(eventDispatcher, patchFormat(format))
        }

        override fun getCryptoType(format: Format): Int {
            val patched = patchFormat(format)
            val type = delegate.getCryptoType(patched)
            return if (type == C.CRYPTO_TYPE_UNSUPPORTED) C.CRYPTO_TYPE_FRAMEWORK else type
        }

        private fun patchFormat(format: Format): Format {
            val originalInit = format.drmInitData
            val merged = mutableListOf<DrmInitData.SchemeData>()

            // 1. Injected ClearKey synthetic schemes ALWAYS come first so DefaultDrmSessionManager
            // (configured with CLEARKEY_UUID) immediately finds a valid SchemeData with actual PSSH box bytes.
            for (i in 0 until syntheticDrmInitData.schemeDataCount) {
                merged.add(syntheticDrmInitData.get(i))
            }

            // 2. Append original schemes from the manifest/stream format, but skip empty placeholders
            // (like cenc:default_KID without PSSH box) that could confuse decoders.
            if (originalInit != null) {
                for (i in 0 until originalInit.schemeDataCount) {
                    val s = originalInit.get(i)
                    if ((s.matches(C.COMMON_PSSH_UUID) || s.matches(C.CLEARKEY_UUID)) && s.data == null) {
                        continue
                    }
                    merged.add(s)
                }
            }

            val schemeType = originalInit?.schemeType ?: C.CENC_TYPE_cenc
            return format.buildUpon()
                .setDrmInitData(DrmInitData(schemeType, merged))
                .build()
        }
    }
}

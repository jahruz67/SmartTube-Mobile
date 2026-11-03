package com.liskovsoft.youtubeapi.app.nsigsolver.common

import kotlinx.coroutines.runBlocking

internal object YouTubeInfoExtractor: InfoExtractor() {
    private const val PLAYER_CODE_CACHE_TTL_MS = 30_000L
    private const val MAX_CACHED_PLAYER_CODE_CHARS = 2_000_000
    val cache: CacheService = CacheService
    private val playerCodeCacheLock = Any()
    private var cachedPlayerUrl: String? = null
    private var cachedPlayerCode: String? = null
    private var cachedPlayerCodeTimeMs: Long = 0

    fun loadPlayer(playerUrl: String): String {
        cachedPlayerCode(playerUrl)?.let { return it }

        val code = runBlocking {
            downloadWebpage(playerUrl)
        }
        rememberPlayerCode(playerUrl, code)
        return code
    }

    fun loadPlayerSilent(playerUrl: String): String? {
        return try {
            loadPlayer(playerUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun downloadWebpageWithRetries(url: String, errorMsg: String? = null): String = runBlocking {
        return@runBlocking downloadWebpage(url, tries = 3, errorMsg = errorMsg)
    }

    fun downloadWebpageSilent(url: String): String? {
        return try {
            downloadWebpageWithRetries(url)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun cachedPlayerCode(playerUrl: String): String? {
        synchronized(playerCodeCacheLock) {
            if (cachedPlayerUrl != playerUrl || cachedPlayerCode == null) {
                return null
            }

            if (System.currentTimeMillis() - cachedPlayerCodeTimeMs >= PLAYER_CODE_CACHE_TTL_MS) {
                cachedPlayerUrl = null
                cachedPlayerCode = null
                cachedPlayerCodeTimeMs = 0
                return null
            }

            return cachedPlayerCode
        }
    }

    private fun rememberPlayerCode(playerUrl: String, code: String) {
        synchronized(playerCodeCacheLock) {
            if (code.length > MAX_CACHED_PLAYER_CODE_CHARS) {
                cachedPlayerUrl = null
                cachedPlayerCode = null
                cachedPlayerCodeTimeMs = 0
                return
            }

            cachedPlayerUrl = playerUrl
            cachedPlayerCode = code
            cachedPlayerCodeTimeMs = System.currentTimeMillis()
        }
    }
}

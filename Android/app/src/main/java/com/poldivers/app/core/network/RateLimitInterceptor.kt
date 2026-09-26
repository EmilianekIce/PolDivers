package com.poldivers.app.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.ArrayDeque

/**
 * api.helldivers2.dev allows 5 requests per 10 s per IP and rejects the rest immediately with
 * 429 (token bucket, no queue). Several screens load in parallel and auto-refresh, so instead of
 * failing we wait for a free slot here, and if the server still says 429 we honour Retry-After
 * and try again.
 */
class RateLimitInterceptor(
    private val maxRequests: Int = 4,
    private val windowMs: Long = 10_500,
) : Interceptor {

    private val sent = ArrayDeque<Long>()
    private val lock = Object()

    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        while (true) {
            acquire()
            val response = chain.proceed(chain.request())
            if (response.code != 429 || attempt >= MAX_RETRIES) return response
            val waitS = response.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 15) ?: 3
            response.close()
            attempt++
            sleep(waitS * 1000)
        }
    }

    private fun acquire() {
        synchronized(lock) {
            while (true) {
                val now = System.currentTimeMillis()
                while (sent.isNotEmpty() && now - sent.peekFirst()!! >= windowMs) sent.pollFirst()
                if (sent.size < maxRequests) {
                    sent.addLast(now)
                    return
                }
                val waitMs = windowMs - (now - sent.peekFirst()!!) + 50
                try {
                    lock.wait(waitMs.coerceAtLeast(50))
                } catch (e: InterruptedException) {
                    throw IOException("Przerwano oczekiwanie na limit API", e)
                }
            }
        }
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            throw IOException("Przerwano oczekiwanie na limit API", e)
        }
    }

    private companion object {
        const val MAX_RETRIES = 2
    }
}

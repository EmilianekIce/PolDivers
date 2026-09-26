package com.poldivers.app.data.hd2

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.CoroutineContext

/**
 * Last successful response of each endpoint, per language, kept on disk so a cold start can show
 * data instantly while the fresh copy downloads (the API is slow and rate limited).
 */
class DiskCache(private val dir: File, private val json: Json) {

    private fun file(key: String, language: String) =
        File(dir, "${key}_${language.ifBlank { "default" }}.json".replace(Regex("[^A-Za-z0-9_.-]"), "_"))

    suspend fun <T> read(key: String, language: String, serializer: KSerializer<T>): T? = withContext(Dispatchers.IO) {
        val f = file(key, language)
        if (!f.exists()) return@withContext null
        runCatching { json.decodeFromString(serializer, f.readText()) }.getOrNull()
    }

    suspend fun <T> write(key: String, language: String, serializer: KSerializer<T>, value: T) = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            val f = file(key, language)
            val tmp = File(dir, f.name + ".tmp")
            tmp.writeText(json.encodeToString(serializer, value))
            tmp.renameTo(f)
        }
        Unit
    }
}

/**
 * Coroutine context marker: while present, repository reads may return the previous (in-memory
 * or on-disk) copy instead of waiting for the network. [com.poldivers.app.ui.common.Loadable]
 * uses it for the first paint, then reloads without it.
 */
object StaleAllowed : CoroutineContext.Element {
    object Key : CoroutineContext.Key<StaleAllowed>

    override val key: CoroutineContext.Key<*> get() = Key
}

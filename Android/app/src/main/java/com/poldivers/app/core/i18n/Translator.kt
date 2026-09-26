package com.poldivers.app.core.i18n

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap

/**
 * English -> Polish on the phone (ML Kit, ~30 MB language model downloaded once on first use).
 * Used for content that only exists in English: wiki articles, effect / hazard descriptions.
 * Game texts from the API are NOT machine-translated -- those come in Polish from the game.
 */
class Translator {

    private val client by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.POLISH)
                .build(),
        )
    }
    private val modelLock = Mutex()
    private var modelReady = false
    private val cache = ConcurrentHashMap<String, String>()

    private suspend fun ensureModel() = modelLock.withLock {
        if (!modelReady) {
            client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            modelReady = true
        }
    }

    suspend fun translate(text: String): String {
        if (text.isBlank()) return text
        cache[text]?.let { return it }
        ensureModel()
        return client.translate(text).await().also { cache[text] = it }
    }

    /** Translates every text node of an HTML fragment, keeping markup, links and images. */
    suspend fun translateHtml(html: String): String = withContext(Dispatchers.Default) {
        ensureModel()
        val doc = Jsoup.parseBodyFragment(html)
        val nodes = doc.body().select("*").flatMap { it.textNodes() }
            .filter { it.text().any(Char::isLetter) && it.parent()?.nodeName() !in setOf("style", "script", "code", "pre") }
        val limit = Semaphore(6)
        coroutineScope {
            nodes.map { node ->
                async {
                    limit.withPermit {
                        val original = node.text()
                        val lead = original.takeWhile { it.isWhitespace() }
                        val trail = original.takeLastWhile { it.isWhitespace() }
                        val translated = runCatching { translate(original.trim()) }.getOrDefault(original.trim())
                        node.text(lead + translated + trail)
                    }
                }
            }.awaitAll()
        }
        doc.body().html()
    }
}

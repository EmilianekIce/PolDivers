package com.poldivers.app.core.i18n

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.poldivers.app.core.prefs.AppPreferences
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
import com.google.mlkit.nl.translate.Translator as MlTranslator

/**
 * On-device machine translation (ML Kit; each language model ~30 MB, downloaded once on first
 * use). Target = the language chosen in settings. Used only for content that exists in English
 * alone (wiki, community descriptions) -- game texts come localised from the API.
 */
class Translator(private val prefs: AppPreferences) {

    private val clients = ConcurrentHashMap<String, MlTranslator>()
    private val ready = ConcurrentHashMap<String, Boolean>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val cache = ConcurrentHashMap<String, String>()

    /** ML Kit language of the app's current API language. */
    val targetLanguage: String get() = mlLanguage(prefs.language.value.tag)

    /** False when the chosen language is English (nothing to translate). */
    val canTranslate: Boolean get() = targetLanguage != TranslateLanguage.ENGLISH

    private fun mlLanguage(tag: String): String = when {
        tag.startsWith("pl") -> TranslateLanguage.POLISH
        tag.startsWith("de") -> TranslateLanguage.GERMAN
        tag.startsWith("fr") -> TranslateLanguage.FRENCH
        tag.startsWith("es") -> TranslateLanguage.SPANISH
        tag.startsWith("it") -> TranslateLanguage.ITALIAN
        tag.startsWith("ru") -> TranslateLanguage.RUSSIAN
        tag.startsWith("zh") -> TranslateLanguage.CHINESE
        else -> TranslateLanguage.ENGLISH
    }

    private suspend fun client(from: String, to: String): MlTranslator {
        val key = "$from>$to"
        val client = clients.getOrPut(key) {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build())
        }
        locks.getOrPut(key) { Mutex() }.withLock {
            if (ready[key] != true) {
                client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                ready[key] = true
            }
        }
        return client
    }

    /** English -> chosen language. */
    suspend fun translate(text: String): String = translate(text, TranslateLanguage.ENGLISH, targetLanguage)

    /** Chosen language -> English (e.g. to search the English wiki for a term from the news). */
    suspend fun toEnglish(text: String): String = translate(text, targetLanguage, TranslateLanguage.ENGLISH)

    private suspend fun translate(text: String, from: String, to: String): String {
        if (text.isBlank() || from == to) return text
        val cacheKey = "$from>$to|$text"
        cache[cacheKey]?.let { return it }
        return client(from, to).translate(text).await().also { cache[cacheKey] = it }
    }

    /** Translates every text node of an HTML fragment, keeping markup, links and images. */
    suspend fun translateHtml(html: String): String = withContext(Dispatchers.Default) {
        val to = targetLanguage
        client(TranslateLanguage.ENGLISH, to)
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
                        val translated = runCatching { translate(original.trim(), TranslateLanguage.ENGLISH, to) }
                            .getOrDefault(original.trim())
                        node.text(lead + translated + trail)
                    }
                }
            }.awaitAll()
        }
        doc.body().html()
    }
}

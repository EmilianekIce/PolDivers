package com.poldivers.app.core.network

import com.poldivers.app.BuildConfig
import com.poldivers.app.core.prefs.AppPreferences
import com.poldivers.app.data.hd2.Hd2ApiService
import com.poldivers.app.data.wiki.WikiApiService
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

private const val HD2_BASE_URL = "https://api.helldivers2.dev/"
private const val WIKI_BASE_URL = "https://helldivers.wiki.gg/"

/**
 * The community API asks every client to identify itself via X-Super-Client / X-Super-Contact,
 * and to negotiate the response language via a plain Accept-Language header
 * (see https://helldivers-2.github.io/api/). No API key is required.
 */
private class Hd2HeaderInterceptor(private val prefs: AppPreferences) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("X-Super-Client", "poldivers-android")
            .header("X-Super-Contact", "poldivers-app (github.com/poldivers)")
            .header("Accept-Language", prefs.language.value.tag)
            .build()
        return chain.proceed(request)
    }
}

/** MediaWiki asks for a descriptive User-Agent identifying the app + a contact method. */
private class WikiHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", "PolDivers-Android/${BuildConfig.VERSION_NAME}")
            .build()
        return chain.proceed(request)
    }
}

object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        // Several fields come back as explicit null (e.g. region health) -- fall back to defaults.
        coerceInputValues = true
    }

    private fun loggingInterceptor() = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    /** Client for GitHub release checks / APK downloads. */
    val plainClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "PolDivers-Android/${BuildConfig.VERSION_NAME}")
                        .build(),
                )
            }
            .build()
    }

    fun provideHd2Api(prefs: AppPreferences): Hd2ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(Hd2HeaderInterceptor(prefs))
            .addInterceptor(loggingInterceptor())
            .build()

        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(HD2_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(Hd2ApiService::class.java)
    }

    fun provideWikiApi(): WikiApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(WikiHeaderInterceptor())
            .addInterceptor(loggingInterceptor())
            .build()

        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(WIKI_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(WikiApiService::class.java)
    }
}

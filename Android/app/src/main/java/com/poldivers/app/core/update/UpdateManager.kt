package com.poldivers.app.core.update

import com.poldivers.app.core.i18n.tr
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.poldivers.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Self-update from GitHub Releases. CI publishes every build as a release tagged `build-<versionCode>`
 * with the signed APK attached (see .github/workflows/android.yml); the app compares that number
 * with its own versionCode, downloads the APK and hands it to the system installer.
 *
 * The repo is public, so the unauthenticated releases API is enough -- no token in the app.
 */
class UpdateManager(context: Context, private val client: OkHttpClient) {

    private val appContext = context.applicationContext

    @Serializable
    data class Release(
        @SerialName("tag_name") val tag: String = "",
        val name: String? = null,
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String = "",
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<Asset> = emptyList(),
    ) {
        val versionCode: Int? get() = tag.substringAfterLast('-').toIntOrNull()
        val apk: Asset? get() = assets.firstOrNull { it.name.endsWith(".apk") }
    }

    @Serializable
    data class Asset(
        val name: String = "",
        @SerialName("browser_download_url") val url: String = "",
        val size: Long = 0,
    )

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class ReadyToInstall(val release: Release, val file: File) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val currentVersion: String = "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"

    suspend fun check() {
        if (_state.value is State.Checking || _state.value is State.Downloading) return
        _state.value = State.Checking
        _state.value = try {
            val release = fetchLatest()
            val code = release.versionCode
            if (code != null && code > BuildConfig.VERSION_CODE && release.apk != null) {
                State.Available(release)
            } else {
                State.UpToDate
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            State.Failed(e.message ?: tr("Brak połączenia z GitHubem", "No connection to GitHub"))
        }
    }

    suspend fun download(release: Release) {
        val asset = release.apk ?: return
        _state.value = State.Downloading(release, 0f)
        _state.value = try {
            val file = withContext(Dispatchers.IO) { downloadApk(release, asset) }
            State.ReadyToInstall(release, file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            State.Failed(e.message ?: tr("Nie udało się pobrać aktualizacji", "Could not download the update"))
        }
    }

    /** Android asks the user once to allow installing apps from PolDivers. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || appContext.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { _state.value = State.Failed(tr("Nie udało się otworzyć instalatora", "Could not open the installer")) }
    }

    private suspend fun fetchLatest(): Release = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub: HTTP ${response.code}")
            json.decodeFromString(Release.serializer(), response.body?.string().orEmpty())
        }
    }

    private fun downloadApk(release: Release, asset: Asset): File {
        val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "PolDivers-${release.versionCode ?: 0}.apk")
        val request = Request.Builder().url(asset.url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException(tr("Pusta odpowiedź", "Empty response"))
            val total = body.contentLength().takeIf { it > 0 } ?: asset.size
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastReported = 0f
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val progress = (copied.toFloat() / total).coerceIn(0f, 1f)
                            if (progress - lastReported >= 0.02f) {
                                lastReported = progress
                                _state.value = State.Downloading(release, progress)
                            }
                        }
                    }
                }
            }
        }
        return target
    }

    companion object {
        const val REPO = "EmilianekIce/PolDivers"
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    }
}

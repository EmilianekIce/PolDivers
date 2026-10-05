package com.poldivers.app.feature.planets

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.poldivers.app.core.art.rememberGameArt
import com.poldivers.app.core.i18n.tr
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.ui.anim.LocalAnimations
import com.poldivers.app.ui.anim.pressScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * 3D galactic war map: the shared WebGL renderer (assets/map3d, also used by the website) in a
 * hardware-accelerated WebView. Everything it needs is bundled in the APK and served from the
 * app's assets; the app feeds it the map model (Map3dModel.kt) and gets planet taps back.
 *
 * Gestures (in the page): one finger pans, two fingers zoom, turn and tilt the galaxy.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GalaxyMap3D(
    data: PlanetsData,
    selectedIndex: Int?,
    hideOurs: Boolean,
    onPlanetClick: (Planet) -> Unit,
    onGesture: () -> Unit,
    onSwitchDimension: () -> Unit,
    onUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    val art = rememberGameArt()
    val animate = LocalAnimations.current
    val latestData by rememberUpdatedState(data)
    val latestClick by rememberUpdatedState(onPlanetClick)
    val latestUnavailable by rememberUpdatedState(onUnavailable)
    val latestAnimate by rememberUpdatedState(animate)

    var webView by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var showLegend by remember { mutableStateOf(false) }
    val sentHideOurs = remember { arrayOfNulls<Boolean>(1) }

    // The model is a few hundred planets plus sector outlines: build it off the main thread.
    val model by produceState<String?>(null, data) {
        value = withContext(Dispatchers.Default) { buildMap3dModel(context, data, art).toString() }
    }

    val bridge = remember {
        object {
            private val main = Handler(Looper.getMainLooper())

            @JavascriptInterface
            fun onReady() = main.post { ready = true }

            @JavascriptInterface
            fun onSelect(index: Int) = main.post {
                latestData.planets.firstOrNull { it.index == index }?.let { latestClick(it) }
            }

            @JavascriptInterface
            fun onFailed(message: String) = main.post { latestUnavailable() }

            @JavascriptInterface
            fun animations(): Boolean = latestAnimate
        }
    }

    fun js(script: String) = webView?.evaluateJavascript("window.PD3D && $script", null)

    LaunchedEffect(ready, model) { if (ready) model?.let { js("PD3D.setModel($it)") } }
    LaunchedEffect(ready, hideOurs) {
        if (!ready) return@LaunchedEffect
        // The first value only sets the state; later changes roll the Super Earth wave.
        js("PD3D.setHideOurs($hideOurs, ${sentHideOurs[0] != null})")
        sentHideOurs[0] = hideOurs
    }
    LaunchedEffect(ready, animate) { if (ready) js("PD3D.setAnim($animate)") }
    LaunchedEffect(ready, selectedIndex) {
        if (!ready) return@LaunchedEffect
        js(if (selectedIndex != null) "PD3D.focus($selectedIndex)" else "PD3D.deselect()")
    }
    DisposableEffect(Unit) { onDispose { webView?.destroy() } }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(0xFF07090C.toInt())
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    webViewClient = object : WebViewClient() {
                        // The page and every image come from the APK's assets (no network needed).
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            if (request.url.host != MAP3D_HOST) return null
                            val path = request.url.path.orEmpty().trimStart('/')
                            return try {
                                WebResourceResponse(mimeType(path), "utf-8", ctx.assets.open(path))
                            } catch (e: IOException) {
                                WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                            }
                        }
                    }
                    addJavascriptInterface(bridge, "PolDivers")
                    loadUrl("https://$MAP3D_HOST/map3d/app.html")
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Camera buttons (the gestures do the same; these are easier with one hand).
        Column(
            Modifier.align(Alignment.TopEnd).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MapButton(Icons.Filled.ZoomOutMap, tr("Resetuj widok", "Reset view")) { onGesture(); js("PD3D.reset()") }
            MapButton(Icons.Filled.RotateLeft, tr("Obróć w lewo", "Rotate left")) { onGesture(); js("PD3D.rotateBy(-Math.PI / 4)") }
            MapButton(Icons.Filled.RotateRight, tr("Obróć w prawo", "Rotate right")) { onGesture(); js("PD3D.rotateBy(Math.PI / 4)") }
            MapButton(Icons.Filled.KeyboardArrowDown, tr("Pochyl (bardziej z boku)", "Tilt (more from the side)")) { onGesture(); js("PD3D.tiltBy(0.35)") }
            MapButton(Icons.Filled.KeyboardArrowUp, tr("Widok z góry", "Top-down view")) { onGesture(); js("PD3D.tiltBy(-0.35)") }
        }
        MapButton(Icons.Filled.Info, tr("Legenda", "Legend"), Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            onGesture()
            showLegend = !showLegend
        }
        DimensionButton(label = "2D", onClick = { onGesture(); onSwitchDimension() }, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))

        AnimatedVisibility(
            visible = showLegend,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 60.dp, end = 60.dp, bottom = 8.dp),
            enter = slideInVertically { it / 2 } + fadeIn() + scaleIn(initialScale = 0.9f),
            exit = slideOutVertically { it / 2 } + fadeOut(),
        ) {
            MapLegendPanel(threeD = true)
        }
    }
}

@Composable
private fun MapButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    FilledTonalIconButton(onClick = onClick, interactionSource = interaction, modifier = modifier.pressScale(interaction)) {
        Icon(icon, contentDescription = label)
    }
}

private fun mimeType(path: String): String = when (path.substringAfterLast('.').lowercase()) {
    "html" -> "text/html"
    "js", "mjs" -> "text/javascript"
    "json" -> "application/json"
    "css" -> "text/css"
    "webp" -> "image/webp"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "svg" -> "image/svg+xml"
    else -> "application/octet-stream"
}

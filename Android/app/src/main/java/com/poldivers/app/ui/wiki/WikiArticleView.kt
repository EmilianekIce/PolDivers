package com.poldivers.app.ui.wiki

import com.poldivers.app.ui.anim.zoomIn
import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.poldivers.app.data.wiki.WikiArticle
import java.net.URLDecoder

private const val WIKI_ORIGIN = "https://helldivers.wiki.gg"

/**
 * Renders a helldivers.wiki.gg article the way the wiki shows it (images, infobox, tables) but
 * without the site's chrome, and restyled for a phone in the app's dark HUD look. JavaScript is
 * off; links to other articles open in-app through [onOpenArticle], images and anything else in an
 * in-app viewer.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WikiArticleView(
    article: WikiArticle,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val html = remember(article) { wrap(article) }
    // Images and non-article links open in an in-app viewer, never an external browser.
    var viewer by remember { mutableStateOf<String?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.parseColor("#0B0D10"))
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = false
                settings.layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                isHorizontalScrollBarEnabled = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url
                        val title = articleTitle(url)
                        when {
                            title != null -> onOpenArticle(title)
                            // In-page anchors (table of contents, footnotes): let the WebView scroll.
                            url.fragment != null && (url.path.isNullOrEmpty() || url.path == "/") -> return false
                            else -> viewer = imageUrl(url) ?: url.toString()
                        }
                        return true
                    }
                }
            }
        },
        update = { view ->
            val key = article.title + "#" + article.html.hashCode()
            if (view.tag != key) {
                view.tag = key
                view.loadDataWithBaseURL("$WIKI_ORIGIN/", html, "text/html", "utf-8", null)
            }
        },
    )
    viewer?.let { url -> InAppViewer(url, onDismiss = { viewer = null }) }
}

/** Direct image URL for a wiki file page ("/wiki/File:X.png") or an image link; null otherwise. */
private fun imageUrl(url: Uri): String? {
    val path = url.path ?: return null
    if (url.host != null && url.host != "helldivers.wiki.gg") {
        return url.toString().takeIf { IMAGE_EXT.containsMatchIn(path) }
    }
    if (path.startsWith("/images/")) return url.toString()
    if (!path.startsWith("/wiki/")) return null
    val title = URLDecoder.decode(path.removePrefix("/wiki/"), "UTF-8")
    val file = listOf("File:", "Plik:", "Image:").firstOrNull { title.startsWith(it) } ?: return null
    return "$WIKI_ORIGIN/wiki/Special:FilePath/" + Uri.encode(title.removePrefix(file))
}

private val IMAGE_EXT = Regex("""\.(png|jpe?g|gif|webp|svg)$""", RegexOption.IGNORE_CASE)

/** Full-screen in-app page: a zoomable image, or any other link (JS on, it may be a real site). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InAppViewer(url: String, onDismiss: () -> Unit) {
    val isImage = url.contains("Special:FilePath/") || url.contains("/images/") ||
        IMAGE_EXT.containsMatchIn(Uri.parse(url).path.orEmpty())
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().zoomIn(0.4f).background(androidx.compose.ui.graphics.Color(0xFF0B0D10)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text(
                    Uri.decode(Uri.parse(url).lastPathSegment.orEmpty()).replace('_', ' '),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(Color.parseColor("#0B0D10"))
                        settings.javaScriptEnabled = !isImage
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        webViewClient = WebViewClient()
                        if (isImage) {
                            val page = """<html><head><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=8">
                                <style>html,body{margin:0;height:100%;background:#0B0D10;display:flex;align-items:center;justify-content:center}
                                img{max-width:100%;max-height:100%;object-fit:contain}</style></head>
                                <body><img src="${escape(url)}"></body></html>"""
                            loadDataWithBaseURL("$WIKI_ORIGIN/", page, "text/html", "utf-8", null)
                        } else {
                            loadUrl(url)
                        }
                    }
                },
            )
        }
    }
}

/** "/wiki/Jet_Brigade" -> "Jet Brigade"; null for files, specials, anchors and other sites. */
private fun articleTitle(url: Uri): String? {
    if (url.host != null && url.host != "helldivers.wiki.gg") return null
    val path = url.path ?: return null
    if (!path.startsWith("/wiki/")) return null
    val title = URLDecoder.decode(path.removePrefix("/wiki/"), "UTF-8").replace('_', ' ')
    if (title.isBlank() || NON_ARTICLE_PREFIXES.any { title.startsWith(it) }) return null
    return title
}

private val NON_ARTICLE_PREFIXES = listOf("File:", "Image:", "Special:", "Template:", "User:", "Talk:", "Plik:")

private fun wrap(article: WikiArticle): String = """
<!doctype html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  :root { color-scheme: dark; }
  html, body { max-width:100%; overflow-x:hidden; }
  /* Nothing may be wider than the phone: wiki markup often hard-codes widths in style="". */
  div, section, aside, figure, span, p, ul, ol, dl, center, .mw-parser-output * { max-width:100% !important; box-sizing:border-box; }
  [style*="width"] { width:auto !important; }
  table [style*="width"], img[style*="width"] { width:auto !important; }
  iframe, embed, object { max-width:100% !important; }
  body { background:#0B0D10; color:#E8EAED; font-family: sans-serif; font-size:15px; line-height:1.5;
         margin:0; padding:12px 14px 40px; word-wrap:break-word; }
  a { color:#FFC400; text-decoration:none; }
  h1,h2,h3,h4 { color:#FFC400; letter-spacing:.5px; line-height:1.25; margin:22px 0 8px; }
  h2 { font-size:19px; border-bottom:1px solid #2A2E35; padding-bottom:4px; text-transform:uppercase; }
  h3 { font-size:16px; }
  /* Keep the wiki's own width/height attributes (small inline icons like stratagem arrows stay
     small) but never let anything overflow the phone screen. */
  img { max-width:100%; height:auto; vertical-align:middle; }
  /* Stratagem input arrows and other inline SVG glyphs: text-sized, not screen-wide. */
  img[src*="Stratagem_Arrow"], img[alt*="Arrow"], img[src$=".svg"]:not([width]) { width:1.5em !important; height:1.5em !important; }
  a > img[width="1"], img[width="0"] { display:none; }
  figure, .thumb, .floatright, .floatleft, .tright, .tleft { float:none !important; margin:10px auto !important;
         max-width:100% !important; text-align:center; }
  figure img, .thumb img, .infobox img, .portable-infobox img { width:auto; max-width:100%; }
  audio { width:100%; height:40px; margin:6px 0; }
  video { width:100%; height:auto; }
  .mw-file-element { max-width:100%; }
  .gallery, ul.gallery { display:flex; flex-wrap:wrap; gap:8px; padding:0; list-style:none; }
  .gallery li, .gallerybox { width:auto !important; max-width:48%; }
  figcaption, .thumbcaption { font-size:12px; color:#9AA0A8; }
  table { display:block; overflow-x:auto; max-width:100%; border-collapse:collapse; font-size:13px; margin:10px 0; }
  th, td { border:1px solid #2A2E35; padding:5px 7px; vertical-align:top; }
  th { background:#14171C; color:#FFC400; }
  .infobox, table.infobox, aside.portable-infobox, .portable-infobox { float:none !important; width:100% !important;
         max-width:100% !important; margin:0 0 14px !important; background:#14171C; border:1px solid #2A2E35; }
  .navbox, .navbox-inner, .mw-editsection, .noprint, #toc, .toc, .mw-empty-elt, .catlinks, .printfooter,
  .mbox-small, .ambox, .metadata, .mw-references-wrap sup, .navigation-not-searchable, .reference-text .mw-cite-backlink,
  .mw-jump-link, .sidebar, .hatnote img { display:none !important; }
  .reference { font-size:10px; }
  ul, ol { padding-left:20px; }
  pre, code { white-space:pre-wrap; background:#14171C; }
  .poldivers-source { margin-top:28px; padding-top:10px; border-top:1px solid #2A2E35; font-size:12px; color:#9AA0A8; }
</style></head>
<body>
<h1>${escape(article.title)}</h1>
${article.html}
<div class="poldivers-source">${com.poldivers.app.core.i18n.tr("Źródło", "Source")}: <a href="$WIKI_ORIGIN/wiki/${Uri.encode(article.title.replace(' ', '_'))}">Helldivers Wiki — ${escape(article.title)}</a>.
${com.poldivers.app.core.i18n.tr("Treść na licencji", "Content licensed under")} <a href="https://creativecommons.org/licenses/by-sa/4.0/">CC BY-SA 4.0</a>; ${com.poldivers.app.core.i18n.tr("autorzy: społeczność helldivers.wiki.gg.", "authors: the helldivers.wiki.gg community.")}</div>
</body></html>
"""

private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

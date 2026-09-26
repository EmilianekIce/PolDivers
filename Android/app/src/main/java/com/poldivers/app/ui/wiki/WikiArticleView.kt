package com.poldivers.app.ui.wiki

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.poldivers.app.data.wiki.WikiArticle
import java.net.URLDecoder

private const val WIKI_ORIGIN = "https://helldivers.wiki.gg"

/**
 * Renders a helldivers.wiki.gg article the way the wiki shows it (images, infobox, tables) but
 * without the site's chrome, and restyled for a phone in the app's dark HUD look. JavaScript is
 * off; links to other articles open in-app through [onOpenArticle], everything else goes to the
 * browser.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WikiArticleView(
    article: WikiArticle,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val html = remember(article) { wrap(article) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.parseColor("#0B0D10"))
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = false
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
                            else -> runCatching {
                                view.context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                        return true
                    }
                }
            }
        },
        update = { view ->
            if (view.tag != article.title) {
                view.tag = article.title
                view.loadDataWithBaseURL("$WIKI_ORIGIN/", html, "text/html", "utf-8", null)
            }
        },
    )
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

private val NON_ARTICLE_PREFIXES = listOf("File:", "Special:", "Category:", "Template:", "User:", "Talk:", "Plik:")

private fun wrap(article: WikiArticle): String = """
<!doctype html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  :root { color-scheme: dark; }
  body { background:#0B0D10; color:#E8EAED; font-family: sans-serif; font-size:15px; line-height:1.5;
         margin:0; padding:12px 14px 40px; word-wrap:break-word; }
  a { color:#FFC400; text-decoration:none; }
  h1,h2,h3,h4 { color:#FFC400; letter-spacing:.5px; line-height:1.25; margin:22px 0 8px; }
  h2 { font-size:19px; border-bottom:1px solid #2A2E35; padding-bottom:4px; text-transform:uppercase; }
  h3 { font-size:16px; }
  img { max-width:100% !important; height:auto !important; }
  figure, .thumb, .floatright, .floatleft, .tright, .tleft { float:none !important; margin:10px auto !important;
         max-width:100% !important; width:auto !important; text-align:center; }
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
<div class="poldivers-source">Źródło: <a href="$WIKI_ORIGIN/wiki/${Uri.encode(article.title.replace(' ', '_'))}">Helldivers Wiki — ${escape(article.title)}</a>.
Treść na licencji <a href="https://creativecommons.org/licenses/by-sa/4.0/">CC BY-SA 4.0</a>; autorzy: społeczność helldivers.wiki.gg.</div>
</body></html>
"""

private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

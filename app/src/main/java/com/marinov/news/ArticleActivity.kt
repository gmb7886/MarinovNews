package com.marinov.news

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomappbar.BottomAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.content.edit

class ArticleActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var bottomAppBar: BottomAppBar
    private var currentFontSize = 100
    private lateinit var currentTheme: String
    private lateinit var preferences: SharedPreferences

    private val CONTENT_SELECTORS = arrayOf(
        "[itemprop='articleBody']",
        ".article-content",
        ".news-article__body",
        ".content-text",
        "article",
        ".post-content"
    )

    companion object {
        private const val PREFS_NAME = "NewsAppPrefs"
        private const val KEY_THEME = "theme"
        private const val KEY_FONT_SIZE = "fontSize"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article)

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        currentTheme = preferences.getString(KEY_THEME, "auto") ?: "auto"
        currentFontSize = preferences.getInt(KEY_FONT_SIZE, 100)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        bottomAppBar = findViewById(R.id.bottomAppBar)

        configureWebView()
        setupControls()
        applyTheme(currentTheme)

        val url = intent.getStringExtra("url") ?: ""
        loadArticle(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        ViewCompat.setOnApplyWindowInsetsListener(webView) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(0, statusBar, 0, navBar)
            insets
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webView.settings.forceDark = WebSettings.FORCE_DARK_AUTO
        }
    }

    private fun setupControls() {
        bottomAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.btnDecreaseFont -> {
                    adjustFontSize(-10)
                    true
                }
                R.id.btnIncreaseFont -> {
                    adjustFontSize(10)
                    true
                }
                R.id.btnThemeAuto -> {
                    applyTheme("auto")
                    true
                }
                R.id.btnThemeLight -> {
                    applyTheme("light")
                    true
                }
                R.id.btnThemeDark -> {
                    applyTheme("dark")
                    true
                }
                R.id.btnThemeSepia -> {
                    applyTheme("sepia")
                    true
                }
                else -> false
            }
        }
    }

    private fun adjustFontSize(delta: Int) {
        currentFontSize = (currentFontSize + delta).coerceIn(80, 150)
        preferences.edit { putInt(KEY_FONT_SIZE, currentFontSize) }
        updateFontSize()
    }

    private fun updateFontSize() {
        val js = """
            document.body.style.fontSize = '$currentFontSize%';
            document.querySelectorAll('h1, h2, h3').forEach(e => e.style.fontSize = '${currentFontSize + 20}%');
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun applyTheme(theme: String) {
        currentTheme = theme
        preferences.edit { putString(KEY_THEME, theme) }

        val (bgColor, textColor) = when (theme) {
            "light" -> Pair(
                ContextCompat.getColor(this, R.color.white),
                ContextCompat.getColor(this, R.color.black)
            )
            "dark" -> Pair(
                ContextCompat.getColor(this, R.color.black),
                ContextCompat.getColor(this, R.color.white)
            )
            "sepia" -> Pair(
                ContextCompat.getColor(this, R.color.sepia),
                ContextCompat.getColor(this, R.color.sepia_text)
            )
            else -> {
                val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                val isDark = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
                if (isDark) {
                    Pair(
                        ContextCompat.getColor(this, R.color.black),
                        ContextCompat.getColor(this, R.color.white)
                    )
                } else {
                    Pair(
                        ContextCompat.getColor(this, R.color.white),
                        ContextCompat.getColor(this, R.color.black)
                    )
                }
            }
        }

        val js = """
            document.body.style.setProperty('--bg-color', '#${"%06X".format(0xFFFFFF and bgColor)}');
            document.body.style.setProperty('--text-color', '#${"%06X".format(0xFFFFFF and textColor)}');
        """.trimIndent()
        webView.evaluateJavascript(js, null)
        webView.setBackgroundColor(bgColor)

        loadingOverlay.setBackgroundColor(bgColor)
        loadingOverlay.findViewById<ProgressBar>(R.id.loadingSpinner)?.apply {
            indeterminateTintList = ColorStateList.valueOf(textColor)
        }
    }

    private fun loadArticle(url: String) {
        lifecycleScope.launch {
            showLoading()
            val html = withContext(Dispatchers.IO) {
                try {
                    extractArticleContent(url)
                } catch (_: IOException) {
                    errorHtml()
                }
            }
            displayHtmlContent(html)
        }
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        loadingOverlay.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
        loadingOverlay.visibility = View.GONE
    }

    private fun extractArticleContent(url: String): String {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0")
            .timeout(10000)
            .get()

        val title = extractTitle(doc)
        val date = extractDate(doc)
        val description = extractDescription(doc)
        val content = extractContent(doc)

        return buildArticleHtml(title, date, description, content)
    }

    private fun extractTitle(doc: Document): String {
        return doc.selectFirst("[itemprop='headline']")?.text()
            ?: doc.selectFirst("meta[property='og:title']")?.attr("content")
            ?: doc.title()
    }

    private fun extractDate(doc: Document): String {
        val dateElement = doc.selectFirst("[itemprop='datePublished']")
        dateElement?.attr("datetime")?.takeIf { it.isNotEmpty() }?.let {
            return formatDate(it)
        }

        return doc.selectFirst("meta[property='article:published_time']")
            ?.attr("content")
            ?.let { formatDate(it) }
            ?: ""
    }

    private fun extractDescription(doc: Document): String {
        return doc.selectFirst("[itemprop='description']")?.text()
            ?: doc.selectFirst("meta[property='og:description']")?.attr("content")
            ?: ""
    }

    private fun formatDate(isoDate: String): String {
        return try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.getDefault())
            val date = isoFormat.parse(isoDate)
            val outputFormat = SimpleDateFormat("d 'de' MMMM 'de' yyyy 'às' HH:mm", Locale("pt", "BR"))
            outputFormat.format(date)
        } catch (_: ParseException) {
            ""
        }
    }

    private fun extractContent(doc: Document): String {
        doc.select(
            "script, style, nav, footer, header, aside, iframe, form, " +
                    "button, input, select, textarea, .ad, .related-news, .share, " +
                    ".social-media, [title*='Compartilhe'], .social-links, .social-buttons, " +
                    ".gallery-widget-carousel__info-share, .c-newsletter__wrapper, " +
                    ".c-newsletter__text, .c-newsletter__button, .c-form__info-message, " +
                    ".gallery-widget-carousel__info-read-more-container, " +
                    ".gallery-widget-carousel__info-read-more, .gallery-widget-others, " +
                    ".gallery-widget-others__go-back, .icon--arrow-rigth, .icon--chevron-left"
        ).remove()

        val content = findMainContent(doc) ?: doc.body()
        cleanContent(content)
        processImages(content)
        processParagraphs(content)

        return content.html()
    }

    private fun findMainContent(doc: Document): Element? {
        return CONTENT_SELECTORS.firstNotNullOfOrNull { selector ->
            doc.select(selector).firstOrNull()
        }
    }

    private fun cleanContent(content: Element) {
        content.select(
            "button, input, select, textarea, .btn, .form-control, " +
                    ".checkbox, .radio, .search-box, .comment-box, .newsletter-form, " +
                    ".social-share, .social-widget, .icon-share, .sharing-tools, " +
                    ".c-newsletter__obs, .is-hidden"
        ).remove()

        content.select(
            "div:empty, span:empty, label, fieldset, " +
                    ".form-group, .input-group, .button-group, " +
                    ".gallery-widget--is-hidden"
        ).remove()
    }

    private fun processImages(content: Element) {
        val images = content.select("img[src], img[data-src]")
        for (img in images) {
            var src = img.absUrl("data-src")
            if (src.isEmpty()) src = img.absUrl("src")
            if (src.startsWith("//")) src = "https:$src"

            if (img.parent()!!.hasClass("image-social-wrapper")) {
                img.parent()!!.remove()
                continue
            }

            img.attr("src", src)
                .attr("style", "max-width:100%; height:auto; display:block; margin:1rem auto;")
                .removeAttr("data-src")
                .removeAttr("onclick")
        }
    }

    private fun processParagraphs(content: Element) {
        val paragraphs = content.select("p")
        for (p in paragraphs) {
            if (p.text().trim().isEmpty()) {
                p.remove()
            } else {
                p.attr("style", "margin:1rem 0; line-height:1.6;")
            }
        }
    }

    private fun buildArticleHtml(title: String, date: String, description: String, content: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name='viewport' content='width=device-width, initial-scale=1.0'>
                <style>
                    :root {
                        --bg-color: inherit;
                        --text-color: inherit;
                    }
                    body {
                        max-width: 800px;
                        margin: 0 auto;
                        padding: 1rem;
                        font-family: -apple-system, sans-serif;
                        line-height: 1.6;
                        background-color: var(--bg-color) !important;
                        color: var(--text-color) !important;
                        transition: all 0.3s ease;
                    }
                    h1 {
                        font-size: 2rem;
                        margin: 0 0 0.5rem;
                    }
                    .meta {
                        color: #666;
                        font-size: 0.9rem;
                        margin-bottom: 1rem;
                    }
                    .description {
                        font-size: 1.1rem;
                        color: #444;
                        margin-bottom: 1.5rem;
                    }
                    img {
                        max-width: 100% !important;
                        height: auto !important;
                    }
                    p {
                        margin: 1rem 0;
                        line-height: 1.6;
                    }
                    button, input, select, textarea {
                        display: none !important;
                    }
                </style>
            </head>
            <body>
                <h1>$title</h1>
                <div class='meta'>$date</div>
                <div class='description'>$description</div>
                <div class='content'>$content</div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun errorHtml(): String {
        return """
            <html>
            <body style='padding:2rem;text-align:center;'>
                <h1>Erro ao carregar conteúdo</h1>
                <p>O artigo não pôde ser carregado.</p>
            </body>
            </html>
        """.trimIndent()
    }

    private fun displayHtmlContent(html: String) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                hideLoading()
                applyTheme(currentTheme)
                updateFontSize()
            }
        }

        if (html.contains("Erro ao carregar conteúdo")) {
            hideLoading()
        }

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}
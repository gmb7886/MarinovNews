package com.marinov.news

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.color.MaterialColors
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

class ArticleActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var bottomAppBar: BottomAppBar
    private var currentFontSize = 100
    private lateinit var currentTheme: String
    private lateinit var preferences: SharedPreferences
    private var isBottomBarVisible = true

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
        configureSystemBarsForLegacyDevices()
        MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorPrimaryContainer,
            Color.BLACK
        )
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
    private fun configureSystemBarsForLegacyDevices() {
        // Determinar modo escuro apenas para barra de status
        val isDarkMode = when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> {
                val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                currentNightMode == Configuration.UI_MODE_NIGHT_YES
            }
        }

        // Configurações básicas para Android 5.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.apply {
                // Flags fundamentais
                clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

                // Barra de status transparente (Android 7.0+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    statusBarColor = Color.TRANSPARENT
                } else {
                    statusBarColor = Color.BLACK
                }

                // Cor fixa para barra de navegação (tom de cinza)
                navigationBarColor = ContextCompat.getColor(
                    this@ArticleActivity,
                    R.color.fundoleitor
                )

                // Layout extendido para transparência
                var flags = decorView.systemUiVisibility
                flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                decorView.systemUiVisibility = flags
            }
        }

        // CONFIGURAÇÃO DA BARRA DE NAVEGAÇÃO (ÍCONES SEMPRE BRANCOS)
        when {
            // Android 15+ (abordagem especial)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2 -> {
                // Configurar ícones CLAROS (brancos) - LIGHT_NAVIGATION_BARS = ícones ESCUROS
                window.decorView.windowInsetsController?.apply {
                    // REMOVER a flag de ícones escuros = ícones claros (brancos)
                    setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }

                // Workaround para timing issues no Android 15
                Handler(Looper.getMainLooper()).postDelayed({
                    window.decorView.windowInsetsController?.apply {
                        setSystemBarsAppearance(
                            0,
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        )
                    }
                }, 100)
            }

            // Android 11+ (API moderna)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                window.decorView.windowInsetsController?.apply {
                    // REMOVER a flag de ícones escuros
                    setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            }

            // Android 8.0+ (Oreo até Android 10)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                var flags = window.decorView.systemUiVisibility
                // REMOVER a flag de ícones escuros (LIGHT_NAVIGATION_BAR)
                flags = flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                window.decorView.systemUiVisibility = flags
            }
        }

        // CONFIGURAÇÃO DA BARRA DE STATUS (ÍCONES ADAPTADOS AO TEMA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                // Android 11+ (API moderna)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    window.decorView.windowInsetsController?.apply {
                        if (isDarkMode) {
                            // Tema ESCURO: ícones CLAROS (remover flag de escuros)
                            setSystemBarsAppearance(
                                0,
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            )
                        } else {
                            // Tema CLARO: ícones ESCUROS (adicionar flag de escuros)
                            setSystemBarsAppearance(
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            )
                        }
                    }
                }

                // Versões legadas (6.0 - 10)
                else -> {
                    var flags = window.decorView.systemUiVisibility
                    if (isDarkMode) {
                        // Tema ESCURO: remover flag de ícones escuros (SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
                        flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    } else {
                        // Tema CLARO: adicionar flag de ícones escuros
                        flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    }
                    window.decorView.systemUiVisibility = flags
                }
            }
        }
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            domStorageEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
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

        // Controle de visibilidade da barra inferior
        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY + 10 && isBottomBarVisible) {
                // Rolar para baixo - ocultar barra
                bottomAppBar.animate().translationY(bottomAppBar.height.toFloat()).setDuration(300).start()
                isBottomBarVisible = false
            } else if (scrollY < oldScrollY - 10 && !isBottomBarVisible) {
                // Rolar para cima - mostrar barra
                bottomAppBar.animate().translationY(0f).setDuration(300).start()
                isBottomBarVisible = true
            }
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
        // Aplica zoom em todo o conteúdo incluindo o título
        document.documentElement.style.fontSize = '$currentFontSize%';
        
        // Força reflow para ativar a transição
        void document.documentElement.offsetWidth;
        
        // Ativa transição suave
        document.documentElement.style.transition = 'font-size 0.3s ease';
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
        processLinks(content)

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

            if (img.parent()?.hasClass("image-social-wrapper") == true) {
                img.parent()?.remove()
                continue
            }

            // Preserva proporções originais
            val width = img.attr("width")
            val height = img.attr("height")
            val style = if (width.isNotEmpty() && height.isNotEmpty()) {
                "max-width:100%; min-width:60%; height:auto; display:block; margin:1.5rem auto; aspect-ratio: $width / $height;"
            } else {
                "max-width:100%; min-width:60%; height:auto; display:block; margin:1.5rem auto;"
            }

            img.attr("src", src)
                .attr("style", style)
                .attr("onclick", "window.open(this.src, '_blank')")
                .removeAttr("data-src")
                .removeAttr("width")
                .removeAttr("height")
        }
    }

    private fun processParagraphs(content: Element) {
        val paragraphs = content.select("p")
        for (p in paragraphs) {
            if (p.text().trim().isEmpty()) {
                p.remove()
            } else {
                p.attr("style", "margin:1.2rem 0; line-height:1.7; text-align: justify;")
            }
        }
    }

    private fun processLinks(content: Element) {
        val links = content.select("a[href]")
        for (link in links) {
            // Substitui links por spans para remover comportamento de hiperlink
            val span = link.ownerDocument()?.createElement("span") ?: continue
            span.html(link.html())
            span.attr("style", "color: inherit; text-decoration: none;")
            link.replaceWith(span)
        }
    }

    private fun buildArticleHtml(title: String, date: String, description: String, content: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=10.0'>
                <style>
                    /* REGRA ADICIONADA PARA TRANSITION */
                    html {
                        transition: font-size 0.3s ease !important;
                    }
                    
                    :root {
                        --bg-color: inherit;
                        --text-color: inherit;
                    }
                    * {
                        color: var(--text-color) !important;
                    }
                    html {
                        font-size: 100%;
                    }
                    body {
                        max-width: 800px;
                        margin: 0 auto;
                        padding: 1rem;
                        font-family: -apple-system, sans-serif;
                        line-height: 1.6;
                        background-color: var(--bg-color) !important;
                        transition: all 0.3s ease;
                        font-size: 1rem;
                        text-size-adjust: 100%;
                        -webkit-text-size-adjust: 100%;
                    }
                    h1 {
                        font-size: 1.8rem;
                        margin: 0 0 0.5rem;
                        line-height: 1.3;
                    }
                    .meta {
                        color: #888 !important;
                        font-size: 0.9rem;
                        margin-bottom: 1rem;
                    }
                    .description {
                        font-size: 1.1rem;
                        color: #aaa !important;
                        margin-bottom: 1.5rem;
                        font-style: italic;
                    }
                    .content {
                        font-size: 1rem;
                        line-height: 1.7;
                    }
                    .content img {
                        max-width: 100% !important;
                        min-width: 60% !important;
                        height: auto !important;
                        display: block;
                        margin: 1.5rem auto;
                        border-radius: 8px;
                        box-shadow: 0 2px 8px rgba(0,0,0,0.1);
                        aspect-ratio: attr(width) / attr(height);
                    }
                    .content iframe,
                    .content video {
                        max-width: 100% !important;
                        height: auto !important;
                        display: block;
                        margin: 1.5rem auto;
                    }
                    .content p {
                        margin: 1.2rem 0;
                        text-align: justify;
                    }
                    .content h1, .content h2, .content h3, .content h4 {
                        margin: 1.8rem 0 1rem;
                        line-height: 1.3;
                    }
                    .content blockquote {
                        border-left: 3px solid #ccc;
                        padding-left: 1rem;
                        margin: 1.5rem 0;
                        color: #aaa;
                    }
                    .content a {
                        color: inherit !important;
                        text-decoration: none !important;
                        cursor: default !important;
                        pointer-events: none !important;
                    }
                    @media (max-width: 600px) {
                        body {
                            padding: 12px;
                        }
                        .content img {
                            min-width: 80% !important;
                            margin: 1rem auto;
                        }
                    }
                </style>
                <script>
                                        document.addEventListener('DOMContentLoaded', function() {
                        // Desativa todos os links
                        document.querySelectorAll('a').forEach(function(link) {
                            link.onclick = function(e) {
                                e.preventDefault();
                                return false;
                            };
                        });
                        
                        // Tratamento especial para imagens
                        document.querySelectorAll('img').forEach(function(img) {
                            img.onclick = function() {
                                window.open(img.src, '_blank');
                            };
                            
                            // Garante bom dimensionamento mesmo sem CSS
                            if (!img.style.width && !img.style.height) {
                                const width = img.naturalWidth;
                                const height = img.naturalHeight;
                                
                                if (width > 0 && height > 0) {
                                    const aspectRatio = height / width;
                                    img.style.maxWidth = '100%';
                                    img.style.height = 'auto';
                                    
                                    if (width < 300 || height < 200) {
                                        img.style.minWidth = '70%';
                                    }
                                }
                            }
                        });
                    });
                </script>
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
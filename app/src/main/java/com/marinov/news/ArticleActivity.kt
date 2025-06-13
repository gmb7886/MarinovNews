package com.marinov.news;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ArticleActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private int currentFontSize = 100;
    private String currentTheme = "auto";
    private SharedPreferences preferences;

    private static final String[] CONTENT_SELECTORS = {
            "[itemprop='articleBody']",
            ".article-content",
            ".news-article__body",
            ".content-text",
            "article",
            ".post-content"
    };

    private static final String PREFS_NAME = "NewsAppPrefs";
    private static final String KEY_THEME = "theme";
    private static final String KEY_FONT_SIZE = "fontSize";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article);

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentTheme = preferences.getString(KEY_THEME, "auto");
        currentFontSize = preferences.getInt(KEY_FONT_SIZE, 100);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);

        configureWebView();
        setupControls();
        new ContentExtractorTask().execute(getIntent().getStringExtra("url"));
    }

    private void configureWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            webView.getSettings().setForceDark(WebSettings.FORCE_DARK_AUTO);
        }
    }

    private void setupControls() {
        ImageButton btnDecreaseFont = findViewById(R.id.btnDecreaseFont);
        ImageButton btnIncreaseFont = findViewById(R.id.btnIncreaseFont);
        ImageButton btnThemeAuto = findViewById(R.id.btnThemeAuto);
        ImageButton btnThemeLight = findViewById(R.id.btnThemeLight);
        ImageButton btnThemeDark = findViewById(R.id.btnThemeDark);
        ImageButton btnThemeSepia = findViewById(R.id.btnThemeSepia);

        btnDecreaseFont.setOnClickListener(v -> adjustFontSize(-10));
        btnIncreaseFont.setOnClickListener(v -> adjustFontSize(10));

        View.OnClickListener themeListener = v -> {
            String theme = "auto";
            if (v == btnThemeLight) theme = "light";
            else if (v == btnThemeDark) theme = "dark";
            else if (v == btnThemeSepia) theme = "sepia";
            applyTheme(theme);
        };

        btnThemeAuto.setOnClickListener(themeListener);
        btnThemeLight.setOnClickListener(themeListener);
        btnThemeDark.setOnClickListener(themeListener);
        btnThemeSepia.setOnClickListener(themeListener);
    }

    private void adjustFontSize(int delta) {
        currentFontSize = Math.max(80, Math.min(150, currentFontSize + delta));
        preferences.edit().putInt(KEY_FONT_SIZE, currentFontSize).apply();
        updateFontSize();
    }

    private void updateFontSize() {
        String js = String.format(
                "document.body.style.fontSize = '%d%%';" +
                        "document.querySelectorAll('h1, h2, h3').forEach(e => e.style.fontSize = '%d%%');",
                currentFontSize,
                currentFontSize + 20
        );
        webView.evaluateJavascript(js, null);
    }

    private void applyTheme(String theme) {
        currentTheme = theme;
        preferences.edit().putString(KEY_THEME, theme).apply();

        int bgColor;
        int textColor;

        switch (theme) {
            case "light":
                bgColor = ContextCompat.getColor(this, R.color.white);
                textColor = ContextCompat.getColor(this, R.color.black);
                break;
            case "dark":
                bgColor = ContextCompat.getColor(this, R.color.black);
                textColor = ContextCompat.getColor(this, R.color.white);
                break;
            case "sepia":
                bgColor = ContextCompat.getColor(this, R.color.sepia);
                textColor = ContextCompat.getColor(this, R.color.sepia_text);
                break;
            default:
                int nightModeFlags = getResources().getConfiguration().uiMode &
                        Configuration.UI_MODE_NIGHT_MASK;
                boolean isDark = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
                bgColor = isDark ? ContextCompat.getColor(this, R.color.black)
                        : ContextCompat.getColor(this, R.color.white);
                textColor = isDark ? ContextCompat.getColor(this, R.color.white)
                        : ContextCompat.getColor(this, R.color.black);
                break;
        }

        String js = String.format(
                "document.body.style.setProperty('--bg-color', '#%06X');" +
                        "document.body.style.setProperty('--text-color', '#%06X');",
                (0xFFFFFF & bgColor),
                (0xFFFFFF & textColor)
        );
        webView.evaluateJavascript(js, null);
        webView.setBackgroundColor(bgColor);
    }

    private class ContentExtractorTask extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected String doInBackground(String... urls) {
            try {
                Document doc = Jsoup.connect(urls[0])
                        .userAgent("Mozilla/5.0")
                        .timeout(10000)
                        .get();

                String title = extractTitle(doc);
                String date = extractDate(doc);
                String description = extractDescription(doc);
                String content = extractContent(doc);

                return buildArticleHtml(title, date, description, content);

            } catch (IOException e) {
                return errorHtml();
            }
        }

        private String extractTitle(Document doc) {
            Element titleElement = doc.selectFirst("[itemprop='headline']");
            if (titleElement != null) return titleElement.text();

            Element ogTitle = doc.selectFirst("meta[property='og:title']");
            return ogTitle != null ? ogTitle.attr("content") : doc.title();
        }

        private String extractDate(Document doc) {
            Element dateElement = doc.selectFirst("[itemprop='datePublished']");
            if (dateElement != null) {
                String isoDate = dateElement.attr("datetime");
                if (!isoDate.isEmpty()) return formatDate(isoDate);
            }

            Element metaDate = doc.selectFirst("meta[property='article:published_time']");
            return metaDate != null ? formatDate(metaDate.attr("content")) : "";
        }

        private String extractDescription(Document doc) {
            Element descElement = doc.selectFirst("[itemprop='description']");
            if (descElement != null) return descElement.text();

            Element ogDesc = doc.selectFirst("meta[property='og:description']");
            return ogDesc != null ? ogDesc.attr("content") : "";
        }

        private String formatDate(String isoDate) {
            try {
                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.getDefault());
                Date date = isoFormat.parse(isoDate);

                SimpleDateFormat outputFormat = new SimpleDateFormat("d 'de' MMMM 'de' yyyy 'às' HH:mm", new Locale("pt", "BR"));
                return outputFormat.format(date);
            } catch (ParseException e) {
                return "";
            }
        }

        private String extractContent(Document doc) {
            doc.select(
                    "script, style, nav, footer, header, aside, iframe, form, " +
                            "button, input, select, textarea, .ad, .related-news, .share, " +
                            ".social-media, [title*='Compartilhe'], .social-links, .social-buttons, " +
                            ".gallery-widget-carousel__info-share, .c-newsletter__wrapper, " +
                            ".c-newsletter__text, .c-newsletter__button, .c-form__info-message, " +
                            ".gallery-widget-carousel__info-read-more-container, " +
                            ".gallery-widget-carousel__info-read-more, .gallery-widget-others, " +
                            ".gallery-widget-others__go-back, .icon--arrow-rigth, .icon--chevron-left"
            ).remove();

            Element content = findMainContent(doc);
            if (content == null) content = doc.body();

            cleanContent(content);
            processImages(content);
            processParagraphs(content);

            return content.html();
        }

        private Element findMainContent(Document doc) {
            for (String selector : CONTENT_SELECTORS) {
                Elements elements = doc.select(selector);
                if (!elements.isEmpty()) return elements.first();
            }
            return null;
        }

        private void cleanContent(Element content) {
            content.select(
                    "button, input, select, textarea, .btn, .form-control, " +
                            ".checkbox, .radio, .search-box, .comment-box, .newsletter-form, " +
                            ".social-share, .social-widget, .icon-share, .sharing-tools, " +
                            ".c-newsletter__obs, .is-hidden"
            ).remove();

            content.select(
                    "div:empty, span:empty, label, fieldset, " +
                            ".form-group, .input-group, .button-group, " +
                            ".gallery-widget--is-hidden"
            ).remove();
        }

        private void processImages(Element content) {
            Elements images = content.select("img[src], img[data-src]");
            for (Element img : images) {
                String src = img.absUrl("data-src");
                if (src.isEmpty()) src = img.absUrl("src");

                if (src.startsWith("//")) src = "https:" + src;

                if (img.parent().hasClass("image-social-wrapper")) {
                    img.parent().remove();
                    continue;
                }

                img.attr("src", src)
                        .attr("style", "max-width:100%; height:auto; display:block; margin:1rem auto;")
                        .removeAttr("data-src")
                        .removeAttr("onclick");
            }
        }

        private void processParagraphs(Element content) {
            Elements paragraphs = content.select("p");
            for (Element p : paragraphs) {
                if (p.text().trim().isEmpty()) {
                    p.remove();
                } else {
                    p.attr("style", "margin:1rem 0; line-height:1.6;");
                }
            }
        }

        private String buildArticleHtml(String title, String date, String description, String content) {
            return "<!DOCTYPE html>" +
                    "<html><head>" +
                    "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                    "<style>" +
                    ":root {" +
                    "  --bg-color: inherit;" +
                    "  --text-color: inherit;" +
                    "}" +
                    "body { " +
                    "  max-width: 800px; " +
                    "  margin: 0 auto; " +
                    "  padding: 1rem; " +
                    "  font-family: -apple-system, sans-serif; " +
                    "  line-height: 1.6; " +
                    "  background-color: var(--bg-color) !important;" +
                    "  color: var(--text-color) !important;" +
                    "  transition: all 0.3s ease;" +
                    "}" +
                    "h1 { font-size: 2rem; margin: 0 0 0.5rem; }" +
                    ".meta { color: #666; font-size: 0.9rem; margin-bottom: 1rem; }" +
                    ".description { font-size: 1.1rem; color: #444; margin-bottom: 1.5rem; }" +
                    "img { max-width: 100% !important; height: auto !important; }" +
                    "p { margin: 1rem 0; line-height: 1.6; }" +
                    "button, input, select, textarea { display: none !important; }" +
                    "</style>" +
                    "</head><body>" +
                    "<h1>" + title + "</h1>" +
                    "<div class='meta'>" + date + "</div>" +
                    "<div class='description'>" + description + "</div>" +
                    "<div class='content'>" + content + "</div>" +
                    "</body></html>";
        }
        private String errorHtml() {
            return "<html><body style='padding:2rem;text-align:center;'>" +
                    "<h1>Erro ao carregar conteúdo</h1>" +
                    "<p>O artigo não pôde ser carregado.</p></body></html>";
        }
        @Override
        protected void onPostExecute(String html) {
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    applyTheme(currentTheme);
                    updateFontSize();
                }
            });
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
            progressBar.setVisibility(View.GONE);
        }
    }
}
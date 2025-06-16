package com.marinov.news

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class FeedFragment : Fragment() {

    private lateinit var rvFeed: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: FeedAdapter
    private val items = mutableListOf<FeedItem>()
    private lateinit var prefs: SharedPreferences
    private var lastScrollPosition = 0
    private var isScrollingDown = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_feed, container, false)
        rvFeed = view.findViewById(R.id.rvFeed)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        progressBar = view.findViewById(R.id.progressBar)

        rvFeed.layoutManager = LinearLayoutManager(requireContext())
        adapter = FeedAdapter(items)
        rvFeed.adapter = adapter

        // Mostra o loading ao iniciar o carregamento
        progressBar.visibility = View.VISIBLE

        // Adiciona listener de rolagem para controle da barra inferior
        rvFeed.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val currentScrollPosition = (rvFeed.layoutManager as LinearLayoutManager)
                    .findFirstVisibleItemPosition()

                isScrollingDown = dy > 0

                // Controle de visibilidade da barra inferior
                if (isScrollingDown && currentScrollPosition > lastScrollPosition) {
                    // Rolando para baixo - esconde barra
                    (activity as? MainActivity)?.hideBottomNavigation()
                } else if (!isScrollingDown && currentScrollPosition < lastScrollPosition) {
                    // Rolando para cima - mostra barra
                    (activity as? MainActivity)?.showBottomNavigation()
                }

                lastScrollPosition = currentScrollPosition
            }
        })

        prefs = requireActivity().getSharedPreferences("feeds", Context.MODE_PRIVATE)
        loadFeeds()

        return view
    }

    override fun onResume() {
        super.onResume()
        // Garante que a barra esteja visível ao entrar no fragmento
        (activity as? MainActivity)?.showBottomNavigation()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadFeeds() {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                val urls = prefs.getStringSet("urls", HashSet()) ?: HashSet()
                Log.d("RSS", "Feeds encontrados: $urls")

                val allItems = mutableListOf<FeedItem>()
                val errors = mutableListOf<String>()

                for (url in urls) {
                    if (!TextUtils.isEmpty(url)) {
                        try {
                            allItems.addAll(parseRss(url))
                        } catch (e: Exception) {
                            errors.add(url)
                            Log.e("RSS", "Erro no feed: $url", e)
                        }
                    }
                }

                if (errors.isNotEmpty()) {
                    Log.w("RSS", "Feeds com erro: ${errors.size}")
                }

                // Ordenação por data usando múltiplos formatos
                allItems.sortedByDescending { item ->
                    item.pubDate?.let { parseDateRobust(it) } ?: 0L
                }
            }

            Log.d("RSS", "Itens carregados: ${result.size}")
            items.clear()
            items.addAll(result)
            adapter.notifyDataSetChanged()

            // Esconde o loading após carregar os itens
            progressBar.visibility = View.GONE
            tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun parseDateRobust(dateString: String): Long {
        val formats = arrayOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy"
        )

        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(dateString)?.time ?: 0L
            } catch (_: ParseException) {
                // Tentar próximo formato
            }
        }
        return 0L
    }

    private fun parseRss(feedUrl: String): List<FeedItem> {
        val list = mutableListOf<FeedItem>()
        try {
            val url = URL(feedUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "NewsApp/1.0")
            val stream: InputStream = conn.inputStream

            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(stream, null)

            var current: FeedItem? = null
            var insideItem = false
            var event = parser.eventType

            while (event != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name ?: ""
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when {
                            tag.equals("item", ignoreCase = true) ||
                                    tag.equals("entry", ignoreCase = true) -> {
                                insideItem = true
                                current = FeedItem().apply {
                                    this.feedSource = feedUrl
                                }
                            }

                            insideItem -> {
                                when (tag.lowercase(Locale.getDefault())) {
                                    "title" -> current?.title = parser.nextText()

                                    "pubdate", "updated" -> current?.pubDate = parser.nextText()

                                    "description", "content", "summary" -> {
                                        val text = parser.nextText()
                                        // Limpa a descrição removendo links e elementos indesejados
                                        current?.description = cleanDescription(text)

                                        // Se não tem imagem, tenta extrair da descrição original
                                        if (current?.imageUrl.isNullOrEmpty()) {
                                            extractImageFromDescription(text)?.let { img ->
                                                current?.imageUrl = img
                                            }
                                        }
                                    }

                                    "link" -> {
                                        val href = parser.getAttributeValue(null, "href")
                                        current?.link = href ?: parser.nextText()
                                    }

                                    "enclosure" -> {
                                        val img = parser.getAttributeValue(null, "url")
                                        if (!img.isNullOrEmpty()) {
                                            current?.imageUrl = img
                                        }
                                    }

                                    "media:content", "media:thumbnail" -> {
                                        val img = parser.getAttributeValue(null, "url")
                                        if (!img.isNullOrEmpty()) {
                                            current?.imageUrl = img
                                        }
                                    }

                                    "itunes:image" -> {
                                        val img = parser.getAttributeValue(null, "href")
                                        if (!img.isNullOrEmpty()) {
                                            current?.imageUrl = img
                                        }
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if ((tag.equals("item", ignoreCase = true) ||
                                    tag.equals("entry", ignoreCase = true)) &&
                            current != null
                        ) {

                            // Última tentativa de extrair imagem da descrição
                            if (current.imageUrl.isNullOrEmpty()) {
                                current.description?.let { desc ->
                                    extractImageFromDescription(desc)?.let { img ->
                                        current.imageUrl = img
                                    }
                                }
                            }

                            list.add(current)
                            insideItem = false
                        }
                    }
                }
                event = parser.next()
            }
            stream.close()
        } catch (e: Exception) {
            Log.e("RSS", "Erro ao processar feed: $feedUrl", e)
        }
        return list
    }

    private fun extractImageFromDescription(html: String?): String? {
        if (html.isNullOrEmpty()) return null

        return try {
            val doc = Jsoup.parse(html)
            val img = doc.select("img").first()
            img?.attr("src")?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanDescription(html: String?): String? {
        if (html.isNullOrEmpty()) return html

        return try {
            val doc = Jsoup.parse(html)

            // Remove todos os links completamente (tags <a>)
            doc.select("a").remove()

            // Remove outros elementos indesejados
            doc.select("script, style, iframe, noscript").remove()

            // Mantém apenas o texto e formatação básica
            doc.body().text()
        } catch (e: Exception) {
            html // Em caso de erro, retorna o original
        }
    }
}
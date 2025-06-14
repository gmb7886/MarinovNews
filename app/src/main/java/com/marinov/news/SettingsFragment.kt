package com.marinov.news

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL

class SettingsFragment : Fragment() {
    private var etRssUrl: TextInputEditText? = null
    private var etWebsiteUrl: TextInputEditText? = null
    private var rvUrls: RecyclerView? = null
    private var adapter: UrlsAdapter? = null
    private var urls: MutableList<String> = ArrayList()
    private var prefs: SharedPreferences? = null
    private var progressBar: ProgressBar? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        prefs = requireActivity().getSharedPreferences("feeds", Context.MODE_PRIVATE)

        val savedUrls = prefs?.getStringSet("urls", HashSet()) ?: HashSet()
        urls = ArrayList(savedUrls)

        etRssUrl = view.findViewById(R.id.etRssUrl)
        etWebsiteUrl = view.findViewById(R.id.etWebsiteUrl)
        rvUrls = view.findViewById(R.id.rvUrls)
        progressBar = view.findViewById(R.id.progressBar)

        rvUrls!!.layoutManager = LinearLayoutManager(context)
        adapter = UrlsAdapter(urls as MutableList<String?>) { position -> removeUrl(position) }
        rvUrls!!.adapter = adapter

        view.findViewById<MaterialButton>(R.id.btnAddFeed)
            .setOnClickListener {
                val url = etRssUrl!!.text.toString().trim()
                if (url.isEmpty()) {
                    Toast.makeText(context, "Digite uma URL válida", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                addFeedUrl(url)
            }

        view.findViewById<MaterialButton>(R.id.btnDiscoverFeeds)
            .setOnClickListener {
                val website = etWebsiteUrl!!.text.toString().trim()
                if (website.isEmpty()) {
                    Toast.makeText(context, "Digite um site válido", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                discoverFeeds(website)
            }

        return view
    }

    private fun addFeedUrl(url: String) {
        if (!urls.contains(url)) {
            urls.add(url)
            Log.d("RSS", "Adicionando feed: $url")
            saveUrls()
            adapter?.notifyItemInserted(urls.size - 1)
            etRssUrl?.setText("")
            Toast.makeText(context, "Feed adicionado", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Feed já existe", Toast.LENGTH_SHORT).show()
        }
    }

    private fun discoverFeeds(website: String) {
        progressBar?.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val validUrl = if (!website.startsWith("http")) "https://$website" else website
                val doc = Jsoup.connect(validUrl)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get()

                val discoveredFeeds = discoverRssLinks(doc)

                withContext(Dispatchers.Main) {
                    progressBar?.visibility = View.GONE
                    showFeedSelectionDialog(discoveredFeeds)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar?.visibility = View.GONE
                    Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("RSS", "Erro ao buscar feeds", e)
                }
            }
        }
    }

    private fun showFeedSelectionDialog(feedUrls: List<String>) {
        if (feedUrls.isEmpty()) {
            Toast.makeText(context, "Nenhum feed encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Selecione os feeds para adicionar")

        val listView = ListView(requireContext())
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_multiple_choice, feedUrls)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        builder.setView(listView)
        builder.setPositiveButton("Adicionar selecionados") { dialog, which ->
            val selectedPositions = listView.checkedItemPositions
            var addedCount = 0
            for (i in 0 until feedUrls.size) {
                if (selectedPositions.get(i)) {
                    val feedUrl = feedUrls[i]
                    if (!urls.contains(feedUrl)) {
                        urls.add(feedUrl)
                        addedCount++
                    }
                }
            }
            saveUrls()
            this.adapter?.notifyDataSetChanged()
            Toast.makeText(context, "$addedCount feeds adicionados", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun discoverRssLinks(doc: Document): List<String> {
        val feedLinks = mutableListOf<String>()

        // 1. Procura por links RSS/Atom explícitos
        val links = doc.select("link[type='application/rss+xml'], link[type='application/atom+xml']")
        for (link in links) {
            var href = link.attr("href")
            if (href.isNotEmpty()) {
                if (href.startsWith("/")) {
                    href = URL(doc.baseUri()).protocol + "://" +
                            URL(doc.baseUri()).host + href
                } else if (!href.startsWith("http")) {
                    href = URL(doc.baseUri()).protocol + "://" +
                            URL(doc.baseUri()).host + "/" + href
                }
                if (isValidFeedUrl(href)) {
                    feedLinks.add(href)
                }
            }
        }

        // 2. Procura por links em tags <a> com palavras-chave
        val possibleLinks = doc.select("a[href]")
        for (link in possibleLinks) {
            var href = link.attr("abs:href")
            val text = link.text().lowercase()
            val hrefLower = href.lowercase()

            if (href.isNotEmpty() &&
                (hrefLower.contains("rss") ||
                        hrefLower.contains("feed") ||
                        hrefLower.contains("atom") ||
                        text.contains("rss") ||
                        text.contains("feed") ||
                        text.contains("atom"))) {
                if (isValidFeedUrl(href) && !feedLinks.contains(href)) {
                    feedLinks.add(href)
                }
            }
        }

        // 3. Verificação de feeds conhecidos
        val commonFeedPaths = listOf(
            "/feed", "/rss", "/atom", "/feed.xml",
            "/rss.xml", "/atom.xml", "/feed/rss", "/feed/atom"
        )

        val baseUrl = URL(doc.baseUri())
        for (path in commonFeedPaths) {
            val feedUrl = "${baseUrl.protocol}://${baseUrl.host}$path"
            if (isValidFeedUrl(feedUrl) && !feedLinks.contains(feedUrl)) {
                feedLinks.add(feedUrl)
            }
        }

        return feedLinks.distinct()
    }

    private fun isValidFeedUrl(url: String): Boolean {
        return url.endsWith(".xml") ||
                url.contains("rss") ||
                url.contains("feed") ||
                url.contains("atom") ||
                url.contains("xml")
    }

    private fun removeUrl(position: Int) {
        urls.removeAt(position)
        saveUrls()
        adapter?.notifyItemRemoved(position)
    }

    private fun saveUrls() {
        prefs?.edit {
            putStringSet("urls", HashSet(urls))
        }
    }
}
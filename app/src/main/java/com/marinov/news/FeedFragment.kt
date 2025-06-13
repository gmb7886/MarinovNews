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
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private lateinit var adapter: FeedAdapter
    private val items = mutableListOf<FeedItem>()
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_feed, container, false)
        rvFeed = view.findViewById(R.id.rvFeed)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        rvFeed.layoutManager = LinearLayoutManager(requireContext())
        adapter = FeedAdapter(items)
        rvFeed.adapter = adapter

        prefs = requireActivity().getSharedPreferences("feeds", Context.MODE_PRIVATE)
        loadFeeds()

        return view
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadFeeds() {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                val urls = prefs.getStringSet("urls", HashSet()) ?: HashSet()
                Log.d("RSS", "Feeds encontrados: $urls")

                val allItems = mutableListOf<FeedItem>()
                for (url in urls) {
                    if (!TextUtils.isEmpty(url)) {
                        allItems.addAll(parseRss(url))
                    }
                }

                // Ordena por data decrescente, trata datas nulas/vazias
                val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
                allItems.sortedWith { a, b ->
                    val sa = a.pubDate
                    val sb = b.pubDate
                    when {
                        TextUtils.isEmpty(sa) && TextUtils.isEmpty(sb) -> 0
                        TextUtils.isEmpty(sa) -> 1
                        TextUtils.isEmpty(sb) -> -1
                        else -> {
                            try {
                                val da = format.parse(sa)
                                val db = format.parse(sb)
                                db.compareTo(da)
                            } catch (_: ParseException) {
                                0
                            }
                        }
                    }
                }
            }

            Log.d("RSS", "Itens carregados: ${result.size}")
            items.clear()
            items.addAll(result)
            adapter.notifyDataSetChanged()
            tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun parseRss(feedUrl: String): List<FeedItem> {
        val list = mutableListOf<FeedItem>()
        try {
            val url = URL(feedUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val stream: InputStream = conn.inputStream

            val factory = XmlPullParserFactory.newInstance()
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
                            tag.equals("item", ignoreCase = true)
                                    || tag.equals("entry", ignoreCase = true) -> {
                                insideItem = true
                                current = FeedItem()
                            }
                            insideItem -> {
                                when {
                                    tag.equals("title", ignoreCase = true) -> {
                                        current?.title = parser.nextText()
                                    }
                                    tag.equals("pubDate", ignoreCase = true)
                                            || tag.equals("updated", ignoreCase = true) -> {
                                        current?.pubDate = parser.nextText()
                                    }
                                    tag.equals("description", ignoreCase = true)
                                            || tag.equals("content", ignoreCase = true) -> {
                                        current?.description = parser.nextText()
                                    }
                                    tag.equals("link", ignoreCase = true) -> {
                                        val href = parser.getAttributeValue(null, "href")
                                        if (href != null) {
                                            current?.link = href
                                        } else {
                                            current?.link = parser.nextText()
                                        }
                                    }
                                    tag.equals("enclosure", ignoreCase = true) -> {
                                        val img = parser.getAttributeValue(null, "url")
                                        img?.let { current?.imageUrl = it }
                                    }
                                    tag.equals("media:content", ignoreCase = true) -> {
                                        val img = parser.getAttributeValue(null, "url")
                                        img?.let { current?.imageUrl = it }
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if ((tag.equals("item", ignoreCase = true)
                                    || tag.equals("entry", ignoreCase = true))
                            && current != null) {
                            list.add(current)
                            insideItem = false
                        }
                    }
                }
                event = parser.next()
            }
            stream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
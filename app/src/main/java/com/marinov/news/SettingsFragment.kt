package com.marinov.news

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import androidx.core.content.edit

class SettingsFragment : Fragment() {
    private var etRssUrl: TextInputEditText? = null
    private var rvUrls: RecyclerView? = null
    private var adapter: UrlsAdapter? = null
    private var urls: MutableList<String?> = ArrayList<String?>()
    private var prefs: SharedPreferences? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        prefs = requireActivity().getSharedPreferences("feeds", Context.MODE_PRIVATE)
        urls = ArrayList<String?>(prefs!!.getStringSet("urls", HashSet<String?>()))

        etRssUrl = view.findViewById<TextInputEditText>(R.id.etRssUrl)
        rvUrls = view.findViewById<RecyclerView>(R.id.rvUrls)
        rvUrls!!.setLayoutManager(LinearLayoutManager(context))
        adapter = UrlsAdapter(urls) { position: Int -> this.removeUrl(position) }
        rvUrls!!.setAdapter(adapter)

        view.findViewById<View?>(R.id.btnAddFeed)
            .setOnClickListener(View.OnClickListener setOnClickListener@{ v: View? ->
                val url = etRssUrl!!.getText().toString().trim { it <= ' ' }
                if (url.isEmpty()) {
                    Toast.makeText(context, "Digite uma URL válida", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                urls.add(url)
                // Log de salvamento
                Log.d("RSS", "Salvando feed: $url")
                saveUrls()
                adapter!!.notifyItemInserted(urls.size - 1)
                etRssUrl!!.setText("")
            })

        return view
    }

    private fun removeUrl(position: Int) {
        urls.removeAt(position)
        saveUrls()
        adapter!!.notifyItemRemoved(position)
    }

    private fun saveUrls() {
        prefs!!.edit {
            putStringSet("urls", HashSet<String?>(urls))
        }
    }
}
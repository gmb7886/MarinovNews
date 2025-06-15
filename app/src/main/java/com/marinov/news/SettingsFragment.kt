package com.marinov.news

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import androidx.core.content.edit

class SettingsFragment : Fragment() {

    private lateinit var etRssUrl: TextInputEditText
    private lateinit var rvUrls: RecyclerView
    private lateinit var adapter: UrlsAdapter
    private var urls: MutableList<String> = mutableListOf()
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        // Inicializa SharedPreferences
        prefs = requireActivity().getSharedPreferences("feeds", Context.MODE_PRIVATE)

        // Carrega lista de URLs salvas
        urls = prefs.getStringSet("urls", emptySet())?.toMutableList() ?: mutableListOf()

        etRssUrl = view.findViewById(R.id.etRssUrl)
        rvUrls    = view.findViewById(R.id.rvUrls)

        // Configura RecyclerView
        rvUrls.layoutManager = LinearLayoutManager(requireContext())
        adapter = UrlsAdapter(urls as MutableList<String?>) { position -> removeUrl(position) }
        rvUrls.adapter = adapter

        // Botão para adicionar novo feed
        view.findViewById<Button>(R.id.btnAddFeed).setOnClickListener {
            val url = etRssUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(requireContext(), "Digite uma URL válida", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Adiciona e salva
            urls.add(url)
            Log.d("RSS", "Salvando feed: $url")
            saveUrls()
            adapter.notifyItemInserted(urls.size - 1)
            etRssUrl.setText("")
        }

        return view
    }

    private fun removeUrl(position: Int) {
        urls.removeAt(position)
        saveUrls()
        adapter.notifyItemRemoved(position)
    }

    private fun saveUrls() {
        prefs.edit {
            putStringSet("urls", urls.toSet())
        }
    }
}

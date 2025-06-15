package com.marinov.news

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class SettingsFragment : Fragment() {

    private lateinit var etRssUrl: TextInputEditText
    private lateinit var rvUrls: RecyclerView
    private lateinit var adapter: UrlsAdapter
    private var urls: MutableList<String> = mutableListOf()
    private lateinit var prefs: SharedPreferences

    // Botões para redes sociais e atualização
    private lateinit var btnTwitter: MaterialButton
    private lateinit var btnReddit: MaterialButton
    private lateinit var btnGithub: MaterialButton
    private lateinit var btnYoutube: MaterialButton
    private lateinit var btnCheckUpdate: MaterialButton

    private var progressBar: ProgressBar? = null

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
        rvUrls = view.findViewById(R.id.rvUrls)

        // Configura RecyclerView
        rvUrls.layoutManager = LinearLayoutManager(requireContext())
        adapter = UrlsAdapter(urls) { position -> removeUrl(position) }
        rvUrls.adapter = adapter
        rvUrls.setHasFixedSize(true) // Otimização de performance

        // Botão para adicionar novo feed
        view.findViewById<MaterialButton>(R.id.btnAddFeed).setOnClickListener {
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

        // Inicializar botões de redes sociais e atualização
        btnTwitter = view.findViewById(R.id.btn_twitter)
        btnReddit = view.findViewById(R.id.btn_reddit)
        btnGithub = view.findViewById(R.id.btn_github)
        btnYoutube = view.findViewById(R.id.btn_youtube)
        btnCheckUpdate = view.findViewById(R.id.btn_check_update)

        // Configurar listeners
        btnTwitter.setOnClickListener { openUrl("http://x.com/gmb7886") }
        btnReddit.setOnClickListener { openUrl("https://www.reddit.com/user/GMB7886/") }
        btnGithub.setOnClickListener { openUrl("https://github.com/gmb7886/") }
        btnYoutube.setOnClickListener { openUrl("https://youtube.com/@CanalDoMarinov") }
        btnCheckUpdate.setOnClickListener { checkUpdate() }

        return view
    }

    private fun removeUrl(position: Int) {
        if (position in 0 until urls.size) {
            urls.removeAt(position)
            saveUrls()
            adapter.notifyItemRemoved(position)
        }
    }

    private fun saveUrls() {
        prefs.edit(commit = true) {
            putStringSet("urls", urls.toSet())
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: Exception) {
            Log.e("SettingsFragment", "Erro ao abrir URL", e)
        }
    }

    private fun checkUpdate() = lifecycleScope.launch {
        try {
            val (json, responseCode) = withContext(Dispatchers.IO) {
                val url = URL("https://api.github.com/repos/gmb7886/MarinovNews/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("User-Agent", "MarinovNews-Android")
                connection.connectTimeout = 10000
                connection.connect()

                try {
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        connection.inputStream.use { input ->
                            JSONObject(input.readText()) to connection.responseCode
                        }
                    } else {
                        null to connection.responseCode
                    }
                } finally {
                    connection.disconnect()
                }
            }

            if (json != null) {
                processReleaseData(json)
            } else {
                showError("Erro na conexão: Código $responseCode")
            }
        } catch (e: Exception) {
            Log.e("SettingsFragment", "Erro na verificação", e)
            showError("Erro: ${e.message}")
        }
    }

    private fun InputStream.readText(): String {
        return BufferedReader(InputStreamReader(this)).use { it.readText() }
    }

    private fun processReleaseData(release: JSONObject) {
        requireActivity().runOnUiThread {
            val latest = release.getString("tag_name")
            val current = BuildConfig.VERSION_NAME

            if (isVersionGreater(latest, current)) {
                val assets = release.getJSONArray("assets")
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                apkUrl?.let { promptForUpdate(it) } ?: showError("Arquivo APK não encontrado no release.")
            } else {
                showMessage()
            }
        }
    }

    private fun isVersionGreater(newVersion: String, currentVersion: String): Boolean {
        val newParts = newVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(newParts.size, currentParts.size)) {
            val newPart = newParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }

            when {
                newPart > currentPart -> return true
                newPart < currentPart -> return false
            }
        }
        return false
    }

    private fun promptForUpdate(url: String) {
        requireActivity().runOnUiThread {
            AlertDialog.Builder(requireContext())
                .setTitle("Atualização Disponível")
                .setMessage("Deseja baixar e instalar a versão mais recente?")
                .setPositiveButton("Sim") { _, _ -> startManualDownload(url) }
                .setNegativeButton("Não", null)
                .show()
        }
    }

    private fun startManualDownload(apkUrl: String) {
        lifecycleScope.launch {
            val progressDialog = createProgressDialog().apply { show() }
            try {
                val apkFile = withContext(Dispatchers.IO) { downloadApk(apkUrl) }
                progressDialog.dismiss()
                apkFile?.let(::showInstallDialog) ?: showError("Falha ao baixar o arquivo.")
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e("SettingsFragment", "Erro no download", e)
                showError("Falha no download: ${e.message}")
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun createProgressDialog(): AlertDialog {
        val view = layoutInflater.inflate(R.layout.dialog_download_progress, null)
        progressBar = view.findViewById(R.id.progress_bar)
        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()
    }

    private suspend fun downloadApk(apkUrl: String): File? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(apkUrl).openConnection() as HttpURLConnection
            connection.connect()

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outputDir = File(downloadsDir, "MarinovNews").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }

            val outputFile = File(outputDir, "app_release.apk")
            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var total: Long = 0
                    val fileLength = connection.contentLength.toLong()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        total += bytesRead
                        if (fileLength > 0) {
                            val progress = (total * 100 / fileLength).toInt()
                            requireActivity().runOnUiThread {
                                progressBar?.progress = progress
                            }
                        }
                    }
                }
            }
            outputFile
        } catch (e: Exception) {
            Log.e("SettingsFragment", "Erro no download", e)
            null
        }
    }

    private fun showInstallDialog(apkFile: File) {
        requireActivity().runOnUiThread {
            try {
                if (!apkFile.exists()) {
                    showError("Arquivo APK não encontrado")
                    return@runOnUiThread
                }

                val apkUri = FileProvider.getUriForFile(
                    requireContext(),
                    "${BuildConfig.APPLICATION_ID}.provider",
                    apkFile
                )

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                if (installIntent.resolveActivity(requireContext().packageManager) != null) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Download concluído")
                        .setMessage("Deseja instalar a atualização agora?")
                        .setPositiveButton("Instalar") { _, _ -> startActivity(installIntent) }
                        .setNegativeButton("Cancelar", null)
                        .show()
                } else {
                    showError("Nenhum aplicativo encontrado para instalar o APK")
                }
            } catch (e: Exception) {
                Log.e("SettingsFragment", "Erro na instalação", e)
                showError("Erro ao iniciar a instalação: ${e.message}")
            }
        }
    }

    private fun showMessage() {
        requireActivity().runOnUiThread {
            AlertDialog.Builder(requireContext())
                .setMessage("Você já está na versão mais recente")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun showError(msg: String) {
        requireActivity().runOnUiThread {
            AlertDialog.Builder(requireContext())
                .setTitle("Erro")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
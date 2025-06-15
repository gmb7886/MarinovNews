package com.marinov.news

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class UrlsAdapter(
    private val urls: MutableList<String>,
    private val onRemoveClick: (Int) -> Unit
) : RecyclerView.Adapter<UrlsAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_url, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(urls[position])
    }

    override fun getItemCount() = urls.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvUrl: TextView = itemView.findViewById(R.id.tvUrl)
        private val btnRemove: MaterialButton = itemView.findViewById(R.id.btnRemove)

        fun bind(url: String) {
            tvUrl.text = url
            btnRemove.setOnClickListener { onRemoveClick(adapterPosition) }
        }
    }
}
package com.marinov.news

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

internal class UrlsAdapter(private val list: MutableList<String?>, private val listener: OnRemoveListener) :
    RecyclerView.Adapter<UrlsAdapter.VH?>() {
    fun interface OnRemoveListener {
        fun onRemove(position: Int)
    }

    override fun onCreateViewHolder(p: ViewGroup, i: Int): VH {
        val v = LayoutInflater.from(p.context)
            .inflate(R.layout.item_url, p, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val url = list[pos]
        h.tvUrl.text = url
        h.btnRemove.setOnClickListener(View.OnClickListener { v: View? -> listener.onRemove(pos) })
    }

    override fun getItemCount(): Int {
        return list.size
    }

    internal class VH(v: View) : RecyclerView.ViewHolder(v) {
        var tvUrl: TextView = v.findViewById<TextView>(R.id.tvUrl)
        var btnRemove: ImageButton = v.findViewById<ImageButton>(R.id.btnRemove)
    }
}

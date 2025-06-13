package com.marinov.news

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

internal class FeedAdapter(private val list: MutableList<FeedItem>) :
    RecyclerView.Adapter<FeedAdapter.VH?>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feed, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]

        holder.tvTitle.text = item.title
        holder.tvPubDate.text = item.pubDate
        holder.tvDescription.text = item.description

        if (item.imageUrl != null && !item.imageUrl!!.isEmpty()) {
            holder.ivImage.setVisibility(View.VISIBLE)
            Glide.with(holder.ivImage.context)
                .load(item.imageUrl)
                .centerCrop()
                .into(holder.ivImage)
        } else {
            holder.ivImage.setVisibility(View.GONE)
        }

        holder.itemView.setOnClickListener(View.OnClickListener { v: View? ->
            val url = item.link
            Log.d("RSS", "Clicou no item: " + item.title + " / " + url)
            if (url != null && !url.isEmpty()) {
                val intent = Intent(v!!.context, ArticleActivity::class.java)
                intent.putExtra("url", url)
                v.context.startActivity(intent)
            }
        })
    }

    override fun getItemCount(): Int {
        return list.size
    }

    internal class VH(v: View) : RecyclerView.ViewHolder(v) {
        var ivImage: ImageView = v.findViewById<ImageView>(R.id.ivImage)
        var tvTitle: TextView = v.findViewById<TextView>(R.id.tvTitle)
        var tvPubDate: TextView = v.findViewById<TextView>(R.id.tvPubDate)
        var tvDescription: TextView = v.findViewById<TextView>(R.id.tvDescription)
    }
}
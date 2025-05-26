package com.marinov.news;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.VH> {
    private final List<FeedItem> list;

    public FeedAdapter(List<FeedItem> data) {
        list = data;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_feed, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        FeedItem item = list.get(position);

        holder.tvTitle.setText(item.getTitle());
        holder.tvPubDate.setText(item.getPubDate());
        holder.tvDescription.setText(item.getDescription());

        if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
            holder.ivImage.setVisibility(View.VISIBLE);
            Glide.with(holder.ivImage.getContext())
                    .load(item.getImageUrl())
                    .centerCrop()
                    .into(holder.ivImage);
        } else {
            holder.ivImage.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            String url = item.getLink();
            Log.d("RSS", "Clicou no item: " + item.getTitle() + " / " + url);
            if (url != null && !url.isEmpty()) {
                Intent intent = new Intent(v.getContext(), ArticleActivity.class);
                intent.putExtra("url", url);
                v.getContext().startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvTitle, tvPubDate, tvDescription;

        VH(View v) {
            super(v);
            ivImage = v.findViewById(R.id.ivImage);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvPubDate = v.findViewById(R.id.tvPubDate);
            tvDescription = v.findViewById(R.id.tvDescription);
        }
    }
}
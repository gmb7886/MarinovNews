package com.marinov.news;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class UrlsAdapter extends RecyclerView.Adapter<UrlsAdapter.VH> {
    private final List<String> list;
    private final OnRemoveListener listener;

    public interface OnRemoveListener {
        void onRemove(int position);
    }

    public UrlsAdapter(List<String> data, OnRemoveListener l) {
        list = data;
        listener = l;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int i) {
        View v = LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_url, p, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        String url = list.get(pos);
        h.tvUrl.setText(url);
        h.btnRemove.setOnClickListener(v -> listener.onRemove(pos));
    }

    @Override public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvUrl;
        ImageButton btnRemove;
        VH(View v) {
            super(v);
            tvUrl = v.findViewById(R.id.tvUrl);
            btnRemove = v.findViewById(R.id.btnRemove);
        }
    }
}

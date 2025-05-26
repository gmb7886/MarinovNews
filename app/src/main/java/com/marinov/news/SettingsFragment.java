package com.marinov.news;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class SettingsFragment extends Fragment {
    private TextInputEditText etRssUrl;
    private RecyclerView rvUrls;
    private UrlsAdapter adapter;
    private List<String> urls = new ArrayList<>();
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        prefs = requireActivity().getSharedPreferences("feeds", getContext().MODE_PRIVATE);
        urls = new ArrayList<>(prefs.getStringSet("urls", new HashSet<>()));

        etRssUrl = view.findViewById(R.id.etRssUrl);
        rvUrls = view.findViewById(R.id.rvUrls);
        rvUrls.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new UrlsAdapter(urls, this::removeUrl);
        rvUrls.setAdapter(adapter);

        view.findViewById(R.id.btnAddFeed).setOnClickListener(v -> {
            String url = etRssUrl.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(getContext(), "Digite uma URL válida", Toast.LENGTH_SHORT).show();
                return;
            }
            urls.add(url);
            // Log de salvamento
            Log.d("RSS", "Salvando feed: " + url);
            saveUrls();
            adapter.notifyItemInserted(urls.size() - 1);
            etRssUrl.setText("");
        });

        return view;
    }

    private void removeUrl(int position) {
        urls.remove(position);
        saveUrls();
        adapter.notifyItemRemoved(position);
    }

    private void saveUrls() {
        SharedPreferences.Editor ed = prefs.edit();
        ed.putStringSet("urls", new HashSet<>(urls));
        ed.apply();
    }
}
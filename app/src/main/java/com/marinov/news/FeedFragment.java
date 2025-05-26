package com.marinov.news;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

public class FeedFragment extends Fragment {
    private RecyclerView rvFeed;
    private TextView tvEmpty;
    private FeedAdapter adapter;
    private ArrayList<FeedItem> items = new ArrayList<>();
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_feed, container, false);
        rvFeed = view.findViewById(R.id.rvFeed);
        tvEmpty = view.findViewById(R.id.tvEmpty);

        rvFeed.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FeedAdapter(items);  // ajustado para o novo construtor
        rvFeed.setAdapter(adapter);

        prefs = requireActivity().getSharedPreferences("feeds", Context.MODE_PRIVATE);
        new LoadFeedsTask().execute();

        return view;
    }

    private class LoadFeedsTask extends AsyncTask<Void, Void, ArrayList<FeedItem>> {
        @Override
        protected ArrayList<FeedItem> doInBackground(Void... voids) {
            HashSet<String> urls = (HashSet<String>) prefs.getStringSet("urls", new HashSet<>());
            Log.d("RSS", "Feeds encontrados: " + urls);

            ArrayList<FeedItem> allItems = new ArrayList<>();
            for (String url : urls) {
                if (!TextUtils.isEmpty(url)) {
                    allItems.addAll(parseRss(url));
                }
            }

            // Ordena por data decrescente, trata datas nulas/vazias
            Collections.sort(allItems, new Comparator<FeedItem>() {
                private final SimpleDateFormat format =
                        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

                @Override
                public int compare(FeedItem a, FeedItem b) {
                    String sa = a.getPubDate(), sb = b.getPubDate();
                    if (TextUtils.isEmpty(sa) && TextUtils.isEmpty(sb)) return 0;
                    if (TextUtils.isEmpty(sa)) return 1;
                    if (TextUtils.isEmpty(sb)) return -1;
                    try {
                        Date da = format.parse(sa);
                        Date db = format.parse(sb);
                        return db.compareTo(da);
                    } catch (ParseException e) {
                        return 0;
                    }
                }
            });

            return allItems;
        }

        @Override
        protected void onPostExecute(ArrayList<FeedItem> result) {
            Log.d("RSS", "Itens carregados: " + result.size());
            items.clear();
            items.addAll(result);
            adapter.notifyDataSetChanged();
            tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private ArrayList<FeedItem> parseRss(String feedUrl) {
        ArrayList<FeedItem> list = new ArrayList<>();
        try {
            URL url = new URL(feedUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            InputStream stream = conn.getInputStream();

            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(stream, null);

            FeedItem current = null;
            boolean insideItem = false;
            int event = parser.getEventType();

            while (event != XmlPullParser.END_DOCUMENT) {
                String tag = parser.getName();
                switch (event) {
                    case XmlPullParser.START_TAG:
                        if ("item".equalsIgnoreCase(tag) || "entry".equalsIgnoreCase(tag)) {
                            insideItem = true;
                            current = new FeedItem();
                        } else if (insideItem) {
                            if ("title".equalsIgnoreCase(tag)) {
                                current.setTitle(parser.nextText());
                            } else if ("pubDate".equalsIgnoreCase(tag) ||
                                    "updated".equalsIgnoreCase(tag)) {
                                current.setPubDate(parser.nextText());
                            } else if ("description".equalsIgnoreCase(tag) ||
                                    "content".equalsIgnoreCase(tag)) {
                                current.setDescription(parser.nextText());
                            } else if ("link".equalsIgnoreCase(tag)) {
                                String href = parser.getAttributeValue(null, "href");
                                if (href != null) current.setLink(href);
                                else current.setLink(parser.nextText());
                            } else if ("enclosure".equalsIgnoreCase(tag)) {
                                String img = parser.getAttributeValue(null, "url");
                                if (img != null) current.setImageUrl(img);
                            } else if ("media:content".equalsIgnoreCase(tag)) {
                                String img = parser.getAttributeValue(null, "url");
                                if (img != null) current.setImageUrl(img);
                            }
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if (("item".equalsIgnoreCase(tag) || "entry".equalsIgnoreCase(tag))
                                && current != null) {
                            list.add(current);
                            insideItem = false;
                        }
                        break;
                }
                event = parser.next();
            }
            stream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}

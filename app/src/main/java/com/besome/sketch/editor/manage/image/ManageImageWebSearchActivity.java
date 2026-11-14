package com.besome.sketch.editor.manage.image;

import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.widget.SearchView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.BitmapImageViewTarget;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.R;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.besome.sketch.beans.ProjectResourceBean;
import pro.sketchware.workspace.WorkspacePathManager;
import pro.sketchware.utility.TranslationFunction;

public class ManageImageWebSearchActivity extends AppCompatActivity {

    private static final String PINTEREST_HOST = "br.pinterest.com";
    private static final String PINTEREST_BASE_URL = "https://" + PINTEREST_HOST;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";
    private static final int MAX_SEARCH_FETCH_ATTEMPTS = 4;
    private static final int TARGET_APPROVED_RESULTS = 60;
    private static final Pattern PWS_DATA_PATTERN = Pattern.compile(
            "<script id=\"__PWS_DATA__\" type=\"application/json\">(.*?)</script>",
            Pattern.DOTALL
    );

    private RecyclerView resultsList;
    private ResultsAdapter adapter;
    private final ArrayList<String> resultUrls = new ArrayList<>();
    private final Map<String, List<Cookie>> pinterestCookieStore = new HashMap<>();
    private final OkHttpClient pinterestClient = new OkHttpClient.Builder()
            .cookieJar(new CookieJar() {
                @Override
                public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                    pinterestCookieStore.put(url.host(), cookies);
                }

                @Override
                public List<Cookie> loadForRequest(HttpUrl url) {
                    List<Cookie> cookies = pinterestCookieStore.get(url.host());
                    return cookies != null ? cookies : Collections.emptyList();
                }
            })
            .build();
    private SearchView searchView;
    private View emptyView;
    private String pinterestAppVersion;

    @Override
    public Resources getResources() {
        return TranslationFunction.wrapResources(this, super.getResources());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_web_search);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.search_menu_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        emptyView = findViewById(R.id.emptyView);
        resultsList = findViewById(R.id.recycler_results);

        resultsList.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new ResultsAdapter();
        resultsList.setAdapter(adapter);
        updateEmptyState();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_image_web_search, menu);
        
        searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setQueryHint(getString(R.string.search_menu_title));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                doSearch(query);
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
        return true;
    }

    private void doSearch(String query) {
        String trimmedQuery = query == null ? "" : query.trim();
        if (TextUtils.isEmpty(trimmedQuery)) {
            Toast.makeText(this, "Enter search term", Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);
        new Thread(() -> {
            try {
                ArrayList<String> urls = searchPinterestImages(trimmedQuery);
                runOnUiThread(() -> {
                    setLoading(false);
                    resultUrls.clear();
                    resultUrls.addAll(urls);
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    setLoading(false);
                    resultUrls.clear();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Search failed", Toast.LENGTH_SHORT).show();
                    updateEmptyState();
                });
            }
        }).start();
    }

    private void setLoading(boolean loading) {
        // Sem indicador visual de loading; apenas desativa a SearchView

        if (searchView != null) {
            searchView.setEnabled(!loading);
        }
    }

    private void updateEmptyState() {
        if (emptyView != null) {
            emptyView.setVisibility(resultUrls.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private class ResultsAdapter extends RecyclerView.Adapter<ViewHolder> {
        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View v = getLayoutInflater().inflate(R.layout.item_image_web_search, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            String url = resultUrls.get(position);
            Glide.with(ManageImageWebSearchActivity.this)
                    .asBitmap()
                    .load(url)
                    .centerCrop()
                    .into(new BitmapImageViewTarget(holder.img).getView());
            holder.itemView.setOnClickListener(v -> selectImage(url));
        }

        @Override
        public int getItemCount() {
            return resultUrls.size();
        }
    }

    private static class ViewHolder extends RecyclerView.ViewHolder {
        final android.widget.ImageView img;
        ViewHolder(View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.img);
        }
    }

    private void selectImage(String imageUrl) {
        setLoading(true);
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(imageUrl)
                        .header("User-Agent", USER_AGENT)
                        .build();
                byte[] bytes;
                try (Response response = pinterestClient.newCall(request).execute()) {
                    okhttp3.ResponseBody body = response.body();
                    if (!response.isSuccessful() || body == null) {
                        throw new RuntimeException("download failed");
                    }
                    bytes = body.bytes();
                }
                String ext = guessImageExtension(imageUrl);
                File dir = new File(WorkspacePathManager.v());
                if (!dir.exists()) dir.mkdirs();
                String base = "pinterest_" + System.currentTimeMillis();
                File out = new File(dir, base + ext);
                try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(bytes); }

                ArrayList<ProjectResourceBean> list = new ArrayList<>();
                ProjectResourceBean bean = new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, base, out.getAbsolutePath());
                bean.savedPos = 1; // from external path

                bean.isNew = true; // ensure project save picks it up

                list.add(bean);

                runOnUiThread(() -> {
                    setLoading(false);
                    android.content.Intent data = new android.content.Intent();
                    data.putParcelableArrayListExtra("images", list);
                    setResult(RESULT_OK, data);
                    finish();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    setLoading(false);
					Toast.makeText(ManageImageWebSearchActivity.this, R.string.common_error_an_error_occurred, Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private ArrayList<String> searchPinterestImages(String query) throws Exception {
        String apiQuery = query.trim();

        ensurePinterestSession(buildSearchPageUrl(apiQuery));

        String fetchBookmark = null;
        String nextBookmark = null;
        int attempts = 0;
        ArrayList<String> approvedUrls = new ArrayList<>();
        Set<String> seenReferenceIds = new HashSet<>();

        while (attempts < MAX_SEARCH_FETCH_ATTEMPTS && approvedUrls.size() < TARGET_APPROVED_RESULTS) {
            PinterestSearchPage page = fetchPinterestSearchPage(apiQuery, fetchBookmark);
            nextBookmark = page.nextBookmark;

            JSONArray results = page.results;
            for (int i = 0; i < results.length() && approvedUrls.size() < TARGET_APPROVED_RESULTS; i++) {
                JSONObject pin = results.optJSONObject(i);
                if (pin == null) {
                    continue;
                }

                String imageUrl = resolvePinterestImageUrl(pin);
                if (TextUtils.isEmpty(imageUrl)) {
                    continue;
                }

                String id = pin.optString("id", imageUrl);
                if (seenReferenceIds.add("pinterest_" + id)) {
                    approvedUrls.add(imageUrl);
                }
            }

            attempts += 1;
            if (TextUtils.isEmpty(nextBookmark)) {
                break;
            }
            fetchBookmark = nextBookmark;
        }

        return approvedUrls;
    }

    private PinterestSearchPage fetchPinterestSearchPage(String normalizedQuery, String bookmark) throws Exception {
        String encodedQuery = urlEncode(normalizedQuery);
        String sourceUrl = "/search/pins/?q=" + encodedQuery + "&rs=typed";
        String searchPageUrl = PINTEREST_BASE_URL + sourceUrl;

        JSONObject options = new JSONObject();
        options.put("query", normalizedQuery);
        options.put("scope", "pins");
        options.put("rs", "typed");
        JSONArray bookmarks = new JSONArray();
        if (!TextUtils.isEmpty(bookmark)) {
            bookmarks.put(bookmark);
        }
        options.put("bookmarks", bookmarks);
        options.put("redux_normalize_feed", true);

        JSONObject payload = new JSONObject();
        payload.put("options", options);
        payload.put("context", new JSONObject());

        String requestUrl = PINTEREST_BASE_URL + "/resource/BaseSearchResource/get/"
                + "?source_url=" + urlEncode(sourceUrl)
                + "&data=" + urlEncode(payload.toString())
                + "&_=1";

        Request request = new Request.Builder()
                .url(requestUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/javascript, */*, q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-Pinterest-AppState", "active")
                .header("X-Pinterest-PWS-Handler", "www/search/[scope].js")
                .header("X-Pinterest-Source-Url", sourceUrl)
                .header("Referer", searchPageUrl)
                .header("X-App-Version", pinterestAppVersion == null ? "" : pinterestAppVersion)
                .header("X-CSRFToken", csrfToken())
                .build();

        try (Response response = pinterestClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("Pinterest search failed with " + response.code());
            }

            JSONObject envelope = new JSONObject(response.body().string());
            JSONObject resourceResponse = envelope.optJSONObject("resource_response");
            if (resourceResponse == null) {
                return new PinterestSearchPage(new JSONArray(), null);
            }

            JSONObject data = resourceResponse.optJSONObject("data");
            JSONArray results = data != null ? data.optJSONArray("results") : null;
            String nextBookmark = resourceResponse.optString("bookmark", null);
            return new PinterestSearchPage(
                    results != null ? results : new JSONArray(),
                    TextUtils.isEmpty(nextBookmark) ? null : nextBookmark
            );
        }
    }

    private void ensurePinterestSession(String searchPageUrl) throws Exception {
        if (!TextUtils.isEmpty(pinterestAppVersion) && !TextUtils.isEmpty(csrfToken())) {
            return;
        }

        Request request = new Request.Builder()
                .url(searchPageUrl)
                .header("User-Agent", USER_AGENT)
                .build();

        try (Response response = pinterestClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("Pinterest bootstrap failed with " + response.code());
            }

            String html = response.body().string();
            Matcher matcher = PWS_DATA_PATTERN.matcher(html);
            if (matcher.find()) {
                pinterestAppVersion = new JSONObject(matcher.group(1)).optString("appVersion", null);
            }
        }
    }

    private String buildSearchPageUrl(String query) throws Exception {
        return PINTEREST_BASE_URL + "/search/pins/?q=" + urlEncode(query) + "&rs=typed";
    }

    private String csrfToken() {
        List<Cookie> cookies = pinterestCookieStore.get(PINTEREST_HOST);
        if (cookies == null) {
            return "";
        }

        for (Cookie cookie : cookies) {
            if ("csrftoken".equals(cookie.name())) {
                return cookie.value();
            }
        }
        return "";
    }

    private static String resolvePinterestImageUrl(JSONObject pin) {
        JSONObject images = pin.optJSONObject("images");
        if (images == null) {
            return null;
        }

        String[] preferredSizes = {"orig", "736x", "564x", "474x", "236x"};
        for (String size : preferredSizes) {
            JSONObject asset = images.optJSONObject(size);
            if (asset == null) {
                continue;
            }

            String url = asset.optString("url", "");
            if (!TextUtils.isEmpty(url)) {
                return url;
            }
        }
        return null;
    }

    private static String guessImageExtension(String imageUrl) {
        String lowerUrl = imageUrl == null ? "" : imageUrl.toLowerCase(Locale.US);
        if (lowerUrl.contains(".png")) {
            return ".png";
        }
        if (lowerUrl.contains(".webp")) {
            return ".webp";
        }
        return ".jpg";
    }

    private static String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static class PinterestSearchPage {
        final JSONArray results;
        final String nextBookmark;

        PinterestSearchPage(JSONArray results, String nextBookmark) {
            this.results = results;
            this.nextBookmark = nextBookmark;
        }
    }
}



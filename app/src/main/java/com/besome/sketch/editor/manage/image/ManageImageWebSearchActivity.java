package com.besome.sketch.editor.manage.image;

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

import java.util.ArrayList;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.utility.Network;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.besome.sketch.beans.ProjectResourceBean;
import a.a.a.wq;

public class ManageImageWebSearchActivity extends AppCompatActivity {

    private static final String PIXABAY_KEY = "52638796-5403e309c919f67901307970e"; // demo key

    private RecyclerView resultsList;
    private ResultsAdapter adapter;
    private final ArrayList<String> resultUrls = new ArrayList<>();
    private SearchView searchView;
    private View emptyView;

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
        if (TextUtils.isEmpty(query)) {
            Toast.makeText(this, "Enter search term", Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);
        try {
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            String url = "https://pixabay.com/api/?key=" + PIXABAY_KEY + "&image_type=photo&per_page=60&safesearch=true&q=" + encodedQuery;
            new Network().get(url, response -> {
                setLoading(false);
                resultUrls.clear();
                try {
                    JSONObject obj = new JSONObject(response);
                    JSONArray hits = obj.optJSONArray("hits");
                    if (hits != null) {
                        for (int i = 0; i < hits.length(); i++) {
                            JSONObject hit = hits.getJSONObject(i);
                            String img = hit.optString("largeImageURL");
                            if (!TextUtils.isEmpty(img)) resultUrls.add(img);
                        }
                    }
                } catch (Exception ignored) {
                }
                adapter.notifyDataSetChanged();
                updateEmptyState();
            });
        } catch (Exception e) {
            setLoading(false);
            Toast.makeText(this, "Search failed", Toast.LENGTH_SHORT).show();
            updateEmptyState();
        }
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
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(imageUrl).build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful() || response.body() == null) throw new RuntimeException("download failed");
                byte[] bytes = response.body().bytes();
                String ext = imageUrl.toLowerCase(Locale.US).contains(".png") ? ".png" : ".jpg";
                File dir = new File(wq.v());
                if (!dir.exists()) dir.mkdirs();
                String base = "pixabay_" + System.currentTimeMillis();
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
}



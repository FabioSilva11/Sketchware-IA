package pro.sketchware.supabase;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import mod.hey.studios.util.Helper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import pro.sketchware.R;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.utility.UI;

/**
 * The project's Supabase connection, opened from the library screen like Firebase's setup. The values are saved
 * when leaving the screen; the test button asks the Auth API for its settings, which only answers when both the
 * URL and the key are right.
 */
public class SupabaseLibraryActivity extends BaseAppCompatActivity {

    private static final String DOCS_URL = "https://supabase.com/docs/guides/api/api-keys";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String scId;
    private TextInputLayout urlLayout;
    private TextInputLayout keyLayout;
    private TextInputEditText urlInput;
    private TextInputEditText keyInput;
    private TextView status;
    private View progress;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manage_library_supabase);
        scId = savedInstanceState == null ? getIntent().getStringExtra("sc_id") : savedInstanceState.getString("sc_id");

        Toolbar toolbar = findViewById(R.id.toolbar);
        // Edge to edge: the toolbar starts below the status bar
        UI.addSystemWindowInsetToPadding(toolbar, false, true, false, false);
        toolbar.getLayoutParams().height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        setSupportActionBar(toolbar);
        findViewById(R.id.layout_main_logo).setVisibility(View.GONE);
        getSupportActionBar().setTitle(R.string.supabase_library_title);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        UI.addSystemWindowInsetToPadding(findViewById(R.id.scroll), false, false, false, true);
        urlLayout = findViewById(R.id.til_url);
        keyLayout = findViewById(R.id.til_anon_key);
        urlInput = findViewById(R.id.ed_url);
        keyInput = findViewById(R.id.ed_anon_key);
        status = findViewById(R.id.tv_status);
        progress = findViewById(R.id.progress);
        if (savedInstanceState == null) {
            urlInput.setText(SupabaseSettings.getUrl(scId));
            keyInput.setText(SupabaseSettings.getAnonKey(scId));
        }

        MaterialButton test = findViewById(R.id.btn_test);
        test.setOnClickListener(v -> testConnection());
        MaterialButton docs = findViewById(R.id.btn_docs);
        docs.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(DOCS_URL)));
            } catch (Exception e) {
                showStatus(getString(R.string.supabase_library_no_browser), false);
            }
        });
        MaterialButton clear = findViewById(R.id.btn_clear);
        clear.setOnClickListener(v -> {
            urlInput.setText("");
            keyInput.setText("");
            urlLayout.setError(null);
            keyLayout.setError(null);
            showStatus(getString(R.string.supabase_library_cleared), true);
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", scId);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    /** Both empty turns Supabase off; otherwise both must be valid before leaving. */
    private boolean validate() {
        String url = Helper.getText(urlInput);
        String key = Helper.getText(keyInput).trim();
        urlLayout.setError(null);
        keyLayout.setError(null);
        if (url.trim().isEmpty() && key.isEmpty()) return true;
        int problem = SupabaseSettings.urlProblem(url);
        if (problem != 0) {
            urlLayout.setError(getString(problem));
            return false;
        }
        if (key.isEmpty()) {
            keyLayout.setError(getString(R.string.supabase_error_key_empty));
            return false;
        }
        return true;
    }

    private void testConnection() {
        if (!validate() || Helper.getText(urlInput).trim().isEmpty()) {
            if (Helper.getText(urlInput).trim().isEmpty()) urlLayout.setError(getString(R.string.supabase_error_url_empty));
            return;
        }
        String url = SupabaseSettings.normalizeUrl(Helper.getText(urlInput));
        String key = Helper.getText(keyInput).trim();
        progress.setVisibility(View.VISIBLE);
        status.setVisibility(View.GONE);
        executor.execute(() -> {
            String message;
            boolean ok = false;
            OkHttpClient client = new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS).build();
            Request request = new Request.Builder().url(url + "/auth/v1/settings")
                    .header("apikey", key).header("Authorization", "Bearer " + key).build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    ok = true;
                    message = getString(R.string.supabase_library_test_ok);
                } else if (response.code() == 401 || response.code() == 403) {
                    message = getString(R.string.supabase_library_test_bad_key);
                } else {
                    message = getString(R.string.supabase_library_test_http, response.code());
                }
            } catch (Exception e) {
                message = getString(R.string.supabase_library_test_unreachable, e.getMessage() == null ? e.toString() : e.getMessage());
            }
            boolean success = ok;
            String text = message;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                progress.setVisibility(View.GONE);
                showStatus(text, success);
            });
        });
    }

    private void showStatus(String text, boolean success) {
        status.setVisibility(View.VISIBLE);
        status.setText(text);
        status.setTextColor(ThemeUtils.getColor(this, success ? R.attr.colorPrimary : R.attr.colorError));
    }

    @Override
    public void onBackPressed() {
        if (!validate()) return;
        SupabaseSettings.save(scId, Helper.getText(urlInput), Helper.getText(keyInput));
        setResult(RESULT_OK);
        super.onBackPressed();
    }
}

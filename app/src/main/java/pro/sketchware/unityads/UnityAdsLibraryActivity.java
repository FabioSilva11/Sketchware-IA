package pro.sketchware.unityads;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.utility.UI;

/**
 * The project's Unity Ads setup, opened from the library screen like AdMob's. The values are saved when leaving
 * the screen.
 */
public class UnityAdsLibraryActivity extends BaseAppCompatActivity {

    private static final String DOCS_URL = "https://docs.unity.com/en-us/ads-android/latest/sdk-integration/initialize-sdk";

    private String scId;
    private TextInputLayout gameIdLayout;
    private TextInputEditText gameIdInput;
    private MaterialSwitch testMode;
    private TextView status;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manage_library_unity_ads);
        scId = savedInstanceState == null ? getIntent().getStringExtra("sc_id") : savedInstanceState.getString("sc_id");

        Toolbar toolbar = findViewById(R.id.toolbar);
        // Edge to edge: the toolbar starts below the status bar
        UI.addSystemWindowInsetToPadding(toolbar, false, true, false, false);
        toolbar.getLayoutParams().height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        setSupportActionBar(toolbar);
        findViewById(R.id.layout_main_logo).setVisibility(View.GONE);
        getSupportActionBar().setTitle(R.string.unity_ads_library_title);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        UI.addSystemWindowInsetToPadding(findViewById(R.id.scroll), false, false, false, true);
        gameIdLayout = findViewById(R.id.til_game_id);
        gameIdInput = findViewById(R.id.ed_game_id);
        testMode = findViewById(R.id.sw_test_mode);
        status = findViewById(R.id.tv_status);
        if (savedInstanceState == null) {
            gameIdInput.setText(UnityAdsSettings.getGameId(scId));
            testMode.setChecked(UnityAdsSettings.isTestMode(scId));
        }

        MaterialButton docs = findViewById(R.id.btn_docs);
        docs.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(DOCS_URL)));
            } catch (Exception e) {
                showStatus(getString(R.string.unity_ads_library_no_browser), false);
            }
        });
        MaterialButton clear = findViewById(R.id.btn_clear);
        clear.setOnClickListener(v -> {
            gameIdInput.setText("");
            testMode.setChecked(false);
            gameIdLayout.setError(null);
            showStatus(getString(R.string.unity_ads_library_cleared), true);
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", scId);
        super.onSaveInstanceState(outState);
    }

    /** Empty turns Unity Ads off; otherwise the game ID must look like one. */
    private boolean validate() {
        String gameId = Helper.getText(gameIdInput).trim();
        gameIdLayout.setError(null);
        if (gameId.isEmpty() || UnityAdsSettings.isValidGameId(gameId)) return true;
        gameIdLayout.setError(getString(R.string.unity_ads_error_game_id));
        return false;
    }

    private void showStatus(String text, boolean success) {
        status.setVisibility(View.VISIBLE);
        status.setText(text);
        status.setTextColor(ThemeUtils.getColor(this, success ? R.attr.colorPrimary : R.attr.colorError));
    }

    @Override
    public void onBackPressed() {
        if (!validate()) return;
        UnityAdsSettings.save(scId, Helper.getText(gameIdInput), testMode.isChecked());
        setResult(RESULT_OK);
        super.onBackPressed();
    }
}

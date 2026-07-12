package pro.sketchware.activities.settings;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import pro.sketchware.R;
import pro.sketchware.activities.chat.port.GitHubMcpService;
import pro.sketchware.activities.chat.port.VoidPortSettings;

/**
 * Standalone settings screen for the GitHub MCP integration.
 * Accessible from the main Settings menu, below "AI Settings".
 */
public class GithubSettingsActivity extends BaseAppCompatActivity {

    private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(VoidPortSettings.PREFS_NAME, MODE_PRIVATE);

        // Root layout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Toolbar
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.github_settings_title);
        toolbar.setNavigationIcon(com.google.android.material.R.drawable.m3_tabs_rounded_line_indicator);
        toolbar.setNavigationOnClickListener(v -> finish());
        LinearLayout.LayoutParams toolbarParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toolbar.setLayoutParams(toolbarParams);
        root.addView(toolbar);

        // Scrollable content
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        content.setPadding(pad, pad, pad, pad);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        buildContent(content);

        scrollView.addView(content);
        root.addView(scrollView);
        setContentView(root);

        // Edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(sys.left, sys.top, sys.right, 0);
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, 0, sys.right, sys.bottom);
            return insets;
        });
    }

    private void buildContent(LinearLayout container) {

        // ── Header ─────────────────────────────────────────────────────────────
        TextView header = new TextView(this);
        header.setText(R.string.github_versioning_menu);
        header.setTextColor(ContextCompat.getColor(this, R.color.chat_text_primary));
        TextViewCompat.setTextAppearance(header,
                com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall);
        container.addView(header);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.github_settings_intro);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitle.setTextColor(ContextCompat.getColor(this, R.color.chat_text_secondary));
        LinearLayout.LayoutParams subtitleParams = rowParams();
        subtitleParams.topMargin = dp(6);
        subtitleParams.bottomMargin = dp(20);
        subtitle.setLayoutParams(subtitleParams);
        container.addView(subtitle);

        // ── Token card ─────────────────────────────────────────────────────────
        MaterialCardView tokenCard = card();
        LinearLayout tokenContent = cardContent(tokenCard);

        addSubheading(tokenContent, getString(R.string.github_settings_pat_title));
        addMuted(tokenContent, getString(R.string.github_settings_pat_description));

        // Token input
        TextInputLayout tokenLayout = new TextInputLayout(this);
        tokenLayout.setHint(R.string.github_settings_token_hint);
        tokenLayout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        tokenLayout.setLayoutParams(inputParams());

        TextInputEditText tokenEdit = new TextInputEditText(this);
        tokenEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenEdit.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tokenEdit.setText(prefs.getString(VoidPortSettings.PREF_GITHUB_TOKEN, ""));
        tokenEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                prefs.edit().putString(VoidPortSettings.PREF_GITHUB_TOKEN, s.toString().trim()).apply();
                refreshTokenStatus(tokenContent);
            }
        });
        tokenLayout.addView(tokenEdit);
        tokenContent.addView(tokenLayout);

        // Token status
        addTokenStatus(tokenContent);

        // Open GitHub button
        MaterialButton openGitHub = outlinedButton(getString(R.string.github_settings_open_tokens));
        openGitHub.setOnClickListener(v -> openUrl(
                "https://github.com/settings/tokens/new?scopes=repo,read:org&description=Sketchware+IA"));
        tokenContent.addView(openGitHub);

        container.addView(tokenCard);

        // ── Available tools card ───────────────────────────────────────────────
        MaterialCardView toolsCard = card();
        LinearLayout toolsContent = cardContent(toolsCard);

        addSubheading(toolsContent, getString(R.string.github_settings_tools_title));
        addMuted(toolsContent, getString(R.string.github_settings_tools_description,
                GitHubMcpService.getToolDefinitions().length()));
        container.addView(toolsCard);

        MaterialCardView versioningCard = card();
        LinearLayout versioningContent = cardContent(versioningCard);
        addSubheading(versioningContent, getString(R.string.github_settings_versioning_title));
        addMuted(versioningContent, getString(R.string.github_settings_versioning_description));
        container.addView(versioningCard);

        // ── Usage tips card ────────────────────────────────────────────────────
        MaterialCardView tipsCard = card();
        LinearLayout tipsContent = cardContent(tipsCard);

        addSubheading(tipsContent, getString(R.string.github_settings_examples_title));
        addMuted(tipsContent, getString(R.string.github_settings_examples_description));
        container.addView(tipsCard);
    }

    private void addTokenStatus(LinearLayout parent) {
        String token = prefs.getString(VoidPortSettings.PREF_GITHUB_TOKEN, "").trim();
        TextView status = new TextView(this);
        status.setTag("github_token_status");
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        status.setLayoutParams(rowParams());

        if (token.isEmpty()) {
            status.setText(R.string.github_settings_token_missing);
            status.setTextColor(ContextCompat.getColor(this, R.color.chat_text_secondary));
        } else {
            status.setText(getString(R.string.github_settings_token_configured, token.length()));
            status.setTextColor(ContextCompat.getColor(this, R.color.chat_accent));
        }
        parent.addView(status);
    }

    private void refreshTokenStatus(LinearLayout parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            if ("github_token_status".equals(v.getTag()) && v instanceof TextView) {
                String token = prefs.getString(VoidPortSettings.PREF_GITHUB_TOKEN, "").trim();
                TextView status = (TextView) v;
                if (token.isEmpty()) {
                    status.setText(R.string.github_settings_token_missing);
                    status.setTextColor(ContextCompat.getColor(this, R.color.chat_text_secondary));
                } else {
                    status.setText(getString(R.string.github_settings_token_configured, token.length()));
                    status.setTextColor(ContextCompat.getColor(this, R.color.chat_accent));
                }
                break;
            }
        }
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(16);
        card.setLayoutParams(p);
        card.setRadius(dp(8));
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.chat_surface));
        card.setStrokeColor(ContextCompat.getColor(this, R.color.chat_border));
        card.setStrokeWidth(dp(1));
        return card;
    }

    private LinearLayout cardContent(MaterialCardView card) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(content);
        return content;
    }

    private void addSubheading(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(ContextCompat.getColor(this, R.color.chat_text_primary));
        TextViewCompat.setTextAppearance(tv,
                com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        tv.setLayoutParams(rowParams());
        parent.addView(tv);
    }

    private void addMuted(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTextColor(ContextCompat.getColor(this, R.color.chat_text_secondary));
        tv.setLayoutParams(rowParams());
        parent.addView(tv);
    }

    private MaterialButton outlinedButton(String text) {
        MaterialButton btn = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        btn.setCornerRadius(dp(8));
        btn.setStrokeColor(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.chat_border)));
        btn.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.chat_surface)));
        btn.setTextColor(ContextCompat.getColor(this, R.color.chat_accent));
        btn.setLayoutParams(rowParams());
        return btn;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(10);
        return p;
    }

    private LinearLayout.LayoutParams inputParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(12);
        return p;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.github_settings_open_link_failed, Toast.LENGTH_SHORT).show();
        }
    }
}

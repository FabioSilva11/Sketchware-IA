package com.besome.sketch.tools;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.view.LayoutInflater;
import android.content.ClipboardManager;
import android.content.ClipData;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;

import mod.hey.studios.util.CompileLogHelper;
import mod.hey.studios.util.Helper;

import mod.jbk.diagnostic.CompileErrorSaver;
import mod.jbk.util.AddMarginOnApplyWindowInsetsListener;
import pro.sketchware.R;
import pro.sketchware.databinding.CompileLogBinding;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.AdManager;
import pro.sketchware.network.AiProviderService;
import io.noties.markwon.Markwon;
import android.os.Environment;
import java.io.File;

 import mod.hey.studios.project.ProjectTracker;
import pro.sketchware.utility.TranslationFunction;

public class CompileLogActivity extends BaseAppCompatActivity {

    private static final String PREFERENCE_WRAPPED_TEXT = "wrapped_text";
    private static final String PREFERENCE_USE_MONOSPACED_FONT = "use_monospaced_font";
    private static final String PREFERENCE_FONT_SIZE = "font_size";
    private CompileErrorSaver compileErrorSaver;
    private SharedPreferences logViewerPreferences;

    private CompileLogBinding binding;
    private String scId;


    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = CompileLogBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.optionsLayout,
            new AddMarginOnApplyWindowInsetsListener(WindowInsetsCompat.Type.navigationBars(), WindowInsetsCompat.CONSUMED));

        logViewerPreferences = getPreferences(Context.MODE_PRIVATE);

        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));

        if (getIntent().getBooleanExtra("showingLastError", false)) {
            binding.topAppBar.setTitle(R.string.compile_log_title_last);
        } else {
            binding.topAppBar.setTitle(R.string.compile_log_title);
        }

        // Resolve sc_id automaticamente (Intent -> ProjectTracker -> scan de diretórios)
        String scIdFromIntent = getIntent().getStringExtra("sc_id");
        this.scId = resolveScId();
        if (scIdFromIntent == null && this.scId != null) {
            SketchwareUtil.toast(getString(R.string.compile_log_project_detected, this.scId));
        }

        if (this.scId != null) {
            compileErrorSaver = new CompileErrorSaver(this.scId);
        }

        if (compileErrorSaver != null && compileErrorSaver.logFileExists()) {
            binding.clearButton.setOnClickListener(v -> {
                if (compileErrorSaver.logFileExists()) {
                    compileErrorSaver.deleteSavedLogs();
                    getIntent().removeExtra("error");
                    SketchwareUtil.toast(getString(R.string.compile_log_cleared));
                } else {
                    SketchwareUtil.toast(getString(R.string.compile_log_not_found));
                }

                setErrorText();
            });
        }

        PopupMenu options = new PopupMenu(this, binding.formatButton);
        options.getMenu().add(0, 1, 0, R.string.compile_log_option_wrap_text)
            .setCheckable(true)
            .setChecked(getWrappedTextPreference());
        options.getMenu().add(0, 2, 1, R.string.compile_log_option_monospaced_font)
            .setCheckable(true)
            .setChecked(getMonospacedFontPreference());
        options.getMenu().add(0, 3, 2, R.string.compile_log_option_font_size);

        options.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case 1 -> {
                    menuItem.setChecked(!menuItem.isChecked());
                    toggleWrapText(menuItem.isChecked());
                }
                case 2 -> {
                    menuItem.setChecked(!menuItem.isChecked());
                    toggleMonospacedText(menuItem.isChecked());
                }
                case 3 -> changeFontSizeDialog();
                default -> {
                    return false;
                }
            }

            return true;
        });

        binding.formatButton.setOnClickListener(v -> options.show());

        applyLogViewerPreferences();

        setErrorText();

        // AI Explain button: analisa o log via Groq e mostra em diálogo com scroll
        if (binding.aiExplainButton != null) {
            binding.aiExplainButton.setOnClickListener(v -> explainLogWithAI());
        }

        AdManager.loadBanner(this, findViewById(R.id.ad_container), "ca-app-pub-6598765502914364/2525937709");

    }

    private void setErrorText() {
        String error = getIntent().getStringExtra("error");
        if (error == null && compileErrorSaver != null) {
            error = compileErrorSaver.getDisplayLogsFromFile();
        } else if (error != null) {
            error = CompileErrorSaver.createDisplayPreview(error, "Intent extra");
        }
        if (error == null) {
            binding.noContentLayout.setVisibility(View.VISIBLE);
            binding.optionsLayout.setVisibility(View.GONE);
            return;
        }


        binding.optionsLayout.setVisibility(View.VISIBLE);
        binding.noContentLayout.setVisibility(View.GONE);

        binding.tvCompileLog.setText(CompileLogHelper.getColoredLogs(this, error));
        binding.tvCompileLog.setTextIsSelectable(true);
    }

    private void applyLogViewerPreferences() {
        toggleWrapText(getWrappedTextPreference());
        toggleMonospacedText(getMonospacedFontPreference());
        binding.tvCompileLog.setTextSize(getFontSizePreference());
    }

    private boolean getWrappedTextPreference() {
        return logViewerPreferences.getBoolean(PREFERENCE_WRAPPED_TEXT, false);
    }

    private boolean getMonospacedFontPreference() {
        return logViewerPreferences.getBoolean(PREFERENCE_USE_MONOSPACED_FONT, true);
    }

    private int getFontSizePreference() {
        return logViewerPreferences.getInt(PREFERENCE_FONT_SIZE, 11);
    }

    private void toggleWrapText(boolean isChecked) {
        logViewerPreferences.edit().putBoolean(PREFERENCE_WRAPPED_TEXT, isChecked).apply();

        if (isChecked) {
            binding.errVScroll.removeAllViews();
            if (binding.tvCompileLog.getParent() != null) {
                ((ViewGroup) binding.tvCompileLog.getParent()).removeView(binding.tvCompileLog);
            }
            binding.errVScroll.addView(binding.tvCompileLog);
        } else {
            binding.errVScroll.removeAllViews();
            if (binding.tvCompileLog.getParent() != null) {
                ((ViewGroup) binding.tvCompileLog.getParent()).removeView(binding.tvCompileLog);
            }
            binding.errHScroll.removeAllViews();
            binding.errHScroll.addView(binding.tvCompileLog);
            binding.errVScroll.addView(binding.errHScroll);
        }
    }

    private void toggleMonospacedText(boolean isChecked) {
        logViewerPreferences.edit().putBoolean(PREFERENCE_USE_MONOSPACED_FONT, isChecked).apply();

        if (isChecked) {
            binding.tvCompileLog.setTypeface(Typeface.MONOSPACE);
        } else {
            binding.tvCompileLog.setTypeface(Typeface.DEFAULT);
        }
    }

    private void changeFontSizeDialog() {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(10); //Must not be less than setValue(), which is currently 11 in compile_log.xml
        picker.setMaxValue(70);
        picker.setWrapSelectorWheel(false);
        picker.setValue(getFontSizePreference());

        LinearLayout layout = new LinearLayout(this);
        layout.addView(picker, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER));

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.compile_log_font_size_title)
            .setView(layout)
            .setPositiveButton(R.string.common_word_save, (dialog, which) -> {
                logViewerPreferences.edit().putInt(PREFERENCE_FONT_SIZE, picker.getValue()).apply();

                binding.tvCompileLog.setTextSize((float) picker.getValue());
            })
            .setNegativeButton(R.string.common_word_cancel, null)
            .show();
    }

    private void explainLogWithAI() {
        CharSequence cs = binding.tvCompileLog.getText();
        String logText = cs != null ? cs.toString().trim() : "";
        if (logText.isEmpty()) {
            SketchwareUtil.toastError(getString(R.string.compile_log_no_log_to_analyze));
            return;
        }
        if (!SketchwareUtil.isConnected()) {
            SketchwareUtil.toastError(getString(R.string.common_message_check_network));
            return;
        }

        // Diálogo de carregamento com Lottie
        View loadingView = LayoutInflater.from(this).inflate(R.layout.dialog_ai_layout_loading, null);
        TextView titleView = loadingView.findViewById(R.id.text_loading_title);
        TextView subtitleView = loadingView.findViewById(R.id.text_loading_subtitle);

        if (titleView != null) titleView.setText(R.string.ai_explain_loading_title);
        if (subtitleView != null) subtitleView.setText(R.string.ai_explain_loading_subtitle);

        var progressDialog = new MaterialAlertDialogBuilder(this)
            .setView(loadingView)
            .setCancelable(false)
            .create();
        progressDialog.show();

        new Thread(() -> {
            try {
                String prompt = "Analyze this Sketchware Android compile log. "
                        + "Explain the root cause, identify the affected screen or generated source when possible, "
                        + "and give concrete correction steps without modifying the project.\n\n"
                        + logText;
                String response = AiProviderService.getInstance().sendTextMessage(
                        "You analyze Sketchware Android compile logs. Respond in Brazilian Portuguese.",
                        prompt
                );
                runOnUiThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception ignored) {
                    }
                    showScrollableDialog(getString(R.string.ai_explain_title), limitAiText(response, 300));
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception ignored) {
                    }
                    SketchwareUtil.showAnErrorOccurredDialog(this, e.getMessage());
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception ignored) {
                    }
                    SketchwareUtil.showAnErrorOccurredDialog(this, getString(R.string.ai_explain_error_processing_response));
                });
            }
        }).start();
    }

    private void showScrollableDialog(String title, String content) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        TextView textView = new TextView(this);
        // Renderiza Markdown usando Markwon
        Markwon markwon = Markwon.create(this);
        markwon.setMarkdown(textView, content);
        textView.setTextIsSelectable(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        textView.setPadding(pad, pad, pad, pad);

        scrollView.addView(textView, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        var dialog = new MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton(R.string.common_word_close, null)
            .setNeutralButton(R.string.common_word_copy, null)
            .create();

        dialog.setOnShowListener(d -> {
            var btnCopy = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL);
            btnCopy.setOnClickListener(v -> {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("ia_response", content));
                        SketchwareUtil.toast(getString(R.string.common_word_copied));
                    }
                } catch (Exception ignored) {
                }
            });
        });

        dialog.show();
    }

    private String limitAiText(String content, int maxChars) {
        if (content == null) {
            return "";
        }

        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }

        if (maxChars <= 3) {
            return normalized.substring(0, Math.max(0, maxChars));
        }

        return normalized.substring(0, maxChars - 3).trim() + "...";
    }

    // Resolve automaticamente o sc_id atual:
    // 1) Intent extra, 2) ProjectTracker.SC_ID, 3) diretório com compile_log mais recente,
    // 4) diretório com logic mais recente, 5) projeto mais recente em .sketchware/mysc/list
    private String resolveScId() {
        try {
            String fromIntent = getIntent().getStringExtra("sc_id");
            if (fromIntent != null && !fromIntent.trim().isEmpty()) return fromIntent;
        } catch (Exception ignored) {
        }

        try {
            if (ProjectTracker.SC_ID != null && !ProjectTracker.SC_ID.trim().isEmpty())
                return ProjectTracker.SC_ID;
        } catch (Exception ignored) {
        }

        // Procura em /.sketchware/data/<sc_id>/compile_log
        File dataRoot = new File(Environment.getExternalStorageDirectory(), ".sketchware/data");
        if (dataRoot.isDirectory()) {
            File[] candidates = dataRoot.listFiles(File::isDirectory);
            if (candidates != null && candidates.length > 0) {
                File latest = null;
                long latestMod = -1;
                for (File dir : candidates) {
                    File log = new File(dir, "compile_log");
                    long m = log.exists() ? log.lastModified() : -1;
                    if (m > latestMod) {
                        latestMod = m;
                        latest = dir;
                    }
                }
                if (latest != null && latestMod > 0) return latest.getName();

                // Segunda tentativa: arquivo logic mais recente
                latest = null;
                latestMod = -1;
                for (File dir : candidates) {
                    File logic = new File(dir, "logic");
                    long m = logic.exists() ? logic.lastModified() : -1;
                    if (m > latestMod) {
                        latestMod = m;
                        latest = dir;
                    }
                }
                if (latest != null && latestMod > 0) return latest.getName();
            }
        }

        // Procura em /.sketchware/mysc/list/<sc_id>/project com último modificado
        File listRoot = new File(Environment.getExternalStorageDirectory(), ".sketchware/mysc/list");
        if (listRoot.isDirectory()) {
            File[] dirs = listRoot.listFiles(File::isDirectory);
            if (dirs != null && dirs.length > 0) {
                File latest = null;
                long mod = -1;
                for (File dir : dirs) {
                    File proj = new File(dir, "project");
                    long m = proj.exists() ? proj.lastModified() : -1;
                    if (m > mod) {
                        mod = m;
                        latest = dir;
                    }
                }
                if (latest != null && mod > 0) return latest.getName();
            }
        }

        return null;
    }

}

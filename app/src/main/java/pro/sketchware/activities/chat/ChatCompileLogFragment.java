package pro.sketchware.activities.chat;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import mod.hey.studios.util.CompileLogHelper;
import mod.jbk.diagnostic.CompileErrorSaver;
import pro.sketchware.R;

public class ChatCompileLogFragment extends Fragment {
    private static final String ARG_SC_ID = "sc_id";

    private String scId;
    private TextView logText;
    private ScrollView scrollView;
    private String currentLog = "";

    public static ChatCompileLogFragment newInstance(String scId) {
        ChatCompileLogFragment fragment = new ChatCompileLogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SC_ID, scId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        scId = args == null ? null : args.getString(ARG_SC_ID);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.chat_surface));
        logText = new TextView(requireContext());
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setTextColor(ContextCompat.getColor(requireContext(), R.color.chat_text_primary));
        logText.setTextSize(12f);
        logText.setTextIsSelectable(true);
        int pad = dp(12);
        logText.setPadding(pad, pad, pad, pad + dp(18));
        scrollView.addView(logText, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        if (currentLog.isEmpty()) {
            loadLastLog();
        } else {
            renderLog();
        }
        return scrollView;
    }

    @Override
    public void onDestroyView() {
        logText = null;
        scrollView = null;
        super.onDestroyView();
    }

    public void startNewLog() {
        setLog(getStringSafe(R.string.chat_compile_log_starting));
    }

    public void appendLine(String line) {
        String safeLine = line == null ? "" : line;
        setLog(currentLog + (currentLog.isEmpty() ? "" : "\n") + safeLine);
    }

    public void setLog(String log) {
        currentLog = log == null ? "" : log;
        renderLog();
    }

    public void loadLastLog() {
        String logs = null;
        if (scId != null) {
            logs = new CompileErrorSaver(scId).getDisplayLogsFromFile();
        }
        setLog(logs == null || logs.trim().isEmpty()
                ? getStringSafe(R.string.compile_log_no_log_available)
                : logs);
    }

    private void renderLog() {
        if (logText == null || !isAdded()) {
            return;
        }
        logText.setText(CompileLogHelper.getColoredLogs(requireContext(), currentLog));
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private String getStringSafe(int resId) {
        return isAdded() ? getString(resId) : "";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

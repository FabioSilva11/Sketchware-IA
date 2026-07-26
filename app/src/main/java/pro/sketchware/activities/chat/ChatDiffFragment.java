package pro.sketchware.activities.chat;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pro.sketchware.R;
import pro.sketchware.activities.chat.port.VoidPortDiffService;
import pro.sketchware.util.FileChangeTracker;
import pro.sketchware.util.SketchwareFileDecryptor;

public class ChatDiffFragment extends Fragment {
    private static final String ARG_SC_ID = "sc_id";
    private static final int MAX_DIFF_FILES = 10;
    private static final int MAX_DIFF_ROWS = 360;

    private String scId;
    private TextView textDiffSummary;
    private LinearLayout diffFilesContainer;
    private LinearLayout globalActions;
    /** Invalidates in-flight background diff computations when a newer refresh starts. */
    private int refreshGeneration = 0;

    public static ChatDiffFragment newInstance(String scId) {
        ChatDiffFragment fragment = new ChatDiffFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SC_ID, scId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        scId = args != null ? args.getString(ARG_SC_ID) : null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat_diffs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        textDiffSummary = view.findViewById(R.id.text_diff_summary);
        diffFilesContainer = view.findViewById(R.id.layout_diff_files);
        globalActions = view.findViewById(R.id.layout_diff_global_actions);
        View acceptAll = view.findViewById(R.id.button_accept_all_diffs);
        View rejectAll = view.findViewById(R.id.button_reject_all_diffs);
        if (acceptAll != null) {
            acceptAll.setOnClickListener(v -> acceptAllChanges());
        }
        if (rejectAll != null) {
            rejectAll.setOnClickListener(v -> confirmRejectAllChanges());
        }
        refreshDiffs();
    }

    @Override
    public void onDestroyView() {
        textDiffSummary = null;
        diffFilesContainer = null;
        globalActions = null;
        super.onDestroyView();
    }

    public void refreshDiffs() {
        if (!isAdded() || textDiffSummary == null || diffFilesContainer == null) {
            return;
        }

        Map<String, FileChangeTracker.FileChange> allChanges = FileChangeTracker.getAllRecentChanges(scId);
        List<FileChangeTracker.FileChange> changes = new ArrayList<>(allChanges.values());
        changes.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        int count = changes.size();
        textDiffSummary.setText(getString(R.string.chat_diff_summary, count));
        if (globalActions != null) {
            globalActions.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        }

        if (count <= 0) {
            diffFilesContainer.removeAllViews();
            diffFilesContainer.addView(makeEmptyView());
            return;
        }

        // The LCS diff is O(n·m) and allocates a large matrix — compute it OFF
        // the main thread (previously it ran twice per file on the UI thread,
        // causing visible jank), then render the precomputed results.
        final int generation = ++refreshGeneration;
        final List<FileChangeTracker.FileChange> toRender =
                new ArrayList<>(changes.subList(0, Math.min(count, MAX_DIFF_FILES)));
        new Thread(() -> {
            final List<List<VoidPortDiffService.ComputedDiff>> computed = new ArrayList<>();
            for (FileChangeTracker.FileChange change : toRender) {
                computed.add(VoidPortDiffService.findDiffs(change.beforeContent, change.afterContent));
            }
            LinearLayout container = diffFilesContainer;
            if (container == null) {
                return;
            }
            container.post(() -> {
                if (!isAdded() || diffFilesContainer == null || generation != refreshGeneration) {
                    return;
                }
                diffFilesContainer.removeAllViews();
                for (int i = 0; i < toRender.size(); i++) {
                    diffFilesContainer.addView(makeFileDiffView(toRender.get(i), computed.get(i)));
                }
            });
        }, "chat-diff-worker").start();
    }

    private View makeEmptyView() {
        TextView empty = new TextView(requireContext());
        empty.setText(R.string.chat_diff_empty);
        empty.setTextColor(color(R.color.chat_text_secondary));
        empty.setTextSize(13f);
        empty.setPadding(dp(10), dp(12), dp(10), dp(12));
        return empty;
    }

    private View makeFileDiffView(FileChangeTracker.FileChange change,
                                  List<VoidPortDiffService.ComputedDiff> diffs) {
        LinearLayout fileBlock = new LinearLayout(requireContext());
        fileBlock.setOrientation(LinearLayout.VERTICAL);
        fileBlock.setBackground(makeRoundedBackground(color(R.color.chat_diff_background), color(R.color.chat_border)));

        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        blockParams.setMargins(0, 0, 0, dp(12));
        fileBlock.setLayoutParams(blockParams);

        VoidPortDiffService.DiffStats stats = VoidPortDiffService.statsOf(diffs);
        TextView header = makeHeader(change.filePath, stats);
        fileBlock.addView(header);
        fileBlock.addView(makeActionsRow(change));

        HorizontalScrollView horizontalScrollView = new HorizontalScrollView(requireContext());
        horizontalScrollView.setFillViewport(true);
        horizontalScrollView.setHorizontalScrollBarEnabled(true);

        LinearLayout codeRows = new LinearLayout(requireContext());
        codeRows.setOrientation(LinearLayout.VERTICAL);
        horizontalScrollView.addView(codeRows, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT
        ));

        appendDiffRows(codeRows, diffs);
        fileBlock.addView(horizontalScrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        return fileBlock;
    }

    private TextView makeHeader(String filePath, VoidPortDiffService.DiffStats stats) {
        TextView header = makeCodeText();
        header.setText(String.format(Locale.US, "%s   +%d -%d", filePath, stats.added, stats.removed));
        header.setTextColor(color(R.color.chat_diff_line_text));
        header.setTextSize(12f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setBackgroundColor(color(R.color.chat_diff_header));
        header.setPadding(dp(10), dp(8), dp(10), dp(8));
        return header;
    }

    private View makeActionsRow(FileChangeTracker.FileChange change) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(8), dp(7), dp(8), dp(7));
        row.setBackgroundColor(color(R.color.chat_diff_background));

        TextView open = makeActionButton(R.string.chat_diff_action_open);
        open.setOnClickListener(v -> openFilePreview(change.filePath));
        row.addView(open);

        TextView accept = makeActionButton(R.string.chat_diff_action_accept);
        accept.setOnClickListener(v -> acceptChange(change.filePath));
        row.addView(accept);

        TextView reject = makeActionButton(R.string.chat_diff_action_reject);
        reject.setTextColor(color(R.color.chat_error));
        reject.setOnClickListener(v -> confirmRejectChange(change.filePath));
        row.addView(reject);

        return row;
    }

    private TextView makeActionButton(int textRes) {
        TextView button = new TextView(requireContext());
        button.setText(textRes);
        button.setTextSize(12f);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(color(R.color.chat_accent));
        button.setGravity(android.view.Gravity.CENTER);
        button.setPadding(dp(10), dp(5), dp(10), dp(5));
        button.setBackground(makeRoundedBackground(color(R.color.chat_accent_soft), color(R.color.chat_border)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, dp(8), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void openFilePreview(String filePath) {
        String content = SketchwareFileDecryptor.decryptFile(scId, filePath);
        if (content == null) {
            Toast.makeText(requireContext(), R.string.chat_diff_open_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        String preview = content.length() > 12000
                ? content.substring(0, 12000) + "\n\n" + getString(R.string.chat_diff_content_truncated)
                : content;
        new AlertDialog.Builder(requireContext())
                .setTitle(filePath)
                .setMessage(preview)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void acceptChange(String filePath) {
        boolean accepted = FileChangeTracker.acceptChange(scId, filePath);
        Toast.makeText(requireContext(),
                accepted ? R.string.chat_diff_accept_success : R.string.chat_diff_accept_missing,
                Toast.LENGTH_SHORT).show();
        notifyHostChanged();
    }

    private void confirmRejectChange(String filePath) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.chat_diff_reject_confirm_title)
                .setMessage(getString(R.string.chat_diff_reject_confirm_message, filePath))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.chat_diff_action_reject, (dialog, which) -> rejectChange(filePath))
                .show();
    }

    private void rejectChange(String filePath) {
        boolean rejected = FileChangeTracker.rejectChange(scId, filePath);
        Toast.makeText(requireContext(),
                rejected ? R.string.chat_diff_reject_success : R.string.chat_diff_reject_failed,
                Toast.LENGTH_SHORT).show();
        notifyHostChanged();
    }

    private void acceptAllChanges() {
        Map<String, FileChangeTracker.FileChange> allChanges = FileChangeTracker.getAllRecentChanges(scId);
        int accepted = 0;
        for (String filePath : new ArrayList<>(allChanges.keySet())) {
            if (FileChangeTracker.acceptChange(scId, filePath)) {
                accepted++;
            }
        }
        Toast.makeText(requireContext(),
                getString(R.string.chat_diff_accept_all_success, accepted),
                Toast.LENGTH_SHORT).show();
        notifyHostChanged();
    }

    private void confirmRejectAllChanges() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.chat_diff_reject_all_confirm_title)
                .setMessage(R.string.chat_diff_reject_all_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.chat_diff_action_reject_all, (dialog, which) -> rejectAllChanges())
                .show();
    }

    private void rejectAllChanges() {
        Map<String, FileChangeTracker.FileChange> allChanges = FileChangeTracker.getAllRecentChanges(scId);
        int rejected = 0;
        for (String filePath : new ArrayList<>(allChanges.keySet())) {
            if (FileChangeTracker.rejectChange(scId, filePath)) {
                rejected++;
            }
        }
        Toast.makeText(requireContext(),
                getString(R.string.chat_diff_reject_all_success, rejected),
                Toast.LENGTH_SHORT).show();
        notifyHostChanged();
    }

    private void notifyHostChanged() {
        refreshDiffs();
        if (getActivity() instanceof ChatActivity) {
            ((ChatActivity) getActivity()).updateChangedFilesSummary();
        }
    }

    private void appendDiffRows(LinearLayout codeRows, List<VoidPortDiffService.ComputedDiff> diffs) {
        if (diffs.isEmpty()) {
            codeRows.addView(makeDiffRow("", "", " ", "No changes", R.color.chat_diff_background));
            return;
        }

        int rows = 0;
        for (VoidPortDiffService.ComputedDiff diff : diffs) {
            if (rows >= MAX_DIFF_ROWS) {
                codeRows.addView(makeDiffRow("", "", "...", "diff truncated", R.color.chat_diff_hunk));
                return;
            }
            String hunk = String.format(Locale.US, "@@ -%d,%d +%d,%d @@ %s",
                    diff.originalStartLine,
                    diff.removedLines(),
                    diff.startLine,
                    diff.addedLines(),
                    diff.type);
            codeRows.addView(makeDiffRow("", "", "", hunk, R.color.chat_diff_hunk));
            rows++;

            int oldLine = diff.originalStartLine;
            for (String line : splitLines(diff.originalCode)) {
                if (rows >= MAX_DIFF_ROWS) {
                    codeRows.addView(makeDiffRow("", "", "...", "diff truncated", R.color.chat_diff_hunk));
                    return;
                }
                codeRows.addView(makeDiffRow(String.valueOf(oldLine), "", "-", line, R.color.chat_diff_removed));
                oldLine++;
                rows++;
            }

            int newLine = diff.startLine;
            for (String line : splitLines(diff.code)) {
                if (rows >= MAX_DIFF_ROWS) {
                    codeRows.addView(makeDiffRow("", "", "...", "diff truncated", R.color.chat_diff_hunk));
                    return;
                }
                codeRows.addView(makeDiffRow("", String.valueOf(newLine), "+", line, R.color.chat_diff_added));
                newLine++;
                rows++;
            }
        }
    }

    private TextView makeDiffRow(String oldLine, String newLine, String marker, String code, int backgroundColorRes) {
        TextView row = makeCodeText();
        row.setText(String.format(Locale.US, "%4s %4s  %-3s %s",
                oldLine == null ? "" : oldLine,
                newLine == null ? "" : newLine,
                marker == null ? "" : marker,
                code == null ? "" : code));
        row.setTextColor(color(R.color.chat_diff_line_text));
        row.setBackgroundColor(color(backgroundColorRes));
        row.setPadding(dp(8), dp(3), dp(10), dp(3));
        row.setMinWidth(getResources().getDisplayMetrics().widthPixels - dp(24));
        row.setSingleLine(false);
        return row;
    }

    private TextView makeCodeText() {
        TextView textView = new TextView(requireContext());
        textView.setTypeface(Typeface.MONOSPACE);
        textView.setTextSize(11f);
        textView.setIncludeFontPadding(false);
        return textView;
    }

    private String[] splitLines(String value) {
        if (value == null || value.isEmpty()) {
            return new String[0];
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        if (lines.length > 0 && lines[lines.length - 1].isEmpty()) {
            String[] trimmed = new String[lines.length - 1];
            System.arraycopy(lines, 0, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return lines;
    }

    private GradientDrawable makeRoundedBackground(int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setStroke(dp(1), strokeColor);
        drawable.setCornerRadius(dp(6));
        return drawable;
    }

    private int color(int colorRes) {
        return ContextCompat.getColor(requireContext(), colorRes);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

package pro.sketchware.chat;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pro.sketchware.R;
import pro.sketchware.chat.port.VoidPortDiffService;
import pro.sketchware.chat.FileChangeTracker;
import pro.sketchware.chat.ProjectPathResolver;

/**
 * "Files changed" review surface: a dense, collapsed-by-default list of the
 * files the agent already changed, with the full diff opened per file on tap.
 *
 * <p>Pure presentation refactor: the data model
 * ({@link FileChangeTracker.FileChange}), the actions (Open preview, Accept,
 * Reject) and the mutation semantics (accept never writes; revert restores
 * through the workspace filesystem bound to the change's project) are exactly
 * the ones this fragment always used.</p>
 */
public class ChatDiffFragment extends Fragment {
    private static final String ARG_SC_ID = "sc_id";
    private static final int MAX_DIFF_FILES = 10;
    private static final int MAX_DIFF_ROWS = 360;

    private String scId;
    private TextView textDiffSummary;
    private View diffBulkActions;
    private RecyclerView recyclerFiles;
    private View emptyView;
    private ChangedFilesAdapter adapter;
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
        diffBulkActions = view.findViewById(R.id.layout_diff_bulk_actions);
        recyclerFiles = view.findViewById(R.id.recycler_diff_files);
        emptyView = view.findViewById(R.id.text_diff_empty);
        view.findViewById(R.id.btn_diff_accept_all).setOnClickListener(v -> acceptAllChanges());
        view.findViewById(R.id.btn_diff_discard_all).setOnClickListener(v -> confirmDiscardAllChanges());

        adapter = new ChangedFilesAdapter();
        recyclerFiles.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerFiles.setAdapter(adapter);
        refreshDiffs();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshDiffs();
    }

    @Override
    public void onDestroyView() {
        textDiffSummary = null;
        diffBulkActions = null;
        if (recyclerFiles != null) {
            recyclerFiles.setAdapter(null);
            recyclerFiles = null;
        }
        adapter = null;
        emptyView = null;
        super.onDestroyView();
    }

    public void refreshDiffs() {
        if (!isAdded() || adapter == null) {
            return;
        }

        Map<String, FileChangeTracker.FileChange> allChanges = FileChangeTracker.getAllRecentChanges(scId);
        List<FileChangeTracker.FileChange> changes = new ArrayList<>(allChanges.values());
        changes.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        int count = changes.size();
        textDiffSummary.setText(getString(R.string.chat_diff_files_changed, count));
        if (diffBulkActions != null) {
            diffBulkActions.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        }
        emptyView.setVisibility(count > 0 ? View.GONE : View.VISIBLE);
        recyclerFiles.setVisibility(count > 0 ? View.VISIBLE : View.GONE);

        // The LCS diff is O(n·m) — compute it OFF the main thread and only for
        // rows the user actually expands. Collapsed rows render instantly.
        final int generation = ++refreshGeneration;
        final List<FileChangeTracker.FileChange> toRender =
                new ArrayList<>(changes.subList(0, Math.min(count, MAX_DIFF_FILES)));
        adapter.submit(toRender);
    }

    private void notifyHostChanged() {
        refreshDiffs();
        if (getActivity() instanceof ChatActivity) {
            ((ChatActivity) getActivity()).updateChangedFilesSummary();
        }
    }

    // ==================================================================
    // Adapter: one compact row per file, diff computed lazily on expand.
    // ==================================================================

    private final class ChangedFilesAdapter extends RecyclerView.Adapter<FileRowHolder> {

        interface OnChangeActionListener {
            void onOpen(FileChangeTracker.FileChange change);

            void onAccept(FileChangeTracker.FileChange change);

            void onReject(FileChangeTracker.FileChange change);
        }

        private final List<FileChangeTracker.FileChange> items = new ArrayList<>();
        /** Row position currently expanded; collapsed-by-default state. */
        private int expandedPosition = RecyclerView.NO_POSITION;
        /** Diff rows per position, computed once on first expand. */
        private final Map<Integer, List<VoidPortDiffService.ComputedDiff>> diffCache = new HashMap<>();
        /** Recycled the expanded row must show while its diff is still computing. */
        private final List<VoidPortDiffService.ComputedDiff> pendingDiffs = new ArrayList<>();

        void submit(List<FileChangeTracker.FileChange> changes) {
            items.clear();
            items.addAll(changes);
            expandedPosition = RecyclerView.NO_POSITION;
            diffCache.clear();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public FileRowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = getLayoutInflater().inflate(R.layout.item_chat_diff_file, parent, false);
            return new FileRowHolder(row, this);
        }

        @Override
        public void onBindViewHolder(@NonNull FileRowHolder holder, int position) {
            holder.bind(position);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private void toggle(int position) {
            if (expandedPosition == position) {
                expandedPosition = RecyclerView.NO_POSITION;
                notifyItemChanged(position);
                return;
            }
            int previous = expandedPosition;
            expandedPosition = position;
            if (previous != RecyclerView.NO_POSITION) {
                notifyItemChanged(previous);
            }
            if (!diffCache.containsKey(position)) {
                computeDiffsAsync(position);
            }
            notifyItemChanged(position);
        }

        private void computeDiffsAsync(int position) {
            // Bug fix: this used to do `++refreshGeneration` — bumping a
            // SHARED counter on every call. Once bind() started triggering
            // this eagerly for every visible row (not just the one row the
            // user taps to expand), several rows compute concurrently, and
            // each new row's call invalidated every other in-flight row's
            // result via this same counter (`generation != refreshGeneration`
            // below would then be true for the earlier rows). Their real
            // result was silently dropped, leaving diffCache stuck on the
            // empty `pendingDiffs` placeholder forever — which rendered as
            // "Sem alterações" once expanded, even for a genuinely modified
            // file, and never retried (the cache already "contained" that
            // position). refreshGeneration must only change on a full list
            // reload (see refreshDiffs()/submit()); a single row's compute
            // must only READ the current epoch, never bump it itself.
            final int generation = refreshGeneration;
            diffCache.put(position, pendingDiffs); // placeholder while computing
            final FileChangeTracker.FileChange change = items.get(position);
            new Thread(() -> {
                final List<VoidPortDiffService.ComputedDiff> computed =
                        VoidPortDiffService.findDiffs(change.beforeContent, change.afterContent);
                if (getView() == null || generation != refreshGeneration) {
                    return; // stale refresh or the fragment went away
                }
                RecyclerView recycler = recyclerFiles;
                if (recycler == null) {
                    return;
                }
                recycler.post(() -> {
                    if (generation != refreshGeneration || diffCache.get(position) != pendingDiffs) {
                        return; // superseded while computing
                    }
                    diffCache.put(position, computed);
                    FileRowHolder live = (FileRowHolder) recycler.findViewHolderForAdapterPosition(position);
                    if (live != null) {
                        // Refresh the collapsed +/- stats regardless of expanded
                        // state (this is what makes them show up without the
                        // user having to tap the row open).
                        live.bindStats(items.get(position));
                        if (expandedPosition == position) {
                            live.renderDiffRows(computed);
                        }
                    }
                });
            }, "chat-diff-worker").start();
        }
        private String displayName(String filePath) {
            if (filePath == null || filePath.isEmpty()) {
                return "";
            }
            int slash = filePath.lastIndexOf('/');
            return slash >= 0 ? filePath.substring(slash + 1) : filePath;
        }
    }

    private final class FileRowHolder extends RecyclerView.ViewHolder {
            private final ChangedFilesAdapter adapter;
            private final View row;
            private final TextView badge;
            private final TextView fileTypeBadge;
            private final TextView name;
            private final TextView path;
            private final TextView stats;
            private final TextView chevron;
            private final View details;
            private final LinearLayout codeRows;
            private final HorizontalScrollView codeScroll;
            private final TextView openAction;
            private final TextView acceptAction;
            private final TextView rejectAction;

            FileRowHolder(@NonNull View itemView, ChangedFilesAdapter adapter) {
                super(itemView);
                this.adapter = adapter;
                row = itemView;
                badge = itemView.findViewById(R.id.text_file_badge);
                fileTypeBadge = itemView.findViewById(R.id.text_file_type_badge);
                name = itemView.findViewById(R.id.text_file_name);
                path = itemView.findViewById(R.id.text_file_path);
                stats = itemView.findViewById(R.id.text_file_stats);
                chevron = itemView.findViewById(R.id.text_file_chevron);
                details = itemView.findViewById(R.id.layout_file_details);
                codeRows = itemView.findViewById(R.id.layout_diff_code_rows);
                codeScroll = itemView.findViewById(R.id.scroll_diff_code);
                openAction = itemView.findViewById(R.id.action_file_open);
                acceptAction = itemView.findViewById(R.id.action_file_accept);
                rejectAction = itemView.findViewById(R.id.action_file_reject);

                // Keep these alive after onDestroyView nulls the fields.
                View.OnClickListener toggleListener = v -> {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        adapter.toggle(position);
                    }
                };
                row.setOnClickListener(toggleListener);
                openAction.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        onOpen(adapter.items.get(position));
                    }
                });
                acceptAction.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        onAccept(adapter.items.get(position));
                    }
                });
                rejectAction.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        confirmReject(adapter.items.get(position));
                    }
                });
            }

            void bind(int position) {
                FileChangeTracker.FileChange change = adapter.items.get(position);
                boolean expanded = adapter.expandedPosition == position;

                name.setText(adapter.displayName(change.filePath));
                path.setText(change.filePath);
                bindBadge(change);
                bindFileTypeIcon(change);
                bindStats(change);
                chevron.setText(expanded ? "▾" : "▸");
                chevron.setContentDescription(getString(expanded
                        ? R.string.chat_diff_collapse_cd : R.string.chat_diff_expand_cd));
                row.setContentDescription(getString(R.string.chat_diff_file_row_cd,
                        change.filePath));

                // Item: the +/- stats used to only be computed once the row was
                // expanded, so a collapsed file row never showed real numbers.
                // Kick the diff computation off as soon as the row is bound
                // (i.e. as soon as it's visible), not only on tap, so the
                // collapsed line can show real +N -N as soon as it's ready —
                // same lazy background computation, just triggered earlier.
                if (!adapter.diffCache.containsKey(position)) {
                    adapter.computeDiffsAsync(position);
                }

                details.setVisibility(expanded ? View.VISIBLE : View.GONE);
                codeRows.removeAllViews();
                if (expanded) {
                    List<VoidPortDiffService.ComputedDiff> computed = adapter.diffCache.get(position);
                    if (computed == adapter.pendingDiffs) {
                        renderDiffRows(adapter.pendingDiffs); // "computing…" placeholder
                    } else if (computed != null) {
                        renderDiffRows(computed);
                    } else {
                        // First inflate raced the async compute: stats stay
                        // pending and fill in when the worker posts back.
                        renderDiffRows(adapter.pendingDiffs);
                    }
                }
            }

            private void bindFileTypeIcon(FileChangeTracker.FileChange change) {
                FileTypeBadge.Info info = FileTypeBadge.forFileName(change.filePath);
                fileTypeBadge.setText(info.label);
                // Each recycled row shares the inflated background drawable by
                // default; mutate() before tinting so coloring one badge does
                // not repaint every other row using the same drawable instance.
                fileTypeBadge.getBackground().mutate();
                fileTypeBadge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(info.color));
            }

            private void bindBadge(FileChangeTracker.FileChange change) {
                String label;
                int color;
                String cd;
                if (change.existedBefore && (change.afterContent == null
                        || change.afterContent.isEmpty())) {
                    label = "D";
                    color = color(R.color.chat_error);
                    cd = getString(R.string.chat_diff_kind_deleted);
                } else if (!change.existedBefore) {
                    label = "A";
                    color = color(R.color.chat_diff_added_text);
                    cd = getString(R.string.chat_diff_kind_added);
                } else {
                    label = "M";
                    color = color(R.color.chat_diff_modified_text);
                    cd = getString(R.string.chat_diff_kind_modified);
                }
                badge.setText(label);
                badge.setTextColor(color);
                badge.setContentDescription(cd);
            }

            private void bindStats(FileChangeTracker.FileChange change) {
                // Collapsed rows have not computed their diff yet (lazy): the
                // stats fill in when the row is expanded and, once cached,
                // stay correct across rebinds. Until then show the signal
                // from the change kind itself.
                int added = 0;
                int removed = 0;
                List<VoidPortDiffService.ComputedDiff> computed = adapter.diffCache.get(
                        getBindingAdapterPosition());
                if (computed != null && computed != adapter.pendingDiffs) {
                    VoidPortDiffService.DiffStats s = VoidPortDiffService.statsOf(computed);
                    added = s.added;
                    removed = s.removed;
                }
                if (added == 0 && removed == 0) {
                    if (change.beforeContent == null) {
                        stats.setText("");
                        stats.setContentDescription(null);
                        return;
                    }
                    stats.setText(change.afterContent == null || change.afterContent.isEmpty()
                            ? "−" : "~");
                    stats.setTextColor(color(change.afterContent == null
                            || change.afterContent.isEmpty()
                            ? R.color.chat_diff_removed_text
                            : R.color.chat_diff_modified_text));
                    return;
                }
                stats.setText(String.format(Locale.US, "+%d −%d", added, removed));
                stats.setTextColor(added >= removed
                        ? color(R.color.chat_diff_added_text)
                        : color(R.color.chat_diff_removed_text));
            }

            private void confirmReject(FileChangeTracker.FileChange change) {
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.chat_diff_reject_confirm_title)
                        .setMessage(getString(R.string.chat_diff_reject_confirm_message, change.filePath))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.chat_diff_action_reject, (dialog, which) ->
                                onReject(change))
                        .show();
            }

            private void renderDiffRows(List<VoidPortDiffService.ComputedDiff> computed) {
                codeRows.removeAllViews();
                if (computed.isEmpty()) {
                    codeRows.addView(makeDiffRow("", "", " ",
                            getString(R.string.chat_diff_no_changes), R.color.chat_diff_background));
                    return;
                }
                int rows = 0;
                for (VoidPortDiffService.ComputedDiff diff : computed) {
                    if (rows >= MAX_DIFF_ROWS) {
                        codeRows.addView(makeDiffRow("", "", "...",
                                getString(R.string.chat_diff_truncated), R.color.chat_diff_hunk));
                        return;
                    }
                    String hunk = String.format(Locale.US, "@@ -%d,%d +%d,%d @@ %s",
                            diff.originalStartLine, diff.removedLines(),
                            diff.startLine, diff.addedLines(), diff.type);
                    codeRows.addView(makeDiffRow("", "", "", hunk, R.color.chat_diff_hunk));
                    rows++;

                    int oldLine = diff.originalStartLine;
                    for (String line : splitLines(diff.originalCode)) {
                        if (rows >= MAX_DIFF_ROWS) {
                            codeRows.addView(makeDiffRow("", "", "...",
                                    getString(R.string.chat_diff_truncated), R.color.chat_diff_hunk));
                            return;
                        }
                        codeRows.addView(makeDiffRow(String.valueOf(oldLine), "", "-",
                                line, R.color.chat_diff_removed));
                        oldLine++;
                        rows++;
                    }

                    int newLine = diff.startLine;
                    for (String line : splitLines(diff.code)) {
                        if (rows >= MAX_DIFF_ROWS) {
                            codeRows.addView(makeDiffRow("", "", "...",
                                    getString(R.string.chat_diff_truncated), R.color.chat_diff_hunk));
                            return;
                        }
                        codeRows.addView(makeDiffRow("", String.valueOf(newLine), "+",
                                line, R.color.chat_diff_added));
                        newLine++;
                        rows++;
                    }
                }
            }

            private TextView makeDiffRow(String oldLine, String newLine, String marker,
                                         String code, int backgroundColorRes) {
                TextView textView = new TextView(requireContext());
                textView.setTypeface(Typeface.MONOSPACE);
                textView.setTextSize(11f);
                textView.setIncludeFontPadding(false);
                textView.setText(String.format(Locale.US, "%4s %4s  %-3s %s",
                        oldLine == null ? "" : oldLine,
                        newLine == null ? "" : newLine,
                        marker == null ? "" : marker,
                        code == null ? "" : code));
                textView.setTextColor(color(R.color.chat_diff_line_text));
                textView.setBackgroundColor(color(backgroundColorRes));
                textView.setPadding(dp(8), dp(3), dp(10), dp(3));
                textView.setMinWidth(getResources().getDisplayMetrics().widthPixels - dp(24));
                textView.setSingleLine(false);
                return textView;
            }
        }

    // ==================================================================
    // Actions — same semantics as before, only re-wired to the adapter.
    // ==================================================================

    private void onOpen(FileChangeTracker.FileChange change) {
        openFilePreview(change.filePath);
    }

    private void onAccept(FileChangeTracker.FileChange change) {
        boolean accepted = FileChangeTracker.acceptChange(scId, change.filePath);
        Toast.makeText(requireContext(),
                accepted ? R.string.chat_diff_accept_success : R.string.chat_diff_accept_missing,
                Toast.LENGTH_SHORT).show();
        notifyHostChanged();
    }

    private void onReject(FileChangeTracker.FileChange change) {
        boolean reverted = FileChangeTracker.rejectChange(scId, change.filePath);
        Toast.makeText(requireContext(),
                reverted ? R.string.chat_diff_reject_success : R.string.chat_diff_reject_failed,
                Toast.LENGTH_SHORT).show();
        notifyHostChanged();
    }

    private void acceptAllChanges() {
        List<String> filePaths = new ArrayList<>(FileChangeTracker.getAllRecentChanges(scId).keySet());
        int accepted = 0;
        for (String filePath : filePaths) {
            if (FileChangeTracker.acceptChange(scId, filePath)) {
                accepted++;
            }
        }
        Toast.makeText(requireContext(),
                getString(R.string.chat_diff_bulk_accept_result, accepted),
                Toast.LENGTH_SHORT).show();
        notifyHostChanged();
    }

    private void confirmDiscardAllChanges() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.chat_diff_bulk_discard_confirm_title)
                .setMessage(R.string.chat_diff_bulk_discard_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.chat_diff_action_discard_all,
                        (dialog, which) -> discardAllChanges())
                .show();
    }

    private void discardAllChanges() {
        List<String> filePaths = new ArrayList<>(FileChangeTracker.getAllRecentChanges(scId).keySet());
        int discarded = 0;
        for (String filePath : filePaths) {
            if (FileChangeTracker.rejectChange(scId, filePath)) {
                discarded++;
            }
        }
        Toast.makeText(requireContext(),
                getString(R.string.chat_diff_bulk_discard_result, discarded),
                Toast.LENGTH_SHORT).show();
        notifyHostChanged();
    }

    private void openFilePreview(String filePath) {
        // The diff shows the current on-disk state, which lives in the active
        // workspace. Resolve through WorkspaceFileSystem first; the legacy
        // resolver stays as a fallback for sessions without an open workspace.
        String content = null;
        try {
            pro.sketchware.chat.workspace.WorkspaceFileSystem fs =
                    pro.sketchware.chat.workspace.WorkspaceManager.getActiveFileSystem();
            String norm = pro.sketchware.chat.workspace.WorkspacePath.normalize(filePath);
            if (fs != null && fs.exists(norm) && !fs.isDirectory(norm)) {
                content = fs.readText(norm);
            }
        } catch (Exception ignored) {
        }
        if (content == null) {
            try {
                ProjectPathResolver.ResolvedPath resolved = ProjectPathResolver.resolveForRead(scId, filePath);
                if (resolved != null && resolved.getFile().exists()) {
                    content = new String(java.nio.file.Files.readAllBytes(resolved.getFile().toPath()), java.nio.charset.StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
            }
        }
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

    // ==================================================================
    // shared helpers
    // ==================================================================

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

    private int color(int colorRes) {
        return ContextCompat.getColor(requireContext(), colorRes);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

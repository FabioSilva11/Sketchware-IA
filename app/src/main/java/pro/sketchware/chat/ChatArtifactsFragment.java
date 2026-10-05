package pro.sketchware.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.R;
import pro.sketchware.chat.port.VoidPortDiffService;
import pro.sketchware.chat.FileChangeTracker;

public class ChatArtifactsFragment extends Fragment {
    private static final String ARG_SC_ID = "sc_id";
    private static final long REFRESH_DEBOUNCE_MS = 180L;
    private String scId;
    private List<ChatMessage> messages;
    private LinearLayout container;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService diffExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "chat-artifact-diffs");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private int refreshGeneration;
    private boolean refreshPending;
    private boolean fragmentDestroyed;
    private final Runnable debouncedRefresh = this::startArtifactRefresh;

    public static ChatArtifactsFragment newInstance(String scId) {
        ChatArtifactsFragment fragment = new ChatArtifactsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SC_ID, scId);
        fragment.setArguments(args);
        return fragment;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
        refreshArtifacts();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        scId = args == null ? null : args.getString(ARG_SC_ID);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(color(R.color.chat_surface));
        container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(12), dp(12), dp(12), dp(18));
        scrollView.addView(container, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        bindMessagesFromHost();
        refreshArtifacts();
        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        bindMessagesFromHost();
        refreshArtifacts();
    }

    @Override
    public void onPause() {
        refreshPending = true;
        refreshGeneration++;
        mainHandler.removeCallbacks(debouncedRefresh);
        super.onPause();
    }

    private void bindMessagesFromHost() {
        if (getActivity() instanceof ChatActivity) {
            messages = ((ChatActivity) getActivity()).getMessagesForFragments();
        }
    }

    @Override
    public void onDestroyView() {
        refreshGeneration++;
        refreshPending = true;
        mainHandler.removeCallbacks(debouncedRefresh);
        container = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        fragmentDestroyed = true;
        refreshGeneration++;
        mainHandler.removeCallbacksAndMessages(null);
        diffExecutor.shutdownNow();
        super.onDestroy();
    }

    public void refreshArtifacts() {
        refreshPending = true;
        // O ViewPager mantém esta view criada fora da tela. Não execute diffs ou
        // carregue imagens até a aba Artefatos ser a página realmente visível.
        if (container == null || !isAdded() || !isResumed() || fragmentDestroyed) {
            return;
        }
        mainHandler.removeCallbacks(debouncedRefresh);
        mainHandler.postDelayed(debouncedRefresh, REFRESH_DEBOUNCE_MS);
    }

    private void startArtifactRefresh() {
        if (container == null || !isAdded() || !isResumed() || fragmentDestroyed) {
            refreshPending = true;
            return;
        }
        refreshPending = false;
        final int generation = ++refreshGeneration;
        final List<ChatMessage> messageSnapshot = messages == null
                ? new ArrayList<>() : new ArrayList<>(messages);
        final String projectId = scId;
        diffExecutor.execute(() -> {
            List<FileArtifactRow> fileRows = computeFileRows(projectId);
            mainHandler.post(() -> renderArtifactSnapshot(generation, fileRows, messageSnapshot));
        });
    }

    private List<FileArtifactRow> computeFileRows(String projectId) {
        Map<String, FileChangeTracker.FileChange> allChanges =
                FileChangeTracker.getAllRecentChanges(projectId);
        List<FileChangeTracker.FileChange> changes = new ArrayList<>(allChanges.values());
        changes.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        List<FileArtifactRow> rows = new ArrayList<>();
        for (int i = 0; i < changes.size() && i < 12; i++) {
            if (Thread.currentThread().isInterrupted()) {
                return rows;
            }
            FileChangeTracker.FileChange change = changes.get(i);
            VoidPortDiffService.DiffStats stats =
                    VoidPortDiffService.stats(change.beforeContent, change.afterContent);
            rows.add(new FileArtifactRow(
                    change.filePath, stats.added, stats.removed, change.timestamp));
        }
        return rows;
    }

    private void renderArtifactSnapshot(int generation, List<FileArtifactRow> fileRows,
                                        List<ChatMessage> messageSnapshot) {
        if (generation != refreshGeneration || container == null || !isAdded()
                || !isResumed() || fragmentDestroyed) {
            return;
        }
        container.removeAllViews();
        addSectionTitle(R.string.chat_artifacts_files_title);
        renderChangedFiles(fileRows);
        addSectionTitle(R.string.chat_artifacts_images_title);
        renderImages(messageSnapshot);
        addSectionTitle(R.string.chat_artifacts_tools_title);
        renderToolSummary(messageSnapshot);
    }

    private void renderChangedFiles(List<FileArtifactRow> rows) {
        if (rows == null || rows.isEmpty()) {
            addTextCard(getString(R.string.chat_artifacts_no_files), "");
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.getDefault());
        for (FileArtifactRow row : rows) {
            String detail = getString(R.string.chat_artifacts_file_detail,
                    row.added,
                    row.removed,
                    format.format(new Date(row.timestamp)));
            addTextCard(row.filePath, detail);
        }
    }

    private void renderImages(List<ChatMessage> sourceMessages) {
        List<ChatReference> images = new ArrayList<>();
        if (sourceMessages != null) {
            for (ChatMessage message : sourceMessages) {
                if (message != null && message.isUser()) {
                    images.addAll(message.getImageReferences());
                }
            }
        }
        if (images.isEmpty()) {
            addTextCard(getString(R.string.chat_artifacts_no_images), "");
            return;
        }
        HorizontalScrollView scrollView = new HorizontalScrollView(requireContext());
        scrollView.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (ChatReference imageReference : images) {
            row.addView(makeImageArtifact(imageReference));
        }
        scrollView.addView(row);
        container.addView(scrollView, fullWidthParams(dp(10)));
    }

    private View makeImageArtifact(ChatReference reference) {
        Context context = requireContext();
        FrameLayout frame = new FrameLayout(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(104), dp(104));
        params.setMargins(0, 0, dp(10), 0);
        frame.setLayoutParams(params);
        frame.setPadding(dp(2), dp(2), dp(2), dp(2));
        frame.setBackground(rounded(color(R.color.chat_diff_background), color(R.color.chat_border)));

        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ChatImageThumbnailLoader.load(
                image,
                reference == null ? null : reference.getUri(),
                dp(104),
                R.drawable.kelivo_lucide_image);
        frame.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        return frame;
    }

    private void renderToolSummary(List<ChatMessage> sourceMessages) {
        ChatToolActivitySummary.Summary summary = ChatToolActivitySummary.summarize(sourceMessages);
        addTextCard(getString(R.string.chat_artifacts_tool_summary), summary.compactLabel(requireContext()));
        if (sourceMessages == null || summary.total() == 0) {
            return;
        }
        int rendered = 0;
        for (int i = sourceMessages.size() - 1; i >= 0 && rendered < 10; i--) {
            ChatMessage message = sourceMessages.get(i);
            if (message == null || !message.isTool()) {
                continue;
            }
            String name = ChatMessage.hasVisibleText(message.getToolName())
                    ? message.getToolName()
                    : getString(R.string.chat_tool_unknown);
            String status = ChatMessage.hasVisibleText(message.getStatus())
                    ? message.getStatus()
                    : (message.isToolError() ? getString(R.string.chat_tool_status_error) : getString(R.string.chat_tool_status_done));
            addTextCard(ChatToolActivitySummary.groupLabel(requireContext(), name) + " - " + name, status);
            rendered++;
        }
    }

    private void addSectionTitle(int stringRes) {
        TextView title = new TextView(requireContext());
        title.setText(stringRes);
        title.setTextColor(color(R.color.chat_text_primary));
        title.setTextSize(14f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(10), 0, dp(6));
        container.addView(title);
    }

    private void addTextCard(String titleText, String detailText) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(color(R.color.chat_diff_background), color(R.color.chat_border)));
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView title = new TextView(requireContext());
        title.setText(titleText);
        title.setTextColor(color(R.color.chat_text_primary));
        title.setTextSize(13f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(2);
        card.addView(title);

        if (ChatMessage.hasVisibleText(detailText)) {
            TextView detail = new TextView(requireContext());
            detail.setText(detailText);
            detail.setTextColor(color(R.color.chat_text_secondary));
            detail.setTextSize(12f);
            detail.setPadding(0, dp(4), 0, 0);
            card.addView(detail);
        }
        container.addView(card, fullWidthParams(dp(8)));
    }

    private static final class FileArtifactRow {
        final String filePath;
        final int added;
        final int removed;
        final long timestamp;

        FileArtifactRow(String filePath, int added, int removed, long timestamp) {
            this.filePath = filePath;
            this.added = added;
            this.removed = removed;
            this.timestamp = timestamp;
        }
    }

    private LinearLayout.LayoutParams fullWidthParams(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, bottomMargin);
        return params;
    }

    private GradientDrawable rounded(int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int color(int resId) {
        return ContextCompat.getColor(requireContext(), resId);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

package pro.sketchware.chat;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;

public class ChatPlanFragment extends Fragment {
    private static final String ARG_SC_ID = "sc_id";
    private static final long REFRESH_DEBOUNCE_MS = 120L;
    private String scId;
    private List<ChatMessage> messages;
    private LinearLayout container;
    private boolean processing;
    private String statusText = "";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable debouncedRefresh = this::renderPlanNow;

    public static ChatPlanFragment newInstance(String scId) {
        ChatPlanFragment fragment = new ChatPlanFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SC_ID, scId);
        fragment.setArguments(args);
        return fragment;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
        refreshPlan();
    }

    public void setRunState(boolean processing, String statusText) {
        this.processing = processing;
        this.statusText = statusText == null ? "" : statusText;
        refreshPlan();
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
        bindStateFromHost();
        refreshPlan();
        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        bindStateFromHost();
        refreshPlan();
    }

    private void bindStateFromHost() {
        if (getActivity() instanceof ChatActivity) {
            ChatActivity host = (ChatActivity) getActivity();
            messages = host.getMessagesForFragments();
            processing = host.isChatProcessingForFragments();
            statusText = host.getRunStatusForFragments();
        }
    }

    @Override
    public void onDestroyView() {
        mainHandler.removeCallbacks(debouncedRefresh);
        container = null;
        super.onDestroyView();
    }

    public void refreshPlan() {
        // O ViewPager mantém a view criada fora da tela. O estado continua sendo
        // atualizado pelos setters e será renderizado no onResume da aba Plano.
        if (container == null || !isAdded() || !isResumed()) {
            return;
        }
        mainHandler.removeCallbacks(debouncedRefresh);
        mainHandler.postDelayed(debouncedRefresh, REFRESH_DEBOUNCE_MS);
    }

    private void renderPlanNow() {
        if (container == null || !isAdded() || !isResumed()) {
            return;
        }
        container.removeAllViews();
        List<ChatPlanManager.Task> tasks = ChatPlanManager.buildPlan(
                requireContext(),
                scId,
                messages == null ? new ArrayList<>() : messages,
                processing,
                statusText
        );
        for (int i = 0; i < tasks.size(); i++) {
            container.addView(makeTaskCard(i + 1, tasks.get(i)), fullWidthParams(dp(10)));
        }
    }

    private View makeTaskCard(int index, ChatPlanManager.Task task) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(rounded(color(R.color.chat_diff_background), statusColor(task.status)));

        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView title = new TextView(requireContext());
        title.setText(index + ". " + task.title);
        title.setTextColor(color(R.color.chat_text_primary));
        title.setTextSize(14f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView chip = new TextView(requireContext());
        chip.setText(statusText(task.status));
        chip.setTextColor(statusTextColor(task.status));
        chip.setTextSize(11f);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setPadding(dp(8), dp(3), dp(8), dp(3));
        chip.setBackground(rounded(color(R.color.chat_background), statusColor(task.status)));
        header.addView(chip);
        card.addView(header);

        TextView detail = new TextView(requireContext());
        detail.setText(task.detail);
        detail.setTextColor(color(R.color.chat_text_secondary));
        detail.setTextSize(12f);
        detail.setPadding(0, dp(6), 0, 0);
        card.addView(detail);
        return card;
    }

    private String statusText(int status) {
        return switch (status) {
            case ChatPlanManager.STATUS_DONE -> getString(R.string.chat_plan_status_done);
            case ChatPlanManager.STATUS_RUNNING -> getString(R.string.chat_plan_status_running);
            case ChatPlanManager.STATUS_FAILED -> getString(R.string.chat_plan_status_failed);
            default -> getString(R.string.chat_plan_status_pending);
        };
    }

    private int statusColor(int status) {
        return switch (status) {
            case ChatPlanManager.STATUS_DONE -> color(R.color.chat_success);
            case ChatPlanManager.STATUS_RUNNING -> color(R.color.chat_accent);
            case ChatPlanManager.STATUS_FAILED -> color(R.color.chat_error);
            default -> color(R.color.chat_border);
        };
    }

    private int statusTextColor(int status) {
        if (status == ChatPlanManager.STATUS_PENDING) {
            return color(R.color.chat_text_secondary);
        }
        return status == ChatPlanManager.STATUS_FAILED
                ? color(R.color.chat_error)
                : color(R.color.chat_accent);
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

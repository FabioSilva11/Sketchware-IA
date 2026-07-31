package pro.sketchware.activities.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import io.noties.markwon.Markwon;
import pro.sketchware.R;
import pro.sketchware.utility.AdManager;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_BOT = 2;
    private static final int VIEW_TYPE_TOOL = 3;
    private static final int VIEW_TYPE_CHECKPOINT = 4;
    private static final int VIEW_TYPE_AWAITING = 5;
    private static final int VIEW_TYPE_INTERRUPTED_TOOL = 6;
    private static final int VIEW_TYPE_NATIVE_AD = 7;
    private static final int NATIVE_AD_INTERVAL = 6;

    private final List<ChatMessage> messages;
    private Markwon markwon;
    private MessageActionListener actionListener;
    private WeakReference<NativeAd> currentNativeAd = new WeakReference<>(null);
    private final AtomicBoolean nativeAdRequested = new AtomicBoolean(false);

    public interface MessageActionListener {
        void onRegenerate(int position);

        void onEdit(int position);

        void onSpeak(String text);

        void onTranslate(String text);

        void onDelete(int position);

        default void onReasoningVisibilityChanged(ChatMessage message) {
        }
    }

    public ChatMessageAdapter(Context context, List<ChatMessage> messages) {
        this.messages = messages;
        int botCount = 0;
        for (ChatMessage msg : messages) {
            if (isBotMessage(msg)) {
                botCount++;
            }
        }
        if (botCount >= NATIVE_AD_INTERVAL) {
            AdManager.preloadNativeAd(context);
        } else {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    () -> AdManager.preloadNativeAd(context), 800L);
        }
    }

    public void setMessageActionListener(MessageActionListener listener) {
        this.actionListener = listener;
    }

    private Markwon getMarkwon(Context context) {
        if (markwon == null) {
            markwon = Markwon.builder(context).build();
        }
        return markwon;
    }

    private boolean isBotMessage(ChatMessage msg) {
        if (msg == null) return false;
        if (msg.isInterruptedStreamingTool()) return false;
        if (msg.getType() == ChatMessage.TYPE_TOOL) return false;
        if (msg.isUser()) return false;
        if (msg.isCheckpoint()) return false;
        if (msg.isAwaitingUser()) return false;
        return true;
    }

    private int countTotalNativeAds() {
        int botCount = 0;
        int ads = 0;
        for (ChatMessage msg : messages) {
            if (isBotMessage(msg)) {
                botCount++;
                if (botCount == NATIVE_AD_INTERVAL) ads = 1;
            }
        }
        return ads;
    }

    public boolean isNativeAdPosition(int virtualPosition) {
        int botCount = 0;
        int virtualCursor = 0;
        for (int i = 0; i < messages.size(); i++) {
            if (virtualCursor == virtualPosition) {
                return false;
            }
            virtualCursor++;
            ChatMessage msg = messages.get(i);
            if (isBotMessage(msg)) {
                botCount++;
                if (botCount == NATIVE_AD_INTERVAL) {
                    if (virtualCursor == virtualPosition) {
                        return true;
                    }
                    virtualCursor++;
                }
            }
        }
        return false;
    }

    public int translateToMessagePosition(int virtualPosition) {
        int botCount = 0;
        int virtualCursor = 0;
        for (int i = 0; i < messages.size(); i++) {
            if (virtualCursor == virtualPosition) {
                return i;
            }
            virtualCursor++;
            ChatMessage msg = messages.get(i);
            if (isBotMessage(msg)) {
                botCount++;
                if (botCount == NATIVE_AD_INTERVAL) {
                    if (virtualCursor == virtualPosition) {
                        return -1;
                    }
                    virtualCursor++;
                }
            }
        }
        return -1;
    }

    @Override
    public int getItemViewType(int position) {
        if (isNativeAdPosition(position)) {
            return VIEW_TYPE_NATIVE_AD;
        }
        ChatMessage msg = messages.get(translateToMessagePosition(position));
        if (msg.isInterruptedStreamingTool()) return VIEW_TYPE_INTERRUPTED_TOOL;
        if (msg.getType() == ChatMessage.TYPE_TOOL) return VIEW_TYPE_TOOL;
        if (msg.isUser()) return VIEW_TYPE_USER;
        if (msg.isCheckpoint()) return VIEW_TYPE_CHECKPOINT;
        if (msg.isAwaitingUser()) return VIEW_TYPE_AWAITING;
        return VIEW_TYPE_BOT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEW_TYPE_NATIVE_AD:
                return new NativeAdViewHolder(inflater.inflate(R.layout.item_native_ad, parent, false));
            case VIEW_TYPE_TOOL:
                return new ToolViewHolder(inflater.inflate(R.layout.item_message_tool, parent, false));
            case VIEW_TYPE_USER:
                return new MessageViewHolder(inflater.inflate(R.layout.item_message_user, parent, false));
            case VIEW_TYPE_CHECKPOINT:
            case VIEW_TYPE_AWAITING:
            case VIEW_TYPE_INTERRUPTED_TOOL:
            default:
                return new MessageViewHolder(inflater.inflate(R.layout.item_message_bot, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof NativeAdViewHolder) {
            bindNativeAd((NativeAdViewHolder) holder);
            return;
        }

        ChatMessage message = messages.get(translateToMessagePosition(position));

        if (holder instanceof MessageViewHolder) {
            bindMessage((MessageViewHolder) holder, message);
        } else if (holder instanceof ToolViewHolder) {
            bindTool((ToolViewHolder) holder, message);
        }
    }

    private void bindNativeAd(@NonNull NativeAdViewHolder holder) {
        NativeAd cached = currentNativeAd.get();
        if (cached != null) {
            AdManager.populateNativeAdView(cached, holder.adView);
        } else {
            AdManager.consumeCachedNativeAd(new AdManager.NativeAdContainerBinder() {
                @Override
                public void bind(@NonNull NativeAd nativeAd) {
                    currentNativeAd = new WeakReference<>(nativeAd);
                    if (holder.adView != null && holder.itemView != null) {
                        holder.itemView.post(() ->
                                AdManager.populateNativeAdView(nativeAd, holder.adView));
                    }
                }

                @Override
                public Context getContext() {
                    return holder.itemView != null ? holder.itemView.getContext() : null;
                }
            }, new AdManager.NativeAdLoadCallback() {
                @Override
                public void onNativeAdLoaded(@NonNull NativeAd nativeAd) {
                    currentNativeAd = new WeakReference<>(nativeAd);
                    if (holder.adView != null && holder.itemView != null) {
                        holder.itemView.post(() ->
                                AdManager.populateNativeAdView(nativeAd, holder.adView));
                    }
                }
            });
        }

        if (nativeAdRequested.compareAndSet(false, true)) {
            Context ctx = holder.itemView != null ? holder.itemView.getContext() : null;
            if (ctx != null) {
                AdManager.preloadNativeAd(ctx, AdManager.NATIVE_AD_UNIT_ID,
                        new AdManager.NativeAdLoadCallback() {
                            @Override
                            public void onNativeAdLoaded(@NonNull NativeAd nativeAd) {
                                nativeAdRequested.set(false);
                            }

                            @Override
                            public void onNativeAdFailedToLoad(@NonNull com.google.android.gms.ads.LoadAdError error) {
                                nativeAdRequested.set(false);
                            }
                        });
            } else {
                nativeAdRequested.set(false);
            }
        }
    }

    private void bindMessage(@NonNull MessageViewHolder holder, @NonNull ChatMessage message) {
        String messageText = sanitizeText(message.getDisplayContent());
        String statusText = sanitizeText(message.getStatus());
        String reasoningText = sanitizeText(message.getReasoning());

        if (holder.textStatusChip != null) {
            holder.textStatusChip.setVisibility(View.GONE);
            if (message.isInterruptedStreamingTool()) {
                holder.textStatusChip.setVisibility(View.VISIBLE);
                holder.textStatusChip.setText(holder.itemView.getContext().getString(
                        R.string.chat_interrupted_streaming_tool,
                        sanitizeText(message.getToolName())));
            } else if (message.isCheckpoint()) {
                holder.textStatusChip.setVisibility(View.VISIBLE);
                holder.textStatusChip.setText(ChatMessage.hasVisibleText(statusText) ? statusText : "Checkpoint");
            } else if (message.isAwaitingUser()) {
                holder.textStatusChip.setVisibility(View.VISIBLE);
                holder.textStatusChip.setText(ChatMessage.hasVisibleText(statusText) ? statusText : "Aguardando usuario");
            }
        }

        String displayText = messageText;
        boolean thinkingOnly = message.isBot()
                && message.isStreaming()
                && !ChatMessage.hasVisibleText(messageText)
                && !ChatMessage.hasVisibleText(reasoningText)
                && !message.isCheckpoint()
                && !message.isAwaitingUser()
                && !message.isInterruptedStreamingTool();

        boolean showStreamingDots = thinkingOnly;
        if (holder.streamingDots != null) {
            if (showStreamingDots) {
                holder.streamingDots.setVisibility(View.VISIBLE);
                holder.streamingDots.startAnimation();
            } else {
                holder.streamingDots.setVisibility(View.GONE);
                holder.streamingDots.stopAnimation();
            }
        }

        holder.textMessage.setMovementMethod(LinkMovementMethod.getInstance());
        holder.textMessage.setAlpha(ChatMessage.hasVisibleText(messageText) ? 1f : 0.78f);

        if (ChatMessage.hasVisibleText(displayText)) {
            holder.textMessage.setVisibility(View.VISIBLE);
            if (message.isStreaming()) {
                holder.textMessage.setText(displayText);
            } else {
                getMarkwon(holder.itemView.getContext()).setMarkdown(holder.textMessage, displayText);
            }
        } else if (thinkingOnly) {
            holder.textMessage.setVisibility(View.VISIBLE);
            holder.textMessage.setText(holder.itemView.getContext().getString(R.string.chat_status_thinking));
        } else {
            holder.textMessage.setText("");
            holder.textMessage.setVisibility(View.GONE);
        }
        bindMessageImages(holder, message);

        bindReasoning(holder, message, reasoningText);

        if (ChatMessage.hasVisibleText(displayText)
                && (displayText.contains(PromptConstants.ORIGINAL)
                || displayText.contains(PromptConstants.FINAL)
                || displayText.contains(PromptConstants.DIVIDER))) {
            holder.textMessage.post(() -> {
                CharSequence text = holder.textMessage.getText();
                if (!(text instanceof Spannable)) {
                    return;
                }
                Spannable spannable = (Spannable) text;
                String textStr = text.toString();
                applyMarkerSpan(spannable, textStr, PromptConstants.ORIGINAL, parseRgbColor(VoidColors.REJECT_BG, Color.RED));
                applyMarkerSpan(spannable, textStr, PromptConstants.FINAL, parseRgbColor(VoidColors.ACCEPT_BG, Color.GREEN));
                applyMarkerSpan(spannable, textStr, PromptConstants.DIVIDER, Color.GRAY);
            });
        }

        holder.textTime.setText(formatTime(message.getTimestamp()));
        bindKelivoHeader(holder, message);
        bindKelivoActions(holder, message, displayText);
    }

    private void bindReasoning(@NonNull MessageViewHolder holder,
                               @NonNull ChatMessage message,
                               String reasoningText) {
        if (holder.layoutReasoning == null || holder.textReasoning == null) {
            return;
        }

        boolean hasReasoning = ChatMessage.hasVisibleText(reasoningText);
        holder.layoutReasoning.setVisibility(hasReasoning ? View.VISIBLE : View.GONE);
        if (!hasReasoning) {
            holder.textReasoning.setText("");
            holder.textReasoning.setVisibility(View.GONE);
            if (holder.actionToggleReasoning != null) {
                holder.actionToggleReasoning.setOnClickListener(null);
                holder.actionToggleReasoning.setVisibility(View.GONE);
            }
            return;
        }

        boolean expanded = message.isReasoningExpanded();
        holder.textReasoning.setText(reasoningText);
        holder.textReasoning.setVisibility(expanded ? View.VISIBLE : View.GONE);

        if (holder.actionToggleReasoning != null) {
            Context context = holder.itemView.getContext();
            holder.actionToggleReasoning.setVisibility(View.VISIBLE);
            holder.actionToggleReasoning.setImageResource(expanded
                    ? R.drawable.ic_mtrl_preview_off
                    : R.drawable.ic_mtrl_preview);
            holder.actionToggleReasoning.setContentDescription(context.getString(expanded
                    ? R.string.hide_reasoning
                    : R.string.show_reasoning));
            holder.actionToggleReasoning.setOnClickListener(v -> {
                message.setReasoningExpanded(!message.isReasoningExpanded());
                bindReasoning(holder, message, sanitizeText(message.getReasoning()));
                if (actionListener != null) {
                    actionListener.onReasoningVisibilityChanged(message);
                }
            });
        }
    }

    private void bindKelivoActions(@NonNull MessageViewHolder holder, @NonNull ChatMessage message, String displayText) {
        if (holder.layoutMessageActions == null) {
            return;
        }
        boolean showActions = ChatMessage.hasVisibleText(displayText)
                && !message.isStreaming()
                && !message.isTool()
                && !message.isCheckpoint()
                && !message.isAwaitingUser();
        holder.layoutMessageActions.setVisibility(showActions ? View.VISIBLE : View.GONE);
        if (!showActions) {
            return;
        }
        Context context = holder.itemView.getContext();
        String copyText = ChatMessage.hasVisibleText(message.getMessage()) ? message.getMessage() : displayText;
        View.OnClickListener copyListener = v -> copyToClipboard(context, copyText);
        if (holder.actionCopy != null) {
            holder.actionCopy.setOnClickListener(copyListener);
        }
        if (holder.actionRefresh != null) {
            holder.actionRefresh.setVisibility(message.isUser() ? View.GONE : View.VISIBLE);
            holder.actionRefresh.setOnClickListener(v -> {
                int virtualPosition = holder.getBindingAdapterPosition();
                if (actionListener != null && virtualPosition != RecyclerView.NO_POSITION) {
                    int realPosition = translateToMessagePosition(virtualPosition);
                    if (realPosition >= 0) {
                        actionListener.onRegenerate(realPosition);
                    }
                }
            });
        }
        if (holder.actionEdit != null) {
            holder.actionEdit.setVisibility((message.isUser() || message.isBot()) ? View.VISIBLE : View.GONE);
            holder.actionEdit.setOnClickListener(v -> {
                int virtualPosition = holder.getBindingAdapterPosition();
                if (actionListener != null && virtualPosition != RecyclerView.NO_POSITION) {
                    int realPosition = translateToMessagePosition(virtualPosition);
                    if (realPosition >= 0) {
                        actionListener.onEdit(realPosition);
                    }
                }
            });
        }
        if (holder.actionSpeak != null) {
            holder.actionSpeak.setVisibility(message.isUser() ? View.GONE : View.VISIBLE);
            holder.actionSpeak.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onSpeak(copyText);
                }
            });
        }
        if (holder.actionTranslate != null) {
            holder.actionTranslate.setVisibility(message.isUser() ? View.GONE : View.VISIBLE);
            holder.actionTranslate.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onTranslate(copyText);
                }
            });
        }
        if (holder.actionMore != null) {
            holder.actionMore.setOnClickListener(v -> {
                int virtualPosition = holder.getBindingAdapterPosition();
                if (virtualPosition != RecyclerView.NO_POSITION) {
                    int realPosition = translateToMessagePosition(virtualPosition);
                    if (realPosition >= 0) {
                        showMoreMenu(v, realPosition, message, copyText);
                    }
                }
            });
        }
        if (holder.textTokenCount != null) {
            holder.textTokenCount.setVisibility(message.isUser() ? View.GONE : View.VISIBLE);
        }
    }

    private void showMoreMenu(View anchor, int position, ChatMessage message, String copyText) {
        Context context = anchor.getContext();
        PopupMenu menu = new PopupMenu(context, anchor);
        menu.getMenu().add(0, 1, 0, R.string.kelivo_action_share);
        menu.getMenu().add(0, 2, 1, R.string.kelivo_action_delete);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                if (ChatMessage.hasVisibleText(copyText)) {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_TEXT, copyText);
                    context.startActivity(Intent.createChooser(share, context.getString(R.string.kelivo_action_share)));
                }
                return true;
            }
            if (item.getItemId() == 2 && actionListener != null && position != RecyclerView.NO_POSITION) {
                actionListener.onDelete(position);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void copyToClipboard(Context context, String text) {
        if (!ChatMessage.hasVisibleText(text)) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("chat", text));
            Toast.makeText(context, R.string.kelivo_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void bindKelivoHeader(@NonNull MessageViewHolder holder, @NonNull ChatMessage message) {
        if (holder.textSenderName != null) {
            Context context = holder.itemView.getContext();
            if (message.isUser()) {
                holder.textSenderName.setText(getUserLabel(context));
                if (holder.textAvatarIcon != null) {
                    holder.textAvatarIcon.setImageResource(R.drawable.kelivo_lucide_bot_message_square);
                    holder.textAvatarIcon.setVisibility(View.VISIBLE);
                }
                if (holder.textAvatar != null) {
                    holder.textAvatar.setVisibility(View.GONE);
                }
            } else if (!message.isCheckpoint() && !message.isAwaitingUser()) {
                holder.textSenderName.setText(getBotLabel(context));
                SharedPreferences prefs = context.getSharedPreferences(
                        AiChatSettingsHelper.PREFS_NAME, Context.MODE_PRIVATE);
                String provider = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "");
                String model = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_MODEL, "");
                int iconRes = KelivoModelIconResolver.resolve(provider, model);
                if (holder.textAvatarIcon != null && iconRes != 0) {
                    holder.textAvatarIcon.setImageResource(iconRes);
                    holder.textAvatarIcon.setVisibility(View.VISIBLE);
                    if (holder.textAvatar != null) {
                        holder.textAvatar.setVisibility(View.GONE);
                    }
                } else if (holder.textAvatar != null) {
                    holder.textAvatar.setVisibility(View.VISIBLE);
                    holder.textAvatar.setText("AI");
                    if (holder.textAvatarIcon != null) {
                        holder.textAvatarIcon.setVisibility(View.GONE);
                    }
                }
            }
        }
    }

    private String getUserLabel(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE);
        String name = prefs.getString("user_name", "");
        if (!ChatMessage.hasVisibleText(name)) {
            name = prefs.getString("user_display_name", context.getString(R.string.kelivo_default_user));
        }
        return name;
    }

    private String getUserInitial(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE);
        String avatarType = prefs.getString("avatar_type", "");
        String avatarValue = prefs.getString("avatar_value", "");
        if ("emoji".equals(avatarType) && ChatMessage.hasVisibleText(avatarValue)) {
            return avatarValue;
        }
        String label = getUserLabel(context).trim();
        if (label.isEmpty()) {
            return "U";
        }
        return String.valueOf(Character.toLowerCase(label.charAt(0)));
    }

    private String getBotLabel(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                AiChatSettingsHelper.PREFS_NAME, Context.MODE_PRIVATE);
        String provider = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "");
        String model = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_MODEL, "");
        if (ChatMessage.hasVisibleText(model)) {
            return provider + "/" + model;
        }
        return context.getString(R.string.chat_mode_agent);
    }

    private void bindMessageImages(@NonNull MessageViewHolder holder, @NonNull ChatMessage message) {
        if (holder.messageImageScroll == null || holder.layoutMessageImages == null) {
            return;
        }
        holder.layoutMessageImages.removeAllViews();
        List<ChatReference> references = message.getStagingSelections();
        if (references.isEmpty()) {
            holder.messageImageScroll.setVisibility(View.GONE);
            return;
        }
        holder.messageImageScroll.setVisibility(View.VISIBLE);
        Context context = holder.itemView.getContext();
        for (ChatReference reference : references) {
            if (reference == null) {
                continue;
            }
            if (!reference.isImage()) {
                TextView chip = new TextView(context);
                String label = sanitizeText(reference.getLabel());
                chip.setText("@" + (label.isEmpty() ? "reference" : label));
                chip.setTextColor(context.getColor(R.color.chat_text_primary));
                chip.setTextSize(12f);
                chip.setGravity(Gravity.CENTER_VERTICAL);
                chip.setMaxWidth(dp(context, 220));
                chip.setMaxLines(2);
                chip.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
                chip.setBackgroundResource(R.drawable.bg_round_outline);
                LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                chipParams.setMarginEnd(dp(context, 8));
                chip.setLayoutParams(chipParams);
                holder.layoutMessageImages.addView(chip);
                continue;
            }
            FrameLayout frame = new FrameLayout(context);
            LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(dp(context, 96), dp(context, 96));
            frameParams.setMarginEnd(dp(context, 8));
            frame.setLayoutParams(frameParams);
            frame.setPadding(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2));
            frame.setBackgroundResource(R.drawable.bg_round_outline);

            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (reference != null && reference.getUri() != null) {
                try {
                    image.setImageURI(reference.getUri());
                } catch (Exception ignored) {
                    image.setImageResource(R.drawable.kelivo_lucide_image);
                }
            } else {
                image.setImageResource(R.drawable.kelivo_lucide_image);
            }
            frame.addView(image, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            holder.layoutMessageImages.addView(frame);
        }
    }

    private void bindTool(@NonNull ToolViewHolder holder, @NonNull ChatMessage message) {
        Context context = holder.itemView.getContext();
        String toolName = sanitizeText(message.getToolName());
        String toolArgs = sanitizeText(message.getToolArgs());
        String toolResult = sanitizeText(message.getToolResult());
        String toolStatus = sanitizeText(message.getStatus());
        String toolNotice = sanitizeText(message.getMessage());
        String toolGroup = ChatToolActivitySummary.groupLabel(toolName);

        holder.textToolName.setText(ChatMessage.hasVisibleText(toolName)
                ? toolGroup + ": " + toolName
                : context.getString(R.string.chat_tool_unknown));
        holder.textToolArgs.setText(ChatMessage.hasVisibleText(toolArgs) ? toolArgs : "{}");

        if (ChatMessage.hasVisibleText(toolStatus)) {
            holder.textToolStatus.setVisibility(View.VISIBLE);
            holder.textToolStatus.setText(toolStatus);
        } else {
            holder.textToolStatus.setVisibility(View.GONE);
        }

        boolean awaitingApproval = message.getRequiresApproval() && !message.isApproved() && !message.isRejected();
        boolean showCancel = message.isToolRunning() && !awaitingApproval;
        boolean hasResult = ChatMessage.hasVisibleText(toolResult);
        boolean hasNotice = ChatMessage.hasVisibleText(toolNotice);

        if (message.isToolRunning() && !hasResult) {
            holder.textResultLabel.setVisibility(View.GONE);
            holder.textToolResult.setVisibility(View.GONE);
        } else {
            holder.textResultLabel.setVisibility(View.VISIBLE);
            holder.textToolResult.setVisibility(View.VISIBLE);
            if (hasResult) {
                holder.textToolResult.setText(toolResult);
            } else if (message.isRejected()) {
                holder.textToolResult.setText(R.string.chat_tool_rejected_message);
            } else if (message.isToolError()) {
                holder.textToolResult.setText(R.string.chat_tool_error_state);
            } else {
                holder.textToolResult.setText(R.string.chat_tool_finished);
            }
        }
        holder.textToolResult.setBackgroundResource(R.drawable.bg_tool_json_box);

        holder.textToolNotice.setVisibility(hasNotice ? View.VISIBLE : View.GONE);
        holder.textToolNotice.setText(toolNotice);

        if (awaitingApproval) {
            holder.progressTool.setVisibility(View.GONE);
            holder.imgToolStatus.setVisibility(View.GONE);
            holder.layoutApproval.setVisibility(View.VISIBLE);
            holder.btnApprove.setVisibility(View.VISIBLE);
            holder.btnApprove.setText(R.string.chat_tool_approve);
            holder.btnApprove.setContentDescription(ActionIds.VOID_ACCEPT_DIFF_ACTION_ID);
            holder.btnApprove.setOnClickListener(v -> {
                if (context instanceof ChatActivity) {
                    ((ChatActivity) context).approveTool();
                }
            });
            holder.btnReject.setVisibility(View.VISIBLE);
            holder.btnReject.setText(R.string.chat_tool_reject);
            holder.btnReject.setContentDescription(ActionIds.VOID_REJECT_DIFF_ACTION_ID);
            holder.btnReject.setOnClickListener(v -> {
                if (context instanceof ChatActivity) {
                    ((ChatActivity) context).rejectTool();
                }
            });
        } else if (showCancel) {
            holder.progressTool.setVisibility(View.VISIBLE);
            holder.imgToolStatus.setVisibility(View.GONE);
            holder.layoutApproval.setVisibility(View.VISIBLE);
            holder.btnApprove.setVisibility(View.GONE);
            holder.btnReject.setVisibility(View.VISIBLE);
            holder.btnReject.setText(R.string.chat_tool_cancel);
            holder.btnReject.setContentDescription(ActionIds.VOID_REJECT_FILE_ACTION_ID);
            holder.btnReject.setOnClickListener(v -> {
                if (context instanceof ChatActivity) {
                    ((ChatActivity) context).cancelCurrentRun();
                }
            });
        } else {
            holder.layoutApproval.setVisibility(View.GONE);
            holder.progressTool.setVisibility(View.GONE);
            holder.imgToolStatus.setVisibility(View.VISIBLE);
            if (message.isToolError() || message.isRejected()) {
                holder.imgToolStatus.setImageResource(R.drawable.ic_mtrl_cancel);
                holder.imgToolStatus.setColorFilter(context.getColor(R.color.chat_error));
            } else {
                holder.imgToolStatus.setImageResource(R.drawable.ic_mtrl_check);
                holder.imgToolStatus.setColorFilter(context.getColor(R.color.chat_accent));
            }
        }

        int iconRes;
        if (toolName.contains("read") || toolName.contains("decrypt")) {
            iconRes = R.drawable.kelivo_lucide_file_text;
        } else if (toolName.contains("write") || toolName.contains("edit") || toolName.contains("encrypt")) {
            iconRes = R.drawable.kelivo_lucide_edit;
        } else if (toolName.contains("list") || toolName.contains("glob")) {
            iconRes = R.drawable.kelivo_lucide_folder;
        } else if (toolName.contains("search") || toolName.contains("grep")) {
            iconRes = R.drawable.kelivo_lucide_search;
        } else {
            iconRes = R.drawable.kelivo_lucide_code;
        }
        holder.imgToolIcon.setImageResource(iconRes);

        boolean canExpand = hasExpandableDetails(message, hasResult, hasNotice);
        boolean forceExpanded = awaitingApproval || message.isToolRunning();
        boolean expanded = forceExpanded || message.isExpanded();

        holder.layoutToolDetails.setVisibility(expanded ? View.VISIBLE : View.GONE);
        holder.imgExpand.setVisibility(canExpand ? View.VISIBLE : View.GONE);
        holder.imgExpand.setImageResource(expanded ? R.drawable.ic_mtrl_arrow_up : R.drawable.ic_mtrl_arrow_down);

        holder.layoutToolHeader.setOnClickListener(v -> {
            int virtualPosition = holder.getBindingAdapterPosition();
            if (virtualPosition == RecyclerView.NO_POSITION) {
                return;
            }
            int realPosition = translateToMessagePosition(virtualPosition);
            if (realPosition < 0) {
                return;
            }
            ChatMessage currentMessage = messages.get(realPosition);
            boolean currentAwaiting = currentMessage.getRequiresApproval() && !currentMessage.isApproved() && !currentMessage.isRejected();
            if (currentAwaiting || currentMessage.isToolRunning() || !hasExpandableDetails(currentMessage,
                    ChatMessage.hasVisibleText(sanitizeText(currentMessage.getToolResult())),
                    ChatMessage.hasVisibleText(sanitizeText(currentMessage.getMessage())))) {
                return;
            }
            currentMessage.setExpanded(!currentMessage.isExpanded());
            notifyItemChanged(virtualPosition);
        });
    }

    private void applyMarkerSpan(Spannable spannable, String text, String marker, int color) {
        int index = text.indexOf(marker);
        while (index >= 0) {
            int endIndex = Math.min(index + marker.length(), text.length());
            spannable.setSpan(new ForegroundColorSpan(color), index, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            index = text.indexOf(marker, endIndex);
        }
    }

    private int parseRgbColor(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.startsWith("rgb(") && trimmed.endsWith(")")) {
                String[] parts = trimmed.substring(4, trimmed.length() - 1).split(",");
                if (parts.length == 3) {
                    int red = Integer.parseInt(parts[0].trim());
                    int green = Integer.parseInt(parts[1].trim());
                    int blue = Integer.parseInt(parts[2].trim());
                    return Color.rgb(red, green, blue);
                }
            }
            return Color.parseColor(trimmed);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean hasExpandableDetails(ChatMessage message, boolean hasResult, boolean hasNotice) {
        return ChatMessage.hasVisibleText(sanitizeText(message.getToolArgs()))
                || hasResult
                || hasNotice
                || message.getRequiresApproval();
    }

    private String sanitizeText(String value) {
        if (!ChatMessage.hasVisibleText(value)) {
            return "";
        }
        return value;
    }

    private String formatTime(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof MessageViewHolder) {
            KelivoTypingDotsView dots = ((MessageViewHolder) holder).streamingDots;
            if (dots != null) {
                dots.stopAnimation();
            }
        } else if (holder instanceof NativeAdViewHolder) {
            NativeAdView adView = ((NativeAdViewHolder) holder).adView;
            if (adView != null) {
                adView.setHeadlineView(null);
                adView.setBodyView(null);
                adView.setCallToActionView(null);
                adView.setIconView(null);
                adView.setStarRatingView(null);
                adView.setMediaView(null);
                adView.setAdvertiserView(null);
                adView.setStoreView(null);
                adView.setPriceView(null);
            }
        }
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return messages.size() + countTotalNativeAds();
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        final TextView textMessage;
        final TextView textTime;
        final TextView textSenderName;
        final TextView textAvatar;
        final ImageView textAvatarIcon;
        final TextView textStatusChip;
        final View layoutReasoning;
        final TextView textReasoning;
        final ImageView actionToggleReasoning;
        final View messageImageScroll;
        final LinearLayout layoutMessageImages;
        final View layoutMessageActions;
        final ImageView actionCopy;
        final ImageView actionRefresh;
        final ImageView actionEdit;
        final ImageView actionSpeak;
        final ImageView actionTranslate;
        final ImageView actionMore;
        final TextView textTokenCount;
        final KelivoTypingDotsView streamingDots;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.text_message);
            textTime = itemView.findViewById(R.id.text_time);
            textSenderName = itemView.findViewById(R.id.text_sender_name);
            textAvatar = itemView.findViewById(R.id.text_avatar);
            textAvatarIcon = itemView.findViewById(R.id.text_avatar_icon);
            textStatusChip = itemView.findViewById(R.id.text_status_chip);
            layoutReasoning = itemView.findViewById(R.id.layout_reasoning);
            textReasoning = itemView.findViewById(R.id.text_reasoning);
            actionToggleReasoning = itemView.findViewById(R.id.action_toggle_reasoning);
            messageImageScroll = itemView.findViewById(R.id.message_image_scroll);
            layoutMessageImages = itemView.findViewById(R.id.layout_message_images);
            layoutMessageActions = itemView.findViewById(R.id.layout_message_actions);
            actionCopy = itemView.findViewById(R.id.action_copy);
            actionRefresh = itemView.findViewById(R.id.action_refresh);
            actionEdit = itemView.findViewById(R.id.action_edit);
            actionSpeak = itemView.findViewById(R.id.action_speak);
            actionTranslate = itemView.findViewById(R.id.action_translate);
            actionMore = itemView.findViewById(R.id.action_more);
            textTokenCount = itemView.findViewById(R.id.text_token_count);
            streamingDots = itemView.findViewById(R.id.kelivo_streaming_dots);
        }
    }

    public static class ToolViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout layoutToolHeader;
        final ImageView imgToolIcon;
        final TextView textToolName;
        final TextView textToolStatus;
        final ProgressBar progressTool;
        final ImageView imgToolStatus;
        final ImageView imgExpand;
        final LinearLayout layoutToolDetails;
        final TextView textToolArgs;
        final TextView textResultLabel;
        final TextView textToolResult;
        final TextView textToolNotice;
        final LinearLayout layoutApproval;
        final Button btnApprove;
        final Button btnReject;

        public ToolViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutToolHeader = itemView.findViewById(R.id.layout_tool_header);
            imgToolIcon = itemView.findViewById(R.id.img_tool_icon);
            textToolName = itemView.findViewById(R.id.text_tool_name);
            textToolStatus = itemView.findViewById(R.id.text_tool_status);
            progressTool = itemView.findViewById(R.id.progress_tool);
            imgToolStatus = itemView.findViewById(R.id.img_tool_status);
            imgExpand = itemView.findViewById(R.id.img_expand);
            layoutToolDetails = itemView.findViewById(R.id.layout_tool_details);
            textToolArgs = itemView.findViewById(R.id.text_tool_args);
            textResultLabel = itemView.findViewById(R.id.text_result_label);
            textToolResult = itemView.findViewById(R.id.text_tool_result);
            textToolNotice = itemView.findViewById(R.id.text_tool_notice);
            layoutApproval = itemView.findViewById(R.id.layout_approval);
            btnApprove = itemView.findViewById(R.id.btn_approve_tool);
            btnReject = itemView.findViewById(R.id.btn_reject_tool);
        }
    }

    public static class NativeAdViewHolder extends RecyclerView.ViewHolder {
        final NativeAdView adView;

        public NativeAdViewHolder(@NonNull View itemView) {
            super(itemView);
            adView = (NativeAdView) itemView;
        }
    }
}

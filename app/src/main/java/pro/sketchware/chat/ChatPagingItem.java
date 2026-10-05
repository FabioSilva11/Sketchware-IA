package pro.sketchware.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** A stable row rendered by Paging 3: either one message or one native-ad slot. */
public final class ChatPagingItem {
    public static final int TYPE_MESSAGE = 0;
    public static final int TYPE_AD = 1;

    public final int itemType;
    public final String threadId;
    public final int messageOrdinal;
    public final int adSlot;
    public final long contentVersion;
    public final @Nullable ChatMessage message;

    private ChatPagingItem(int itemType, String threadId, int messageOrdinal, int adSlot,
                           long contentVersion, @Nullable ChatMessage message) {
        this.itemType = itemType;
        this.threadId = threadId == null ? "" : threadId;
        this.messageOrdinal = messageOrdinal;
        this.adSlot = adSlot;
        this.contentVersion = contentVersion;
        this.message = message;
    }

    public static ChatPagingItem message(String threadId, int ordinal,
                                         @NonNull ChatMessage message, long contentVersion) {
        return new ChatPagingItem(TYPE_MESSAGE, threadId, ordinal, -1,
                contentVersion, message);
    }

    public static ChatPagingItem ad(String threadId, int slot) {
        return new ChatPagingItem(TYPE_AD, threadId, -1, slot, slot, null);
    }

    public boolean isAd() {
        return itemType == TYPE_AD;
    }

    public String stableKey() {
        return threadId + (isAd() ? ":ad:" + adSlot : ":message:" + messageOrdinal);
    }
}

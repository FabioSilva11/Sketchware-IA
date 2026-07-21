package pro.sketchware.activities.chat;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class ChatHistoryManager {
    private static final String TAG = "ChatHistoryManager";

    private final VoidStyleChatStorage voidStorage;

    public ChatHistoryManager(Context context) {
        this.voidStorage = new VoidStyleChatStorage(context);
    }

    public void shutdown() {
        voidStorage.shutdown();
    }

    public void saveMessage(String scId, ChatMessage message) {
        if (scId == null || scId.trim().isEmpty() || message == null || message.isStreaming()) {
            return;
        }
        voidStorage.saveMessage(scId, ensureDefaultThread(scId), message);
    }

    public void saveMessage(String scId, String threadId, ChatMessage message) {
        if (scId == null || scId.trim().isEmpty() || message == null || message.isStreaming()) {
            return;
        }
        voidStorage.saveMessage(scId, threadId, message);
    }

    public void saveHistory(String scId, List<ChatMessage> messages) {
        if (scId == null || messages == null) {
            return;
        }
        voidStorage.saveHistory(scId, ensureDefaultThread(scId), messages);
    }

    public void saveHistory(String scId, String threadId, List<ChatMessage> messages) {
        if (scId == null || messages == null) {
            return;
        }
        voidStorage.saveHistory(scId, threadId, messages);
    }

    public void saveHistoryAsync(String scId, String threadId, List<ChatMessage> messages) {
        if (scId == null || messages == null) {
            return;
        }
        voidStorage.saveHistoryAsync(scId, threadId, messages);
    }

    public List<ChatMessage> loadHistory(String scId) {
        if (scId == null) {
            return new ArrayList<>();
        }
        return loadHistory(scId, ensureDefaultThread(scId));
    }

    public List<ChatMessage> loadHistory(String scId, String threadId) {
        if (scId == null) {
            return new ArrayList<>();
        }
        try {
            return voidStorage.loadHistory(scId, threadId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load chat history for " + scId, e);
            return new ArrayList<>();
        }
    }

    public boolean containsReferenceUri(String uri) {
        return voidStorage.containsReferenceUri(uri);
    }

    public void clearHistory(String scId) {
        if (scId == null) {
            return;
        }
        voidStorage.clearHistory(scId, ensureDefaultThread(scId));
    }

    public void clearHistory(String scId, String threadId) {
        if (scId == null) {
            return;
        }
        voidStorage.clearHistory(scId, threadId);
    }

    public void deleteProjectHistory(String scId) {
        if (scId == null) {
            return;
        }
        voidStorage.deleteProjectHistory(scId);
    }

    public String ensureDefaultThread(String scId) {
        return voidStorage.ensureDefaultThread(scId);
    }

    public String createThread(String scId) {
        return voidStorage.createThread(scId);
    }

    public List<ChatThread> getThreads(String scId) {
        return voidStorage.getThreads(scId);
    }

    public void updateThreadSummary(String scId, String threadId, String title, String summary, String activeModel) {
        voidStorage.updateThreadSummary(scId, threadId, title, summary, activeModel);
    }

    public void renameThread(String scId, String threadId, String title) {
        voidStorage.renameThread(scId, threadId, title);
    }

    public void setThreadPinned(String scId, String threadId, boolean pinned) {
        voidStorage.setThreadPinned(scId, threadId, pinned);
    }

    public void deleteThread(String scId, String threadId) {
        voidStorage.deleteThread(scId, threadId);
    }
}

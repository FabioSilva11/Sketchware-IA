package pro.sketchware.chat;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.paging.PagingData;

import java.util.ArrayList;
import java.util.List;

public class ChatHistoryManager {
    private static final String TAG = "ChatHistoryManager";

    private final SqliteChatStorage sqliteStorage;

    public ChatHistoryManager(Context context) {
        this.sqliteStorage = new SqliteChatStorage(context);
    }

    public void shutdown() {
        sqliteStorage.shutdown();
    }

    public void saveMessage(String scId, ChatMessage message) {
        if (scId == null || scId.trim().isEmpty() || message == null || message.isStreaming()) {
            return;
        }
        sqliteStorage.saveMessage(scId, ensureDefaultThread(scId), message);
    }

    public void saveMessage(String scId, String threadId, ChatMessage message) {
        if (scId == null || scId.trim().isEmpty() || message == null || message.isStreaming()) {
            return;
        }
        sqliteStorage.saveMessage(scId, threadId, message);
    }

    public void saveHistory(String scId, List<ChatMessage> messages) {
        if (scId == null || messages == null) {
            return;
        }
        sqliteStorage.saveHistory(scId, ensureDefaultThread(scId), messages);
    }

    public void saveHistory(String scId, String threadId, List<ChatMessage> messages) {
        if (scId == null || messages == null) {
            return;
        }
        sqliteStorage.saveHistory(scId, threadId, messages);
    }

    public void saveHistoryAsync(String scId, String threadId, List<ChatMessage> messages) {
        if (scId == null || messages == null) {
            return;
        }
        sqliteStorage.saveHistoryAsync(scId, threadId, messages);
    }

    public void saveMessageAtOrdinal(String scId, String threadId, int ordinal,
                                     ChatMessage message) {
        if (scId == null || message == null) return;
        sqliteStorage.saveMessageAtOrdinal(scId, threadId, ordinal, message);
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
            return sqliteStorage.loadHistory(scId, threadId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load chat history for " + scId, e);
            return new ArrayList<>();
        }
    }

    public boolean containsReferenceUri(String uri) {
        return sqliteStorage.containsReferenceUri(uri);
    }

    public void clearHistory(String scId) {
        if (scId == null) {
            return;
        }
        sqliteStorage.clearHistory(scId, ensureDefaultThread(scId));
    }

    public void clearHistory(String scId, String threadId) {
        if (scId == null) {
            return;
        }
        sqliteStorage.clearHistory(scId, threadId);
    }

    public void deleteProjectHistory(String scId) {
        if (scId == null) {
            return;
        }
        sqliteStorage.deleteProjectHistory(scId);
    }

    public String ensureDefaultThread(String scId) {
        return sqliteStorage.ensureDefaultThread(scId);
    }

    public String getCurrentThreadId(String scId) {
        return sqliteStorage.getCurrentThreadId(scId);
    }

    public void setCurrentThreadId(String scId, String threadId) {
        sqliteStorage.setCurrentThreadId(scId, threadId);
    }

    public String createThread(String scId) {
        return sqliteStorage.createThread(scId);
    }

    public List<ChatThread> getThreads(String scId) {
        return sqliteStorage.getThreads(scId);
    }

    public void updateThreadSummary(String scId, String threadId, String title, String summary, String activeModel) {
        sqliteStorage.updateThreadSummary(scId, threadId, title, summary, activeModel);
    }

    public void renameThread(String scId, String threadId, String title) {
        sqliteStorage.renameThread(scId, threadId, title);
    }

    public void setThreadPinned(String scId, String threadId, boolean pinned) {
        sqliteStorage.setThreadPinned(scId, threadId, pinned);
    }

    public void deleteThread(String scId, String threadId) {
        sqliteStorage.deleteThread(scId, threadId);
    }

    public void beginOperation(String scId, String threadId, String operationId,
                               String requestText, String status) {
        sqliteStorage.beginOperation(scId, threadId, operationId, requestText, status);
    }

    public void updateOperation(String scId, String threadId, String operationId,
                                String state, String status) {
        sqliteStorage.updateOperation(scId, threadId, operationId, state, status);
    }

    public int interruptRunningOperations(String scId, String threadId, String status) {
        return sqliteStorage.interruptRunningOperations(scId, threadId, status);
    }

    public void saveCompactionState(String scId, String threadId, String summary,
                                    int compactedUntil) {
        sqliteStorage.saveCompactionState(scId, threadId, summary, compactedUntil);
    }

    public SqliteChatStorage.CompactionState loadCompactionState(String scId, String threadId) {
        return sqliteStorage.loadCompactionState(scId, threadId);
    }

    public void clearCompactionState(String scId, String threadId) {
        sqliteStorage.clearCompactionState(scId, threadId);
    }

    public LiveData<PagingData<ChatPagingItem>> pagingData(String scId, String threadId) {
        return ChatPagingFactory.create(sqliteStorage, scId == null ? "" : scId,
                threadId == null ? "" : threadId);
    }
}

package pro.sketchware.chat;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import pro.sketchware.chat.port.VoidPortChatThreadService;

import org.json.JSONArray;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/** App-private SQLite storage for chat threads and Paging 3 rows. */
public final class SqliteChatStorage extends SQLiteOpenHelper {
    private static final String TAG = "SqliteChatStorage";
    static final String DATABASE_NAME = "axion_chat.db";
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE_THREADS = "chat_threads";
    private static final String TABLE_CURRENT = "current_threads";
    private static final String TABLE_MESSAGES = "chat_messages";
    private static final String TABLE_OPERATIONS = "chat_operations";
    private static final String TABLE_CONTEXT = "chat_context_state";
    private static final String LEGACY_JSON = "void.chatThreadStorageII.json";

    private final ReentrantLock storageLock = new ReentrantLock();
    private final ConcurrentHashMap<String, AtomicLong> writeGenerations = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<Runnable> invalidationListeners = new CopyOnWriteArraySet<>();
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "sqlite-chat-storage");
        thread.setDaemon(true);
        return thread;
    });

    public SqliteChatStorage(@NonNull Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
        discardLegacyJson(context.getApplicationContext());
        // Open immediately so schema errors surface at construction. Streaming
        // rows are intentionally retained: they are the last local checkpoint
        // when Android stops the process while a response is still arriving.
        getWritableDatabase();
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_THREADS + " ("
                + "id TEXT PRIMARY KEY NOT NULL,"
                + "project_id TEXT NOT NULL,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "summary TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL,"
                + "active_model TEXT NOT NULL DEFAULT '',"
                + "pinned INTEGER NOT NULL DEFAULT 0,"
                + "manual_title INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_threads_project ON " + TABLE_THREADS
                + "(project_id, pinned DESC, updated_at DESC)");
        db.execSQL("CREATE TABLE " + TABLE_CURRENT + " ("
                + "project_id TEXT PRIMARY KEY NOT NULL,"
                + "thread_id TEXT NOT NULL,"
                + "FOREIGN KEY(thread_id) REFERENCES " + TABLE_THREADS + "(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE " + TABLE_MESSAGES + " ("
                + "project_id TEXT NOT NULL,"
                + "thread_id TEXT NOT NULL,"
                + "ordinal INTEGER NOT NULL,"
                + "role TEXT NOT NULL,"
                + "message_type INTEGER NOT NULL,"
                + "display_content TEXT NOT NULL DEFAULT '',"
                + "llm_content TEXT NOT NULL DEFAULT '',"
                + "timestamp INTEGER NOT NULL,"
                + "tool_name TEXT, tool_args TEXT, tool_running INTEGER NOT NULL DEFAULT 0,"
                + "tool_result TEXT, tool_error INTEGER NOT NULL DEFAULT 0, tool_id TEXT,"
                + "tool_state TEXT, mcp_server_name TEXT, expanded INTEGER NOT NULL DEFAULT 0,"
                + "status TEXT, requires_approval INTEGER NOT NULL DEFAULT 0,"
                + "approved INTEGER NOT NULL DEFAULT 0, rejected INTEGER NOT NULL DEFAULT 0,"
                + "checkpoint_id TEXT, checkpoint_type TEXT, checkpoint_snapshots_json TEXT,"
                + "reasoning TEXT, anthropic_reasoning_json TEXT,"
                + "reasoning_expanded INTEGER NOT NULL DEFAULT 0, streaming INTEGER NOT NULL DEFAULT 0,"
                + "context_payload TEXT, being_edited INTEGER NOT NULL DEFAULT 0,"
                + "references_json TEXT NOT NULL DEFAULT '[]',"
                + "content_version INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY(thread_id, ordinal),"
                + "FOREIGN KEY(thread_id) REFERENCES " + TABLE_THREADS + "(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX idx_messages_order ON " + TABLE_MESSAGES
                + "(project_id, thread_id, ordinal)");
        createRecoveryTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createRecoveryTables(db);
        }
    }

    private void createRecoveryTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_OPERATIONS + " ("
                + "operation_id TEXT PRIMARY KEY NOT NULL,"
                + "project_id TEXT NOT NULL,"
                + "thread_id TEXT NOT NULL,"
                + "request_text TEXT NOT NULL DEFAULT '',"
                + "state TEXT NOT NULL,"
                + "status TEXT NOT NULL DEFAULT '',"
                + "started_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL,"
                + "FOREIGN KEY(thread_id) REFERENCES " + TABLE_THREADS + "(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_operations_thread_state ON "
                + TABLE_OPERATIONS + "(project_id, thread_id, state, updated_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_CONTEXT + " ("
                + "thread_id TEXT PRIMARY KEY NOT NULL,"
                + "project_id TEXT NOT NULL,"
                + "history_summary TEXT NOT NULL DEFAULT '',"
                + "compacted_until INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL,"
                + "FOREIGN KEY(thread_id) REFERENCES " + TABLE_THREADS + "(id) ON DELETE CASCADE)");
    }

    public void shutdown() {
        writeExecutor.shutdown();
        try {
            if (!writeExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            writeExecutor.shutdownNow();
        }
        close();
    }

    public void addInvalidationListener(@NonNull Runnable listener) {
        invalidationListeners.add(listener);
    }

    public void removeInvalidationListener(@NonNull Runnable listener) {
        invalidationListeners.remove(listener);
    }

    public String ensureDefaultThread(String scId) {
        storageLock.lock();
        try {
            String threadId = VoidPortChatThreadService.threadIdForProject(safe(scId, "unknown"));
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                ensureThreadLocked(db, scId, threadId, "Principal");
                if (!hasCurrentThreadLocked(db, scId)) {
                    setCurrentThreadLocked(db, scId, threadId);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return threadId;
        } finally {
            storageLock.unlock();
        }
    }

    public String getCurrentThreadId(String scId) {
        storageLock.lock();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT c.thread_id FROM " + TABLE_CURRENT + " c JOIN " + TABLE_THREADS
                        + " t ON t.id = c.thread_id WHERE c.project_id = ? AND t.project_id = ? LIMIT 1",
                new String[]{safe(scId, ""), safe(scId, "")})) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } finally {
            storageLock.unlock();
        }
        return ensureDefaultThread(scId);
    }

    public void setCurrentThreadId(String scId, String threadId) {
        if (!ChatMessage.hasVisibleText(threadId)) return;
        storageLock.lock();
        try {
            SQLiteDatabase db = getWritableDatabase();
            if (!threadBelongsToProjectLocked(db, scId, threadId)) return;
            setCurrentThreadLocked(db, scId, threadId);
        } finally {
            storageLock.unlock();
        }
    }

    public String createThread(String scId) {
        String threadId = "axion:" + safe(scId, "unknown") + ":" + UUID.randomUUID();
        storageLock.lock();
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                ensureThreadLocked(db, scId, threadId, "Nova conversa");
                setCurrentThreadLocked(db, scId, threadId);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return threadId;
        } finally {
            storageLock.unlock();
        }
    }

    public void saveMessage(String scId, String threadId, @Nullable ChatMessage message) {
        if (message == null || message.isStreaming()) return;
        nextGeneration(scId, threadId);
        MessageRecord record = MessageRecord.from(message);
        storageLock.lock();
        try {
            SQLiteDatabase db = getWritableDatabase();
            String safeThread = resolveThreadIdLocked(db, scId, threadId);
            if (safeThread == null) {
                return;
            }
            db.beginTransaction();
            try {
                int ordinal = 0;
                try (Cursor cursor = db.rawQuery("SELECT COALESCE(MAX(ordinal) + 1, 0) FROM "
                        + TABLE_MESSAGES + " WHERE thread_id = ?", new String[]{safeThread})) {
                    if (cursor.moveToFirst()) ordinal = cursor.getInt(0);
                }
                db.insertOrThrow(TABLE_MESSAGES, null,
                        record.toValues(scId, safeThread, ordinal));
                touchThreadLocked(db, safeThread);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            storageLock.unlock();
        }
        notifyMessagesChanged();
    }

    /** Updates one known chat row without rewriting the complete conversation. */
    public void saveMessageAtOrdinal(String scId, String threadId, int ordinal,
                                     @Nullable ChatMessage message) {
        if (message == null || ordinal < 0) return;
        long generation = nextGeneration(scId, threadId);
        MessageRecord record = MessageRecord.from(message);
        Runnable write = () -> {
            storageLock.lock();
            try {
                if (currentGeneration(scId, threadId) != generation) return;
                SQLiteDatabase db = getWritableDatabase();
                String safeThread = resolveThreadIdLocked(db, scId, threadId);
                if (safeThread == null) return;
                db.insertWithOnConflict(
                        TABLE_MESSAGES,
                        null,
                        record.toValues(scId, safeThread, ordinal),
                        SQLiteDatabase.CONFLICT_REPLACE);
                touchThreadLocked(db, safeThread);
            } finally {
                storageLock.unlock();
            }
            notifyMessagesChanged();
        };
        if (writeExecutor.isShutdown()) {
            write.run();
            return;
        }
        try {
            writeExecutor.execute(write);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            write.run();
        }
    }

    public void saveHistory(String scId, String threadId, List<ChatMessage> messages) {
        long generation = nextGeneration(scId, threadId);
        persistIfCurrent(scId, threadId, snapshot(messages), generation);
    }

    public void saveHistoryAsync(String scId, String threadId, List<ChatMessage> messages) {
        List<MessageRecord> records = snapshot(messages);
        long generation = nextGeneration(scId, threadId);
        Runnable write = () -> persistIfCurrent(scId, threadId, records, generation);
        if (writeExecutor.isShutdown()) {
            write.run();
            return;
        }
        try {
            writeExecutor.execute(write);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            write.run();
        }
    }

    public List<ChatMessage> loadHistory(String scId, String threadId) {
        storageLock.lock();
        try {
            SQLiteDatabase db = getReadableDatabase();
            String safeThread = resolveThreadIdLocked(db, scId, threadId);
            if (safeThread == null) return new ArrayList<>();
            List<ChatMessage> result = new ArrayList<>();
            try (Cursor cursor = db.query(TABLE_MESSAGES, null,
                    "project_id = ? AND thread_id = ?",
                    new String[]{safe(scId, ""), safeThread}, null, null, "ordinal ASC")) {
                while (cursor.moveToNext()) result.add(messageFromCursor(cursor));
            }
            return result;
        } finally {
            storageLock.unlock();
        }
    }

    public boolean containsReferenceUri(String uri) {
        if (!ChatMessage.hasVisibleText(uri)) return false;
        String expected = uri.trim();
        storageLock.lock();
        try (Cursor cursor = getReadableDatabase().query(TABLE_MESSAGES,
                new String[]{"references_json"}, "references_json LIKE ?",
                new String[]{"%" + escapeLike(expected) + "%"}, null, null, null)) {
            while (cursor.moveToNext()) {
                JSONArray array = parseArray(cursor.getString(0));
                for (int i = 0; i < array.length(); i++) {
                    Object value = array.opt(i);
                    if (value != null && value.toString().contains(expected)) return true;
                }
            }
            return false;
        } finally {
            storageLock.unlock();
        }
    }

    public void clearHistory(String scId, String threadId) {
        saveHistory(scId, threadId, Collections.emptyList());
    }

    public void deleteProjectHistory(String scId) {
        storageLock.lock();
        try {
            getWritableDatabase().delete(TABLE_THREADS, "project_id = ?",
                    new String[]{safe(scId, "")});
        } finally {
            storageLock.unlock();
        }
        notifyMessagesChanged();
    }

    public List<ChatThread> getThreads(String scId) {
        List<ChatThread> result = new ArrayList<>();
        storageLock.lock();
        try (Cursor cursor = getReadableDatabase().query(TABLE_THREADS,
                new String[]{"id", "project_id", "title", "summary", "created_at", "updated_at",
                        "active_model", "pinned"},
                "project_id = ?", new String[]{safe(scId, "")}, null, null,
                "pinned DESC, updated_at DESC")) {
            while (cursor.moveToNext()) {
                result.add(new ChatThread(cursor.getString(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getLong(4), cursor.getLong(5), cursor.getString(6),
                        cursor.getInt(7) != 0));
            }
        } finally {
            storageLock.unlock();
        }
        return result;
    }

    public void updateThreadSummary(String scId, String threadId, String title,
                                    String summary, String activeModel) {
        storageLock.lock();
        try {
            SQLiteDatabase db = getWritableDatabase();
            String safeThread = resolveThreadIdLocked(db, scId, threadId);
            if (safeThread == null) {
                return;
            }
            ContentValues values = new ContentValues();
            values.put("summary", safe(summary, ""));
            values.put("active_model", safe(activeModel, ""));
            values.put("updated_at", System.currentTimeMillis());
            db.update(TABLE_THREADS, values, "id = ?", new String[]{safeThread});
            ContentValues automaticTitle = new ContentValues();
            automaticTitle.put("title", safe(title, ""));
            db.update(TABLE_THREADS, automaticTitle, "id = ? AND manual_title = 0",
                    new String[]{safeThread});
        } finally {
            storageLock.unlock();
        }
    }

    public void renameThread(String scId, String threadId, String title) {
        if (!ChatMessage.hasVisibleText(title)) return;
        storageLock.lock();
        try {
            ContentValues values = new ContentValues();
            values.put("title", title.trim());
            values.put("manual_title", 1);
            values.put("updated_at", System.currentTimeMillis());
            getWritableDatabase().update(TABLE_THREADS, values,
                    "id = ? AND project_id = ?", new String[]{safe(threadId, ""), safe(scId, "")});
        } finally {
            storageLock.unlock();
        }
    }

    public void setThreadPinned(String scId, String threadId, boolean pinned) {
        storageLock.lock();
        try {
            ContentValues values = new ContentValues();
            values.put("pinned", pinned ? 1 : 0);
            values.put("updated_at", System.currentTimeMillis());
            getWritableDatabase().update(TABLE_THREADS, values,
                    "id = ? AND project_id = ?", new String[]{safe(threadId, ""), safe(scId, "")});
        } finally {
            storageLock.unlock();
        }
    }

    public void deleteThread(String scId, String threadId) {
        if (!ChatMessage.hasVisibleText(threadId)) return;
        // Invalidate every queued snapshot for this exact conversation before
        // deleting it. Otherwise an older asynchronous save could run afterward
        // and recreate the row that the user just removed.
        nextGeneration(scId, threadId);
        storageLock.lock();
        try {
            getWritableDatabase().delete(TABLE_THREADS, "id = ? AND project_id = ?",
                    new String[]{threadId, safe(scId, "")});
        } finally {
            storageLock.unlock();
        }
        notifyMessagesChanged();
    }

    /** Records one logical AI run entirely in the app-private SQLite database. */
    public void beginOperation(String scId, String threadId, String operationId,
                               String requestText, String status) {
        if (!ChatMessage.hasVisibleText(operationId)) return;
        storageLock.lock();
        try {
            SQLiteDatabase db = getWritableDatabase();
            String safeThread = resolveThreadIdLocked(db, scId, threadId);
            if (safeThread == null) return;
            long now = System.currentTimeMillis();
            db.beginTransaction();
            try {
                ContentValues interrupted = new ContentValues();
                interrupted.put("state", "interrupted");
                interrupted.put("status", "Replaced by a newer local operation");
                interrupted.put("updated_at", now);
                db.update(TABLE_OPERATIONS, interrupted,
                        "project_id = ? AND thread_id = ? AND state = 'running'",
                        new String[]{safe(scId, ""), safeThread});

                ContentValues values = new ContentValues();
                values.put("operation_id", operationId.trim());
                values.put("project_id", safe(scId, ""));
                values.put("thread_id", safeThread);
                values.put("request_text", safe(requestText, ""));
                values.put("state", "running");
                values.put("status", safe(status, ""));
                values.put("started_at", now);
                values.put("updated_at", now);
                db.insertWithOnConflict(TABLE_OPERATIONS, null, values,
                        SQLiteDatabase.CONFLICT_REPLACE);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            storageLock.unlock();
        }
    }

    public void updateOperation(String scId, String threadId, String operationId,
                                String state, String status) {
        if (!ChatMessage.hasVisibleText(operationId)) return;
        storageLock.lock();
        try {
            ContentValues values = new ContentValues();
            if (ChatMessage.hasVisibleText(state)) values.put("state", state.trim());
            values.put("status", safe(status, ""));
            values.put("updated_at", System.currentTimeMillis());
            getWritableDatabase().update(TABLE_OPERATIONS, values,
                    "operation_id = ? AND project_id = ? AND thread_id = ?",
                    new String[]{operationId.trim(), safe(scId, ""), safe(threadId, "")});
        } finally {
            storageLock.unlock();
        }
    }

    /** Converts process-owned work into an explicit recoverable local state. */
    public int interruptRunningOperations(String scId, String threadId, String status) {
        storageLock.lock();
        try {
            ContentValues values = new ContentValues();
            values.put("state", "interrupted");
            values.put("status", safe(status, ""));
            values.put("updated_at", System.currentTimeMillis());
            return getWritableDatabase().update(TABLE_OPERATIONS, values,
                    "project_id = ? AND thread_id = ? AND state = 'running'",
                    new String[]{safe(scId, ""), safe(threadId, "")});
        } finally {
            storageLock.unlock();
        }
    }

    public void saveCompactionState(String scId, String threadId, String summary,
                                    int compactedUntil) {
        storageLock.lock();
        try {
            SQLiteDatabase db = getWritableDatabase();
            String safeThread = resolveThreadIdLocked(db, scId, threadId);
            if (safeThread == null) return;
            ContentValues values = new ContentValues();
            values.put("thread_id", safeThread);
            values.put("project_id", safe(scId, ""));
            values.put("history_summary", safe(summary, ""));
            values.put("compacted_until", Math.max(0, compactedUntil));
            values.put("updated_at", System.currentTimeMillis());
            db.insertWithOnConflict(TABLE_CONTEXT, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } finally {
            storageLock.unlock();
        }
    }

    public CompactionState loadCompactionState(String scId, String threadId) {
        storageLock.lock();
        try (Cursor cursor = getReadableDatabase().query(TABLE_CONTEXT,
                new String[]{"history_summary", "compacted_until"},
                "project_id = ? AND thread_id = ?",
                new String[]{safe(scId, ""), safe(threadId, "")},
                null, null, null, "1")) {
            if (cursor.moveToFirst()) {
                return new CompactionState(cursor.getString(0), cursor.getInt(1));
            }
            return new CompactionState("", 0);
        } finally {
            storageLock.unlock();
        }
    }

    public void clearCompactionState(String scId, String threadId) {
        storageLock.lock();
        try {
            getWritableDatabase().delete(TABLE_CONTEXT,
                    "project_id = ? AND thread_id = ?",
                    new String[]{safe(scId, ""), safe(threadId, "")});
        } finally {
            storageLock.unlock();
        }
    }

    /** Loads one virtual adapter window, including stable ad slots for free accounts. */
    public PagingWindow loadPagingWindow(String scId, String threadId, int requestedStart,
                                         int loadSize, boolean showAds, int adEvery) {
        storageLock.lock();
        try {
            SQLiteDatabase db = getReadableDatabase();
            String resolvedThread = resolveThreadIdLocked(db, scId, threadId);
            if (resolvedThread == null) return new PagingWindow(0, 0, Collections.emptyList());
            List<Integer> ordinals = new ArrayList<>();
            List<String> roles = new ArrayList<>();
            try (Cursor cursor = db.query(TABLE_MESSAGES, new String[]{"ordinal", "role"},
                    "project_id = ? AND thread_id = ?", new String[]{safe(scId, ""), resolvedThread},
                    null, null, "ordinal ASC")) {
                while (cursor.moveToNext()) {
                    ordinals.add(cursor.getInt(0));
                    roles.add(cursor.getString(1));
                }
            }
            // adEvery = conversation turns between ads; ads sit only before user messages
            int[] rows = ChatMessagePositionMapper.layout(ordinals, roles, showAds, adEvery);
            int totalCount = rows.length;
            int safeLoadSize = Math.max(1, loadSize);
            int start = requestedStart < 0 || requestedStart >= totalCount
                    ? Math.max(0, totalCount - safeLoadSize)
                    : Math.max(0, requestedStart);
            int end = Math.min(totalCount, start + safeLoadSize);
            if (start >= end) return new PagingWindow(start, totalCount, Collections.emptyList());

            int minOrdinal = Integer.MAX_VALUE;
            int maxOrdinal = -1;
            for (int position = start; position < end; position++) {
                if (!ChatMessagePositionMapper.isAd(rows[position])) {
                    minOrdinal = Math.min(minOrdinal, rows[position]);
                    maxOrdinal = Math.max(maxOrdinal, rows[position]);
                }
            }
            Map<Integer, ChatPagingItem> messagesByOrdinal = new HashMap<>();
            if (maxOrdinal >= minOrdinal) {
                try (Cursor cursor = db.query(TABLE_MESSAGES, null,
                        "project_id = ? AND thread_id = ? AND ordinal BETWEEN ? AND ?",
                        new String[]{safe(scId, ""), resolvedThread,
                                String.valueOf(minOrdinal), String.valueOf(maxOrdinal)},
                        null, null, "ordinal ASC")) {
                    while (cursor.moveToNext()) {
                        int ordinal = intColumn(cursor, "ordinal");
                        ChatMessage message = messageFromCursor(cursor);
                        messagesByOrdinal.put(ordinal, ChatPagingItem.message(
                                resolvedThread, ordinal, message, longColumn(cursor, "content_version")));
                    }
                }
            }

            List<ChatPagingItem> items = new ArrayList<>(end - start);
            for (int position = start; position < end; position++) {
                int row = rows[position];
                if (ChatMessagePositionMapper.isAd(row)) {
                    items.add(ChatPagingItem.ad(resolvedThread, ChatMessagePositionMapper.adSlot(row)));
                } else {
                    ChatPagingItem item = messagesByOrdinal.get(row);
                    if (item != null) items.add(item);
                }
            }
            return new PagingWindow(start, totalCount, items);
        } finally {
            storageLock.unlock();
        }
    }

    private void persistIfCurrent(String scId, String threadId, List<MessageRecord> records,
                                  long generation) {
        storageLock.lock();
        try {
            if (currentGeneration(scId, threadId) != generation) return;
            SQLiteDatabase db = getWritableDatabase();
            String safeThread = resolveThreadIdLocked(db, scId, threadId);
            if (safeThread == null) {
                return;
            }
            db.beginTransaction();
            try {
                db.delete(TABLE_MESSAGES, "thread_id = ?", new String[]{safeThread});
                for (int i = 0; i < records.size(); i++) {
                    db.insertOrThrow(TABLE_MESSAGES, null,
                            records.get(i).toValues(scId, safeThread, i));
                }
                touchThreadLocked(db, safeThread);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            storageLock.unlock();
        }
        notifyMessagesChanged();
    }

    private List<MessageRecord> snapshot(@Nullable List<ChatMessage> messages) {
        List<MessageRecord> records = new ArrayList<>();
        if (messages == null) return records;
        for (ChatMessage message : new ArrayList<>(messages)) {
            if (message != null) records.add(MessageRecord.from(message));
        }
        return records;
    }

    private long nextGeneration(String scId, String threadId) {
        return writeGenerations.computeIfAbsent(generationKey(scId, threadId),
                ignored -> new AtomicLong()).incrementAndGet();
    }

    private long currentGeneration(String scId, String threadId) {
        AtomicLong value = writeGenerations.get(generationKey(scId, threadId));
        return value == null ? 0L : value.get();
    }

    private String generationKey(String scId, String threadId) {
        return safe(scId, "") + "\n" + safe(threadId, "");
    }

    private void notifyMessagesChanged() {
        for (Runnable listener : invalidationListeners) {
            try {
                listener.run();
            } catch (RuntimeException error) {
                Log.w(TAG, "Paging invalidation listener failed", error);
            }
        }
    }

    private void ensureThreadLocked(SQLiteDatabase db, String scId, String threadId, String title) {
        ContentValues values = new ContentValues();
        long now = System.currentTimeMillis();
        values.put("id", threadId);
        values.put("project_id", safe(scId, ""));
        values.put("title", safe(title, ""));
        values.put("summary", "");
        values.put("created_at", now);
        values.put("updated_at", now);
        values.put("active_model", "");
        values.put("pinned", 0);
        values.put("manual_title", 0);
        db.insertWithOnConflict(TABLE_THREADS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    @Nullable
    private String resolveThreadIdLocked(SQLiteDatabase db, String scId, String threadId) {
        if (ChatMessage.hasVisibleText(threadId) && threadBelongsToProjectLocked(db, scId, threadId)) {
            return threadId;
        }
        return null;
    }

    private boolean threadBelongsToProjectLocked(SQLiteDatabase db, String scId, String threadId) {
        try (Cursor cursor = db.rawQuery("SELECT 1 FROM " + TABLE_THREADS
                + " WHERE id = ? AND project_id = ? LIMIT 1",
                new String[]{safe(threadId, ""), safe(scId, "")})) {
            return cursor.moveToFirst();
        }
    }

    private boolean hasCurrentThreadLocked(SQLiteDatabase db, String scId) {
        try (Cursor cursor = db.rawQuery("SELECT 1 FROM " + TABLE_CURRENT
                + " WHERE project_id = ? LIMIT 1", new String[]{safe(scId, "")})) {
            return cursor.moveToFirst();
        }
    }

    private void setCurrentThreadLocked(SQLiteDatabase db, String scId, String threadId) {
        ContentValues values = new ContentValues();
        values.put("project_id", safe(scId, ""));
        values.put("thread_id", threadId);
        db.insertWithOnConflict(TABLE_CURRENT, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void touchThreadLocked(SQLiteDatabase db, String threadId) {
        ContentValues values = new ContentValues();
        values.put("updated_at", System.currentTimeMillis());
        db.update(TABLE_THREADS, values, "id = ?", new String[]{threadId});
    }

    private int countMessagesLocked(SQLiteDatabase db, String scId, String threadId) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MESSAGES
                + " WHERE project_id = ? AND thread_id = ?",
                new String[]{safe(scId, ""), safe(threadId, "")})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private ChatMessage messageFromCursor(Cursor cursor) {
        ChatMessage message = new ChatMessage();
        message.setType(intColumn(cursor, "message_type"));
        message.setDisplayContent(stringColumn(cursor, "display_content"));
        message.setLlmContent(stringColumn(cursor, "llm_content"));
        message.setTimestamp(longColumn(cursor, "timestamp"));
        message.setToolName(nullableStringColumn(cursor, "tool_name"));
        message.setToolArgs(nullableStringColumn(cursor, "tool_args"));
        message.setToolRunning(boolColumn(cursor, "tool_running"));
        message.setToolResult(nullableStringColumn(cursor, "tool_result"));
        message.setToolError(boolColumn(cursor, "tool_error"));
        message.setToolId(nullableStringColumn(cursor, "tool_id"));
        message.setToolState(nullableStringColumn(cursor, "tool_state"));
        message.setMcpServerName(nullableStringColumn(cursor, "mcp_server_name"));
        message.setExpanded(boolColumn(cursor, "expanded"));
        message.setStatus(nullableStringColumn(cursor, "status"));
        message.setRequiresApproval(boolColumn(cursor, "requires_approval"));
        message.setApproved(boolColumn(cursor, "approved"));
        message.setRejected(boolColumn(cursor, "rejected"));
        message.setCheckpointId(nullableStringColumn(cursor, "checkpoint_id"));
        message.setCheckpointType(nullableStringColumn(cursor, "checkpoint_type"));
        message.setCheckpointSnapshotsJson(nullableStringColumn(cursor, "checkpoint_snapshots_json"));
        message.setReasoning(nullableStringColumn(cursor, "reasoning"));
        message.setAnthropicReasoningJson(nullableStringColumn(cursor, "anthropic_reasoning_json"));
        message.setReasoningExpanded(boolColumn(cursor, "reasoning_expanded"));
        message.setStreaming(boolColumn(cursor, "streaming"));
        message.setContextPayload(nullableStringColumn(cursor, "context_payload"));
        message.setBeingEdited(boolColumn(cursor, "being_edited"));
        message.setStagingSelections(parseReferences(stringColumn(cursor, "references_json")));
        return message;
    }

    private static List<ChatReference> parseReferences(String json) {
        List<ChatReference> references = new ArrayList<>();
        JSONArray array = parseArray(json);
        for (int i = 0; i < array.length(); i++) {
            if (array.optJSONObject(i) != null) references.add(ChatReference.fromJson(array.optJSONObject(i)));
        }
        return references;
    }

    private static JSONArray parseArray(String json) {
        try {
            return new JSONArray(json == null ? "[]" : json);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static int intColumn(Cursor cursor, String name) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(name));
    }

    private static long longColumn(Cursor cursor, String name) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(name));
    }

    private static boolean boolColumn(Cursor cursor, String name) {
        return intColumn(cursor, name) != 0;
    }

    private static String stringColumn(Cursor cursor, String name) {
        String value = nullableStringColumn(cursor, name);
        return value == null ? "" : value;
    }

    @Nullable
    private static String nullableStringColumn(Cursor cursor, String name) {
        int index = cursor.getColumnIndexOrThrow(name);
        return cursor.isNull(index) ? null : cursor.getString(index);
    }

    private static String safe(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static String escapeLike(String value) {
        return value.replace("%", "\\%").replace("_", "\\_");
    }

    private void discardLegacyJson(Context context) {
        File files = context.getFilesDir();
        for (String suffix : new String[]{"", ".bak", ".tmp"}) {
            File legacy = new File(files, LEGACY_JSON + suffix);
            if (legacy.exists() && !legacy.delete()) {
                Log.w(TAG, "Could not delete legacy chat file " + legacy.getName());
            }
        }
    }

    public static final class PagingWindow {
        public final int start;
        public final int totalCount;
        public final List<ChatPagingItem> items;

        PagingWindow(int start, int totalCount, List<ChatPagingItem> items) {
            this.start = start;
            this.totalCount = totalCount;
            this.items = items;
        }
    }

    public static final class CompactionState {
        public final String summary;
        public final int compactedUntil;

        CompactionState(String summary, int compactedUntil) {
            this.summary = safe(summary, "");
            this.compactedUntil = Math.max(0, compactedUntil);
        }
    }

    private static final class MessageRecord {
        final ChatMessage message;
        final String referencesJson;
        final long contentVersion;

        private MessageRecord(ChatMessage message, String referencesJson, long contentVersion) {
            this.message = message;
            this.referencesJson = referencesJson;
            this.contentVersion = contentVersion;
        }

        static MessageRecord from(ChatMessage source) {
            ChatMessage copy = new ChatMessage();
            copy.setType(source.getType());
            copy.setDisplayContent(source.getDisplayContent());
            copy.setLlmContent(source.getLlmContent());
            copy.setTimestamp(source.getTimestamp());
            copy.setToolName(source.getToolName());
            copy.setToolArgs(source.getToolArgs());
            copy.setToolRunning(source.isToolRunning());
            copy.setToolResult(source.getToolResult());
            copy.setToolError(source.isToolError());
            copy.setToolId(source.getToolId());
            copy.setToolState(source.getToolState());
            copy.setMcpServerName(source.getMcpServerName());
            copy.setExpanded(source.isExpanded());
            copy.setStatus(source.getStatus());
            copy.setRequiresApproval(source.getRequiresApproval());
            copy.setApproved(source.isApproved());
            copy.setRejected(source.isRejected());
            copy.setCheckpointId(source.getCheckpointId());
            copy.setCheckpointType(source.getCheckpointType());
            copy.setCheckpointSnapshotsJson(source.getCheckpointSnapshotsJson());
            copy.setReasoning(source.getReasoning());
            copy.setAnthropicReasoningJson(source.getAnthropicReasoningJson());
            copy.setReasoningExpanded(source.isReasoningExpanded());
            copy.setStreaming(source.isStreaming());
            copy.setContextPayload(source.getContextPayload());
            copy.setBeingEdited(source.isBeingEdited());
            copy.setStagingSelections(source.getStagingSelections());
            JSONArray references = new JSONArray();
            for (ChatReference reference : source.getStagingSelections()) {
                if (reference != null) references.put(reference.toJson());
            }
            long version = fingerprint(copy, references.toString());
            return new MessageRecord(copy, references.toString(), version);
        }

        ContentValues toValues(String scId, String threadId, int ordinal) {
            ContentValues values = new ContentValues();
            values.put("project_id", safe(scId, ""));
            values.put("thread_id", threadId);
            values.put("ordinal", ordinal);
            values.put("role", message.getRole());
            values.put("message_type", message.getType());
            values.put("display_content", message.getDisplayContent());
            values.put("llm_content", message.getLlmContent());
            values.put("timestamp", message.getTimestamp());
            putNullable(values, "tool_name", message.getToolName());
            putNullable(values, "tool_args", message.getToolArgs());
            values.put("tool_running", message.isToolRunning() ? 1 : 0);
            putNullable(values, "tool_result", message.getToolResult());
            values.put("tool_error", message.isToolError() ? 1 : 0);
            putNullable(values, "tool_id", message.getToolId());
            putNullable(values, "tool_state", message.getToolState());
            putNullable(values, "mcp_server_name", message.getMcpServerName());
            values.put("expanded", message.isExpanded() ? 1 : 0);
            putNullable(values, "status", message.getStatus());
            values.put("requires_approval", message.getRequiresApproval() ? 1 : 0);
            values.put("approved", message.isApproved() ? 1 : 0);
            values.put("rejected", message.isRejected() ? 1 : 0);
            putNullable(values, "checkpoint_id", message.getCheckpointId());
            putNullable(values, "checkpoint_type", message.getCheckpointType());
            putNullable(values, "checkpoint_snapshots_json", message.getCheckpointSnapshotsJson());
            putNullable(values, "reasoning", message.getReasoning());
            putNullable(values, "anthropic_reasoning_json", message.getAnthropicReasoningJson());
            values.put("reasoning_expanded", message.isReasoningExpanded() ? 1 : 0);
            values.put("streaming", message.isStreaming() ? 1 : 0);
            putNullable(values, "context_payload", message.getContextPayload());
            values.put("being_edited", message.isBeingEdited() ? 1 : 0);
            values.put("references_json", referencesJson);
            values.put("content_version", contentVersion);
            return values;
        }

        private static void putNullable(ContentValues values, String key, @Nullable String value) {
            if (value == null) values.putNull(key); else values.put(key, value);
        }

        private static long fingerprint(ChatMessage message, String references) {
            String value = message.getType() + "|" + message.getDisplayContent() + "|"
                    + message.getLlmContent() + "|" + message.getTimestamp() + "|"
                    + message.getToolName() + "|" + message.getToolArgs() + "|"
                    + message.isToolRunning() + "|" + message.getToolResult() + "|"
                    + message.isToolError() + "|" + message.getToolId() + "|"
                    + message.getToolState() + "|" + message.getMcpServerName() + "|"
                    + message.isExpanded() + "|" + message.getStatus() + "|"
                    + message.getRequiresApproval() + "|" + message.isApproved() + "|"
                    + message.isRejected() + "|" + message.getCheckpointId() + "|"
                    + message.getCheckpointType() + "|" + message.getCheckpointSnapshotsJson() + "|"
                    + message.getReasoning() + "|" + message.getAnthropicReasoningJson() + "|"
                    + message.isReasoningExpanded() + "|" + message.isStreaming() + "|"
                    + message.getContextPayload() + "|" + message.isBeingEdited() + "|" + references;
            return (((long) value.hashCode()) << 32) ^ value.length();
        }
    }
}

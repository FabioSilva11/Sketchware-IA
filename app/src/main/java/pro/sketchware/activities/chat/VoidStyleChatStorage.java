package pro.sketchware.activities.chat;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import pro.sketchware.activities.chat.port.VoidPortChatThreadService;

/**
 * Persists Void-shaped chat threads ({@code void.chatThreadStorageII}) with
 * role-based messages, file locking and asynchronous writes.
 */
public class VoidStyleChatStorage {
    private static final String TAG = "VoidStyleChatStorage";
    public static final String STORAGE_KEY = "void.chatThreadStorageII";
    private static final String FILE_NAME = STORAGE_KEY + ".json";

    private final File storageFile;
    private final ReentrantLock storageLock = new ReentrantLock();
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "void-chat-storage");
        thread.setDaemon(true);
        return thread;
    });

    public VoidStyleChatStorage(Context context) {
        this.storageFile = new File(context.getFilesDir(), FILE_NAME);
    }

    public void shutdown() {
        writeExecutor.shutdownNow();
    }

    public boolean exists() {
        return storageFile.exists() && storageFile.length() > 0;
    }

    public String ensureDefaultThread(String scId) {
        storageLock.lock();
        try {
            String threadId = VoidPortChatThreadService.threadIdForProject(scId);
            JSONObject root = readRootLocked();
            JSONObject threads = root.optJSONObject("allThreads");
            if (threads == null) {
                threads = new JSONObject();
                put(root, "allThreads", threads);
            }
            if (!threads.has(threadId)) {
                put(threads, threadId, makeThread(threadId, scId, "Principal", "", "", System.currentTimeMillis()));
                if (!root.has("currentThreadId")) {
                    put(root, "currentThreadId", threadId);
                }
                writeRootLocked(root);
            }
            return threadId;
        } finally {
            storageLock.unlock();
        }
    }

    public String createThread(String scId) {
        storageLock.lock();
        try {
            JSONObject root = readRootLocked();
            JSONObject threads = root.optJSONObject("allThreads");
            if (threads == null) {
                threads = new JSONObject();
                put(root, "allThreads", threads);
            }
            String threadId = "sketchware:" + safe(scId, "unknown") + ":" + UUID.randomUUID();
            put(threads, threadId, makeThread(threadId, scId, "Nova conversa", "", "", System.currentTimeMillis()));
            put(root, "currentThreadId", threadId);
            writeRootLocked(root);
            return threadId;
        } finally {
            storageLock.unlock();
        }
    }

    public void saveMessage(String scId, String threadId, ChatMessage message) {
        if (message == null || message.isStreaming()) {
            return;
        }
        storageLock.lock();
        try {
            List<ChatMessage> messages = loadHistoryLocked(scId, threadId);
            messages.add(message);
            persistHistoryLocked(scId, threadId, messages);
        } finally {
            storageLock.unlock();
        }
    }

    public void saveHistory(String scId, String threadId, List<ChatMessage> messages) {
        storageLock.lock();
        try {
            persistHistoryLocked(scId, threadId, messages);
        } finally {
            storageLock.unlock();
        }
    }

    public void saveHistoryAsync(String scId, String threadId, List<ChatMessage> messages) {
        List<ChatMessage> snapshot = snapshotMessages(messages);
        if (writeExecutor.isShutdown()) {
            // Executor was already shut down (e.g. onDestroy race); save synchronously
            // on a plain thread so no data is lost and no RejectedExecutionException is thrown.
            new Thread(() -> saveHistory(scId, threadId, snapshot), "void-chat-storage-fallback").start();
            return;
        }
        try {
            writeExecutor.execute(() -> saveHistory(scId, threadId, snapshot));
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Race condition: executor was shut down between the isShutdown() check and execute().
            new Thread(() -> saveHistory(scId, threadId, snapshot), "void-chat-storage-fallback").start();
        }
    }

    public List<ChatMessage> loadHistory(String scId, String threadId) {
        storageLock.lock();
        try {
            return loadHistoryLocked(scId, threadId);
        } finally {
            storageLock.unlock();
        }
    }

    /**
     * Returns whether a persisted chat thread still references the given SAF URI.
     * This is intentionally global across projects/threads because Android grants
     * URI access per application, not per chat.
     */
    public boolean containsReferenceUri(String uri) {
        if (!ChatMessage.hasVisibleText(uri)) {
            return false;
        }
        storageLock.lock();
        try {
            return jsonContainsExactValue(readRootLocked(), uri.trim());
        } finally {
            storageLock.unlock();
        }
    }

    public void clearHistory(String scId, String threadId) {
        saveHistory(scId, threadId, new ArrayList<>());
    }

    public void deleteProjectHistory(String scId) {
        storageLock.lock();
        try {
            JSONObject root = readRootLocked();
            JSONObject threads = root.optJSONObject("allThreads");
            if (threads == null) {
                return;
            }
            JSONArray names = threads.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                String id = names.optString(i, "");
                JSONObject thread = threads.optJSONObject(id);
                if (thread != null && safe(scId, "").equals(thread.optString("scId", ""))) {
                    threads.remove(id);
                }
            }
            writeRootLocked(root);
        } finally {
            storageLock.unlock();
        }
    }

    public List<ChatThread> getThreads(String scId) {
        ensureDefaultThread(scId);
        storageLock.lock();
        try {
            List<ChatThread> result = new ArrayList<>();
            JSONObject threads = readRootLocked().optJSONObject("allThreads");
            JSONArray names = threads == null ? null : threads.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                JSONObject thread = threads.optJSONObject(names.optString(i, ""));
                if (thread == null || !safe(scId, "").equals(thread.optString("scId", ""))) {
                    continue;
                }
                result.add(new ChatThread(
                        thread.optString("id", ""),
                        thread.optString("scId", ""),
                        thread.optString("title", ""),
                        thread.optString("summary", ""),
                        thread.optLong("createdAt", 0L),
                        thread.optLong("lastModified", 0L),
                        thread.optString("activeModel", ""),
                        thread.optBoolean("isPinned", false)
                ));
            }
            result.sort((a, b) -> {
                if (a.pinned != b.pinned) {
                    return a.pinned ? -1 : 1;
                }
                return Long.compare(b.updatedAt, a.updatedAt);
            });
            return result;
        } finally {
            storageLock.unlock();
        }
    }

    public void updateThreadSummary(String scId, String threadId, String title, String summary, String activeModel) {
        storageLock.lock();
        try {
            String safeThreadId = safeThreadId(scId, threadId);
            JSONObject root = readRootLocked();
            JSONObject threads = root.optJSONObject("allThreads");
            if (threads == null) {
                threads = new JSONObject();
                put(root, "allThreads", threads);
            }
            JSONObject thread = threads.optJSONObject(safeThreadId);
            if (thread == null) {
                thread = makeThread(safeThreadId, scId, title, summary, activeModel, System.currentTimeMillis());
            }
            if (!thread.optBoolean("manualTitle", false)) {
                put(thread, "title", safe(title, ""));
            }
            put(thread, "summary", safe(summary, ""));
            put(thread, "activeModel", safe(activeModel, ""));
            put(thread, "lastModified", System.currentTimeMillis());
            put(threads, safeThreadId, thread);
            writeRootLocked(root);
        } finally {
            storageLock.unlock();
        }
    }

    public void renameThread(String scId, String threadId, String title) {
        if (!ChatMessage.hasVisibleText(title)) {
            return;
        }
        storageLock.lock();
        try {
            String safeThreadId = safeThreadId(scId, threadId);
            JSONObject root = readRootLocked();
            JSONObject threads = root.optJSONObject("allThreads");
            if (threads == null) {
                return;
            }
            JSONObject thread = threads.optJSONObject(safeThreadId);
            if (thread == null) {
                return;
            }
            put(thread, "title", title.trim());
            put(thread, "manualTitle", true);
            put(thread, "lastModified", System.currentTimeMillis());
            put(threads, safeThreadId, thread);
            writeRootLocked(root);
        } finally {
            storageLock.unlock();
        }
    }

    public void setThreadPinned(String scId, String threadId, boolean pinned) {
        storageLock.lock();
        try {
            String safeThreadId = safeThreadId(scId, threadId);
            JSONObject root = readRootLocked();
            JSONObject threads = root.optJSONObject("allThreads");
            if (threads == null) {
                return;
            }
            JSONObject thread = threads.optJSONObject(safeThreadId);
            if (thread == null) {
                return;
            }
            put(thread, "isPinned", pinned);
            put(thread, "lastModified", System.currentTimeMillis());
            put(threads, safeThreadId, thread);
            writeRootLocked(root);
        } finally {
            storageLock.unlock();
        }
    }

    public void deleteThread(String scId, String threadId) {
        if (!ChatMessage.hasVisibleText(threadId)) {
            return;
        }
        storageLock.lock();
        try {
            JSONObject root = readRootLocked();
            JSONObject threads = root.optJSONObject("allThreads");
            if (threads == null) {
                return;
            }
            JSONObject thread = threads.optJSONObject(threadId);
            if (thread == null || !safe(scId, "").equals(thread.optString("scId", ""))) {
                return;
            }
            threads.remove(threadId);
            if (threadId.equals(root.optString("currentThreadId", ""))) {
                root.remove("currentThreadId");
            }
            writeRootLocked(root);
        } finally {
            storageLock.unlock();
        }
    }

    private void persistHistoryLocked(String scId, String threadId, List<ChatMessage> messages) {
        String safeThreadId = safeThreadId(scId, threadId);
        JSONObject root = readRootLocked();
        JSONObject threads = root.optJSONObject("allThreads");
        if (threads == null) {
            threads = new JSONObject();
            put(root, "allThreads", threads);
        }
        JSONObject thread = threads.optJSONObject(safeThreadId);
        long now = System.currentTimeMillis();
        if (thread == null) {
            thread = makeThread(safeThreadId, scId, "Principal", "", "", now);
        }
        put(thread, "messages", serializeMessages(messages));
        put(thread, "state", buildThreadState(messages));
        put(thread, "lastModified", now);
        put(threads, safeThreadId, thread);
        put(root, "currentThreadId", safeThreadId);
        writeRootLocked(root);
    }

    private List<ChatMessage> loadHistoryLocked(String scId, String threadId) {
        String safeThreadId = safeThreadId(scId, threadId);
        JSONObject root = readRootLocked();
        JSONObject allThreads = root.optJSONObject("allThreads");
        JSONObject thread = allThreads == null ? null : allThreads.optJSONObject(safeThreadId);
        if (thread == null) {
            return new ArrayList<>();
        }
        return parseMessages(thread.optJSONArray("messages"));
    }

    private JSONObject readRootLocked() {
        String text = readText(storageFile);
        if (text == null || text.trim().isEmpty()) {
            return newRoot();
        }
        try {
            JSONObject root = new JSONObject(text);
            if (root.optJSONObject("allThreads") == null) {
                put(root, "allThreads", new JSONObject());
            }
            return root;
        } catch (Exception e) {
            Log.w(TAG, "Corrupt chat storage, starting fresh", e);
            return newRoot();
        }
    }

    private JSONObject newRoot() {
        JSONObject root = new JSONObject();
        put(root, "storageKey", STORAGE_KEY);
        put(root, "allThreads", new JSONObject());
        return root;
    }

    private JSONObject makeThread(String id, String scId, String title, String summary, String activeModel, long now) {
        JSONObject state = new JSONObject();
        put(state, "currCheckpointIdx", JSONObject.NULL);
        put(state, "stagingSelections", new JSONArray());

        JSONObject thread = new JSONObject();
        put(thread, "id", id);
        put(thread, "scId", safe(scId, ""));
        put(thread, "title", safe(title, ""));
        put(thread, "manualTitle", false);
        put(thread, "isPinned", false);
        put(thread, "summary", safe(summary, ""));
        put(thread, "activeModel", safe(activeModel, ""));
        put(thread, "createdAt", now);
        put(thread, "lastModified", now);
        put(thread, "state", state);
        put(thread, "messages", new JSONArray());
        return thread;
    }

    private JSONArray serializeMessages(List<ChatMessage> messages) {
        JSONArray array = new JSONArray();
        for (ChatMessage message : messages == null ? new ArrayList<ChatMessage>() : messages) {
            if (message != null && !message.isStreaming()) {
                array.put(serializeMessage(message));
            }
        }
        return array;
    }

    private JSONObject serializeMessage(ChatMessage message) {
        JSONObject object = new JSONObject();
        String role = message.getRole();
        put(object, "role", role);

        if (ChatMessage.ROLE_USER.equals(role)) {
            put(object, "content", message.getLlmContent());
            put(object, "displayContent", message.getDisplayContent());
            put(object, "timestamp", message.getTimestamp());
            put(object, "selections", serializeSelections(message.getStagingSelections()));
            JSONObject state = new JSONObject();
            put(state, "stagingSelections", serializeSelections(message.getStagingSelections()));
            put(state, "isBeingEdited", message.isBeingEdited());
            put(object, "state", state);
            return object;
        }

        if (ChatMessage.ROLE_ASSISTANT.equals(role)) {
            put(object, "displayContent", message.getDisplayContent());
            put(object, "content", message.getDisplayContent());
            put(object, "reasoning", safe(message.getReasoning(), ""));
            put(object, "reasoningExpanded", message.isReasoningExpanded());
            put(object, "anthropicReasoning", parseArray(message.getAnthropicReasoningJson()));
            put(object, "timestamp", message.getTimestamp());
            put(object, "status", message.getStatus());
            return object;
        }

        if (ChatMessage.ROLE_INTERRUPTED_STREAMING_TOOL.equals(role)) {
            put(object, "name", safe(message.getToolName(), ""));
            put(object, "mcpServerName", message.getMcpServerName());
            put(object, "timestamp", message.getTimestamp());
            return object;
        }

        if (ChatMessage.ROLE_CHECKPOINT.equals(role)) {
            put(object, "type", message.getCheckpointType());
            put(object, "message", message.getDisplayContent());
            put(object, "timestamp", message.getTimestamp());
            put(object, "status", message.getStatus());
            put(object, "checkpointId", message.getCheckpointId());
            put(object, "checkpointType", message.getCheckpointType());
            put(object, "checkpointSnapshots", parseObject(message.getCheckpointSnapshotsJson()));
            return object;
        }

        if (ChatMessage.ROLE_TOOL.equals(role)) {
            put(object, "name", safe(message.getToolName(), ""));
            put(object, "type", safe(message.getToolState(), "success"));
            put(object, "content", safe(message.getToolResult(), message.getDisplayContent()));
            put(object, "displayContent", message.getDisplayContent());
            put(object, "rawParams", parseObject(message.getToolArgs()));
            put(object, "params", parseObject(message.getToolArgs()));
            put(object, "id", message.getToolId());
            put(object, "result", message.getToolResult());
            put(object, "mcpServerName", message.getMcpServerName());
            put(object, "timestamp", message.getTimestamp());
            put(object, "toolRunning", message.isToolRunning());
            put(object, "toolError", message.isToolError());
            put(object, "requiresApproval", message.getRequiresApproval());
            put(object, "approved", message.isApproved());
            put(object, "rejected", message.isRejected());
            put(object, "isExpanded", message.isExpanded());
            return object;
        }

        put(object, "message", message.getDisplayContent());
        put(object, "type", message.getType());
        put(object, "timestamp", message.getTimestamp());
        return object;
    }

    private JSONObject buildThreadState(List<ChatMessage> messages) {
        JSONObject state = new JSONObject();
        int checkpointIdx = -1;
        for (int i = 0; messages != null && i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message != null && message.isCheckpoint()) {
                checkpointIdx = i;
            }
        }
        put(state, "currCheckpointIdx", checkpointIdx >= 0 ? checkpointIdx : JSONObject.NULL);
        put(state, "stagingSelections", new JSONArray());
        return state;
    }

    private List<ChatMessage> parseMessages(@Nullable JSONArray array) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            ChatMessage message = parseMessage(object);
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    @Nullable
    private ChatMessage parseMessage(JSONObject object) {
        if (object.has("role")) {
            return parseVoidMessage(object);
        }
        return parseLegacyMessage(object);
    }

    @Nullable
    private ChatMessage parseVoidMessage(JSONObject object) {
        String role = object.optString("role", "");
        long timestamp = object.optLong("timestamp", System.currentTimeMillis());

        if (ChatMessage.ROLE_USER.equals(role)) {
            ChatMessage message = new ChatMessage(object.optString("displayContent", ""), true, timestamp);
            message.setLlmContent(object.optString("content", message.getDisplayContent()));
            message.setBeingEdited(object.optJSONObject("state") != null
                    && object.optJSONObject("state").optBoolean("isBeingEdited", false));
            message.setStagingSelections(parseSelections(
                    object.optJSONArray("selections"),
                    object.optJSONObject("state") == null ? null : object.optJSONObject("state").optJSONArray("stagingSelections")
            ));
            return message;
        }

        if (ChatMessage.ROLE_ASSISTANT.equals(role)) {
            ChatMessage message = new ChatMessage(object.optString("displayContent", ""), false, timestamp);
            message.setReasoning(object.optString("reasoning", ""));
            message.setReasoningExpanded(object.optBoolean("reasoningExpanded", false));
            JSONArray anthropic = object.optJSONArray("anthropicReasoning");
            message.setAnthropicReasoningJson(anthropic == null ? "" : anthropic.toString());
            message.setStatus(object.optString("status", ""));
            return message;
        }

        if (ChatMessage.ROLE_INTERRUPTED_STREAMING_TOOL.equals(role)) {
            return ChatMessage.interruptedStreamingTool(
                    object.optString("name", ""),
                    object.optString("mcpServerName", null),
                    timestamp
            );
        }

        if (ChatMessage.ROLE_CHECKPOINT.equals(role)) {
            ChatMessage message = new ChatMessage(
                    object.optString("message", ""),
                    ChatMessage.TYPE_CHECKPOINT,
                    timestamp,
                    object.optString("status", "Checkpoint")
            );
            message.setCheckpointId(object.optString("checkpointId", ""));
            message.setCheckpointType(object.optString("checkpointType", object.optString("type", "")));
            JSONObject snapshots = object.optJSONObject("checkpointSnapshots");
            message.setCheckpointSnapshotsJson(snapshots == null ? "" : snapshots.toString());
            return message;
        }

        if (ChatMessage.ROLE_TOOL.equals(role)) {
            String toolName = object.optString("name", "");
            String rawParams = object.optJSONObject("rawParams") != null
                    ? object.optJSONObject("rawParams").toString()
                    : object.optJSONObject("params") != null
                    ? object.optJSONObject("params").toString()
                    : "";
            ChatMessage message = new ChatMessage(toolName, rawParams, timestamp, object.optString("id", ""));
            message.setDisplayContent(object.optString("displayContent", object.optString("message", "")));
            message.setToolResult(object.optString("result", object.optString("content", "")));
            message.setToolState(object.optString("type", "success"));
            message.setToolRunning(object.optBoolean("toolRunning", false));
            message.setToolError(object.optBoolean("toolError", false));
            message.setMcpServerName(object.optString("mcpServerName", null));
            message.setRequiresApproval(object.optBoolean("requiresApproval", false));
            message.setApproved(object.optBoolean("approved", false));
            message.setRejected(object.optBoolean("rejected", false));
            message.setExpanded(object.optBoolean("isExpanded", true));
            return message;
        }

        return null;
    }

    @Nullable
    private ChatMessage parseLegacyMessage(JSONObject object) {
        int type = object.optInt("type", ChatMessage.TYPE_BOT);
        long timestamp = object.optLong("timestamp", 0L);

        if (type == ChatMessage.TYPE_TOOL) {
            ChatMessage message = new ChatMessage(
                    object.optString("toolName", ""),
                    object.optString("toolArgs", ""),
                    timestamp,
                    object.optString("toolId", "")
            );
            message.setDisplayContent(object.optString("message", ""));
            message.setLlmContent(object.optString("toolResult", ""));
            message.setToolResult(object.optString("toolResult", ""));
            message.setToolRunning(object.optBoolean("toolRunning", false));
            message.setToolError(object.optBoolean("toolError", false));
            message.setToolState(object.optString("toolState", ""));
            message.setRequiresApproval(object.optBoolean("requiresApproval", false));
            message.setApproved(object.optBoolean("approved", false));
            message.setRejected(object.optBoolean("rejected", false));
            message.setExpanded(object.optBoolean("isExpanded", true));
            message.setMcpServerName(object.optString("mcpServerName", null));
            return message;
        }

        if (type == ChatMessage.TYPE_INTERRUPTED_STREAMING_TOOL) {
            return ChatMessage.interruptedStreamingTool(
                    object.optString("toolName", ""),
                    object.optString("mcpServerName", null),
                    timestamp
            );
        }

        if (type == ChatMessage.TYPE_USER || type == ChatMessage.TYPE_BOT) {
            ChatMessage message = new ChatMessage(object.optString("message", ""), type == ChatMessage.TYPE_USER, timestamp);
            message.setLlmContent(object.optString("llmContent", object.optString("content", message.getDisplayContent())));
            message.setReasoning(object.optString("reasoning", ""));
            message.setReasoningExpanded(object.optBoolean("reasoningExpanded", false));
            message.setStagingSelections(parseSelections(object.optJSONArray("imageReferences"), null));
            return message;
        }

        if (type == ChatMessage.TYPE_CHECKPOINT) {
            ChatMessage message = new ChatMessage(
                    object.optString("message", ""),
                    ChatMessage.TYPE_CHECKPOINT,
                    timestamp,
                    object.optString("status", "Checkpoint")
            );
            message.setCheckpointId(object.optString("checkpointId", ""));
            message.setCheckpointType(object.optString("checkpointType", ""));
            JSONObject snapshots = object.optJSONObject("checkpointSnapshots");
            message.setCheckpointSnapshotsJson(snapshots == null ? "" : snapshots.toString());
            return message;
        }

        return new ChatMessage(object.optString("message", ""), type, timestamp, object.optString("status", ""));
    }

    private JSONArray serializeSelections(List<ChatReference> references) {
        JSONArray array = new JSONArray();
        for (ChatReference reference : references == null ? new ArrayList<ChatReference>() : references) {
            if (reference != null) {
                array.put(reference.toJson());
            }
        }
        return array;
    }

    private boolean jsonContainsExactValue(Object value, String expected) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            JSONArray names = object.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                String name = names.optString(i, "");
                if (!name.isEmpty() && jsonContainsExactValue(object.opt(name), expected)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                if (jsonContainsExactValue(array.opt(i), expected)) {
                    return true;
                }
            }
            return false;
        }
        return value != null
                && value != JSONObject.NULL
                && expected.equals(String.valueOf(value));
    }

    private List<ChatReference> parseSelections(@Nullable JSONArray primary, @Nullable JSONArray fallback) {
        List<ChatReference> references = new ArrayList<>();
        appendSelections(references, primary);
        if (references.isEmpty()) {
            appendSelections(references, fallback);
        }
        return references;
    }

    private void appendSelections(List<ChatReference> target, @Nullable JSONArray array) {
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            // Always delegate to fromJson — it handles both the legacy path-only format and
            // the current format that stores an explicit "uri" field for image references.
            target.add(ChatReference.fromJson(object));
        }
    }

    private List<ChatMessage> snapshotMessages(List<ChatMessage> messages) {
        List<ChatMessage> snapshot = new ArrayList<>();
        if (messages == null) {
            return snapshot;
        }
        snapshot.addAll(messages);
        return snapshot;
    }

    private String safeThreadId(String scId, String threadId) {
        return ChatMessage.hasVisibleText(threadId) ? threadId : ensureDefaultThread(scId);
    }

    private JSONObject parseObject(String json) {
        if (!ChatMessage.hasVisibleText(json)) {
            return new JSONObject();
        }
        try {
            return new JSONObject(json);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private JSONArray parseArray(String json) {
        if (!ChatMessage.hasVisibleText(json)) {
            return new JSONArray();
        }
        try {
            return new JSONArray(json);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private void writeRootLocked(JSONObject root) {
        File backup = new File(storageFile.getParentFile(), storageFile.getName() + ".bak");
        if (storageFile.exists()) {
            try {
                copyFile(storageFile, backup);
            } catch (Exception ignored) {
            }
        }
        try (FileWriter writer = new FileWriter(storageFile, false)) {
            writer.write(root == null ? newRoot().toString(2) : root.toString(2));
        } catch (Exception e) {
            Log.e(TAG, "Failed to write chat storage", e);
        }
    }

    private void copyFile(File source, File target) throws java.io.IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(source);
             java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    @Nullable
    private String readText(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } catch (Exception e) {
            Log.w(TAG, "Failed to read chat storage", e);
            return null;
        }
    }

    private void put(JSONObject object, String key, Object value) {
        try {
            object.put(key, value == null ? JSONObject.NULL : value);
        } catch (Exception ignored) {
        }
    }

    private String safe(String value, String fallback) {
        return value == null ? fallback : value;
    }
}

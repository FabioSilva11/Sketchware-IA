package pro.sketchware.chat;

import androidx.annotation.Nullable;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chat message model aligned with Void {@code chatThreadServiceTypes.ChatMessage}:
 * separate LLM {@code content} vs UI {@code displayContent}, staging selections,
 * tool states, checkpoints and {@code interrupted_streaming_tool}.
 */
public class ChatMessage {
    public static final int TYPE_USER = 0;
    public static final int TYPE_BOT = 1;
    public static final int TYPE_TOOL = 2;
    public static final int TYPE_CHECKPOINT = 3;
    public static final int TYPE_AWAITING_USER = 4;
    public static final int TYPE_INTERRUPTED_STREAMING_TOOL = 5;

    /** Void role string persisted in storage. */
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";
    public static final String ROLE_CHECKPOINT = "checkpoint";
    public static final String ROLE_INTERRUPTED_STREAMING_TOOL = "interrupted_streaming_tool";

    private String displayContent = "";
    private String llmContent = "";
    private int type;
    private long timestamp;

    private @Nullable String toolName;
    private @Nullable String toolArgs;
    private boolean isToolRunning;
    private @Nullable String toolResult;
    private boolean isToolError;
    private @Nullable String toolId;
    private @Nullable String toolState;
    private @Nullable String mcpServerName;

    private boolean isExpanded;
    private @Nullable String status;
    private boolean requiresApproval;
    private boolean isApproved;
    private boolean isRejected;
    private @Nullable String checkpointId;
    private @Nullable String checkpointType;
    private @Nullable String checkpointSnapshotsJson;

    private @Nullable String reasoning;
    private @Nullable String anthropicReasoningJson;
    private boolean isReasoningExpanded;
    private transient boolean streaming;

    private @Nullable String contextPayload;
    private boolean isBeingEdited;
    private final List<ChatReference> stagingSelections = new ArrayList<>();
    private final List<ChatReference> imageReferences = new ArrayList<>();

    public ChatMessage() {
        this.type = TYPE_USER;
        this.timestamp = 0L;
    }

    public ChatMessage(String displayContent, int type, long timestamp) {
        this.displayContent = displayContent == null ? "" : displayContent;
        this.type = type;
        this.timestamp = timestamp;
        syncLlmFromDisplayIfEmpty();
    }

    public ChatMessage(String displayContent, boolean isUser, long timestamp) {
        this(displayContent, isUser ? TYPE_USER : TYPE_BOT, timestamp);
    }

    public ChatMessage(String displayContent, int type, long timestamp, @Nullable String status) {
        this(displayContent, type, timestamp);
        this.status = status;
    }

    public ChatMessage(@Nullable String toolName, @Nullable String toolArgs, long timestamp, @Nullable String toolId) {
        this("", TYPE_TOOL, timestamp);
        this.toolName = toolName;
        this.toolArgs = toolArgs;
        this.toolId = toolId;
        this.isToolRunning = true;
        this.isExpanded = true;
    }

    public static ChatMessage interruptedStreamingTool(String toolName, @Nullable String mcpServerName, long timestamp) {
        ChatMessage message = new ChatMessage("", TYPE_INTERRUPTED_STREAMING_TOOL, timestamp);
        message.toolName = toolName;
        message.mcpServerName = mcpServerName;
        return message;
    }

    public String getRole() {
        switch (type) {
            case TYPE_USER:
                return ROLE_USER;
            case TYPE_BOT:
                return ROLE_ASSISTANT;
            case TYPE_TOOL:
                return ROLE_TOOL;
            case TYPE_CHECKPOINT:
                return ROLE_CHECKPOINT;
            case TYPE_INTERRUPTED_STREAMING_TOOL:
                return ROLE_INTERRUPTED_STREAMING_TOOL;
            default:
                return ROLE_ASSISTANT;
        }
    }

    public void applyRole(String role) {
        if (ROLE_USER.equals(role)) {
            type = TYPE_USER;
        } else if (ROLE_ASSISTANT.equals(role)) {
            type = TYPE_BOT;
        } else if (ROLE_TOOL.equals(role)) {
            type = TYPE_TOOL;
        } else if (ROLE_CHECKPOINT.equals(role)) {
            type = TYPE_CHECKPOINT;
        } else if (ROLE_INTERRUPTED_STREAMING_TOOL.equals(role)) {
            type = TYPE_INTERRUPTED_STREAMING_TOOL;
        }
    }

    /** UI text (Void {@code displayContent}). */
    public String getDisplayContent() {
        return displayContent == null ? "" : displayContent;
    }

    public void setDisplayContent(String displayContent) {
        this.displayContent = displayContent == null ? "" : displayContent;
    }

    /** Text sent to the LLM on future turns (Void {@code content} for user/tool). */
    public String getLlmContent() {
        if (ChatMessage.hasVisibleText(llmContent)) {
            return llmContent;
        }
        return buildDefaultLlmContent();
    }

    public void setLlmContent(String llmContent) {
        this.llmContent = llmContent == null ? "" : llmContent;
    }

    /** Backward-compatible alias for {@link #getDisplayContent()}. */
    public String getMessage() {
        return getDisplayContent();
    }

    /** Backward-compatible alias for {@link #setDisplayContent(String)}. */
    public void setMessage(String message) {
        setDisplayContent(message);
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Nullable
    public String getToolName() {
        return toolName;
    }

    public void setToolName(@Nullable String toolName) {
        this.toolName = toolName;
    }

    @Nullable
    public String getToolArgs() {
        return toolArgs;
    }

    public void setToolArgs(@Nullable String toolArgs) {
        this.toolArgs = toolArgs;
    }

    public boolean isToolRunning() {
        return isToolRunning;
    }

    public void setToolRunning(boolean toolRunning) {
        isToolRunning = toolRunning;
    }

    @Nullable
    public String getToolResult() {
        return toolResult;
    }

    public void setToolResult(@Nullable String toolResult) {
        this.toolResult = toolResult;
    }

    public boolean isToolError() {
        return isToolError;
    }

    public void setToolError(boolean toolError) {
        isToolError = toolError;
    }

    @Nullable
    public String getToolId() {
        return toolId;
    }

    public void setToolId(@Nullable String toolId) {
        this.toolId = toolId;
    }

    @Nullable
    public String getToolState() {
        return toolState;
    }

    public void setToolState(@Nullable String toolState) {
        this.toolState = toolState;
    }

    @Nullable
    public String getMcpServerName() {
        return mcpServerName;
    }

    public void setMcpServerName(@Nullable String mcpServerName) {
        this.mcpServerName = mcpServerName;
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public void setExpanded(boolean expanded) {
        isExpanded = expanded;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    public void setStatus(@Nullable String status) {
        this.status = status;
    }

    public boolean getRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public boolean isApproved() {
        return isApproved;
    }

    public void setApproved(boolean approved) {
        isApproved = approved;
    }

    public boolean isRejected() {
        return isRejected;
    }

    public void setRejected(boolean rejected) {
        isRejected = rejected;
    }

    @Nullable
    public String getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(@Nullable String checkpointId) {
        this.checkpointId = checkpointId;
    }

    @Nullable
    public String getCheckpointType() {
        return checkpointType;
    }

    public void setCheckpointType(@Nullable String checkpointType) {
        this.checkpointType = checkpointType;
    }

    @Nullable
    public String getCheckpointSnapshotsJson() {
        return checkpointSnapshotsJson;
    }

    public void setCheckpointSnapshotsJson(@Nullable String checkpointSnapshotsJson) {
        this.checkpointSnapshotsJson = checkpointSnapshotsJson;
    }

    @Nullable
    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(@Nullable String reasoning) {
        this.reasoning = reasoning;
    }

    @Nullable
    public String getAnthropicReasoningJson() {
        return anthropicReasoningJson;
    }

    public void setAnthropicReasoningJson(@Nullable String anthropicReasoningJson) {
        this.anthropicReasoningJson = anthropicReasoningJson;
    }

    public boolean isReasoningExpanded() {
        return isReasoningExpanded;
    }

    public void setReasoningExpanded(boolean reasoningExpanded) {
        isReasoningExpanded = reasoningExpanded;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    @Nullable
    public String getContextPayload() {
        return contextPayload;
    }

    public void setContextPayload(@Nullable String contextPayload) {
        this.contextPayload = contextPayload;
    }

    public boolean isBeingEdited() {
        return isBeingEdited;
    }

    public void setBeingEdited(boolean beingEdited) {
        isBeingEdited = beingEdited;
    }

    public List<ChatReference> getStagingSelections() {
        return Collections.unmodifiableList(stagingSelections);
    }

    public void setStagingSelections(@Nullable List<ChatReference> references) {
        stagingSelections.clear();
        imageReferences.clear();
        if (references == null) {
            return;
        }
        for (ChatReference reference : references) {
            if (reference == null) {
                continue;
            }
            stagingSelections.add(reference);
            if (reference.isImage()) {
                imageReferences.add(reference);
            }
        }
    }

    public List<ChatReference> getImageReferences() {
        return Collections.unmodifiableList(imageReferences);
    }

    public void setImageReferences(@Nullable List<ChatReference> references) {
        setStagingSelections(references);
    }

    public boolean hasImageReferences() {
        return !imageReferences.isEmpty();
    }

    public boolean hasStagingSelections() {
        return !stagingSelections.isEmpty();
    }

    public String getPromptContent() {
        return getLlmContent();
    }

    public boolean isUser() {
        return type == TYPE_USER;
    }

    public boolean isBot() {
        return type == TYPE_BOT;
    }

    public boolean isTool() {
        return type == TYPE_TOOL;
    }

    public boolean isCheckpoint() {
        return type == TYPE_CHECKPOINT;
    }

    public boolean isAwaitingUser() {
        return type == TYPE_AWAITING_USER;
    }

    public boolean isInterruptedStreamingTool() {
        return type == TYPE_INTERRUPTED_STREAMING_TOOL;
    }

    public boolean hasDisplayContent() {
        return hasVisibleText(displayContent);
    }

    public boolean hasMessageContent() {
        return hasDisplayContent();
    }

    public boolean hasReasoningContent() {
        return hasVisibleText(reasoning);
    }

    public static boolean hasVisibleText(@Nullable String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return !trimmed.isEmpty() && !"null".equalsIgnoreCase(trimmed);
    }

    private void syncLlmFromDisplayIfEmpty() {
        if (!hasVisibleText(llmContent) && hasVisibleText(displayContent)) {
            llmContent = displayContent;
        }
    }

    private String buildDefaultLlmContent() {
        StringBuilder builder = new StringBuilder();
        if (hasVisibleText(displayContent)) {
            builder.append(displayContent.trim());
        }
        if (hasVisibleText(contextPayload)) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(contextPayload.trim());
        }
        return builder.toString();
    }
}

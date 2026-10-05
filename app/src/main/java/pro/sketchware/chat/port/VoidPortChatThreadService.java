package pro.sketchware.chat.port;

import java.util.List;

import pro.sketchware.chat.ChatMessage;

/**
 * Android thread-state facade inspired by browser/chatThreadService.ts.
 */
public final class VoidPortChatThreadService {
    private VoidPortChatThreadService() {
    }

    public static String threadIdForProject(String scId) {
        String safeScId = scId == null || scId.trim().isEmpty() ? "unknown" : scId.trim();
        return "axion:" + safeScId;
    }

    public static int visibleMessageCount(List<ChatMessage> messages) {
        if (messages == null) {
            return 0;
        }
        int count = 0;
        for (ChatMessage message : messages) {
            if (message != null && !message.isCheckpoint()) {
                count++;
            }
        }
        return count;
    }

    public static String threadSubtitle(String scId, List<ChatMessage> messages) {
        int count = visibleMessageCount(messages);
        String threadId = threadIdForProject(scId);
        if (count == 1) {
            return threadId + " | 1 message";
        }
        return threadId + " | " + count + " messages";
    }

    public static String changedFilesLabel(int count) {
        if (count <= 0) {
            return "0 files with changes";
        }
        if (count == 1) {
            return "1 file with changes";
        }
        return count + " files with changes";
    }
}


package pro.sketchware.chat.analytics;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Event names the ported chat reports. Sketchware doesn't send chat analytics, so every call is a no-op; the names
 * stay so the chat code reads like the Axion code it came from.
 */
public final class AxionAnalytics {

    public static final class Events {
        public static final String CHAT_MESSAGE_SENT = "chat_message_sent";
        public static final String CHAT_RUN_RESULT = "chat_run_result";
        public static final String CHAT_RUN_CANCELLED = "chat_run_cancelled";
        public static final String CHAT_THREAD_CREATED = "chat_thread_created";
        public static final String CHAT_THREAD_OPENED = "chat_thread_opened";
        public static final String CHAT_THREAD_RENAMED = "chat_thread_renamed";
        public static final String CHAT_THREAD_DELETED = "chat_thread_deleted";
        public static final String CHAT_REFERENCE_ADDED = "chat_reference_added";
        public static final String CHAT_EXPORTED = "chat_exported";
        public static final String CHAT_MODE_CHANGED = "chat_mode_changed";
        public static final String MODEL_SELECTED = "model_selected";

        private Events() {
        }
    }

    public static final class Params {
        public static final String MODE = "mode";
        public static final String RESULT = "result";
        public static final String SOURCE = "source";
        public static final String ENABLED = "enabled";
        public static final String HAS_TEXT = "has_text";
        public static final String HAS_ATTACHMENTS = "has_attachments";
        public static final String ATTACHMENT_TYPE = "attachment_type";
        public static final String THREAD_COUNT = "thread_count";
        public static final String MESSAGE_COUNT = "message_count";
        public static final String DURATION_MS = "duration_ms";

        private Params() {
        }
    }

    private AxionAnalytics() {
    }

    public static void initialize(@NonNull Application application) {
    }

    public static void logEvent(@NonNull Context context, @NonNull String eventName) {
    }

    public static void logEvent(@NonNull Context context, @NonNull String eventName, @Nullable Bundle parameters) {
    }

    public static Bundle params(@NonNull Object... keyValues) {
        return new Bundle();
    }
}

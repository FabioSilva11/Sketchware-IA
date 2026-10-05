package pro.sketchware.chat;

import android.util.Log;

public class ChatToolLog {

    public static void d(String tag, String message) {
        Log.d("ChatTool_" + tag, message);
    }

    public static void w(String tag, String message) {
        Log.w("ChatTool_" + tag, message);
    }

    public static void e(String tag, String message, Throwable t) {
        Log.e("ChatTool_" + tag, message, t);
    }

    public static String preview(String text, int maxLen) {
        if (text == null) return "null";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}



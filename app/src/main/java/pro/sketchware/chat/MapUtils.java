package pro.sketchware.chat;

import java.util.HashMap;

public class MapUtils {
    public static String c(HashMap<String, Object> map, String key) {
        if (map == null) return "";
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : "";
    }

    public static int b(HashMap<String, Object> map, String key) {
        if (map == null) return 0;
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    public static int a(HashMap<String, Object> map, String key, int defaultValue) {
        if (map == null) return defaultValue;
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static long a(HashMap<String, Object> map, String key, long defaultValue) {
        if (map == null) return defaultValue;
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }
}


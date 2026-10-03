package pro.sketchware.store.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Hashing, hex and the canonical JSON form records are signed in.
 */
public final class Codec {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Codec() {
    }

    public static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[i * 2] = HEX[(bytes[i] >> 4) & 0xf];
            out[i * 2 + 1] = HEX[bytes[i] & 0xf];
        }
        return new String(out);
    }

    public static byte[] unhex(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hex");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hex");
            }
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }

    public static boolean isHex(String value, int length) {
        if (value == null || value.length() != length) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0 || Character.isUpperCase(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static byte[] sha256(byte[] data) {
        return digest("SHA-256").digest(data);
    }

    public static byte[] sha1(byte[] data) {
        return digest("SHA-1").digest(data);
    }

    public static String sha256Hex(String text) {
        return hex(sha256(text.getBytes(StandardCharsets.UTF_8)));
    }

    public static String sha256Hex(File file) throws IOException {
        MessageDigest digest = digest("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * JSON with object keys sorted and no whitespace, so the same record always has the same bytes.
     */
    public static byte[] canonical(JsonElement element) {
        StringWriter out = new StringWriter();
        try (JsonWriter writer = new JsonWriter(out)) {
            writer.setHtmlSafe(false);
            write(writer, element);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void write(JsonWriter writer, JsonElement element) throws IOException {
        if (element == null || element.isJsonNull()) {
            writer.nullValue();
        } else if (element.isJsonObject()) {
            writer.beginObject();
            List<String> keys = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                keys.add(entry.getKey());
            }
            Collections.sort(keys);
            for (String key : keys) {
                writer.name(key);
                write(writer, element.getAsJsonObject().get(key));
            }
            writer.endObject();
        } else if (element.isJsonArray()) {
            writer.beginArray();
            for (JsonElement item : element.getAsJsonArray()) {
                write(writer, item);
            }
            writer.endArray();
        } else {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                writer.value(primitive.getAsBoolean());
            } else if (primitive.isNumber()) {
                // Records only carry integers; a long keeps "1" and "1.0" from signing differently.
                writer.value(primitive.getAsLong());
            } else {
                writer.value(primitive.getAsString());
            }
        }
    }

    public static JsonObject parseObject(String json) {
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Not a JSON object");
        }
        return element.getAsJsonObject();
    }

    public static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    public static long number(JsonObject object, String key, long fallback) {
        JsonElement element = object.get(key);
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsLong() : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static List<String> strings(JsonObject object, String key) {
        List<String> values = new ArrayList<>();
        JsonElement element = object.get(key);
        if (element != null && element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    values.add(item.getAsString());
                }
            }
        }
        return values;
    }

    public static JsonArray array(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }
}

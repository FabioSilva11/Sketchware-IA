package pro.sketchware.chat.toolcalling;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class JsonBlockScanner {
    static final class Block {
        final int start;
        final int end;
        final Object value;

        Block(int start, int end, Object value) {
            this.start = start;
            this.end = end;
            this.value = value;
        }
    }

    private JsonBlockScanner() {
    }

    static List<Block> scan(String text) {
        List<Block> blocks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return blocks;
        }
        int index = 0;
        while (index < text.length()) {
            char first = text.charAt(index);
            if (first != '{' && first != '[') {
                index++;
                continue;
            }
            int end = findBalancedEnd(text, index);
            if (end <= index) {
                index++;
                continue;
            }
            String candidate = text.substring(index, end);
            try {
                Object value = first == '{'
                        ? new JSONObject(candidate)
                        : new JSONArray(candidate);
                blocks.add(new Block(index, end, value));
                index = end;
            } catch (Exception ignored) {
                index++;
            }
        }
        return blocks;
    }

    static String remove(String text, List<Block> blocks) {
        if (text == null || text.isEmpty() || blocks == null || blocks.isEmpty()) {
            return text == null ? "" : text;
        }
        List<Block> ordered = new ArrayList<>(blocks);
        Collections.sort(ordered, Comparator.comparingInt(block -> block.start));
        StringBuilder cleaned = new StringBuilder(text.length());
        int cursor = 0;
        for (Block block : ordered) {
            if (block.start < cursor || block.start < 0 || block.end > text.length()) {
                continue;
            }
            cleaned.append(text, cursor, block.start);
            cursor = block.end;
        }
        cleaned.append(text, cursor, text.length());
        return cleanupEmptyJsonFences(cleaned.toString()).trim();
    }

    private static int findBalancedEnd(String text, int start) {
        char opening = text.charAt(start);
        char closing = opening == '{' ? '}' : ']';
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char current = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == opening) {
                depth++;
            } else if (current == closing) {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    private static String cleanupEmptyJsonFences(String text) {
        return text.replaceAll("(?is)```\\s*(?:json|javascript|js)?\\s*```", "");
    }
}

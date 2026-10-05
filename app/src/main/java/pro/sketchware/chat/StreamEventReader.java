package pro.sketchware.chat;

import java.io.IOException;

import okio.BufferedSource;

/** Frames SSE events and NDJSON records without interpreting provider payloads. */
final class StreamEventReader {
    interface Listener {
        void onRawLine(String line);

        void onEvent(String eventName, String data, boolean sse) throws IOException;
    }

    private StreamEventReader() {
    }

    static void read(BufferedSource source, Listener listener) throws IOException {
        if (source == null) {
            throw new IOException("Stream sem corpo.");
        }
        String eventName = "";
        StringBuilder data = new StringBuilder();
        boolean sseMode = false;
        String line;
        while ((line = source.readUtf8Line()) != null) {
            listener.onRawLine(line);

            if (line.isEmpty()) {
                if (data.length() > 0) {
                    listener.onEvent(eventName, data.toString(), true);
                }
                eventName = "";
                data.setLength(0);
                continue;
            }
            if (line.startsWith(":")) {
                // SSE comment/heartbeat.
                sseMode = true;
                continue;
            }
            if (line.startsWith("event:")) {
                sseMode = true;
                eventName = line.substring(6).trim();
                continue;
            }
            if (line.startsWith("data:")) {
                sseMode = true;
                if (data.length() > 0) {
                    data.append('\n');
                }
                String value = line.substring(5);
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }
                data.append(value);
                continue;
            }

            if (!sseMode) {
                // Ollama and some compatible gateways use one JSON object per line.
                listener.onEvent("", line.trim(), false);
            }
            // Unknown SSE fields are ignored for forward compatibility.
        }

        if (data.length() > 0) {
            listener.onEvent(eventName, data.toString(), true);
        }
    }
}

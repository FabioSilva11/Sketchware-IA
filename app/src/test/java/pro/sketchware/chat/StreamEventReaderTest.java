package pro.sketchware.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import okio.Buffer;

public class StreamEventReaderTest {
    @Test
    public void framesNamedSseEventsAndDoneMarker() throws Exception {
        Buffer source = new Buffer().writeUtf8(
                ": keepalive\n"
                        + "event: response.output_text.delta\n"
                        + "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Oi\"}\n\n"
                        + "data: [DONE]\n\n");
        List<Event> events = read(source);

        assertEquals(2, events.size());
        assertEquals("response.output_text.delta", events.get(0).name);
        assertTrue(events.get(0).data.contains("\"delta\":\"Oi\""));
        assertTrue(events.get(0).sse);
        assertEquals("[DONE]", events.get(1).data);
    }

    @Test
    public void joinsMultipleSseDataLines() throws Exception {
        Buffer source = new Buffer().writeUtf8(
                "event: content_block_delta\n"
                        + "data: {\"type\":\"content_block_delta\",\n"
                        + "data: \"index\":0}\n\n");
        List<Event> events = read(source);

        assertEquals(1, events.size());
        assertEquals("{\"type\":\"content_block_delta\",\n\"index\":0}", events.get(0).data);
    }

    @Test
    public void framesOllamaNdjsonIncludingMidStreamError() throws Exception {
        Buffer source = new Buffer().writeUtf8(
                "{\"message\":{\"content\":\"Oi\"},\"done\":false}\n"
                        + "{\"error\":\"modelo indisponivel\"}\n");
        List<Event> events = read(source);

        assertEquals(2, events.size());
        assertFalse(events.get(0).sse);
        assertTrue(events.get(1).data.contains("error"));
    }

    private static List<Event> read(Buffer source) throws Exception {
        List<Event> events = new ArrayList<>();
        StreamEventReader.read(source, new StreamEventReader.Listener() {
            @Override
            public void onRawLine(String line) {
            }

            @Override
            public void onEvent(String eventName, String data, boolean sse) {
                events.add(new Event(eventName, data, sse));
            }
        });
        return events;
    }

    private static final class Event {
        final String name;
        final String data;
        final boolean sse;

        Event(String name, String data, boolean sse) {
            this.name = name;
            this.data = data;
            this.sse = sse;
        }
    }
}

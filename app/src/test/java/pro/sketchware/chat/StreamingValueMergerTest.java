package pro.sketchware.chat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StreamingValueMergerTest {
    @Test
    public void repeatedSnapshotsDoNotDuplicateToolName() {
        StringBuilder value = new StringBuilder();
        StreamingValueMerger.merge(value, "read_file");
        StreamingValueMerger.merge(value, "read_file");
        StreamingValueMerger.merge(value, "read_file");
        assertEquals("read_file", value.toString());
    }

    @Test
    public void cumulativeSnapshotsReplacePriorValue() {
        StringBuilder value = new StringBuilder();
        StreamingValueMerger.merge(value, "{\"uri\":");
        StreamingValueMerger.merge(value, "{\"uri\":\"index.html\"}");
        assertEquals("{\"uri\":\"index.html\"}", value.toString());
    }

    @Test
    public void trueDeltasAreAppended() {
        StringBuilder value = new StringBuilder();
        StreamingValueMerger.merge(value, "read_");
        StreamingValueMerger.merge(value, "file");
        assertEquals("read_file", value.toString());
    }
}

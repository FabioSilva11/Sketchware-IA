package pro.sketchware.activities.chat;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class AgentManagerToolCallCollectorTest {

    @Test
    public void repeatedToolIdKeepsOnlyLatestArguments() {
        List<String[]> calls = new ArrayList<>();
        Map<String, Integer> indexes = new LinkedHashMap<>();

        AgentManager.collectOrReplaceToolCall(
                calls, indexes, "get_dir_tree", "{\"uri\":\"/\"}", "xml-1");
        AgentManager.collectOrReplaceToolCall(
                calls, indexes, "get_dir_tree", "{\"uri\":\"/storage/project\"}", "xml-1");

        assertEquals(1, calls.size());
        assertEquals("{\"uri\":\"/storage/project\"}", calls.get(0)[1]);
    }

    @Test
    public void updatingOneIdPreservesIndependentCallOrder() {
        List<String[]> calls = new ArrayList<>();
        Map<String, Integer> indexes = new LinkedHashMap<>();

        AgentManager.collectOrReplaceToolCall(calls, indexes, "read_file", "{\"uri\":\"A\"}", "a");
        AgentManager.collectOrReplaceToolCall(calls, indexes, "read_file", "{\"uri\":\"B\"}", "b");
        AgentManager.collectOrReplaceToolCall(calls, indexes, "read_file", "{\"uri\":\"A2\"}", "a");

        assertEquals(2, calls.size());
        assertEquals("{\"uri\":\"A2\"}", calls.get(0)[1]);
        assertEquals("{\"uri\":\"B\"}", calls.get(1)[1]);
    }

    @Test
    public void callsWithoutIdsRemainIndependent() {
        List<String[]> calls = new ArrayList<>();
        Map<String, Integer> indexes = new LinkedHashMap<>();

        AgentManager.collectOrReplaceToolCall(calls, indexes, "read_file", "{}", "");
        AgentManager.collectOrReplaceToolCall(calls, indexes, "read_file", "{}", "");

        assertEquals(2, calls.size());
    }
}

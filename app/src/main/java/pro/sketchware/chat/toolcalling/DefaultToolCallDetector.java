package pro.sketchware.chat.toolcalling;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultToolCallDetector implements ToolCallDetector {
    private final List<ToolCallParser> parsers = new CopyOnWriteArrayList<>();

    public DefaultToolCallDetector() {
        parsers.add(new NativeToolParser());
        parsers.add(new DsmlToolParser());
        parsers.add(new McpParser());
        parsers.add(new XmlToolParser());
        parsers.add(new JsonToolParser());
    }

    @Override
    public ToolCallParseResult detect(ToolCallResponse response) {
        ToolCallResponse safeResponse = response == null
                ? ToolCallResponse.text("")
                : response;

        String remainingContent = safeResponse.getContent();
        String remainingReasoning = safeResponse.getReasoning();
        List<ToolCall> authoritativeCalls = new ArrayList<>();
        String authoritativeProtocol = "";

        for (ToolCallParser parser : parsers) {
            try {
                ToolCallResponse current = new ToolCallResponse(
                        remainingContent,
                        remainingReasoning,
                        authoritativeProtocol.isEmpty()
                                ? safeResponse.getNativeToolCalls()
                                : java.util.Collections.emptyList(),
                        safeResponse.getAvailableToolNames());
                if (!parser.recognizes(current)) {
                    continue;
                }
                ToolCallParseResult result = parser.parse(current);
                if (result == null) {
                    continue;
                }

                // Sanitizers continue running even after the authoritative source
                // was selected, so duplicated DSML/XML/JSON protocol blocks never
                // remain visible in the chat.
                remainingContent = result.getRemainingContent();
                remainingReasoning = result.getRemainingReasoning();

                if (authoritativeProtocol.isEmpty() && result.hasToolCalls()) {
                    authoritativeCalls = validateAndNormalize(
                            result.getToolCalls(), safeResponse.getAvailableToolNames());
                    if (!authoritativeCalls.isEmpty()) {
                        authoritativeProtocol = result.getProtocol();
                    }
                }
            } catch (Exception ignored) {
                // A malformed protocol must never break delivery of the response.
            }
        }

        return new ToolCallParseResult(
                authoritativeProtocol,
                authoritativeCalls,
                remainingContent,
                remainingReasoning);
    }

    private List<ToolCall> validateAndNormalize(List<ToolCall> calls, List<String> availableNames) {
        List<ToolCall> valid = new ArrayList<>();
        for (ToolCall call : calls) {
            if (call == null || !ToolCall.hasValidArguments(call.getArguments())) {
                continue;
            }
            String normalizedName = resolveAvailableName(call.getName(), availableNames);
            if (normalizedName.isEmpty()) {
                continue;
            }
            valid.add(new ToolCall(normalizedName, call.getArguments(), call.getId()));
        }
        return valid;
    }

    private String resolveAvailableName(String rawName, List<String> availableNames) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            return "";
        }
        if (availableNames == null || availableNames.isEmpty()) {
            return name;
        }
        for (String available : availableNames) {
            if (name.equals(available)) {
                return available;
            }
        }
        // Conservative recovery for the exact corruption caused by repeated
        // cumulative stream snapshots, e.g. read_fileread_fileread_file.
        for (String available : availableNames) {
            if (available == null || available.isEmpty() || name.length() <= available.length()
                    || name.length() % available.length() != 0) {
                continue;
            }
            StringBuilder repeated = new StringBuilder(name.length());
            while (repeated.length() < name.length()) {
                repeated.append(available);
            }
            if (name.contentEquals(repeated)) {
                return available;
            }
        }
        return "";
    }

    @Override
    public void registerParser(ToolCallParser parser) {
        if (parser != null) {
            parsers.add(parser);
        }
    }
}

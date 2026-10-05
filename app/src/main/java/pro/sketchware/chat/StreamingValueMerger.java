package pro.sketchware.chat;

/**
 * Merges values received from streaming providers that may send either strict
 * deltas or cumulative snapshots. Several OpenAI-compatible gateways repeat
 * tool-call metadata on every chunk, while others send only the new suffix.
 */
final class StreamingValueMerger {
    private StreamingValueMerger() {
    }

    static void merge(StringBuilder target, String incomingValue) {
        if (target == null || incomingValue == null) {
            return;
        }
        String incoming = incomingValue.trim();
        if (incoming.isEmpty() || "null".equalsIgnoreCase(incoming)) {
            return;
        }

        String current = target.toString();
        if (current.isEmpty()) {
            target.append(incomingValue);
            return;
        }
        if (incomingValue.equals(current)) {
            return; // repeated full snapshot
        }
        if (incomingValue.startsWith(current)) {
            target.setLength(0);
            target.append(incomingValue); // newer cumulative snapshot
            return;
        }
        if (current.startsWith(incomingValue)) {
            return; // stale/shorter cumulative snapshot
        }
        target.append(incomingValue); // real delta
    }
}

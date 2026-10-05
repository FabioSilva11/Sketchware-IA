package pro.sketchware.chat;

import android.os.SystemClock;

/**
 * Marca tempos relativos de uma interação do chat (envio do usuário até resposta final).
 */
public class ChatInteractionTrace {

    private final int interactionId;
    private final long startedAt = SystemClock.elapsedRealtime();
    private long lastMarkAt = startedAt;
    private int stepCounter;

    public ChatInteractionTrace(int interactionId) {
        this.interactionId = interactionId;
    }

    public int getInteractionId() {
        return interactionId;
    }

    public String mark(String event) {
        return mark(event, null);
    }

    public String mark(String event, String detail) {
        long now = SystemClock.elapsedRealtime();
        long totalMs = now - startedAt;
        long deltaMs = now - lastMarkAt;
        lastMarkAt = now;
        stepCounter++;

        StringBuilder line = new StringBuilder();
        line.append("#").append(interactionId).append(".").append(stepCounter);
        line.append(" +").append(totalMs).append("ms");
        if (deltaMs > 0 || stepCounter > 1) {
            line.append(" (Δ").append(deltaMs).append("ms)");
        }
        line.append(" ").append(event);
        if (detail != null && !detail.trim().isEmpty()) {
            line.append(" | ").append(detail.trim());
        }
        return line.toString();
    }

    public String summary(String label) {
        long totalMs = SystemClock.elapsedRealtime() - startedAt;
        return "#" + interactionId + " FIM: " + label + " | total=" + totalMs + "ms";
    }

    public long elapsedMs() {
        return SystemClock.elapsedRealtime() - startedAt;
    }
}

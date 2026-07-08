package pro.sketchware.activities.chat;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.activities.chat.port.VoidPortScmService;

public final class ChatPlanManager {
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_DONE = 2;

    private ChatPlanManager() {
    }

    public static final class Task {
        public final String title;
        public final String detail;
        public final int status;

        public Task(String title, String detail, int status) {
            this.title = title == null ? "" : title;
            this.detail = detail == null ? "" : detail;
            this.status = status;
        }
    }

    /**
     * Plans provided by the model itself via the {@code update_plan} tool,
     * keyed by project id. When present they replace the heuristic plan below,
     * mirroring how Codex keeps an explicit, model-maintained task plan.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, List<Task>> MODEL_PLANS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void setModelPlan(String scId, List<Task> tasks) {
        if (scId == null) {
            return;
        }
        if (tasks == null || tasks.isEmpty()) {
            MODEL_PLANS.remove(scId);
        } else {
            MODEL_PLANS.put(scId, new ArrayList<>(tasks));
        }
    }

    public static void clearModelPlan(String scId) {
        if (scId != null) {
            MODEL_PLANS.remove(scId);
        }
    }

    public static List<Task> buildPlan(String scId, List<ChatMessage> messages, boolean processing, String statusText) {
        List<Task> modelPlan = scId == null ? null : MODEL_PLANS.get(scId);
        if (modelPlan != null && !modelPlan.isEmpty()) {
            return new ArrayList<>(modelPlan);
        }
        List<Task> tasks = new ArrayList<>();
        int latestUserIndex = latestUserIndex(messages);
        if (latestUserIndex < 0) {
            tasks.add(new Task("Pedido", "Envie uma mensagem para iniciar um plano.", STATUS_PENDING));
            tasks.add(new Task("Contexto", "Referencias, arquivos e imagens aparecem aqui durante a execucao.", STATUS_PENDING));
            return tasks;
        }

        int toolCount = 0;
        int runningTools = 0;
        int errorTools = 0;
        boolean hasAssistantAfterUser = false;
        for (int i = latestUserIndex + 1; messages != null && i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message == null) {
                continue;
            }
            if (message.isTool()) {
                toolCount++;
                if (message.isToolRunning()) {
                    runningTools++;
                }
                if (message.isToolError() || message.isRejected()) {
                    errorTools++;
                }
            }
            if (message.isBot() && (message.hasMessageContent() || message.hasReasoningContent())) {
                hasAssistantAfterUser = true;
            }
        }

        int changedFiles = VoidPortScmService.changedFileCount(scId);
        String safeStatus = statusText == null ? "" : statusText.trim();
        tasks.add(new Task("Pedido", compactUserText(messages.get(latestUserIndex)), STATUS_DONE));
        tasks.add(new Task("Contexto", toolCount > 0
                ? "Ferramentas usadas: " + toolCount
                : "Aguardando leitura ou analise de contexto",
                toolCount > 0 ? STATUS_DONE : (processing ? STATUS_RUNNING : STATUS_PENDING)));
        tasks.add(new Task("Ferramentas", toolCount > 0
                ? ChatToolActivitySummary.summarize(messages).compactLabel()
                : "Nenhuma ferramenta executada nesta rodada",
                runningTools > 0 ? STATUS_RUNNING : (toolCount > 0 ? STATUS_DONE : STATUS_PENDING)));
        tasks.add(new Task("Artifacts e diffs", changedFiles > 0
                ? changedFiles + " arquivo(s) com alteracoes locais"
                : "Sem arquivos alterados no rastreador",
                changedFiles > 0 ? STATUS_DONE : (processing ? STATUS_RUNNING : STATUS_PENDING)));
        tasks.add(new Task("Finalizacao", errorTools > 0
                ? errorTools + " ferramenta(s) com erro para revisar"
                : (hasAssistantAfterUser ? "Resposta gerada" : (safeStatus.isEmpty() ? "Aguardando conclusao" : safeStatus)),
                processing ? STATUS_RUNNING : (hasAssistantAfterUser ? STATUS_DONE : STATUS_PENDING)));
        return tasks;
    }

    private static int latestUserIndex(List<ChatMessage> messages) {
        if (messages == null) {
            return -1;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message != null && message.isUser()) {
                return i;
            }
        }
        return -1;
    }

    private static String compactUserText(ChatMessage message) {
        String text = message == null ? "" : message.getMessage();
        if (!ChatMessage.hasVisibleText(text)) {
            return "Mensagem com anexos ou referencias.";
        }
        String trimmed = text.trim().replace('\n', ' ');
        return trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
    }
}

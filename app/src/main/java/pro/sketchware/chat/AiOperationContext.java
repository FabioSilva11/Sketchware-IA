package pro.sketchware.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

/**
 * Contexto imutável de uma operação de IA.
 * Congela o modelo e provedor no início da operação e garante que não sejam alterados
 * durante retries. Também mantém um identificador único para rastreamento.
 */
public class AiOperationContext {
    
    @NonNull
    private final String requestId;
    
    @NonNull
    private final String providerId;
    
    @NonNull
    private final String modelName;
    
    private final long createdAtMillis;
    
    @Nullable
    private final String chatMode;

    private final boolean webSearchEnabled;

    private AiOperationContext(@NonNull Builder builder) {
        this.requestId = builder.requestId;
        this.providerId = builder.providerId;
        this.modelName = builder.modelName;
        this.createdAtMillis = builder.createdAtMillis;
        this.chatMode = builder.chatMode;
        this.webSearchEnabled = builder.webSearchEnabled;
    }

    /**
     * Identificador único desta operação no formato "axion-{uuid}".
     * Usado para rastreamento em logs e diagnóstico.
     */
    @NonNull
    public String getRequestId() {
        return requestId;
    }

    /**
     * Gera um identificador exclusivo para uma única tentativa HTTP.
     *
     * O ID da operação permanece estável para logs e interface, enquanto o
     * X-Request-Id deve mudar em cada nova chamada/retry enviado ao servidor.
     */
    @NonNull
    public String newHttpRequestId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Provedor de IA selecionado no início da operação.
     * Este valor não deve ser alterado durante retries.
     */
    @NonNull
    public String getProviderId() {
        return providerId;
    }

    /**
     * Modelo de IA selecionado no início da operação.
     * Este valor não deve ser alterado durante retries.
     */
    @NonNull
    public String getModelName() {
        return modelName;
    }

    /**
     * Timestamp de criação da operação.
     */
    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    /**
     * Modo do chat (agent, normal, etc).
     */
    @Nullable
    public String getChatMode() {
        return chatMode;
    }

    public boolean isWebSearchEnabled() {
        return webSearchEnabled;
    }

    /**
     * Valida que o modelo/provedor atual ainda corresponde ao contexto original.
     * 
     * @param currentProvider provedor atual nas preferências
     * @param currentModel modelo atual nas preferências
     * @return true se correspondem, false se foram alterados
     */
    public boolean validateModelProviderMatch(@NonNull String currentProvider, 
                                              @NonNull String currentModel) {
        return this.providerId.equals(currentProvider) && this.modelName.equals(currentModel);
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        @NonNull
        private String requestId = generateRequestId();
        @NonNull
        private String providerId = "";
        @NonNull
        private String modelName = "";
        private long createdAtMillis = System.currentTimeMillis();
        @Nullable
        private String chatMode = null;
        private boolean webSearchEnabled = false;

        @NonNull
        public Builder requestId(@NonNull String requestId) {
            this.requestId = requestId;
            return this;
        }

        @NonNull
        public Builder providerId(@NonNull String providerId) {
            this.providerId = providerId;
            return this;
        }

        @NonNull
        public Builder modelName(@NonNull String modelName) {
            this.modelName = modelName;
            return this;
        }

        @NonNull
        public Builder chatMode(@Nullable String chatMode) {
            this.chatMode = chatMode;
            return this;
        }

        @NonNull
        public Builder webSearchEnabled(boolean enabled) {
            this.webSearchEnabled = enabled;
            return this;
        }

        @NonNull
        public AiOperationContext build() {
            if (providerId.isEmpty()) {
                throw new IllegalStateException("providerId is required");
            }
            if (modelName.isEmpty()) {
                throw new IllegalStateException("modelName is required");
            }
            return new AiOperationContext(this);
        }

        @NonNull
        private static String generateRequestId() {
            return "axion-" + UUID.randomUUID().toString();
        }
    }

    @Override
    public String toString() {
        return "AiOperationContext{" +
                "requestId='" + requestId + '\'' +
                ", provider='" + providerId + '\'' +
                ", model='" + modelName + '\'' +
                ", chatMode='" + chatMode + '\'' +
                ", webSearchEnabled=" + webSearchEnabled +
                '}';
    }
}

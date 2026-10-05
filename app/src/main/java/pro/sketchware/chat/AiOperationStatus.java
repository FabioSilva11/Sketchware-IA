package pro.sketchware.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Status completo de uma operação de IA.
 * Contém todas as informações necessárias para exibir o progresso ao usuário
 * e tomar decisões sobre retry, cancelamento e tratamento de erros.
 */
public class AiOperationStatus {
    @NonNull
    private final AiOperationState state;
    
    @NonNull
    private final String userMessage;
    
    @Nullable
    private final String technicalMessage;
    
    private final int currentAttempt;
    private final int maximumAttempts;
    private final long retryAfterMillis;
    
    @NonNull
    private final String provider;
    
    @NonNull
    private final String model;
    
    @Nullable
    private final String requestId;
    
    private final boolean cancellable;
    private final boolean retryable;
    
    @Nullable
    private final CancellationReason cancellationReason;
    
    @Nullable
    private final String errorCode;

    private AiOperationStatus(@NonNull Builder builder) {
        this.state = builder.state;
        this.userMessage = builder.userMessage;
        this.technicalMessage = builder.technicalMessage;
        this.currentAttempt = builder.currentAttempt;
        this.maximumAttempts = builder.maximumAttempts;
        this.retryAfterMillis = builder.retryAfterMillis;
        this.provider = builder.provider;
        this.model = builder.model;
        this.requestId = builder.requestId;
        this.cancellable = builder.cancellable;
        this.retryable = builder.retryable;
        this.cancellationReason = builder.cancellationReason;
        this.errorCode = builder.errorCode;
    }

    @NonNull
    public AiOperationState getState() {
        return state;
    }

    @NonNull
    public String getUserMessage() {
        return userMessage;
    }

    @Nullable
    public String getTechnicalMessage() {
        return technicalMessage;
    }

    public int getCurrentAttempt() {
        return currentAttempt;
    }

    public int getMaximumAttempts() {
        return maximumAttempts;
    }

    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }

    @NonNull
    public String getProvider() {
        return provider;
    }

    @NonNull
    public String getModel() {
        return model;
    }

    @Nullable
    public String getRequestId() {
        return requestId;
    }

    public boolean isCancellable() {
        return cancellable;
    }

    public boolean isRetryable() {
        return retryable;
    }

    @Nullable
    public CancellationReason getCancellationReason() {
        return cancellationReason;
    }

    @Nullable
    public String getErrorCode() {
        return errorCode;
    }

    @NonNull
    public static Builder builder(@NonNull AiOperationState state) {
        return new Builder(state);
    }

    public static class Builder {
        @NonNull
        private final AiOperationState state;
        @NonNull
        private String userMessage = "";
        @Nullable
        private String technicalMessage = null;
        private int currentAttempt = 0;
        private int maximumAttempts = 4;
        private long retryAfterMillis = 0;
        @NonNull
        private String provider = "";
        @NonNull
        private String model = "";
        @Nullable
        private String requestId = null;
        private boolean cancellable = true;
        private boolean retryable = false;
        @Nullable
        private CancellationReason cancellationReason = null;
        @Nullable
        private String errorCode = null;

        private Builder(@NonNull AiOperationState state) {
            this.state = state;
        }

        @NonNull
        public Builder userMessage(@NonNull String userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        @NonNull
        public Builder technicalMessage(@Nullable String technicalMessage) {
            this.technicalMessage = technicalMessage;
            return this;
        }

        @NonNull
        public Builder currentAttempt(int currentAttempt) {
            this.currentAttempt = currentAttempt;
            return this;
        }

        @NonNull
        public Builder maximumAttempts(int maximumAttempts) {
            this.maximumAttempts = maximumAttempts;
            return this;
        }

        @NonNull
        public Builder retryAfterMillis(long retryAfterMillis) {
            this.retryAfterMillis = retryAfterMillis;
            return this;
        }

        @NonNull
        public Builder provider(@NonNull String provider) {
            this.provider = provider;
            return this;
        }

        @NonNull
        public Builder model(@NonNull String model) {
            this.model = model;
            return this;
        }

        @NonNull
        public Builder requestId(@Nullable String requestId) {
            this.requestId = requestId;
            return this;
        }

        @NonNull
        public Builder cancellable(boolean cancellable) {
            this.cancellable = cancellable;
            return this;
        }

        @NonNull
        public Builder retryable(boolean retryable) {
            this.retryable = retryable;
            return this;
        }

        @NonNull
        public Builder cancellationReason(@Nullable CancellationReason cancellationReason) {
            this.cancellationReason = cancellationReason;
            return this;
        }

        @NonNull
        public Builder errorCode(@Nullable String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        @NonNull
        public AiOperationStatus build() {
            return new AiOperationStatus(this);
        }
    }
}

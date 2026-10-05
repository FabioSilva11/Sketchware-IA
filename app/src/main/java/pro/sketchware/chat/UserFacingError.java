package pro.sketchware.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Representa um erro formatado para apresentação ao usuário.
 * Contém título, mensagem amigável, ação sugerida e informações técnicas opcionais.
 */
public class UserFacingError {
    @NonNull
    private final String title;
    
    @NonNull
    private final String message;
    
    @Nullable
    private final String actionLabel;
    
    private final boolean canRetry;
    
    @Nullable
    private final String technicalCode;
    
    @Nullable
    private final String technicalDetails;

    private UserFacingError(@NonNull Builder builder) {
        this.title = builder.title;
        this.message = builder.message;
        this.actionLabel = builder.actionLabel;
        this.canRetry = builder.canRetry;
        this.technicalCode = builder.technicalCode;
        this.technicalDetails = builder.technicalDetails;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    @Nullable
    public String getActionLabel() {
        return actionLabel;
    }

    public boolean canRetry() {
        return canRetry;
    }

    @Nullable
    public String getTechnicalCode() {
        return technicalCode;
    }

    @Nullable
    public String getTechnicalDetails() {
        return technicalDetails;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        @NonNull
        private String title = "Erro";
        @NonNull
        private String message = "";
        @Nullable
        private String actionLabel = null;
        private boolean canRetry = false;
        @Nullable
        private String technicalCode = null;
        @Nullable
        private String technicalDetails = null;

        @NonNull
        public Builder title(@NonNull String title) {
            this.title = title;
            return this;
        }

        @NonNull
        public Builder message(@NonNull String message) {
            this.message = message;
            return this;
        }

        @NonNull
        public Builder actionLabel(@Nullable String actionLabel) {
            this.actionLabel = actionLabel;
            return this;
        }

        @NonNull
        public Builder canRetry(boolean canRetry) {
            this.canRetry = canRetry;
            return this;
        }

        @NonNull
        public Builder technicalCode(@Nullable String technicalCode) {
            this.technicalCode = technicalCode;
            return this;
        }

        @NonNull
        public Builder technicalDetails(@Nullable String technicalDetails) {
            this.technicalDetails = technicalDetails;
            return this;
        }

        @NonNull
        public UserFacingError build() {
            return new UserFacingError(this);
        }
    }
}

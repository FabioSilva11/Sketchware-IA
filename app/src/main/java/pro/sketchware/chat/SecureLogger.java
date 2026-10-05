package pro.sketchware.chat;


import pro.sketchware.BuildConfig;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Logger seguro que evita vazamento de dados sensíveis em produção.
 * Em builds de produção, mascara ou remove:
 * - Conteúdo completo de prompts e mensagens
 * - Argumentos de ferramentas
 * - Código de arquivos
 * - Chaves de API
 * - Tokens
 * - Dados privados do usuário
 */
public class SecureLogger {
    
    private static final String TAG_PREFIX = "Axion";
    private static final boolean IS_DEBUG_BUILD = BuildConfig.DEBUG;
    
    /**
     * Registra início de requisição com informações não-sensíveis.
     */
    public static void logRequestStart(@NonNull String requestId, @NonNull String provider, 
                                      @NonNull String model, int messagesCount, int toolsCount) {
        if (IS_DEBUG_BUILD) {
            Log.d(TAG_PREFIX, "=== REQUEST START ===");
            Log.d(TAG_PREFIX, "RequestId: " + requestId);
            Log.d(TAG_PREFIX, "Provider: " + provider);
            Log.d(TAG_PREFIX, "Model: " + model);
            Log.d(TAG_PREFIX, "Messages: " + messagesCount);
            Log.d(TAG_PREFIX, "Tools: " + toolsCount);
        } else {
            // Em produção, apenas métricas básicas
            Log.i(TAG_PREFIX, "Request " + requestId + " started: " + provider + "/" + model);
        }
    }

    /**
     * Registra conclusão de requisição.
     */
    public static void logRequestComplete(@NonNull String requestId, long durationMs, 
                                         int attemptNumber, boolean success) {
        String status = success ? "SUCCESS" : "FAILED";
        if (IS_DEBUG_BUILD) {
            Log.d(TAG_PREFIX, "=== REQUEST COMPLETE ===");
            Log.d(TAG_PREFIX, "RequestId: " + requestId);
            Log.d(TAG_PREFIX, "Duration: " + durationMs + "ms");
            Log.d(TAG_PREFIX, "Attempts: " + attemptNumber);
            Log.d(TAG_PREFIX, "Status: " + status);
        } else {
            Log.i(TAG_PREFIX, "Request " + requestId + " " + status + " in " + durationMs + "ms");
        }
    }

    /**
     * Registra erro sem expor conteúdo sensível.
     */
    public static void logError(@NonNull String requestId, int statusCode, 
                               @Nullable String errorCode, int attemptNumber) {
        if (IS_DEBUG_BUILD) {
            Log.e(TAG_PREFIX, "=== REQUEST ERROR ===");
            Log.e(TAG_PREFIX, "RequestId: " + requestId);
            Log.e(TAG_PREFIX, "StatusCode: " + statusCode);
            Log.e(TAG_PREFIX, "ErrorCode: " + (errorCode != null ? errorCode : "unknown"));
            Log.e(TAG_PREFIX, "Attempt: " + attemptNumber);
        } else {
            // Em produção, não expor detalhes do erro
            Log.w(TAG_PREFIX, "Request " + requestId + " error at attempt " + attemptNumber);
        }
    }

    /**
     * Registra retry sem expor conteúdo.
     */
    public static void logRetry(@NonNull String requestId, int attemptNumber, 
                               int maxAttempts, long delayMs, @NonNull String reason) {
        if (IS_DEBUG_BUILD) {
            Log.d(TAG_PREFIX, "=== RETRY SCHEDULED ===");
            Log.d(TAG_PREFIX, "RequestId: " + requestId);
            Log.d(TAG_PREFIX, "Attempt: " + attemptNumber + "/" + maxAttempts);
            Log.d(TAG_PREFIX, "Delay: " + delayMs + "ms");
            Log.d(TAG_PREFIX, "Reason: " + reason);
        } else {
            Log.i(TAG_PREFIX, "Request " + requestId + " retry " + attemptNumber + "/" + maxAttempts);
        }
    }

    /**
     * Registra execução de ferramenta sem expor argumentos completos.
     */
    public static void logToolExecution(@NonNull String requestId, @NonNull String toolName, 
                                       boolean success) {
        if (IS_DEBUG_BUILD) {
            Log.d(TAG_PREFIX, "Tool: " + toolName + " - " + (success ? "SUCCESS" : "FAILED"));
        } else {
            // Em produção, apenas nome da ferramenta
            Log.i(TAG_PREFIX, "Tool " + toolName + " executed");
        }
    }

    /**
     * Registra cancelamento.
     */
    public static void logCancellation(@NonNull String requestId, 
                                      @NonNull CancellationReason reason) {
        if (IS_DEBUG_BUILD) {
            Log.d(TAG_PREFIX, "=== REQUEST CANCELLED ===");
            Log.d(TAG_PREFIX, "RequestId: " + requestId);
            Log.d(TAG_PREFIX, "Reason: " + reason);
        } else {
            Log.i(TAG_PREFIX, "Request " + requestId + " cancelled");
        }
    }

    /**
     * Mascara uma API key para logs.
     * Exemplo: "sk-proj-abc123xyz" -> "sk-proj-...xyz"
     */
    @NonNull
    public static String maskApiKey(@Nullable String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "***";
        }
        // Mostrar apenas primeiros 7 e últimos 3 caracteres
        return apiKey.substring(0, 7) + "..." + apiKey.substring(apiKey.length() - 3);
    }

    /**
     * Trunca conteúdo para preview seguro.
     * Em produção, não registra nada. Em debug, limita a 200 caracteres.
     */
    @NonNull
    public static String safePreview(@Nullable String content) {
        if (!IS_DEBUG_BUILD) {
            return "[content hidden in production]";
        }
        if (content == null || content.isEmpty()) {
            return "[empty]";
        }
        if (content.length() <= 200) {
            return content;
        }
        return content.substring(0, 200) + "... [truncated]";
    }

    /**
     * Mascara URL removendo query parameters e tokens.
     */
    @NonNull
    public static String maskUrl(@Nullable String url) {
        if (url == null || url.isEmpty()) {
            return "[no url]";
        }
        
        // Remover query parameters que podem conter API keys
        int queryStart = url.indexOf('?');
        if (queryStart != -1) {
            return url.substring(0, queryStart) + "?[params hidden]";
        }
        
        // Remover tokens do path
        String masked = url.replaceAll("/sk-[a-zA-Z0-9_-]+", "/[token]");
        masked = masked.replaceAll("key=[a-zA-Z0-9_-]+", "key=[hidden]");
        masked = masked.replaceAll("token=[a-zA-Z0-9_-]+", "token=[hidden]");
        
        return masked;
    }

    /**
     * Verifica se estamos em modo debug.
     */
    public static boolean isDebugBuild() {
        return IS_DEBUG_BUILD;
    }

    /**
     * Log de debug - apenas em builds de desenvolvimento.
     */
    public static void d(@NonNull String tag, @NonNull String message) {
        if (IS_DEBUG_BUILD) {
            Log.d(TAG_PREFIX + "/" + tag, message);
        }
    }

    /**
     * Log de informação - visível em produção mas sem dados sensíveis.
     */
    public static void i(@NonNull String tag, @NonNull String message) {
        Log.i(TAG_PREFIX + "/" + tag, message);
    }

    /**
     * Log de warning - visível em produção.
     */
    public static void w(@NonNull String tag, @NonNull String message) {
        Log.w(TAG_PREFIX + "/" + tag, message);
    }

    /**
     * Log de erro - visível em produção mas sem stack traces completos ou dados sensíveis.
     */
    public static void e(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
        if (IS_DEBUG_BUILD) {
            Log.e(TAG_PREFIX + "/" + tag, message, throwable);
        } else {
            // Em produção, apenas a mensagem do erro sem stack trace
            String errorClass = throwable != null ? throwable.getClass().getSimpleName() : "unknown";
            Log.e(TAG_PREFIX + "/" + tag, message + " [" + errorClass + "]");
        }
    }
}

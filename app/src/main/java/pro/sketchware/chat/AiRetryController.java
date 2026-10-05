package pro.sketchware.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

/**
 * Controlador centralizado de retry para operações de IA.
 * Implementa política única de retry com:
 * - Limite global de 4 tentativas
 * - Backoff exponencial com jitter
 * - Respeito ao cabeçalho Retry-After
 * - Classificação de erros retriáveis vs permanentes
 */
public class AiRetryController {
    
    private static final String TAG = "AiRetryController";
    
    /** Limite global de tentativas por operação. */
    public static final int MAX_ATTEMPTS = 4;
    
    /** Delays-base por falha: ~2,5s, 6s, 12s e 24s antes do jitter. */
    private static final long[] BASE_DELAYS_MS = {2_500L, 6_000L, 12_000L, 24_000L};
    
    /** Delay mínimo após aplicar jitter (250ms). */
    private static final long MIN_DELAY_MS = 250L;
    
    /** Delay máximo permitido (60 segundos). */
    private static final long MAX_DELAY_MS = 60_000L;
    
    /** Porcentagem de jitter (20%). */
    private static final double JITTER_PERCENT = 0.20;

    /**
     * Marcadores de erros determinísticos de request/schema. Repetir a mesma
     * chamada só repetiria a mesma rejeição, então nunca são retriáveis —
     * mesmo quando o status HTTP bruto seria retriável (ex.: um 5xx que na
     * verdade é um erro de validação de declaração de ferramenta).
     */
    private static final String[] DETERMINISTIC_ERROR_MARKERS = {
            "invalid_argument",
            "invalid tool declaration",
            "schema validation",
            "validation error",
            "malformed request",
            "unknown field",
            "unknown name",
            "cannot find field",
            "invalid_request_error",
            "unsupported field",
    };

    /**
     * Resultado da avaliação de retry.
     */
    public static class RetryDecision {
        private final boolean shouldRetry;
        private final long delayMillis;
        private final String reason;

        private RetryDecision(boolean shouldRetry, long delayMillis, @NonNull String reason) {
            this.shouldRetry = shouldRetry;
            this.delayMillis = delayMillis;
            this.reason = reason;
        }

        public boolean shouldRetry() {
            return shouldRetry;
        }

        public long getDelayMillis() {
            return delayMillis;
        }

        @NonNull
        public String getReason() {
            return reason;
        }

        @NonNull
        public static RetryDecision retry(long delayMillis, @NonNull String reason) {
            return new RetryDecision(true, delayMillis, reason);
        }

        @NonNull
        public static RetryDecision noRetry(@NonNull String reason) {
            return new RetryDecision(false, 0, reason);
        }
    }

    /**
     * Determina se uma operação deve ser retriada com base no erro e tentativa atual.
     * 
     * @param attemptNumber tentativa atual (1-indexed, primeira tentativa = 1)
     * @param statusCode código HTTP de status, ou -1 para erros não-HTTP
     * @param errorBody corpo da resposta de erro
     * @param retryAfterSeconds valor do cabeçalho Retry-After em segundos, ou -1 se ausente
     * @param isNetworkError true se for erro de rede (IOException)
     * @return decisão de retry com delay calculado
     */
    @NonNull
    public RetryDecision shouldRetry(int attemptNumber, int statusCode, 
                                     @Nullable String errorBody, long retryAfterSeconds,
                                     boolean isNetworkError) {
        // Validar número da tentativa
        if (attemptNumber < 1) {
            attemptNumber = 1;
        }
        
        // Limite global de tentativas
        if (attemptNumber >= MAX_ATTEMPTS) {
            return RetryDecision.noRetry("Limite global de tentativas atingido (" + MAX_ATTEMPTS + ")");
        }

        // Erros determinísticos (400 / INVALID_ARGUMENT / schema inválido /
        // campo desconhecido / malformed request) não melhoram ao repetir.
        if (isDeterministicRequestError(statusCode, errorBody)) {
            return RetryDecision.noRetry("Erro determinístico de request/schema do provider");
        }

        // Classificar se o erro permite retry
        if (statusCode > 0) {
            // Erro HTTP
            if (!isRetriableHttpStatus(statusCode)) {
                return RetryDecision.noRetry("Erro HTTP não retriável: " + statusCode);
            }
        } else if (isNetworkError) {
            // Erro de rede é retriável
            Log.d(TAG, "Erro de rede detectado - permitindo retry");
        } else {
            // Erro desconhecido - não retrier
            return RetryDecision.noRetry("Tipo de erro desconhecido");
        }
        
        // Calcular delay
        long delayMs = calculateDelay(attemptNumber, retryAfterSeconds);
        
        String reason = statusCode == 429 
                ? "Rate limit - aguardando " + (delayMs / 1000) + "s"
                : "Erro temporário - tentativa " + (attemptNumber + 1) + " de " + MAX_ATTEMPTS;
        
        Log.d(TAG, "Retry aprovado: tentativa " + attemptNumber + ", delay " + delayMs + "ms, razão: " + reason);
        
        return RetryDecision.retry(delayMs, reason);
    }

    /**
     * Verifica se um código HTTP é retriável.
     */
    private boolean isRetriableHttpStatus(int statusCode) {
        // 408: Request Timeout
        // 429: Too Many Requests  
        // 500: Internal Server Error
        // 502: Bad Gateway
        // 503: Service Unavailable
        // 504: Gateway Timeout
        return statusCode == 408
                || statusCode == 429
                || statusCode == 500
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504;
    }

    /**
     * Erro determinístico de request/schema: 400 ou um corpo que declare
     * INVALID_ARGUMENT, declaração de ferramenta inválida, erro de validação
     * de schema, malformed request ou campo desconhecido. Repetir a mesma
     * chamada reproduziria exatamente a mesma rejeição.
     */
    private boolean isDeterministicRequestError(int statusCode, @Nullable String errorBody) {
        if (statusCode == 400) {
            return true;
        }
        if (errorBody == null || errorBody.trim().isEmpty()) {
            return false;
        }
        String lower = errorBody.toLowerCase(java.util.Locale.ROOT);
        for (String marker : DETERMINISTIC_ERROR_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calcula o delay para a próxima tentativa usando backoff exponencial com jitter.
     * 
     * @param attemptNumber tentativa atual (1-indexed)
     * @param retryAfterSeconds valor do cabeçalho Retry-After, ou -1 se ausente
     * @return delay em milissegundos
     */
    private long calculateDelay(int attemptNumber, long retryAfterSeconds) {
        long delayMs;
        
        // Se Retry-After foi fornecido, usá-lo como prioridade
        if (retryAfterSeconds > 0) {
            delayMs = Math.min(retryAfterSeconds * 1000L, MAX_DELAY_MS);
            Log.d(TAG, "Usando Retry-After: " + retryAfterSeconds + "s = " + delayMs + "ms");
            return delayMs;
        }
        
        // Faixas aproximadas após jitter de ±20%:
        // 2–3s, 5–7s, 10–14s e 20–28s.
        int index = Math.max(0, Math.min(attemptNumber - 1, BASE_DELAYS_MS.length - 1));
        long baseDelay = BASE_DELAYS_MS[index];

        // Limitar ao máximo
        baseDelay = Math.min(baseDelay, MAX_DELAY_MS);
        
        // Aplicar jitter (±20%)
        // Exemplo: se baseDelay = 4000ms, jitter varia de -800ms a +800ms
        double jitterRange = baseDelay * JITTER_PERCENT;
        double jitter = jitterRange * (Math.random() * 2.0 - 1.0);
        
        delayMs = (long) (baseDelay + jitter);
        
        // Garantir delay mínimo
        delayMs = Math.max(delayMs, MIN_DELAY_MS);
        
        Log.d(TAG, "Backoff calculado: tentativa " + attemptNumber 
                + ", base=" + baseDelay + "ms, jitter=" + (long)jitter + "ms, final=" + delayMs + "ms");
        
        return delayMs;
    }

    /**
     * Extrai o valor do cabeçalho Retry-After em segundos.
     * Suporta formato numérico (segundos) e formato HTTP-date.
     * 
     * @param retryAfterHeader valor do cabeçalho
     * @return segundos para aguardar, ou -1 se inválido/ausente
     */
    public static long parseRetryAfter(@Nullable String retryAfterHeader) {
        if (retryAfterHeader == null || retryAfterHeader.trim().isEmpty()) {
            return -1;
        }
        
        String value = retryAfterHeader.trim();
        
        try {
            // Tentar formato numérico (segundos)
            long seconds = Long.parseLong(value);
            // Limitar ao máximo razoável (60s)
            return Math.min(seconds, MAX_DELAY_MS / 1000L);
        } catch (NumberFormatException notNumeric) {
            // Tentar formato HTTP-date
            try {
                java.util.Date date = new java.text.SimpleDateFormat(
                        "EEE, dd MMM yyyy HH:mm:ss zzz", 
                        java.util.Locale.US).parse(value);
                if (date != null) {
                    long deltaMs = date.getTime() - System.currentTimeMillis();
                    if (deltaMs > 0) {
                        long seconds = deltaMs / 1000L;
                        return Math.min(seconds, MAX_DELAY_MS / 1000L);
                    }
                }
            } catch (Exception parseError) {
                Log.w(TAG, "Não foi possível parsear Retry-After: " + value, parseError);
            }
        }
        
        return -1;
    }
}

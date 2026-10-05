package pro.sketchware.chat;


import pro.sketchware.R;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.content.Context;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Classifica erros de requisições de IA e os converte em mensagens amigáveis ao usuário.
 * Distingue entre diferentes tipos de erros HTTP, rate limiting, timeouts e outros problemas.
 */
public class AiErrorClassifier {
    
    private final Context context;

    public AiErrorClassifier(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Classifica um erro HTTP e retorna uma representação amigável ao usuário.
     */
    @NonNull
    public UserFacingError classifyHttpError(int statusCode, @Nullable String errorBody, 
                                            @Nullable String providerId) {
        String provider = providerId != null ? providerId : context.getString(R.string.ai_provider_generic_name);

        UserFacingError axionError = classifyAxionGatewayError(statusCode, errorBody);
        if (axionError != null) {
            return axionError;
        }
        
        switch (statusCode) {
            case 400:
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_invalid_request_title))
                        .message(context.getString(R.string.ai_error_invalid_request_message))
                        .canRetry(false)
                        .technicalCode("HTTP 400")
                        .technicalDetails(errorBody)
                        .build();
                        
            case 401:
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_auth_required_title))
                        .message(context.getString(R.string.ai_error_auth_required_message, provider))
                        .actionLabel(context.getString(R.string.ai_action_check_configuration))
                        .canRetry(false)
                        .technicalCode("HTTP 401")
                        .technicalDetails(errorBody)
                        .build();
                        
            case 403:
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_access_denied_title))
                        .message(context.getString(R.string.ai_error_access_denied_message))
                        .canRetry(false)
                        .technicalCode("HTTP 403")
                        .technicalDetails(errorBody)
                        .build();
                        
            case 404:
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_model_not_found_title))
                        .message(context.getString(R.string.ai_error_model_not_found_message, provider))
                        .actionLabel(context.getString(R.string.ai_action_select_other_model))
                        .canRetry(false)
                        .technicalCode("HTTP 404")
                        .technicalDetails(errorBody)
                        .build();
                        
            case 408:
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_timeout_title))
                        .message(context.getString(R.string.ai_error_timeout_message))
                        .actionLabel(context.getString(R.string.common_retry))
                        .canRetry(true)
                        .technicalCode("HTTP 408")
                        .technicalDetails(errorBody)
                        .build();
                        
            case 429:
                return classifyRateLimitError(errorBody, provider);
                
            case 500:
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_server_title))
                        .message(context.getString(R.string.ai_error_server_message, provider))
                        .actionLabel(context.getString(R.string.common_retry))
                        .canRetry(true)
                        .technicalCode("HTTP 500")
                        .technicalDetails(errorBody)
                        .build();
                        
            case 502:
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_service_temporarily_unavailable_title))
                        .message(context.getString(R.string.ai_error_service_temporarily_unavailable_message))
                        .actionLabel(context.getString(R.string.common_retry))
                        .canRetry(true)
                        .technicalCode("HTTP 502")
                        .technicalDetails(errorBody)
                        .build();
                        
            case 503:
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_service_unavailable_title))
                        .message(context.getString(R.string.ai_error_service_unavailable_message, provider))
                        .actionLabel(context.getString(R.string.common_retry))
                        .canRetry(true)
                        .technicalCode("HTTP 503")
                        .technicalDetails(errorBody)
                        .build();
                        
            case 504:
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_gateway_timeout_title))
                        .message(context.getString(R.string.ai_error_gateway_timeout_message))
                        .actionLabel(context.getString(R.string.common_retry))
                        .canRetry(true)
                        .technicalCode("HTTP 504")
                        .technicalDetails(errorBody)
                        .build();
                        
            default:
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_request_title))
                        .message(context.getString(R.string.ai_error_request_message, provider))
                        .actionLabel(context.getString(R.string.common_retry))
                        .canRetry(statusCode >= 500)
                        .technicalCode("HTTP " + statusCode)
                        .technicalDetails(errorBody)
                        .build();
        }
    }


    @Nullable
    private UserFacingError classifyAxionGatewayError(int statusCode, @Nullable String errorBody) {
        if (errorBody == null || errorBody.trim().isEmpty()) return null;
        String code = "";
        String serverMessage = "";
        try {
            JSONObject root = new JSONObject(errorBody);
            JSONObject error = root.optJSONObject("error");
            if (error != null) {
                code = error.optString("code", "").trim();
                serverMessage = error.optString("message", "").trim();
            }
        } catch (Exception ignored) {
            return null;
        }
        if (code.isEmpty()) return null;
        String details = serverMessage.isEmpty() ? errorBody : serverMessage;
        switch (code) {
            case "authentication_required":
            case "invalid_token":
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_session_expired_title))
                        .message(context.getString(R.string.ai_error_session_expired_message))
                        .actionLabel(context.getString(R.string.chat_sign_in_again))
                        .canRetry(false)
                        .technicalCode(code)
                        .technicalDetails(details)
                        .build();
            case "provider_plan_required":
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_provider_plan_title))
                        .message(context.getString(R.string.ai_error_provider_plan_message))
                        .actionLabel(context.getString(R.string.ai_action_choose_provider))
                        .canRetry(false)
                        .technicalCode(code)
                        .technicalDetails(details)
                        .build();
            case "model_not_in_plan":
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_model_plan_title))
                        .message(context.getString(R.string.ai_error_model_plan_message))
                        .actionLabel(context.getString(R.string.ai_action_update_models))
                        .canRetry(false)
                        .technicalCode(code)
                        .technicalDetails(details)
                        .build();
            case "insufficient_credits":
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_insufficient_credits_title))
                        .message(context.getString(R.string.ai_error_insufficient_credits_message))
                        .actionLabel(context.getString(R.string.chat_view_plans_action))
                        .canRetry(false)
                        .technicalCode(code)
                        .technicalDetails(details)
                        .build();
            case "rate_limit_exceeded":
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_many_requests_title))
                        .message(context.getString(R.string.ai_error_many_requests_message))
                        .actionLabel(context.getString(R.string.ai_action_wait))
                        .canRetry(true)
                        .technicalCode(code)
                        .technicalDetails(details)
                        .build();
            case "daily_limit_exceeded":
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_daily_limit_title))
                        .message(context.getString(R.string.ai_error_daily_limit_message))
                        .canRetry(false)
                        .technicalCode(code)
                        .technicalDetails(details)
                        .build();
            case "duplicate_request":
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_duplicate_request_title))
                        .message(context.getString(R.string.ai_error_duplicate_request_message))
                        .canRetry(false)
                        .technicalCode(code)
                        .technicalDetails(details)
                        .build();
            case "user_not_found":
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_server_title))
                        .message(context.getString(
                                R.string.ai_error_server_message,
                                context.getString(R.string.ai_provider_generic_name)))
                        .actionLabel(context.getString(R.string.common_retry))
                        .canRetry(true)
                        .technicalCode(code)
                        .technicalDetails(details)
                        .build();
            case "model_not_found":
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_model_unavailable_title))
                        .message(context.getString(R.string.ai_error_model_unavailable_message))
                        .actionLabel(context.getString(R.string.ai_action_choose_model))
                        .canRetry(false)
                        .technicalCode(code)
                        .technicalDetails(details)
                        .build();
            case "provider_error":
            case "provider_timeout":
            case "provider_unavailable":
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_provider_temp_unavailable_title))
                        .message(context.getString(R.string.ai_error_provider_temp_unavailable_message))
                        .actionLabel(context.getString(R.string.common_retry))
                        .canRetry(statusCode >= 500)
                        .technicalCode(code)
                        .technicalDetails(details)
                        .build();
            default:
                return null;
        }
    }

    /**
     * Classifica especificamente erros de rate limit (HTTP 429).
     * Distingue entre diferentes tipos de rate limiting.
     */
    @NonNull
    private UserFacingError classifyRateLimitError(@Nullable String errorBody, @NonNull String provider) {
        if (errorBody == null) {
            errorBody = "";
        }
        
        String errorLower = errorBody.toLowerCase(Locale.ROOT);
        
        // Provedor temporariamente ocupado
        if (errorLower.contains("provider_rate_limited") || 
            errorLower.contains("provider is currently overloaded")) {
            return UserFacingError.builder()
                    .title(context.getString(R.string.ai_error_provider_busy_title))
                    .message(context.getString(R.string.ai_error_provider_busy_message))
                    .actionLabel(context.getString(R.string.ai_action_wait))
                    .canRetry(true)
                    .technicalCode("HTTP 429 - provider_rate_limited")
                    .technicalDetails(errorBody)
                    .build();
        }
        
        // Limite de solicitações por minuto
        if (errorLower.contains("rate_limit_exceeded") ||
            errorLower.contains("requests per minute") ||
            errorLower.contains("rpm")) {
            return UserFacingError.builder()
                    .title(context.getString(R.string.ai_error_temporary_limit_title))
                    .message(context.getString(R.string.ai_error_temporary_limit_message))
                    .actionLabel(context.getString(R.string.ai_action_wait))
                    .canRetry(true)
                    .technicalCode("HTTP 429 - rate_limit_exceeded")
                    .technicalDetails(errorBody)
                    .build();
        }
        
        // Limite de tokens por minuto
        if (errorLower.contains("tokens per minute") ||
            errorLower.contains("tpm") ||
            errorLower.contains("token limit")) {
            return UserFacingError.builder()
                    .title(context.getString(R.string.ai_error_token_limit_title))
                    .message(context.getString(R.string.ai_error_token_limit_message))
                    .actionLabel(context.getString(R.string.ai_action_wait))
                    .canRetry(true)
                    .technicalCode("HTTP 429 - token_limit")
                    .technicalDetails(errorBody)
                    .build();
        }
        
        // Limite da conta ou plano
        if (errorLower.contains("insufficient_quota") ||
            errorLower.contains("quota exceeded") ||
            errorLower.contains("billing") ||
            errorLower.contains("credits")) {
            return UserFacingError.builder()
                    .title(context.getString(R.string.ai_error_account_limit_title))
                    .message(context.getString(R.string.ai_error_account_limit_message))
                    .actionLabel(context.getString(R.string.ai_action_check_plan))
                    .canRetry(false)
                    .technicalCode("HTTP 429 - insufficient_quota")
                    .technicalDetails(errorBody)
                    .build();
        }
        
        // Rate limit genérico
        return UserFacingError.builder()
                .title(context.getString(R.string.ai_error_rate_limit_title))
                .message(context.getString(R.string.ai_error_rate_limit_message, provider))
                .actionLabel(context.getString(R.string.ai_action_wait))
                .canRetry(true)
                .technicalCode("HTTP 429")
                .technicalDetails(errorBody)
                .build();
    }

    /**
     * Classifica erros de rede (IOException, timeouts, etc).
     */
    @NonNull
    public UserFacingError classifyNetworkError(@Nullable Throwable error) {
        if (error == null) {
            return UserFacingError.builder()
                    .title(context.getString(R.string.ai_error_network_title))
                    .message(context.getString(R.string.ai_error_network_message))
                    .canRetry(true)
                    .build();
        }
        
        String errorMessage = error.getMessage();
        if (errorMessage == null) {
            errorMessage = error.getClass().getSimpleName();
        }
        
        String messageLower = errorMessage.toLowerCase(Locale.ROOT);
        
        // Timeout
        if (messageLower.contains("timeout") || messageLower.contains("timed out")) {
            return UserFacingError.builder()
                    .title(context.getString(R.string.ai_error_response_slow_title))
                    .message(context.getString(R.string.ai_error_response_slow_message))
                    .actionLabel(context.getString(R.string.common_retry))
                    .canRetry(true)
                    .technicalCode("Timeout")
                    .technicalDetails(errorMessage)
                    .build();
        }
        
        // Sem internet
        if (messageLower.contains("network") || 
            messageLower.contains("no route") ||
            messageLower.contains("unreachable") ||
            messageLower.contains("connection refused")) {
            return UserFacingError.builder()
                    .title(context.getString(R.string.ai_error_no_connection_title))
                    .message(context.getString(R.string.ai_error_no_connection_message))
                    .actionLabel(context.getString(R.string.common_retry))
                    .canRetry(true)
                    .technicalCode("Network Error")
                    .technicalDetails(errorMessage)
                    .build();
        }
        
        // Erro genérico de rede
        return UserFacingError.builder()
                .title(context.getString(R.string.ai_error_communication_title))
                .message(context.getString(R.string.ai_error_communication_message))
                .actionLabel(context.getString(R.string.common_retry))
                .canRetry(true)
                .technicalCode("IOException")
                .technicalDetails(errorMessage)
                .build();
    }

    /**
     * Classifica erro de cancelamento.
     */
    @NonNull
    public UserFacingError classifyCancellation(@NonNull CancellationReason reason) {
        switch (reason) {
            case USER_REQUESTED:
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_operation_cancelled_title))
                        .message(context.getString(R.string.ai_error_cancelled_by_user_message))
                        .canRetry(false)
                        .build();
                        
            case NEW_MESSAGE_SENT:
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_operation_interrupted_title))
                        .message(context.getString(R.string.ai_error_new_message_interrupted_message))
                        .canRetry(false)
                        .build();
                        
            case TIMEOUT:
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_timeout_title))
                        .message(context.getString(R.string.ai_error_timeout_cancel_message))
                        .actionLabel(context.getString(R.string.common_retry))
                        .canRetry(true)
                        .build();
                        
            case SCREEN_CLOSED:
            case APP_BACKGROUNDED:
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_operation_interrupted_title))
                        .message(context.getString(R.string.ai_error_changes_preserved_message))
                        .canRetry(false)
                        .build();
                        
            case INTERNAL_ERROR:
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_internal_title))
                        .message(context.getString(R.string.ai_error_internal_message))
                        .actionLabel(context.getString(R.string.common_retry))
                        .canRetry(true)
                        .build();
                        
            case UNKNOWN:
            default:
                return UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_operation_cancelled_title))
                        .message(context.getString(R.string.ai_error_cancelled_generic_message))
                        .canRetry(false)
                        .build();
        }
    }
}

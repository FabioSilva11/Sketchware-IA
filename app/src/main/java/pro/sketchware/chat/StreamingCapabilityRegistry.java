package pro.sketchware.chat;

import pro.sketchware.chat.port.VoidPortLlmMessage.ProviderFamily;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aprende em runtime se um provider/modelo realmente entrega streaming.
 *
 * A capacidade e cacheada somente quando existe evidencia positiva (SSE/NDJSON)
 * ou quando o fallback sem streaming conclui com sucesso. Erros de auth, rate
 * limit, timeout e 5xx genericos nunca desativam streaming.
 */
public final class StreamingCapabilityRegistry {
    public enum Capability { UNKNOWN, SUPPORTED, UNSUPPORTED }

    private static final long TTL_MILLIS = 6L * 60L * 60L * 1000L;
    private static final ConcurrentHashMap<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static String activeSelectionKey = "";

    private static final class Entry {
        final Capability capability;
        final long timestamp;
        Entry(Capability capability, long timestamp) {
            this.capability = capability;
            this.timestamp = timestamp;
        }
    }

    private StreamingCapabilityRegistry() {}

    public static String key(String providerId, String baseUrl, String modelName) {
        return normalize(providerId) + '|' + normalize(baseUrl) + '|' + normalize(modelName);
    }

    public static Capability get(String key) {
        Entry entry = CACHE.get(key == null ? "" : key);
        if (entry == null) return Capability.UNKNOWN;
        if (System.currentTimeMillis() - entry.timestamp > TTL_MILLIS) {
            CACHE.remove(key, entry);
            return Capability.UNKNOWN;
        }
        return entry.capability;
    }

    public static boolean shouldUseStreaming(String key) {
        return get(key) != Capability.UNSUPPORTED;
    }

    /**
     * Seleciona o modo para a requisicao atual. Ao detectar troca de
     * provider/endpoint/modelo, esquece qualquer decisao antiga do novo alvo e
     * volta a tentar streaming. Assim, stream=false permanece apenas enquanto
     * o mesmo modelo continua selecionado.
     */
    public static synchronized boolean shouldUseStreamingForSelection(String key) {
        String safeKey = key == null ? "" : key;
        if (!safeKey.equals(activeSelectionKey)) {
            CACHE.remove(safeKey);
            activeSelectionKey = safeKey;
        }
        return shouldUseStreaming(safeKey);
    }

    public static void markSupported(String key) {
        put(key, Capability.SUPPORTED);
    }

    public static void markUnsupported(String key) {
        put(key, Capability.UNSUPPORTED);
    }

    private static void put(String key, Capability capability) {
        if (key == null || key.isEmpty()) return;
        CACHE.put(key, new Entry(capability, System.currentTimeMillis()));
    }

    /**
     * Retorna true somente para falhas plausivelmente relacionadas ao modo
     * streaming. Erros explicitamente deterministas (por exemplo
     * stream_not_supported/stream=false) podem ser memorizados antes do retry;
     * falhas ambiguas so viram UNSUPPORTED quando o fallback JSON tem sucesso.
     */
    public static boolean shouldAttemptFallback(
            ProviderFamily family, int statusCode, String errorBody) {
        if (statusCode == 401 || statusCode == 403 || statusCode == 408
                || statusCode == 409 || statusCode == 425 || statusCode == 429) {
            return false;
        }
        if (statusCode >= 500 && statusCode != 501) {
            return false;
        }
        if (isExplicitStreamingDisabledError(statusCode, errorBody)) {
            return true;
        }
        String body = normalize(errorBody);
        boolean mentionsStream = body.contains("stream")
                || body.contains("sse")
                || body.contains("server-sent")
                || body.contains("streamgeneratecontent");
        boolean unsupported = body.contains("unsupported")
                || body.contains("not_supported")
                || body.contains("not-supported")
                || body.contains("not supported")
                || body.contains("does not support")
                || body.contains("unknown parameter")
                || body.contains("unrecognized")
                || body.contains("invalid parameter")
                || body.contains("not allowed")
                || body.contains("not implemented")
                || body.contains("not available");

        if ((statusCode == 400 || statusCode == 415 || statusCode == 422 || statusCode == 501)
                && mentionsStream && unsupported) {
            return true;
        }
        // Gemini usa um endpoint diferente para streamGenerateContent. Um 404/405
        // nesse endpoint pode ser recuperado por generateContent; so marcamos a
        // capacidade como UNSUPPORTED se esse segundo request realmente funcionar.
        if (family == ProviderFamily.GEMINI && (statusCode == 404 || statusCode == 405)) {
            return true;
        }
        return statusCode == 405 && mentionsStream;
    }

    /**
     * Detecta respostas definitivas de que a chamada precisa usar stream=false.
     * Esses sinais podem ser memorizados imediatamente, antes mesmo do retry,
     * pois nao representam um erro ambiguo de parametros.
     */
    public static boolean isExplicitStreamingDisabledError(int statusCode, String errorBody) {
        if (!(statusCode == 400 || statusCode == 405 || statusCode == 415
                || statusCode == 422 || statusCode == 501)) {
            return false;
        }
        String body = normalize(errorBody);
        if (body.isEmpty()) return false;

        if (body.contains("stream_not_supported")
                || body.contains("stream-not-supported")
                || body.contains("streaming_not_supported")
                || body.contains("streaming-not-supported")) {
            return true;
        }

        // Gateways OpenAI-compativeis costumam responder literalmente que o
        // parametro stream precisa ser false. Aceitamos variacoes compactas e
        // mensagens em PT/EN sem depender de um texto exato do backend.
        String compact = body.replace(" ", "").replace("\t", "");
        if (compact.contains("stream=false")) {
            return true;
        }

        boolean mentionsStream = body.contains("stream");
        boolean requiresFalse = body.contains("must be false")
                || body.contains("must be set to false")
                || body.contains("requires false")
                || body.contains("requires stream false")
                || body.contains("requer false")
                || body.contains("requer stream false")
                || body.contains("deve ser false")
                || body.contains("precisa ser false");
        return mentionsStream && requiresFalse;
    }


    /** Decide o parser de resposta sem depender de headers perfeitos de proxies. */
    public static boolean shouldParseJsonBody(String contentType, boolean requestedStreaming) {
        String type = normalize(contentType);
        if (type.contains("text/event-stream") || type.contains("ndjson")) {
            return false;
        }
        if (type.contains("application/json") || type.contains("+json")) {
            return true;
        }
        // Alguns gateways omitem Content-Type no modo nao-streaming.
        return !requestedStreaming;
    }

    static synchronized void clearForTests() {
        CACHE.clear();
        activeSelectionKey = "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

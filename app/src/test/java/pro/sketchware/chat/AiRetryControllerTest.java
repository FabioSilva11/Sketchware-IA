package pro.sketchware.chat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.AiRetryController.RetryDecision;

import org.junit.Test;

/**
 * Retry classification: deterministic request/schema errors (HTTP 400,
 * INVALID_ARGUMENT, invalid tool declaration, schema validation error,
 * malformed request, unknown field) must never be retried, while legitimate
 * transient failures (timeout, connection, rate limit, 5xx) keep retrying.
 */
public class AiRetryControllerTest {

    private final AiRetryController controller = new AiRetryController();

    private static final String INVALID_ARGUMENT_BODY =
            "{\"error\":{\"code\":400,\"message\":\"Invalid argument\","
            + "\"status\":\"INVALID_ARGUMENT\",\"details\":{}}}";

    private static final String OUTPUT_SCHEMA_UNKNOWN_FIELD_BODY =
            "{\"error\":{\"code\":400,\"message\":\"Unknown name \\\"output_schema\\\" "
            + "at 'request.tools[0].function_declarations[5]': Cannot find field.\"}}";

    private static final String INVALID_TOOL_DECLARATION_BODY =
            "{\"error\":{\"message\":\"Invalid tool declaration: parameters must "
            + "be an object schema\",\"type\":\"invalid_request_error\"}}";

    private static final String SCHEMA_VALIDATION_ERROR_BODY =
            "{\"error\":{\"code\":400,\"message\":\"Schema validation error at "
            + "tools[0]: additionalProperties is not allowed\"}}";

    // ------------------------------------------------------------------
    // Deterministic errors → never retried
    // ------------------------------------------------------------------

    @Test
    public void http400IsNeverRetried() {
        RetryDecision decision = controller.shouldRetry(1, 400, "{\"error\":{\"message\":\"bad request\"}}",
                -1L, false);
        assertFalse(decision.shouldRetry());
    }

    @Test
    public void invalidArgumentIsNeverRetried() {
        RetryDecision decision = controller.shouldRetry(1, 400, INVALID_ARGUMENT_BODY, -1L, false);
        assertFalse(decision.shouldRetry());
        assertTrue(decision.getReason().toLowerCase().contains("determin"));
    }

    @Test
    public void unknownFieldProtobufErrorIsNeverRetried() {
        RetryDecision decision = controller.shouldRetry(
                1, 400, OUTPUT_SCHEMA_UNKNOWN_FIELD_BODY, -1L, false);
        assertFalse(decision.shouldRetry());
    }

    @Test
    public void invalidToolDeclarationIsNeverRetried() {
        RetryDecision decision = controller.shouldRetry(
                1, 400, INVALID_TOOL_DECLARATION_BODY, -1L, false);
        assertFalse(decision.shouldRetry());
    }

    @Test
    public void schemaValidationErrorIsNeverRetried() {
        RetryDecision decision = controller.shouldRetry(
                1, 400, SCHEMA_VALIDATION_ERROR_BODY, -1L, false);
        assertFalse(decision.shouldRetry());
    }

    @Test
    public void deterministicBodyWinsOverRetriableHttpStatus() {
        // A 500 whose body is really a deterministic schema rejection must not
        // be retried — repeating the same payload only repeats the rejection.
        RetryDecision decision = controller.shouldRetry(
                1, 500, OUTPUT_SCHEMA_UNKNOWN_FIELD_BODY, -1L, false);
        assertFalse(decision.shouldRetry());
    }

    @Test
    public void nullBody400IsNotRetried() {
        RetryDecision decision = controller.shouldRetry(1, 400, null, -1L, false);
        assertFalse(decision.shouldRetry());
    }

    // ------------------------------------------------------------------
    // Transient errors → keep retrying
    // ------------------------------------------------------------------

    @Test
    public void networkErrorIsRetried() {
        RetryDecision decision = controller.shouldRetry(1, -1, null, -1L, true);
        assertTrue(decision.shouldRetry());
    }

    @Test
    public void timeout408IsRetried() {
        RetryDecision decision = controller.shouldRetry(1, 408, "", -1L, false);
        assertTrue(decision.shouldRetry());
    }

    @Test
    public void rateLimited429IsRetried() {
        RetryDecision decision = controller.shouldRetry(1, 429, "", 2L, false);
        assertTrue(decision.shouldRetry());
        assertTrue(decision.getDelayMillis() > 0);
    }

    @Test
    public void serverError500IsRetriedWhenBodyIsNotDeterministic() {
        RetryDecision decision = controller.shouldRetry(
                1, 500, "{\"error\":\"upstream timeout after 30s\"}", -1L, false);
        assertTrue(decision.shouldRetry());
    }

    @Test
    public void serverError503IsRetried() {
        RetryDecision decision = controller.shouldRetry(1, 503, "service unavailable", -1L, false);
        assertTrue(decision.shouldRetry());
    }

    @Test
    public void attemptLimitIsRespected() {
        RetryDecision decision = controller.shouldRetry(AiRetryController.MAX_ATTEMPTS,
                500, "transient", -1L, false);
        assertFalse(decision.shouldRetry());
    }
}
package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.agentsdk.tools.ToolRegistration;
import pro.sketchware.chat.toolcalling.ToolCall;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The single decision source of the permission model (Codex parity): turns a
 * {@link PermissionConfig} plus a registered tool into ALLOW / PROMPT / DENY.
 * The {@code PermissionLayer} then only ENFORCES the decision; it never
 * re-decides, and the {@code ApprovalPolicy} never converts a DENY into an
 * ALLOW.
 *
 * <p>Rules (spec parity):
 * <pre>
 *   READ_ONLY            -> safe (read-only) tools ALLOW, everything else DENY
 *   WORKSPACE + ON_REQUEST -> safe reads ALLOW, risky tools PROMPT
 *   WORKSPACE + NEVER    -> EXTERNAL_ACCESS DENY (absolute), rest ALLOW
 *   DANGER_FULL_ACCESS   -> everything ALLOW (user confirmed full authority)
 *   handoffs             -> ALWAYS ALLOW
 * </pre></p>
 */
public final class PermissionEvaluator {

    /** The outcome for a given tool call. */
    public enum Decision { ALLOW, PROMPT, DENY }

    /** Capabilities that are NEVER allowed inside the logical workspace. */
    public static final Set<ToolCapability> ABSOLUTELY_FORBIDDEN_WORKSPACE =
            Collections.unmodifiableSet(EnumSet.of(ToolCapability.EXTERNAL_ACCESS));

    /** Capabilities that make a tool "risky" (need a decision under ON_REQUEST). */
    private static final Set<ToolCapability> RISKY = Collections.unmodifiableSet(EnumSet.of(
            ToolCapability.WORKSPACE_WRITE,
            ToolCapability.DESTRUCTIVE,
            ToolCapability.NETWORK,
            ToolCapability.EXTERNAL_ACCESS));

    private static final Set<String> KNOWN_READ_NAMES = new HashSet<>(Arrays.asList(
            "get_context_remaining", "tool_search",
            "update_plan", "request_user_input"));

    private static final Set<String> MUTATION_TOKENS =
            new HashSet<>(Arrays.asList("write", "create", "update", "edit", "set",
                    "add", "put", "append", "insert", "upload"));

    private static final Set<String> DELETE_TOKENS =
            new HashSet<>(Arrays.asList("delete", "remove", "drop", "clear", "truncate"));

    private static final Set<String> READ_TOKENS =
            new HashSet<>(Arrays.asList("read", "list", "get", "search", "find", "query", "fetch"));

    private PermissionEvaluator() {
        throw new AssertionError("no instances");
    }

    // ------------------------------------------------------------------
    // Decision
    // ------------------------------------------------------------------

    /** Decides for a registered tool (call args are moot for capability-based policy). */
    public static Decision evaluate(PermissionConfig config, ToolRegistration registration) {
        return evaluate(config, registration, null);
    }

    /**
     * Decides for a registered tool and its pending call. The call is
     * currently unused — capability metadata is the whole story — but kept as
     * the extension point for future GRANULAR policies that consider
     * arguments.
     */
    public static Decision evaluate(PermissionConfig config, ToolRegistration registration, ToolCall call) {
        // Fail-closed (Requirement 39): an unknown registration is never
        // trusted with ALLOW-by-default. There is nothing to evaluate a
        // capability against, so the safe outcome is DENY.
        if (registration == null) {
            return Decision.DENY;
        }
        // A missing config is not "no restrictions" — it falls back to the
        // known-safe WORKSPACE + ON_REQUEST profile (reads allowed, risky
        // capabilities PROMPT) rather than ALLOW-everything.
        if (config == null) {
            config = PermissionConfig.workspaceRequest();
        }
        if (registration.isHandoff()) {
            return Decision.ALLOW;
        }
        Set<ToolCapability> caps = capabilitiesOf(registration);
        switch (config.profile()) {
            case READ_ONLY:
                return isSafe(caps) ? Decision.ALLOW : Decision.DENY;
            case DANGER_FULL_ACCESS:
                return Decision.ALLOW;
            case WORKSPACE:
            default:
                if (config.approvalPolicy() == ApprovalPolicy.NEVER) {
                    return containsAny(caps, ABSOLUTELY_FORBIDDEN_WORKSPACE)
                            ? Decision.DENY : Decision.ALLOW;
                }
                return isSafe(caps) ? Decision.ALLOW : Decision.PROMPT;
        }
    }

    // ------------------------------------------------------------------
    // Capability resolution
    // ------------------------------------------------------------------

    /**
     * Declared capabilities when present; otherwise a defensive inference
     * from metadata flags, source and name. Inferred capabilities are only a
     * fallback — registrations always DECLARE what they can do.
     */
    public static Set<ToolCapability> capabilitiesOf(ToolRegistration registration) {
        if (registration == null) {
            return Collections.emptySet();
        }
        Set<ToolCapability> declared = registration.capabilities();
        if (!declared.isEmpty()) {
            return declared;
        }
        return Collections.unmodifiableSet(infer(registration));
    }

    static boolean isSafe(Set<ToolCapability> caps) {
        return caps == null || caps.isEmpty() || !containsAny(caps, RISKY);
    }

    static boolean containsAny(Set<ToolCapability> caps, Set<ToolCapability> wanted) {
        if (caps == null || wanted == null) {
            return false;
        }
        for (ToolCapability cap : caps) {
            if (wanted.contains(cap)) {
                return true;
            }
        }
        return false;
    }

    static Set<ToolCapability> infer(ToolRegistration registration) {
        EnumSet<ToolCapability> caps = EnumSet.noneOf(ToolCapability.class);

        // 1. Declared safety flags (legacy registrations kept parity).
        if (registration.isDestructive()) {
            caps.add(ToolCapability.DESTRUCTIVE);
            caps.add(ToolCapability.WORKSPACE_WRITE);
        } else if (registration.isFileMutation()) {
            caps.add(ToolCapability.WORKSPACE_WRITE);
        }

        String qualified = registration.qualifiedName() == null ? "" : registration.qualifiedName();
        String name = registration.spec().name().name() == null ? "" : registration.spec().name().name();
        String source = registration.source() == null ? "" : registration.source();

        if (caps.isEmpty()) {
            // 2. MCP tools carry NETWORK by name classification (the adapter
            //    declares real caps; this is the defensive fallback).
            if (source.startsWith("mcp:")) {
                caps.addAll(mcpCapabilities(name));
            }
            // 4. Known helpers / clock namespaces read context.
            else if (KNOWN_READ_NAMES.contains(qualified) || "clock".equals(registration.spec().name().namespace())) {
                caps.add(ToolCapability.READ);
            }
        }
        return copiesOf(caps);
    }

    private static boolean nameHasToken(String name, Set<String> tokens) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static Set<ToolCapability> copiesOf(EnumSet<ToolCapability> caps) {
        return caps.isEmpty() ? EnumSet.noneOf(ToolCapability.class) : EnumSet.copyOf(caps);
    }

    /**
     * Capabilities for a raw MCP tool name: always NETWORK, plus
     * DESTRUCTIVE/WORKSPACE_WRITE/READ hints from the name. Declared by the
     * {@code McpToolAdapter} so the evaluator never guesses.
     */
    public static Set<ToolCapability> mcpCapabilities(String toolName) {
        EnumSet<ToolCapability> caps = EnumSet.of(ToolCapability.NETWORK);
        String lower = toolName == null ? "" : toolName.toLowerCase(Locale.ROOT);
        if (nameHasToken(lower, DELETE_TOKENS)) {
            caps.add(ToolCapability.DESTRUCTIVE);
            caps.add(ToolCapability.WORKSPACE_WRITE);
        } else if (nameHasToken(lower, MUTATION_TOKENS)) {
            caps.add(ToolCapability.WORKSPACE_WRITE);
        } else if (nameHasToken(lower, READ_TOKENS)) {
            caps.add(ToolCapability.READ);
        }
        return Collections.unmodifiableSet(caps);
    }
}
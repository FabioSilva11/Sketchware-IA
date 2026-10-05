package pro.sketchware.chat.agentsdk.tools;

/**
 * Model-facing tool identity, ported from Codex {@code ToolName}: a tool name
 * is either plain ({@code "read_file"}) or namespaced
 * ({@code "mcp__server_a.search"}, {@code "clock.curr_time"}).
 *
 * <p>The fully-qualified string is {@code namespace + "." + name}; equality
 * and hashing operate on both fields, so {@code server_a.search} is a
 * different tool from {@code server_b.search} (MCP collision parity).</p>
 */
public final class ToolName {

    private final String namespace;
    private final String name;

    private ToolName(String namespace, String name) {
        this.namespace = namespace == null || namespace.trim().isEmpty() ? "" : namespace.trim();
        this.name = name == null || name.trim().isEmpty() ? "" : name.trim();
    }

    /** Plain (root-namespace) tool name, e.g. {@code "read_file"}. */
    public static ToolName plain(String name) {
        return new ToolName("", name);
    }

    /** Namespaced tool name, e.g. {@code ("clock", "curr_time")}. */
    public static ToolName namespaced(String namespace, String name) {
        return new ToolName(namespace, name);
    }

    /**
     * Parses a fully-qualified string ({@code "ns.name"} -> namespaced,
     * {@code "name"} -> plain). Mirrors Codex {@code ToolName::from_str}:
     * splits at the LAST dot; empty namespace is tolerated and normalised.
     */
    public static ToolName parse(String fullyQualified) {
        if (fullyQualified == null || fullyQualified.trim().isEmpty()) {
            return new ToolName("", "");
        }
        String value = fullyQualified.trim();
        int dot = value.lastIndexOf('.');
        if (dot <= 0 || dot == value.length() - 1) {
            return new ToolName("", value);
        }
        return new ToolName(value.substring(0, dot), value.substring(dot + 1));
    }

    /** The namespace this tool lives in ({@code ""} for plain tools). */
    public String namespace() {
        return namespace;
    }

    /** Whether this tool is a plain (root-namespace) tool. */
    public boolean isPlain() {
        return namespace.isEmpty();
    }

    /** The plain tool name without its namespace. */
    public String name() {
        return name;
    }

    /**
     * The model-visible fully-qualified name:
     * {@code "namespace.name"} when namespaced, {@code "name"} otherwise.
     */
    public String qualifiedName() {
        return isPlain() ? name : namespace + "." + name;
    }

    @Override
    public String toString() {
        return qualifiedName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ToolName)) {
            return false;
        }
        ToolName other = (ToolName) o;
        return namespace.equals(other.namespace) && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return 31 * namespace.hashCode() + name.hashCode();
    }
}
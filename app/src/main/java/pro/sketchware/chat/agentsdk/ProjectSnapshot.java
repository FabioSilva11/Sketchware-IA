package pro.sketchware.chat.agentsdk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured facts about the workspace discovered ONCE per run (Codex
 * environment snapshot parity). Everything not actually verified from the
 * workspace stays {@code UNKNOWN} — the snapshot never invents values
 * (anti-hallucination contract):
 *
 * <pre>
 * ERRADO: namespace = "com.my.project" (chutado)
 * CORRETO: namespace = "UNKNOWN" → o agente pesquisa e preenche via tools
 * </pre>
 *
 * <p>The snapshot is discovered by the {@link ProjectDiscovery} service from
 * THIS run's filesystem and rendered into the prompt through
 * {@link #renderPromptBlock()}, so the model works from verified facts and
 * knows which values still need real discovery.</p>
 */
public final class ProjectSnapshot {

    /** Sentinel for facts not yet verified from the workspace. */
    public static final String UNKNOWN = "UNKNOWN";

    private final String projectType;
    private final String root;
    private final String cwd;
    private final String buildSystem;
    private final String applicationId;
    private final String namespace;
    private final List<String> modules;
    private final List<String> sourceRoots;
    private final List<String> relevantFiles;
    private final List<String> knownPackages;

    private ProjectSnapshot(Builder b) {
        this.projectType = b.projectType;
        this.root = b.root;
        this.cwd = b.cwd;
        this.buildSystem = b.buildSystem;
        this.applicationId = b.applicationId;
        this.namespace = b.namespace;
        this.modules = Collections.unmodifiableList(new ArrayList<>(b.modules));
        this.sourceRoots = Collections.unmodifiableList(new ArrayList<>(b.sourceRoots));
        this.relevantFiles = Collections.unmodifiableList(new ArrayList<>(b.relevantFiles));
        this.knownPackages = Collections.unmodifiableList(new ArrayList<>(b.knownPackages));
    }

    public String projectType() {
        return projectType;
    }

    public String root() {
        return root;
    }

    public String cwd() {
        return cwd;
    }

    public String buildSystem() {
        return buildSystem;
    }

    public String applicationId() {
        return applicationId;
    }

    public String namespace() {
        return namespace;
    }

    public List<String> modules() {
        return modules;
    }

    public List<String> sourceRoots() {
        return sourceRoots;
    }

    public List<String> relevantFiles() {
        return relevantFiles;
    }

    public List<String> knownPackages() {
        return knownPackages;
    }

    /** Compact block for the system prompt; only verified facts are asserted. */
    public String renderPromptBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("<workspace_snapshot>\n");
        sb.append("project_type: ").append(value(projectType)).append('\n');
        sb.append("root: ").append(value(root)).append('\n');
        sb.append("cwd: ").append(value(cwd)).append('\n');
        sb.append("build_system: ").append(value(buildSystem)).append('\n');
        sb.append("application_id: ").append(value(applicationId)).append('\n');
        sb.append("namespace: ").append(value(namespace)).append('\n');
        if (!modules.isEmpty()) {
            sb.append("modules: ").append(join(modules)).append('\n');
        }
        if (!sourceRoots.isEmpty()) {
            sb.append("source_roots: ").append(join(sourceRoots)).append('\n');
        }
        if (!knownPackages.isEmpty()) {
            sb.append("known_packages: ").append(join(knownPackages)).append('\n');
        }
        if (!relevantFiles.isEmpty()) {
            sb.append("relevant_files: ").append(join(relevantFiles)).append('\n');
        }
        sb.append("Any field marked UNKNOWN was not verified yet — discover the real value ");
        sb.append("with the available tools (search/read) before using it. Never guess package ");
        sb.append("names, namespaces, application ids, classes or paths.\n");
        sb.append("</workspace_snapshot>");
        return sb.toString();
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("projectType", value(projectType));
            json.put("root", value(root));
            json.put("cwd", value(cwd));
            json.put("buildSystem", value(buildSystem));
            json.put("applicationId", value(applicationId));
            json.put("namespace", value(namespace));
            json.put("modules", new JSONArray(modules));
            json.put("sourceRoots", new JSONArray(sourceRoots));
            json.put("relevantFiles", new JSONArray(relevantFiles));
            json.put("knownPackages", new JSONArray(knownPackages));
        } catch (Exception ignored) {
        }
        return json;
    }

    private static String value(String v) {
        return v == null || v.trim().isEmpty() ? UNKNOWN : v.trim();
    }

    private static String join(List<String> values) {
        return String.join(", ", values);
    }

    /** Incremental builder: discovery fills only what it actually verified. */
    public static final class Builder {
        // Raw values readable by ProjectDiscovery before build(): lets the
        // discovery service know which facts it already verified.
        String applicationIdValue = "";
        String namespaceValue = "";
        private String projectType = "";
        private String root = "";
        private String cwd = "";
        private String buildSystem = "";
        private String applicationId = "";
        private String namespace = "";
        private final List<String> modules = new ArrayList<>();
        private final List<String> sourceRoots = new ArrayList<>();
        private final List<String> relevantFiles = new ArrayList<>();
        private final List<String> knownPackages = new ArrayList<>();

        public Builder projectType(String v) {
            this.projectType = v == null ? "" : v.trim();
            return this;
        }

        public Builder root(String v) {
            this.root = v == null ? "" : v.trim();
            return this;
        }

        public Builder cwd(String v) {
            this.cwd = v == null ? "" : v.trim();
            return this;
        }

        public Builder buildSystem(String v) {
            this.buildSystem = v == null ? "" : v.trim();
            return this;
        }

        public Builder applicationId(String v) {
            this.applicationId = v == null ? "" : v.trim();
            this.applicationIdValue = this.applicationId;
            return this;
        }

        public Builder namespace(String v) {
            this.namespace = v == null ? "" : v.trim();
            this.namespaceValue = this.namespace;
            return this;
        }

        public Builder modules(List<String> v) {
            if (v != null) {
                modules.addAll(v);
            }
            return this;
        }

        public Builder sourceRoots(List<String> v) {
            if (v != null) {
                sourceRoots.addAll(v);
            }
            return this;
        }

        public Builder relevantFiles(List<String> v) {
            if (v != null) {
                relevantFiles.addAll(v);
            }
            return this;
        }

        public Builder knownPackages(List<String> v) {
            if (v != null) {
                knownPackages.addAll(v);
            }
            return this;
        }

        public ProjectSnapshot build() {
            // Anti-hallucination contract at the boundary: anything the
            // discovery did not verify is normalized to UNKNOWN, so accessors
            // never hand the model an empty string it could fill by guessing.
            if (projectType.isEmpty()) projectType = UNKNOWN;
            if (buildSystem.isEmpty()) buildSystem = UNKNOWN;
            if (applicationId.isEmpty()) applicationId = UNKNOWN;
            if (namespace.isEmpty()) namespace = UNKNOWN;
            if (root.isEmpty()) root = UNKNOWN;
            if (cwd.isEmpty()) cwd = UNKNOWN;
            return new ProjectSnapshot(this);
        }
    }
}

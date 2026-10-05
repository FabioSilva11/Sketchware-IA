package pro.sketchware.chat.skills;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Uma "skill" cadastrada pelo usuário: bloco reutilizável de conhecimento /
 * instruções que o runtime pode consultar e aplicar quando o pedido do
 * usuário corresponder. A descrição diz QUANDO usar; o conteúdo é o que o
 * agente recebe quando a skill é selecionada.
 *
 * <p>Evolução (código-x): o antigo campo {@code trigger} é migrado para
 * {@code description} na leitura, sem perder conteúdo. A identidade é um
 * {@link SkillIdentity} — o nome é apenas rótulo visual.</p>
 */
public class Skill {

    public static final long VERSION_V2 = 2;

    public final String id;
    public String name;
    /** Quando a skill deve ser usada (antigo trigger, agora descrição). */
    public String description;
    /** Resumo curto exibido no catálogo (optional). */
    public String shortDescription;
    /** Conteúdo/instruções completas resolvidas na seleção. */
    public String content;
    public boolean enabled;
    public SkillInvocationPolicy invocationPolicy;
    public SkillScope scope;
    /** Id do projeto quando {@link SkillScope#PROJECT}; vazio quando USER. */
    public String projectId;
    public long version;
    public long createdAt;
    public long updatedAt;
    public final List<SkillResource> resources;

    public Skill(String id, String name, String description, String content, boolean enabled,
                 long createdAt, long updatedAt) {
        this(id, name, description, "", content, enabled,
                SkillInvocationPolicy.AUTOMATIC, SkillScope.USER, "", VERSION_V2,
                createdAt, updatedAt, new ArrayList<SkillResource>());
    }

    public Skill(String id, String name, String description, String content, boolean enabled,
                 long createdAt, long updatedAt, List<SkillResource> resources) {
        this(id, name, description, "", content, enabled,
                SkillInvocationPolicy.AUTOMATIC, SkillScope.USER, "", VERSION_V2,
                createdAt, updatedAt, resources);
    }

    public Skill(String id, String name, String description, String shortDescription,
                 String content, boolean enabled, SkillInvocationPolicy invocationPolicy,
                 SkillScope scope, String projectId, long version,
                 long createdAt, long updatedAt, List<SkillResource> resources) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.shortDescription = shortDescription == null ? "" : shortDescription;
        this.content = content == null ? "" : content;
        this.enabled = enabled;
        this.invocationPolicy = invocationPolicy == null ? SkillInvocationPolicy.AUTOMATIC : invocationPolicy;
        this.scope = scope == null ? SkillScope.USER : scope;
        this.projectId = projectId == null ? "" : projectId;
        this.version = version <= 0 ? VERSION_V2 : version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.resources = resources == null ? new ArrayList<SkillResource>() : new ArrayList<SkillResource>(resources);
    }

    public static Skill create(String name, String description, String content) {
        long now = System.currentTimeMillis();
        return new Skill(UUID.randomUUID().toString(), name, description, content, true, now, now);
    }

    public static Skill createFull(String name, String description, String shortDescription,
                                   String content, SkillInvocationPolicy policy, SkillScope scope,
                                   String projectId, List<SkillResource> resources) {
        long now = System.currentTimeMillis();
        return new Skill(UUID.randomUUID().toString(), name, description, shortDescription,
                content, true, policy, scope, projectId, VERSION_V2, now, now, resources);
    }

    /** Identidade canônica desta Skill. Nunca deriva do nome como chave. */
    public SkillIdentity identity() {
        return new SkillIdentity(id, "user", scope, packageName(name));
    }

    /** Nome lógico (package) derivado do nome visual, ex. "Convenções do projeto" → "convencoes-do-projeto". */
    public static String packageName(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char c : name.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                builder.append(c);
            } else if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '-') {
                builder.append('-');
            }
        }
        String result = builder.toString();
        while (result.endsWith("-")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /** Descrição exibível: descrição, senão o resumo, senão vazio. */
    public String getDescription() {
        String trimmed = description.trim();
        if (!trimmed.isEmpty()) {
            return trimmed;
        }
        return shortDescription.trim();
    }

    public List<SkillResource> resourcesUnmodifiable() {
        return Collections.unmodifiableList(resources);
    }

    public boolean hasVisibleContent() {
        return !content.trim().isEmpty();
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("name", name);
            obj.put("shortDescription", shortDescription);
            obj.put("description", description);
            obj.put("content", content);
            obj.put("enabled", enabled);
            obj.put("invocationPolicy", invocationPolicy.name());
            obj.put("scope", scope.name());
            obj.put("projectId", projectId);
            obj.put("version", version);
            obj.put("createdAt", createdAt);
            obj.put("updatedAt", updatedAt);
            JSONArray resourcesJson = new JSONArray();
            for (SkillResource resource : resources) {
                resourcesJson.put(resource.toJson());
            }
            obj.put("resources", resourcesJson);
        } catch (Exception ignored) {
        }
        return obj;
    }

    public static Skill fromJson(JSONObject obj) {
        if (obj == null) {
            return null;
        }
        String id = obj.optString("id", UUID.randomUUID().toString());
        String name = obj.optString("name", "");
        boolean isLegacy = !obj.has("description");
        // Migração: o antigo "trigger" vira "description" sem perda de conteúdo.
        String description = isLegacy
                ? obj.optString("trigger", "")
                : obj.optString("description", "");
        String shortDescription = obj.optString("shortDescription", "");
        String content = obj.optString("content", "");
        boolean enabled = obj.optBoolean("enabled", true);
        SkillInvocationPolicy policy = SkillInvocationPolicy.AUTOMATIC;
        String rawPolicy = obj.optString("invocationPolicy", "");
        for (SkillInvocationPolicy candidate : SkillInvocationPolicy.values()) {
            if (candidate.name().equalsIgnoreCase(rawPolicy)) {
                policy = candidate;
                break;
            }
        }
        SkillScope scope = SkillScope.USER;
        String rawScope = obj.optString("scope", "");
        for (SkillScope candidate : SkillScope.values()) {
            if (candidate.name().equalsIgnoreCase(rawScope)) {
                scope = candidate;
                break;
            }
        }
        String projectId = obj.optString("projectId", "");
        if (scope == SkillScope.PROJECT && projectId.isEmpty()) {
            scope = SkillScope.USER;
        }
        long version = obj.optLong("version", VERSION_V2);
        long createdAt = obj.optLong("createdAt", System.currentTimeMillis());
        long updatedAt = obj.optLong("updatedAt", createdAt);
        List<SkillResource> resources = new ArrayList<SkillResource>();
        JSONArray resourcesJson = obj.optJSONArray("resources");
        if (resourcesJson != null) {
            for (int i = 0; i < resourcesJson.length(); i++) {
                SkillResource resource = SkillResource.fromJson(resourcesJson.optJSONObject(i));
                if (resource != null) {
                    resources.add(resource);
                }
            }
        }
        return new Skill(id, name, description, shortDescription, content, enabled,
                policy, scope, projectId, version, createdAt, updatedAt, resources);
    }
}
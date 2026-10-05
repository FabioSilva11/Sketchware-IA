package pro.sketchware.chat.skills;

import org.json.JSONObject;

import java.util.Objects;

/**
 * Referência a um recurso externo usado por uma Skill. O conteúdo é carregado
 * sob demanda pelo {@link SkillLoader} — o JSON da Skill guarda apenas a
 * localização, nunca o texto completo (progressive disclosure).
 */
public final class SkillResource {
    private final SkillResourceType type;
    private final String name;
    private final String location;

    public SkillResource(SkillResourceType type, String name, String location) {
        this.type = type == null ? SkillResourceType.REFERENCE : type;
        this.name = name == null ? "" : name;
        this.location = location == null ? "" : location;
    }

    public SkillResourceType type() {
        return type;
    }

    public String name() {
        return name;
    }

    public String location() {
        return location;
    }

    public boolean isEmpty() {
        return location.trim().isEmpty();
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("type", type.name());
            obj.put("name", name);
            obj.put("location", location);
        } catch (Exception ignored) {
        }
        return obj;
    }

    public static SkillResource fromJson(JSONObject obj) {
        if (obj == null) {
            return null;
        }
        SkillResourceType type = SkillResourceType.REFERENCE;
        String rawType = obj.optString("type", "");
        for (SkillResourceType candidate : SkillResourceType.values()) {
            if (candidate.name().equalsIgnoreCase(rawType)) {
                type = candidate;
                break;
            }
        }
        return new SkillResource(type, obj.optString("name", ""), obj.optString("location", ""));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SkillResource)) {
            return false;
        }
        SkillResource that = (SkillResource) o;
        return type == that.type
                && name.equals(that.name)
                && location.equals(that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name, location);
    }

    @Override
    public String toString() {
        return name.isEmpty() ? location : name;
    }
}
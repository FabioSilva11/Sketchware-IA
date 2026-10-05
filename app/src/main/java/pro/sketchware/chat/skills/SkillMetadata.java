package pro.sketchware.chat.skills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Visão enxuta de uma Skill para o catálogo: identidade, nome, descrição e
 * política. O {@link SkillCatalog} expõe apenas metadata — o conteúdo só é
 * carregado pelo {@link SkillLoader} quando a Skill é selecionada.
 */
public final class SkillMetadata {
    final SkillIdentity identity;
    final String name;
    final String description;
    final String shortDescription;
    final boolean enabled;
    final SkillInvocationPolicy invocationPolicy;
    final String projectId;
    final String contentRef;
    final long version;

    public SkillMetadata(SkillIdentity identity, String name, String description,
                         String shortDescription, boolean enabled,
                         SkillInvocationPolicy invocationPolicy, String projectId,
                         String contentRef, long version) {
        this.identity = identity;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.shortDescription = shortDescription == null ? "" : shortDescription;
        this.enabled = enabled;
        this.invocationPolicy = invocationPolicy == null ? SkillInvocationPolicy.AUTOMATIC : invocationPolicy;
        this.projectId = projectId == null ? "" : projectId;
        this.contentRef = contentRef;
        this.version = version;
    }

    public static SkillMetadata from(Skill skill) {
        return new SkillMetadata(skill.identity(), skill.name, skill.getDescription(),
                skill.shortDescription, skill.enabled, skill.invocationPolicy, skill.projectId,
                skill.id, skill.version);
    }

    public SkillIdentity identity() {
        return identity;
    }

    public String id() {
        return identity.id();
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String displayDescription() {
        String trimmed = description.trim();
        if (!trimmed.isEmpty()) {
            return trimmed;
        }
        return shortDescription.trim();
    }

    public boolean enabled() {
        return enabled;
    }

    public SkillInvocationPolicy invocationPolicy() {
        return invocationPolicy;
    }

    public SkillScope scope() {
        return identity.scope();
    }

    public String projectId() {
        return projectId;
    }

    public long version() {
        return version;
    }

    /** Id da Skill na camada de conteúdo (progressive disclosure). */
    public String contentRef() {
        return contentRef;
    }

    public List<String> selectionNames() {
        List<String> names = new ArrayList<>(2);
        if (!name.trim().isEmpty()) {
            names.add(name.trim());
        }
        String pkg = identity.packageName();
        if (!pkg.isEmpty() && !pkg.equalsIgnoreCase(name.trim())) {
            names.add(pkg);
        }
        return Collections.unmodifiableList(names);
    }
}
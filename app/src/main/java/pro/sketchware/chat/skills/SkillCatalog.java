package pro.sketchware.chat.skills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Catálogo em memória das Skills — expõe apenas metadata para a seleção.
 * Carregado sob demanda via {@link #refresh()} e tolerante a falhas: uma
 * Skill malformada ou um repositório indisponível nunca quebram o runtime
 * (a seleção simplesmente vê um catálogo vazio).
 */
public final class SkillCatalog {
    private final SkillStore store;
    private final SkillDebugSink sink;
    private List<SkillMetadata> loaded = Collections.emptyList();

    public SkillCatalog(SkillStore store) {
        this(store, SkillDebugSink.NOOP);
    }

    public SkillCatalog(SkillStore store, SkillDebugSink sink) {
        this.store = store;
        this.sink = sink == null ? SkillDebugSink.NOOP : sink;
        refresh();
    }

    /** Recarrega o catálogo da camada de persistência. */
    public void refresh() {
        try {
            List<Skill> all = store.loadAll();
            List<SkillMetadata> metadata = new ArrayList<SkillMetadata>();
            for (Skill skill : all) {
                if (skill == null || skill.name == null) {
                    continue;
                }
                SkillMetadata value = SkillMetadata.from(skill);
                if (value == null) {
                    continue;
                }
                metadata.add(value);
            }
            loaded = Collections.unmodifiableList(metadata);
        } catch (Exception e) {
            sink.log("catalog.refresh.failed:" + safeMessage(e));
            loaded = Collections.emptyList();
        }
    }

    /** Todas as Skills (metadata). */
    public List<SkillMetadata> listAll() {
        return loaded;
    }

    /** Apenas Skills habilitadas. */
    public List<SkillMetadata> listEnabled() {
        List<SkillMetadata> result = new ArrayList<SkillMetadata>();
        for (SkillMetadata skill : loaded) {
            if (skill.enabled()) {
                result.add(skill);
            }
        }
        return result;
    }

    /** Skills visíveis no projeto (USER + PROJECT do projeto informado). */
    public List<SkillMetadata> listForProject(String projectId) {
        List<SkillMetadata> result = new ArrayList<SkillMetadata>();
        for (SkillMetadata skill : loaded) {
            if (skill.scope() == SkillScope.USER || projectMatches(skill, projectId)) {
                result.add(skill);
            }
        }
        return result;
    }

    /** Skills habilitadas e visíveis no projeto (base da seleção automática). */
    public List<SkillMetadata> listEnabledForProject(String projectId) {
        List<SkillMetadata> result = new ArrayList<SkillMetadata>();
        for (SkillMetadata skill : loaded) {
            if (skill.enabled()
                    && (skill.scope() == SkillScope.USER || projectMatches(skill, projectId))) {
                result.add(skill);
            }
        }
        return result;
    }

    /** Busca por id estável (nunca pelo nome). */
    public SkillMetadata findById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (SkillMetadata skill : loaded) {
            if (id.equals(skill.id())) {
                return skill;
            }
        }
        return null;
    }

    /** Autorresolução do catálogo: guarda os resultados para diagnóstico (item 34). */
    public List<SkillMetadata> findByName(String name) {
        List<SkillMetadata> result = new ArrayList<SkillMetadata>();
        if (name == null) {
            return result;
        }
        String normalized = name.trim();
        if (normalized.isEmpty()) {
            return result;
        }
        for (SkillMetadata skill : loaded) {
            if (skill.name().trim().equalsIgnoreCase(normalized)
                    || skill.identity().packageName().equalsIgnoreCase(normalized)
                    || skill.name().trim().toLowerCase(Locale.ROOT).contains(normalized.toLowerCase(Locale.ROOT))) {
                result.add(skill);
            }
        }
        return result;
    }

    /** Substitui/insere a Skill e recarrega o catálogo. */
    public void merge(Skill skill) {
        if (skill == null) {
            return;
        }
        try {
            store.upsert(skill);
        } catch (Exception e) {
            sink.log("catalog.merge.failed:" + safeMessage(e));
        }
        refresh();
    }

    public void removeById(String id) {
        try {
            store.deleteById(id);
        } catch (Exception e) {
            sink.log("catalog.remove.failed:" + safeMessage(e));
        }
        refresh();
    }

    private static boolean projectMatches(SkillMetadata skill, String projectId) {
        return projectId != null && projectId.equals(skill.projectId());
    }

    private static String safeMessage(Exception e) {
        return e == null || e.getMessage() == null ? "unknown" : e.getMessage();
    }
}
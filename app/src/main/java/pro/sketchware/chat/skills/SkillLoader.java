package pro.sketchware.chat.skills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Carrega o CONTEÚDO das Skills selecionadas sob demanda (progressive
 * disclosure) aplicando um teto configurável por Skill — o limite rígido de
 * 20 skills/4000 chars é substituído por seleção + corte de conteúdo com
 * registro explícito de truncamento no bloco injetado.
 */
public final class SkillLoader {
    /** Teto padrão por Skill (colocável; caracteres). */
    public static final long DEFAULT_MAX_CONTENT_CHARS = 8000;

    private final SkillStore store;
    private final long maxContentChars;
    private final SkillDebugSink sink;

    public SkillLoader(SkillStore store) {
        this(store, DEFAULT_MAX_CONTENT_CHARS, SkillDebugSink.NOOP);
    }

    public SkillLoader(SkillStore store, long maxContentChars, SkillDebugSink sink) {
        this.store = store;
        this.maxContentChars = Math.max(256, maxContentChars);
        this.sink = sink == null ? SkillDebugSink.NOOP : sink;
    }

    /** Skill concreta + flag de truncamento para o renderer. */
    public static final class LoadedSkill {
        final Skill skill;
        final String usedContent;
        final boolean truncated;

        LoadedSkill(Skill skill, String usedContent, boolean truncated) {
            this.skill = skill;
            this.usedContent = usedContent;
            this.truncated = truncated;
        }

        public Skill skill() {
            return skill;
        }

        public String usedContent() {
            return usedContent;
        }

        public boolean truncated() {
            return truncated;
        }
    }

    /** Carrega por id estável. Falhas são no-ops auditáveis. */
    public List<LoadedSkill> load(String projectId, List<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Skill> all;
        try {
            all = store.loadAll();
        } catch (Exception e) {
            sink.log("loader.store.failed:" + safeMessage(e));
            return Collections.emptyList();
        }
        List<LoadedSkill> result = new ArrayList<LoadedSkill>();
        for (String id : skillIds) {
            Skill skill = findById(all, id);
            if (skill == null || !visibleInProject(skill, projectId)) {
                sink.log("loader.missing:" + id);
                continue;
            }
            String trimmed = skill.content == null ? "" : skill.content;
            boolean truncated = false;
            if (trimmed.length() > maxContentChars) {
                trimmed = trimmed.substring(0, (int) maxContentChars) + "\n...(truncated)";
                truncated = true;
            }
            result.add(new LoadedSkill(skill, trimmed, truncated));
        }
        return result;
    }

    private static Skill findById(List<Skill> skills, String id) {
        for (Skill skill : skills) {
            if (skill.id.equals(id)) {
                return skill;
            }
        }
        return null;
    }

    private static boolean visibleInProject(Skill skill, String projectId) {
        return skill.scope == SkillScope.USER
                || (projectId != null && projectId.equals(skill.projectId));
    }

    private static String safeMessage(Exception e) {
        return e == null || e.getMessage() == null ? "unknown" : e.getMessage();
    }
}
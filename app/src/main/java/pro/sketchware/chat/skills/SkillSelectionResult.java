package pro.sketchware.chat.skills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado imutável da seleção de Skills para um pedido do usuário:
 * confirma a lista selecionada, os candidatos descartados e a justificativa
 * de cada decisão (diagnóstico auditável, item 34 do projeto).
 */
public final class SkillSelectionResult {
    private final List<SkillMetadata> selected;
    private final List<SkillMetadata> candidates;
    private final List<SkillMetadata> rejected;
    private final Map<String, String> reasons;
    private final List<String> explicitRefs;

    public SkillSelectionResult(List<SkillMetadata> selected,
                                List<SkillMetadata> candidates,
                                List<SkillMetadata> rejected,
                                Map<String, String> reasons,
                                List<String> explicitRefs) {
        this.selected = Collections.unmodifiableList(new ArrayList<>(selected));
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.rejected = Collections.unmodifiableList(new ArrayList<>(rejected));
        this.reasons = Collections.unmodifiableMap(new LinkedHashMap<>(reasons));
        this.explicitRefs = Collections.unmodifiableList(new ArrayList<>(explicitRefs));
    }

    public static SkillSelectionResult empty() {
        return new SkillSelectionResult(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyMap(), Collections.emptyList());
    }

    public List<SkillMetadata> selected() {
        return selected;
    }

    public List<SkillMetadata> candidates() {
        return candidates;
    }

    public List<SkillMetadata> rejected() {
        return rejected;
    }

    public Map<String, String> reasons() {
        return reasons;
    }

    public List<String> explicitRefs() {
        return explicitRefs;
    }

    public boolean hasSelected() {
        return !selected.isEmpty();
    }

    public List<String> selectedIds() {
        List<String> ids = new ArrayList<>();
        for (SkillMetadata skill : selected) {
            ids.add(skill.id());
        }
        return ids;
    }
}
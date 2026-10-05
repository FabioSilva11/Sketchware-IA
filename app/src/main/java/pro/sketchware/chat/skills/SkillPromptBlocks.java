package pro.sketchware.chat.skills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Blocos de prompt já renderizados para uma execução (valor imutável): o
 * catálogo disponível e as Skills selecionadas, na forma final injetável.
 */
public final class SkillPromptBlocks {
    private final List<SkillMetadata> availableSource;
    private final List<SkillLoader.LoadedSkill> selectedSource;
    private final String availableBlock;
    private final String selectedBlock;

    public SkillPromptBlocks(List<SkillMetadata> availableSource,
                             List<SkillLoader.LoadedSkill> selectedSource) {
        this.availableSource = Collections.unmodifiableList(new ArrayList<>(availableSource));
        this.selectedSource = Collections.unmodifiableList(new ArrayList<>(selectedSource));
        String available = "";
        if (!availableSource.isEmpty()) {
            available = SkillPromptRenderer.renderAvailable(availableSource);
        }
        this.availableBlock = available;
        StringBuilder selected = new StringBuilder();
        for (SkillLoader.LoadedSkill loaded : selectedSource) {
            String block = SkillPromptRenderer.renderSelected(
                    loaded.skill().name, loaded.skill().id, loaded.usedContent(), loaded.truncated());
            if (!block.isEmpty()) {
                if (selected.length() > 0) {
                    selected.append('\n');
                }
                selected.append(block);
            }
        }
        this.selectedBlock = selected.toString();
    }

    public static SkillPromptBlocks empty() {
        return new SkillPromptBlocks(Collections.emptyList(), Collections.emptyList());
    }

    public boolean isEmpty() {
        return availableBlock.isEmpty() && selectedBlock.isEmpty();
    }

    public String availableBlock() {
        return availableBlock;
    }

    public String selectedBlock() {
        return selectedBlock;
    }

    public List<SkillMetadata> availableSource() {
        return availableSource;
    }

    public List<SkillLoader.LoadedSkill> selectedSource() {
        return selectedSource;
    }

    /** Renderização completa (available + selected) no formato final. */
    public String renderAll() {
        StringBuilder builder = new StringBuilder();
        if (!availableBlock.isEmpty()) {
            builder.append(availableBlock);
        }
        if (!selectedBlock.isEmpty()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(selectedBlock);
        }
        return builder.toString();
    }
}
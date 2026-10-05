package pro.sketchware.chat.skills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultado completo da resolução de Skills para uma execução: blocos prontos
 * para o prompt + diagnóstico da seleção + conteúdo carregado.
 */
public final class SkillContext {
    private final SkillPromptBlocks blocks;
    private final SkillSelectionResult result;
    private final List<SkillLoader.LoadedSkill> loaded;

    public SkillContext(SkillPromptBlocks blocks, SkillSelectionResult result,
                        List<SkillLoader.LoadedSkill> loaded) {
        this.blocks = blocks == null ? SkillPromptBlocks.empty() : blocks;
        this.result = result == null ? SkillSelectionResult.empty() : result;
        this.loaded = loaded == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(loaded));
    }

    public static SkillContext empty() {
        return new SkillContext(SkillPromptBlocks.empty(), SkillSelectionResult.empty(),
                Collections.emptyList());
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public SkillPromptBlocks blocks() {
        return blocks;
    }

    public SkillSelectionResult result() {
        return result;
    }

    public List<SkillLoader.LoadedSkill> loaded() {
        return loaded;
    }
}
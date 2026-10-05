package pro.sketchware.chat.skills;

import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline completo de Skills (código-x: SkillCatalog → SkillSelector →
 * SkillLoader), o ÚNICO fluxo oficial usado pelo runtime:
 *
 * <pre>
 * SkillStore ─▶ SkillCatalog (metadata)
 *                     │  select(userText, projectId)
 *                     ▼
 *              DefaultSkillSelector ─▶ SkillSelectionResult
 *                     │  ids selecionadas
 *                     ▼
 *              SkillLoader ─▶ LoadedSkill (conteúdo + truncamento)
 *                     ▼
 *              SkillPromptBlocks ─▶ system prompt
 * </pre>
 *
 * <p>Todo o pipeline roda dentro de um try/catch estrito: uma falha em
 * qualquer etapa vira {@link SkillContext#empty()} + evento de diagnóstico,
 * nunca uma exceção que derrube a conversa.</p>
 */
public final class SkillPipeline implements SkillFlow {

    private final SkillStore store;
    private final SkillCatalog catalog;
    private final SkillSelector selector;
    private final SkillLoader loader;
    private final SkillDebugSink sink;

    public SkillPipeline(SkillStore store) {
        this(store, new DefaultSkillSelector(), new SkillLoader(store), SkillDebugSink.NOOP);
    }

    public SkillPipeline(SkillStore store, SkillSelector selector, SkillLoader loader) {
        this(store, selector, loader, SkillDebugSink.NOOP);
    }

    public SkillPipeline(SkillStore store, SkillSelector selector, SkillLoader loader,
                         SkillDebugSink sink) {
        this.store = store;
        this.sink = sink == null ? SkillDebugSink.NOOP : sink;
        this.catalog = new SkillCatalog(store, this.sink);
        this.selector = selector == null ? new DefaultSkillSelector() : selector;
        this.loader = loader == null ? new SkillLoader(store) : loader;
    }

    public SkillCatalog catalog() {
        return catalog;
    }

    @Override
    public SkillContext resolveFor(String scId, String userText) {
        try {
            String projectId = scId == null ? "" : scId;
            catalog.refresh();
            // O bloco <available_skills> expõe apenas metadata de Skills
            // AUTOMATIC habilitadas; EXPLICIT_ONLY só entra via referência.
            List<SkillMetadata> visible = catalog.listEnabledForProject(projectId);
            List<SkillMetadata> available = new ArrayList<SkillMetadata>();
            for (SkillMetadata skill : visible) {
                if (skill.invocationPolicy() == SkillInvocationPolicy.AUTOMATIC) {
                    available.add(skill);
                }
            }
            SkillSelectionResult result = selector.select(userText, projectId, catalog);
            List<SkillLoader.LoadedSkill> loaded = loader.load(projectId, result.selectedIds());
            SkillPromptBlocks blocks = new SkillPromptBlocks(available, loaded);
            return new SkillContext(blocks, result, loaded);
        } catch (Exception e) {
            sink.log("pipeline.failed:" + (e == null || e.getMessage() == null
                    ? "unknown" : e.getMessage()));
            return SkillContext.empty();
        }
    }
}
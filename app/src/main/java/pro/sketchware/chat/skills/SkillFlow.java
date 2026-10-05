package pro.sketchware.chat.skills;

/**
 * Fluxo único de Skills que o runtime consulta por execução: catálogo →
 * seleção → carregamento → renderização. A implementação produtiva é o
 * {@link SkillPipeline}; o contrato permite ao teste injetar um fluxo
 * determinístico sem tocar em Android/SharedPreferences.
 */
public interface SkillFlow {

    /**
     * Resolve os blocos de Skills para o pedido {@code userText} dentro do
     * projeto {@code scId}. Implementações DEVEM ser tolerantes: nunca
     * lançar — em qualquer falha retorna {@link SkillContext#empty()}.
     */
    SkillContext resolveFor(String scId, String userText);

    /** Fluxo desligado: nenhuma Skill entra no contexto. */
    SkillFlow NOOP = (scId, userText) -> SkillContext.empty();
}
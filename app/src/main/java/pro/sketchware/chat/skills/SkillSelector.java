package pro.sketchware.chat.skills;

/**
 * Estratégia de seleção de Skills para um pedido do usuário. O runtime usa
 * uma única implementação produtiva ({@link DefaultSkillSelector}) configurável;
 * a interface permite testar a decisão sem acoplar ao catálogo.
 */
public interface SkillSelector {

    /**
     * Decide quais Skills entram no contexto para {@code userText} dentro do
     * projeto {@code projectId}. A decisão é determinística e auditável:
     * referências explícitas ($nome) primeiro; depois a correspondência
     * léxica automática sobre Skills AUTOMATIC habilitadas.
     */
    SkillSelectionResult select(String userText, String projectId, SkillCatalog catalog);
}
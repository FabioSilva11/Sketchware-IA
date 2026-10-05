package pro.sketchware.chat.skills;

/**
 * Recurso anexo a uma Skill analisado pelo {@link SkillLoader}. A evolução
 * do sistema troca o "conteúdo inline de até N caracteres" por um caminho
 * de acesso tardio ({@code LOCATION}), permitindo reutilizar conhecimento
 * sem duplicar texto no JSON da Skill.
 */
public enum SkillResourceType {
    /** Apontador para arquivo dentro do workspace do projeto (asset). */
    ASSET,
    /** Documentação de referência já disponível no repositório. */
    REFERENCE,
    /** Bloco de script/comando executado sob demanda. */
    SCRIPT
}
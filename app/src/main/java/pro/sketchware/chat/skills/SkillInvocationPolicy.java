package pro.sketchware.chat.skills;

/**
 * Como uma Skill pode entrar no contexto do agente:
 *
 * <ul>
 *   <li>{@link #AUTOMATIC}: o runtime a oferece no <available_skills> e pode
 *       selecioná-la quando a tarefa do usuário corresponder à descrição.</li>
 *   <li>{@link #EXPLICIT_ONLY}: a Skill NUNCA entra por seleção automática;
 *       só quando o usuário a cita explicitamente no pedido (ex.: $nome).</li>
 * </ul>
 */
public enum SkillInvocationPolicy {
    AUTOMATIC,
    EXPLICIT_ONLY
}
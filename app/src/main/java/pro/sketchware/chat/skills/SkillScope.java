package pro.sketchware.chat.skills;

/**
 * Origem de disponibilidade de uma Skill: o runtime restringe a seleção e o
 * bloco <available_skills> ao escopo resolvido do projeto em execução.
 */
public enum SkillScope {
    /** Disponível para qualquer projeto do usuário. */
    USER,
    /** Vinculada ao projeto atual (projectId preenchido). */
    PROJECT
}
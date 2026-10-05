package pro.sketchware.chat.skills;

/**
 * Destino de diagnósticos da seleção de Skills. Em produção o
 * {@link SkillManager} conecta o {@code ChatFlowLogger.event("skills", …)}
 * (consistente com a trilha de chat existente); testes usam um sink em
 * memória. Nunca deve lançar exceção — a seleção nunca quebra por logging.
 */
public interface SkillDebugSink {
    void log(String message);

    SkillDebugSink NOOP = message -> { };
}
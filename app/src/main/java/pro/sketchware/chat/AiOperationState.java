package pro.sketchware.chat;

/**
 * Estados possíveis de uma operação de IA.
 * Cada estado representa uma etapa específica do processamento e determina
 * quais informações devem ser exibidas ao usuário.
 */
public enum AiOperationState {
    /** Operação ainda não iniciada ou aguardando. */
    IDLE,
    
    /** Preparando a solicitação e contexto. */
    PREPARING,
    
    /** Construindo contexto da conversa e histórico. */
    BUILDING_CONTEXT,
    
    /** Enviando requisição para o provedor de IA. */
    SENDING_REQUEST,
    
    /** Aguardando resposta do provedor. */
    WAITING_PROVIDER,
    
    /** Provedor temporariamente com limite de taxa (rate limited). */
    RATE_LIMITED,
    
    /** Aguardando antes de repetir a tentativa. */
    WAITING_RETRY,
    
    /** Lendo arquivos do projeto. */
    READING_FILES,
    
    /** Executando ferramenta (tool). */
    EXECUTING_TOOL,
    
    /** Escrevendo arquivo. */
    WRITING_FILE,
    
    /** Validando alterações realizadas. */
    VALIDATING_CHANGES,
    
    /** Gerando resposta final. */
    GENERATING_FINAL_RESPONSE,
    
    /** Operação concluída com sucesso. */
    COMPLETED,
    
    /** Operação cancelada pelo usuário ou sistema. */
    CANCELLED,
    
    /** Operação falhou após todas as tentativas. */
    FAILED
}

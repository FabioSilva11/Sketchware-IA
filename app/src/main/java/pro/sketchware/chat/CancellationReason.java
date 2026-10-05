package pro.sketchware.chat;

/**
 * Razões pelas quais uma operação de IA pode ser cancelada.
 * Permite rastreamento detalhado e apresentação de mensagens apropriadas ao usuário.
 */
public enum CancellationReason {
    /** Usuário solicitou cancelamento manualmente. */
    USER_REQUESTED,
    
    /** Nova mensagem foi enviada, interrompendo a anterior. */
    NEW_MESSAGE_SENT,
    
    /** Tela foi fechada. */
    SCREEN_CLOSED,
    
    /** Aplicativo foi colocado em segundo plano. */
    APP_BACKGROUNDED,
    
    /** Timeout - operação excedeu tempo limite. */
    TIMEOUT,
    
    /** Erro interno do sistema. */
    INTERNAL_ERROR,
    
    /** Razão desconhecida. */
    UNKNOWN
}

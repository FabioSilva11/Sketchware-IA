# Plano de Atualização e Correção — Sistema de IA Sketchware-IA

> **STATUS (2026-07-07):** Implementado: Fase 1 completa (1.1 exceto EncryptedSharedPreferences, 1.2–1.5), Fase 2 (2.1 multi tool call, 2.2 anti-loop, 2.3 cancelamento, 2.4 timeouts), Fase 3 (3.1 paginação, 3.2 compactação, 3.3 caching Anthropic + usage no debug), Fase 4 (4.2 update_plan, 4.3 checkpoint por turno, 4.4 Retry-After HTTP-date/schema Gemini/Ollama think).
> **Pendente:** EncryptedSharedPreferences (1.1), 4.1 execução paralela de tools read-only, UI de usage por turno (3.3), Fase 5 (testes automatizados). Validar tudo com build Gradle no Android Studio.

Baseado em `ANALISE_SISTEMA_IA.md`. Organizado em 5 fases; cada item lista arquivos afetados, mudança concreta e critério de aceite. Ordem pensada para: primeiro parar de corromper dados/queimar tokens, depois confiabilidade, depois paridade com Void/Codex.

---

## Fase 1 — Correções críticas (1–2 semanas)

### 1.1 Segurança das API keys
**Arquivos:** `AiProviderService.java`, `AiChatSettingsHelper.java`, `VoidPortSettings.java`, `ProviderDetailActivity.java`
- Gemini: mover key da query string para header `x-goog-api-key`; remover `key=` da URL.
- Nunca logar URL completa: em `emitDebug("LLM request -> ...")`, logar só host+path sanitizado.
- Migrar armazenamento de keys de `SharedPreferences` puro para `EncryptedSharedPreferences` (androidx.security). Migração automática na primeira leitura (ler valor antigo → gravar criptografado → apagar antigo).
- **Aceite:** nenhuma key aparece em logcat/painel debug; keys antigas migradas sem perda.

### 1.2 Resultado de tool estruturado (elimina falsos erros e loops falsos)
**Arquivos:** `ToolManager.java`, `Tool.java`, `VoidPortToolsService.java`, `AgentManager.java`, `VoidToolWrapper.java`
- Criar `ToolResult { boolean ok; String output; String errorMessage; }`.
- Cada tool retorna `ToolResult` explícito; exceções capturadas viram `ok=false`.
- Remover `looksLikeToolError()` (sniffing de "error"/"exception" no texto).
- **Aceite:** `read_file` de um arquivo contendo "exception" retorna estado success; teste unitário cobrindo esse caso.

### 1.3 Tratar `finish_reason` / `stop_reason`
**Arquivos:** `AiProviderService.java` (handleOpenAiChunk, dispatchAnthropicEvent, handleGeminiChunk, parsers de texto)
- Capturar `finish_reason` (OpenAI), `stop_reason` via `message_delta` (Anthropic), `finishReason` + `promptFeedback.blockReason` (Gemini).
- Se `length`/`max_tokens` durante tool call: **não executar** args truncados; emitir erro claro ao modelo ("output truncado, reduza a resposta") e permitir 1 continuation.
- Se Gemini bloquear por safety: erro específico em vez de "response was empty".
- **Aceite:** JSON truncado de tool call nunca chega ao `ToolManager.executeTool`.

### 1.4 Unificar retry (remover duplicação de conteúdo)
**Arquivos:** `AiProviderService.java`, `AgentManager.java`
- Regra: retry no `AiProviderService` **somente se nenhum delta foi emitido** (flag `anyContentEmitted` no state do stream). Se já streamou, propagar erro para o AgentManager decidir.
- Remover o retry paralelo do AgentManager OU mantê-lo como única camada (recomendado: AgentManager decide, AiProviderService só retry de conexão pré-primeiro-byte). Máximo total: 3 tentativas com backoff exponencial + jitter (2.5s, 5s±20%, 10s±20%).
- `dispatchAnthropicEvent`: não relançar `RuntimeException` em parse de chunk — logar e ignorar o chunk (como já faz o parser OpenAI).
- `sendTextMessage`: lançar `IllegalStateException` se chamado na main thread.
- **Aceite:** falha simulada no chunk #50 não duplica os 49 anteriores na UI; máx. 3 requests por turno.

### 1.5 Watchdog de stream
**Arquivos:** `AiProviderService.java`
- Timer de inatividade entre chunks: 90 s sem delta → cancelar call → tratar como falha retryável (se nada emitido) ou erro visível.
- Manter `readTimeout(0)`, o watchdog cobre o caso.
- **Aceite:** servidor que abre SSE e silencia não deixa a UI em "Thinking" para sempre.

---

## Fase 2 — Loop agêntico confiável (1–2 semanas)

### 2.1 Múltiplos tool calls por turno ⭐ (maior impacto)
**Arquivos:** `AiProviderService.java`, `AgentManager.java`, `ChatMessage.java`, `ContextBuilder.java`, `ChatMessageAdapter.java`
- `OpenAiStreamState.toolCalls`: remover `if (index != 0) continue` — acumular todos os índices.
- Anthropic: acumular todos os blocos `tool_use` (lista, não `firstTool`).
- Gemini: acumular todos os `functionCall` das parts.
- `StreamListener.onToolCall` → emitir lista completa no final; `onFinalMessage` ganha parâmetro `List<ToolCall>`.
- AgentManager: executar tools em sequência (fase 2) — read-only em paralelo fica para Fase 4. Cada tool gera sua `ChatMessage` e seu resultado entra no histórico com o `tool_call_id` correto.
- ContextBuilder: mensagem assistant com array `tool_calls` completo + uma mensagem `tool` por resultado (formato OpenAI); equivalente para Anthropic (`tool_use`/`tool_result` múltiplos) e Gemini.
- **Aceite:** prompt "leia os arquivos A, B e C" com modelo que emite 3 calls paralelos executa os 3 e o modelo recebe os 3 resultados.

### 2.2 Anti-loop robusto
**Arquivos:** `AgentManager.java`
- Janela deslizante das últimas 8 assinaturas `name:args`; detectar ciclo de período 1–3 (A A A, A B A B, A B C A B C).
- Contador de falhas consecutivas de tools (qualquer tool): ≥4 falhas seguidas → abortar com mensagem clara.
- Manter `MAX_LOOP_STEPS=40` como teto final.
- **Aceite:** loop A→B→A→B é cortado em ~6 steps, não 40.

### 2.3 Cancelamento completo
**Arquivos:** `AgentManager.java`, `VoidPortToolsService.java`, `AiProviderService.java`
- `cancelCurrentRun`: matar processos de `run_command` ativos e limpar `activeTerminals`/`terminalOutputs`/`terminalReaders` do run atual.
- `currentStreamingCall`: trocar por mapa keyed por runVersion (ou por instância de sessão) para não cancelar o call errado.
- Resetar estado ERROR→IDLE em todos os caminhos de saída do retry.
- **Aceite:** cancelar durante build Gradle mata o processo (verificar via `ps`); dois chats simultâneos cancelam independentemente.

### 2.4 Timeouts de tools realistas
**Arquivos:** `VoidPortToolsService.java`
- `run_command`: timeout configurável por chamada (arg opcional `timeout_seconds`, default 60 s, máx 300 s).
- `run_persistent_command`: retorno parcial em 15 s ok, mas adicionar tool `get_command_output(terminal_id)` para o modelo consultar depois (padrão Codex).
- **Aceite:** build Gradle de 2 min completa via persistent command + polling sem falso timeout.

---

## Fase 3 — Contexto e custo (1 semana)

### 3.1 Limitar payload de tools no contexto
**Arquivos:** `VoidPortToolsService.java`, `ContextBuilder.java`
- `read_file`: página default de ~500 linhas / 20k chars (hoje 500k chars), com `offset/limit` — o modelo pagina.
- Resultados de tool no histórico: truncar a ~8k chars com marcador "…truncado, use offset".
- **Aceite:** ler arquivo de 1 MB não consome >20k chars de contexto por chamada.

### 3.2 Compactação de histórico
**Arquivos:** `ContextBuilder.java`, `AgentManager.java` (novo `ContextCompactor.java`)
- Ao atingir 70% do budget (128k): sumarizar os turnos mais antigos via `sendTextMessage` (prompt de sumarização) e substituí-los por uma mensagem `[Resumo da conversa anterior: ...]`. Preservar: system prompt, últimos N turnos, tool results recentes.
- Fallback se a sumarização falhar: truncamento atual.
- **Aceite:** sessão de 200k tokens acumulados continua funcional com referências ao início da conversa.

### 3.3 Prompt caching + usage
**Arquivos:** `AiProviderService.java`, `ChatActivity.java` (UI)
- Anthropic: `cache_control: {type: "ephemeral"}` no system prompt e no array de tools.
- Capturar `usage` (OpenAI: chunk final / `stream_options.include_usage`; Anthropic: `message_start`/`message_delta`; Gemini: `usageMetadata`) e exibir tokens por turno no painel debug.
- **Aceite:** segundo turno Anthropic mostra `cache_read_input_tokens > 0`; UI mostra uso por turno.

---

## Fase 4 — Paridade Void/Codex (2–3 semanas)

### 4.1 Execução paralela de tools read-only
- Classificar tools (`isReadOnly()` em `Tool`); executar read-only do mesmo turno em thread pool; mutações sempre sequenciais.

### 4.2 Plano interno do agente
- Alimentar o `ChatPlanManager` existente: nova tool `update_plan` (como no Codex) que o modelo chama para manter passos pendentes/concluídos; renderizar no `ChatPlanFragment`.

### 4.3 Checkpoints transacionais multi-arquivo
**Arquivos:** `ChatCheckpointManager.java`
- Checkpoint por **turno** (snapshot de todos os arquivos tocados no turno), não por tool. Rollback restaura o turno inteiro atomicamente.

### 4.4 Melhorias de provedores
- Suporte a `thinking` do Gemini 2.5 e opção de habilitar `think` no Ollama (setting por provedor).
- Revisar `convertJsonSchemaToGemini` (preservar `enum`, `required`, `items` aninhados) com testes.
- Substituir todos os `catch (Exception ignored)` do caminho de tools por log em debug.
- `Retry-After` em formato HTTP-date.

---

## Fase 5 — Qualidade contínua

- Testes unitários dos parsers de stream (fixtures SSE reais de cada provedor, incl. chunks malformados, multi-tool, `finish_reason=length`).
- Teste de integração do loop agêntico com provedor mock (cenários: loop A/B, tool falhando, cancelamento).
- Telemetria local: contagem de retries, loops detectados, duração média por turno — para validar as fases anteriores.

---

## Ordem de execução resumida

| # | Item | Impacto | Esforço |
|---|------|---------|---------|
| 1 | 1.2 ToolResult estruturado | Alto | Baixo |
| 2 | 1.3 finish_reason | Alto | Baixo |
| 3 | 1.4 Retry unificado | Alto | Médio |
| 4 | 1.1 Segurança keys | Alto | Baixo |
| 5 | 1.5 Watchdog | Médio | Baixo |
| 6 | 2.1 Multi tool call ⭐ | Muito alto | Alto |
| 7 | 2.2 Anti-loop | Alto | Baixo |
| 8 | 2.3 Cancelamento | Médio | Médio |
| 9 | 2.4 Timeouts tools | Médio | Baixo |
| 10 | 3.1 Payload de tools | Alto | Baixo |
| 11 | 3.2 Compactação | Alto | Médio |
| 12 | 3.3 Caching + usage | Alto | Médio |
| 13 | Fase 4 completa | Paridade | Alto |

Estimativa total: 5–8 semanas de trabalho focado.

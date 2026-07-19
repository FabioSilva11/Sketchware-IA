# Análise do Sistema de IA — Sketchware-IA

Escopo analisado: `AiProviderService.java` (1575 linhas), `AgentManager.java` (1101), `ContextBuilder.java`, `VoidPortToolsService.java` (1576), `VoidPortLlmMessage`, `VoidPortSettings`, `IaSettingsActivity`/`ProviderDetailActivity`.

---

## 1. Falhas críticas (corrigir primeiro)

### 1.1 Apenas 1 tool call por turno — maior gap vs Void/Codex
- `AiProviderService.appendOpenAiToolCalls()` descarta qualquer tool call com `index != 0` (linha ~768: `if (index != 0) continue;`).
- No Anthropic, só o **primeiro** bloco `tool_use` é acumulado (`AnthropicStreamState.firstTool`).
- `AgentManager` só carrega `toolName/toolArgs/toolId` únicos.
- Consequência: modelos que emitem tool calls paralelos (Claude, GPT-4o, Gemini fazem isso rotineiramente) têm chamadas **silenciosamente perdidas** — o modelo acha que leu 3 arquivos, mas só 1 foi executado. Isso causa alucinação em cascata e loops de re-tentativa.
- Void/Codex executam N tool calls por turno (paralelos quando read-only). Refatorar para `List<ToolCall>` end-to-end é o upgrade nº 1.

### 1.2 Retry pode duplicar conteúdo já streamado
- Em `executeWithRetry`, se a leitura do stream falha no meio (`Stream reading error`), `shouldRetryForFailure` pode agendar retry da **mesma request**. Mas os deltas parciais já foram emitidos via `onContent` → o `contentAccumulator` do AgentManager duplica o texto na UI e no histórico.
- Em `dispatchAnthropicEvent`, qualquer exceção de parse é relançada como `RuntimeException` → cai no catch genérico do `executeWithRetry` → retry → duplicação. Um único chunk malformado da Anthropic reinicia o stream inteiro.
- Correção: retry só se `contentAccumulator` vazio (o AgentManager já faz isso no nível dele, mas o AiProviderService faz retry por conta própria **antes** — os dois níveis de retry se sobrepõem: até 3×3 = 9 tentativas + duplicação).

### 1.3 `finish_reason` totalmente ignorado
- Nenhum dos 3 parsers lê `finish_reason`/`stop_reason`. Se o modelo estoura `max_tokens` no meio de argumentos de tool call, o JSON truncado é passado adiante: `ToolCallAccumulator.getArguments()` faz fallback para o raw inválido → tool executa com args quebrados ou `{}`.
- Codex/Void detectam `finish_reason == "length"` e fazem continuation ou erro explícito. Sem isso você tem edições de arquivo corrompidas silenciosas.

### 1.4 `looksLikeToolError()` — heurística por string causa falsos positivos e loops
- Um `read_file` de qualquer arquivo Java contendo a palavra `exception` ou `"error"` é marcado como **erro de tool**. O modelo recebe estado de erro incorreto, tenta de novo, dispara o anti-loop (assinaturas repetidas), e a sessão morre com "loop detected".
- Correção: tools devem retornar resultado estruturado `{ok: boolean, output: string}` — nunca inferir erro por conteúdo.

### 1.5 API key do Gemini vaza em logs
- A key vai como query param (`?key=...`) e a URL completa é emitida em `emitDebug("LLM request -> ... endpoint=" + url)` → key visível no painel de debug/logcat. Usar header `x-goog-api-key` e nunca logar URL com credencial.
- Adicionalmente: todas as keys ficam em `SharedPreferences` em texto puro. Migrar para `EncryptedSharedPreferences`/Keystore.

---

## 2. Loops e condições de travamento

### 2.1 Detecção anti-loop só pega repetição exata consecutiva
- `handleToolCall`: assinatura `name:args` — o contador **zera** quando a assinatura muda. Loop oscilante `A→B→A→B...` (muito comum: ler arquivo → editar falha → ler de novo) nunca é detectado; só o teto de `MAX_LOOP_STEPS=40` segura, queimando ~40 chamadas de LLM.
- Melhorar: janela deslizante das últimas N assinaturas + detecção de ciclo; e limite de erros consecutivos de tools (Codex corta após ~3 falhas seguidas de qualquer tool).

### 2.2 Stream sem watchdog — travamento infinito
- `readTimeout(0)` = infinito. Se o servidor mantém a conexão aberta sem enviar chunks (comum em proxies OpenAI-compatíveis), a UI fica em "Thinking" para sempre; só o cancelamento manual salva. Adicionar timeout de inatividade entre chunks (ex.: 60 s sem delta → abortar/retry).

### 2.3 Retry duplo e sem jitter
- AiProviderService: 3 retries internos. AgentManager: mais 3 (`MAX_LLM_RETRIES`) chamando `startAgentLoop` de novo. Pior caso: 9+ requests para uma falha permanente (ex.: key inválida retorna 401 — ok, 401 não retry; mas 500 persistente sim). Consolidar retry numa única camada, com jitter exponencial.

### 2.4 `sendTextMessage` bloqueia a thread chamadora
- `sleepBeforeBlockingRetry` usa `Thread.sleep` — se algum caller invocar da main thread, ANR. Não há guard.

### 2.5 Cancelamento incompleto
- `cancelCurrentRun` interrompe a thread da tool, mas processos de `run_command`/`run_persistent_command` vivem em mapas estáticos (`activeTerminals`) que nunca são limpos em cancelamento — vazamento de processos/memória entre sessões.
- `currentStreamingCall` é um único `volatile` — se dois chats rodarem, cancelar um cancela o call errado (ou nenhum).

### 2.6 Corrida de estado no retry de erro
- `onError` chama `setState(State.ERROR)` fora do main handler e depois pode agendar retry — o estado fica ERROR até o próximo `startAgentLoop` setar THINKING; se o retry for cancelado no meio, o estado nunca volta a IDLE por esse caminho.

---

## 3. Contexto e memória (gap grande vs Void/Codex)

- `ContextBuilder` tem budget fixo de 128k tokens e trunca, mas **não há compactação/sumarização** de histórico. Sessões longas perdem o começo abruptamente (Codex faz auto-compact com sumário; Claude Code também).
- `read_file` pagina em 500k chars (`MAX_FILE_CHARS_PAGE`) — meio milhão de chars de um arquivo entra direto no contexto; explode o budget e degrada tudo. Void limita a ~janelas de linhas com paginação de poucos KB.
- Sem contagem real de tokens (estimativa por chars, presumo) e sem tracking de uso/custo por turno — impossível avisar o usuário antes de estourar.
- Sem prompt caching (Anthropic `cache_control`, OpenAI automatic) — cada turno reenvia o system prompt + histórico inteiro, custo 5-10× maior num loop agêntico.

---

## 4. Robustez de provedores

- **Retry-After** só aceita segundos; formato HTTP-date é ignorado (já comentado no código).
- **Anthropic**: `redacted_thinking` injetado como texto `[redacted_thinking]` no reasoning; eventos `message_delta` (onde vem `stop_reason` e usage) não são processados.
- **Gemini**: sem tratamento de `promptFeedback`/`blockReason` (resposta bloqueada por safety vira "response was empty" genérico); `finishReason` ignorado; sem suporte a `thinking` do Gemini 2.5.
- **Ollama**: `think:false` hardcoded — usuário não pode habilitar reasoning local.
- Conversão de JSON Schema → Gemini (`convertJsonSchemaToGemini`) tende a perder `enum`/`anyOf`/`required` aninhados (verificar); erros de conversão são engolidos com `catch (Exception ignored)` — padrão repetido em vários pontos (tools inválidas somem silenciosamente).
- Timeout de tools: `run_command` 30 s / persistent 15 s — builds Gradle reais passam disso; o modelo recebe "timeout" e re-executa, gastando loop steps.

---

## 5. O que falta para nível Void/Codex (roadmap)

1. **Multi tool call por turno + execução paralela de tools read-only** (item 1.1). É a mudança de maior impacto.
2. **Resultado de tool estruturado** (`ok/output/exitCode`) em vez de sniffing de string (1.4).
3. **Tratar `finish_reason`/`stop_reason`** com continuation automática em `length` (1.3).
4. **Compactação de contexto**: quando passar de ~70% do budget, sumarizar turnos antigos com `sendTextMessage` e substituir no histórico.
5. **Prompt caching** (Anthropic `cache_control` no system + tools; reduz custo/latência drasticamente no loop).
6. **Watchdog de stream** (2.2) e retry unificado com jitter (2.3).
7. **Detecção de ciclo A/B** + corte por falhas consecutivas (2.1).
8. **Plan/todo interno do agente** (Codex mantém um plano atualizado por turno — vocês já têm `ChatPlanManager`, mas ele não é alimentado pelo loop).
9. **Checkpoints multi-arquivo transacionais** — hoje `createCheckpointIfNeeded` salva 1 arquivo por tool; um turno que edita 5 arquivos não tem rollback atômico.
10. **Segurança**: keys criptografadas, key fora da URL, nunca logar endpoints com credencial (1.5).
11. **Usage/custo por turno** (ler `usage` dos chunks finais) exibido na UI.

---

## Resumo executivo

A arquitetura já é sólida (port fiel do Void: retries, XML tool fallback, checkpoints, diff preview, anti-loop básico). Os 5 defeitos que mais separam o projeto de um Void/Codex real: **(1)** só 1 tool call por turno com descarte silencioso dos demais, **(2)** duplicação de conteúdo em retry de stream, **(3)** `finish_reason` ignorado → args truncados executados, **(4)** detecção de erro de tool por string → loops falsos, **(5)** ausência de compactação de contexto e prompt caching. Corrigindo 1–4 o agente fica confiável; adicionando 5 + plano interno ele chega ao patamar dos agentes de referência.

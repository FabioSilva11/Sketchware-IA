# Mapeamento validado de dependências do Agent/Chat

**Origem:** Fase 1 da refatoração

**Revisão:** 17 de julho de 2026

**Status:** fases de implementação e validação concluídas

## Escopo

Este mapa descreve o fluxo de Agent/Chat depois da refatoração. Ele substitui o mapa preliminar que tratava `AgentMemory`, `PatternMatcher`, `TaskPlanner`, `ToolSequenceValidator`, `FinishChecker` e `RetryManager` como componentes futuros.

## Componentes principais

### AgentManager

Arquivo: `app/src/main/java/pro/sketchware/activities/chat/AgentManager.java`

Responsabilidades:

- receber a solicitação e controlar o estado do turno;
- inicializar intenção, memória e plano;
- construir o contexto do provedor;
- receber streaming, raciocínio e tool calls;
- validar, aprovar e executar ferramentas em sequência;
- atualizar plano e memória após cada resultado;
- aplicar retry de provedor e alternativas seguras de ferramenta;
- validar a conclusão antes de encerrar;
- manter checkpoints, cancelamento, compactação e detecção de ciclos.

Dependências centrais:

- `ContextBuilder`;
- `ToolManager` e `VoidToolWrapper`;
- `AiProviderService`;
- `ChatMessage` e `ChatCheckpointManager`;
- os seis componentes em `activities/chat/agent`.

Limites principais:

- 40 passos por turno;
- três tentativas de provedor;
- 2,5 segundos entre tentativas;
- três rejeições de finalização;
- quatro falhas consecutivas de ferramenta;
- nove assinaturas recentes para detecção de repetição.

### ContextBuilder

Arquivo: `app/src/main/java/pro/sketchware/activities/chat/ContextBuilder.java`

Responsabilidades:

- montar system prompt e informações do workspace;
- resolver o formato OpenAI, Anthropic, Gemini ou XML;
- converter o histórico para o formato do provedor;
- injetar memória e plano somente no modo Agent;
- preservar o primeiro pedido do usuário durante compactação;
- podar grupos de tool call e resultado sem deixá-los órfãos;
- aplicar budgets de contexto;
- manter cache curto da árvore do projeto.

Entradas relevantes:

- `scId` do projeto;
- histórico de `ChatMessage`;
- `ToolManager`;
- modo `agent`, `gather` ou `normal`;
- identificador/modelo do provedor;
- resumo compactado e orientação produzida pelo Agent.

### ToolManager e wrappers

Arquivos:

- `app/src/main/java/pro/sketchware/ia/tools/ToolManager.java`;
- `app/src/main/java/pro/sketchware/activities/chat/port/VoidToolWrapper.java`;
- `app/src/main/java/pro/sketchware/activities/chat/port/VoidPortAiToolWrapper.java`.

O `ToolManager` registra definições, fornece schemas ao contexto e executa as ferramentas. Os wrappers adaptam os serviços portados do Void ao contrato Android, incluindo leitura, busca, edição, planejamento, terminal e utilitários locais de IA.

### AgentMemory

Mantém o pedido original, objetivo compacto, requisitos, arquivos centrais, seleções, fase e progresso. Sua injeção é limitada para não dominar a janela do modelo.

### PatternMatcher

Classifica solicitações em português e inglês, normalizando acentos e reconhecendo caminhos Windows/Unix. Distingue saudações e perguntas conceituais de ações que exigem inspeção ou mutação.

### TaskPlanner

Converte a classificação em etapas observáveis. Atualiza o progresso com os resultados reais e aceita `edit_file` ou `rewrite_file` como alternativas quando ambas satisfazem a mesma intenção de edição.

### ToolSequenceValidator

Valida pré-condições usando somente resultados bem-sucedidos da execução atual. A relação com o mesmo arquivo ou alvo é obrigatória; uma leitura qualquer não libera outra mutação.

### RetryManager

Produz apenas recuperações automáticas não destrutivas. O contador pertence à assinatura da falha para impedir repetição indefinida.

### FinishChecker

Decide se o turno pode terminar. Considera memória, padrão, plano, ferramentas atuais, arquivos centrais e a natureza textual ou operacional da solicitação.

### ToolExecResult

Normaliza sucesso, conteúdo e erro. É a fonte usada pelo loop para atualizar sequência, retry e plano.

## Fluxo atual

1. `processUserMessage()` inicia uma nova versão da execução.
2. `PatternMatcher` analisa o pedido.
3. `AgentMemory` preserva a intenção original.
4. `TaskPlanner` cria o plano quando a solicitação exige trabalho no workspace.
5. `ContextBuilder` monta prompt, orientação e mensagens no formato do provedor.
6. `AiProviderService` entrega texto e/ou tool calls.
7. `AgentManager` sanitiza e enfileira as chamadas.
8. `ToolSequenceValidator` bloqueia sequências inválidas.
9. O aplicativo solicita aprovação quando a ferramenta exige.
10. `ToolManager` executa a ferramenta e produz `ToolExecResult`.
11. O resultado atualiza memória, plano, histórico, cache e contadores.
12. `RetryManager` pode enfileirar uma leitura ou busca segura.
13. Sem chamadas pendentes, `FinishChecker` valida a resposta final.
14. O turno termina, continua com feedback ou falha explicitamente.

## Relações de segurança

- `edit_file` depende de `read_file` bem-sucedido no mesmo caminho.
- `rewrite_file` depende de leitura do mesmo caminho ou criação prévia.
- remoção depende de inspeção relacionada ao alvo.
- mutação invalida o cache do workspace.
- cancelamento altera `runVersion`, tornando callbacks e retries antigos inativos.
- compactação preserva o pedido original.
- resultados de turnos anteriores não concluem etapas do turno atual.

## Matriz de modos

| Modo | Leitura e busca | Mutação | Terminal | Orientação de Agent |
| --- | --- | --- | --- | --- |
| Agent | Sim | Sim, com aprovação quando aplicável | Sim, com aprovação | Sim |
| Gather | Sim | Não | Não | Não |
| Normal | Não | Não | Não | Não |

## Pontos de extensão

- Novos tipos de intenção devem entrar em `PatternMatcher.RequestType` e receber plano correspondente.
- Nova ferramenta mutável deve declarar corretamente `requiresApproval`, `isDestructive` e `isFileMutation`.
- Dependências entre novas ferramentas devem ser adicionadas ao `ToolSequenceValidator`.
- Um novo formato de provedor precisa de conversão e agrupamento de histórico próprios no `ContextBuilder`.
- Novas recuperações automáticas só devem entrar no `RetryManager` quando forem idempotentes e não destrutivas.

## Testes relacionados

- `ContextBuilderTest`;
- `PatternMatcherTest`;
- `TaskPlannerTest`;
- `ToolSequenceValidatorTest`;
- `RetryManagerTest`;
- `FinishCheckerTest`;
- `ToolExecResultTest`.

O mapa foi conferido contra o código compilado e não contém mais etapas futuras apresentadas como pendentes.

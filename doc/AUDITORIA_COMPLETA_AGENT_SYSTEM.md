# Auditoria validada do sistema de Agent e tool calling

**Data da revisão:** 17 de julho de 2026

**Status:** problemas críticos corrigidos e validados

**Implementação principal:** commit `69ef3422a3a02ac6bc7151f74a1c767e680281c2`

## Objetivo

Este documento preserva o diagnóstico que iniciou a refatoração e registra o estado real depois da implementação. O escopo é somente o Agent/Chat do Sketchware-IA.

A arquitetura final foi comparada com `voideditor/void`, especialmente o loop de chat, a construção de prompts, a conversão de mensagens e o comportamento dos formatos OpenAI, Anthropic, Gemini e XML.

## Resultado executivo

O problema não era uma única função. Havia uma combinação de regras ambíguas no prompt e ausência de controles determinísticos no runtime. Isso permitia que uma solicitação de alteração terminasse em texto, que o pedido original desaparecesse após compactação e que resultados antigos de ferramentas fossem considerados na tarefa atual.

As correções foram implementadas em:

- `ContextBuilder.java`: política por modo e formato, preservação de histórico e cache do workspace;
- `AgentManager.java`: orquestração de memória, plano, retry, validação de sequência e encerramento;
- `agent/AgentMemory.java`: intenção original e progresso;
- `agent/PatternMatcher.java`: classificação de pedidos em português e inglês;
- `agent/TaskPlanner.java`: plano determinístico;
- `agent/ToolSequenceValidator.java`: pré-condições entre ferramentas;
- `agent/FinishChecker.java`: validação de conclusão;
- `agent/RetryManager.java`: alternativas seguras para falhas recuperáveis;
- `ToolExecResult.java`: classificação realista de falhas retornadas pelas ferramentas.

## Achados e resolução

### 1. Prompt contraditório

**Antes:** o prompt dizia que o Agent deveria sempre usar ferramentas para agir, mas também permitia que o próprio modelo decidisse não usá-las. A regra era vaga para solicitações sobre o workspace.

**Agora:** pedidos que dependem de fatos do projeto, leitura, comandos ou mudanças exigem ferramentas. Saudações, perguntas conceituais e esclarecimentos indispensáveis podem ser respondidos diretamente. Gather é somente leitura e Normal não expõe ferramentas.

### 2. Encerramento decidido apenas pelo modelo

**Antes:** uma resposta final sem tool call era aceita mesmo quando o usuário havia pedido uma ação real.

**Agora:** `FinishChecker` verifica etapas críticas, ferramentas obrigatórias, arquivos centrais e o uso de ferramentas na execução atual. O modelo recebe no máximo três ciclos de feedback; depois disso o Agent encerra com erro explícito, nunca com sucesso falso.

### 3. Intenção original enfraquecida pela compactação

**Antes:** a primeira mensagem do usuário podia sair da janela de contexto.

**Agora:** `AgentMemory` mantém o pedido original, requisitos, arquivos, fase e progresso. `ContextBuilder` reinsere explicitamente a primeira mensagem quando o histórico foi compactado.

### 4. Sequência de ferramentas não validada

**Antes:** o prompt recomendava leitura antes da edição, mas o aplicativo não aplicava a regra.

**Agora:** `ToolSequenceValidator` exige:

- leitura bem-sucedida do mesmo arquivo antes de `edit_file`;
- leitura do mesmo arquivo ou criação prévia antes de `rewrite_file`;
- inspeção relacionada ao alvo antes de `delete_file_or_folder`.

### 5. Planner ausente

**Antes:** todo o planejamento dependia do texto livre do modelo.

**Agora:** `PatternMatcher` identifica a intenção e `TaskPlanner` cria etapas para análise, busca, leitura, edição, criação, remoção, execução, correção e refatoração. Mutações sempre incluem verificação.

### 6. Retry inseguro ou genérico

**Antes:** não havia política central para distinguir uma recuperação segura de uma repetição potencialmente destrutiva.

**Agora:** falhas do provedor usam três tentativas com intervalo de 2,5 segundos, seguindo a referência do Void. Ferramentas só recebem alternativas automáticas de leitura ou busca. Timeout de comando, permissão negada, argumentos inválidos e arquivo já existente não disparam mutação automática.

### 7. Tool calls órfãs após poda do histórico

**Antes:** a remoção por tamanho podia separar uma chamada de ferramenta do seu resultado.

**Agora:** `trimProviderMessages()` preserva a primeira mensagem do usuário e a mensagem mais recente, removendo grupos de solicitação e resultado como uma unidade para cada formato de provedor.

### 8. Regra única de quantidade de ferramentas

**Antes:** todos os provedores eram instruídos como se suportassem o mesmo protocolo.

**Agora:** o fallback XML emite exatamente uma ferramenta e aguarda. Formatos nativos podem solicitar várias ferramentas independentes, executadas sequencialmente pelo aplicativo.

### 9. Reconstrução repetida da árvore do projeto

**Antes:** loops curtos podiam reconstruir a árvore sem necessidade.

**Agora:** há cache de cinco segundos por projeto e invalidação imediata depois de uma mutação.

### 10. Classificação frágil de resultado

**Antes:** mensagens reais de falha podiam ser tratadas como sucesso, enquanto conteúdo comum contendo palavras como “error” podia gerar falso negativo.

**Agora:** `ToolExecResult` reconhece os prefixos efetivamente retornados pelas ferramentas e não reprova texto comum apenas por conter termos técnicos.

## Limites intencionais

- O Agent mantém limite de 40 passos e quatro falhas consecutivas de ferramenta.
- A aprovação de ações destrutivas continua sob controle do aplicativo.
- Gather não modifica arquivos nem executa terminal.
- Normal não recebe ferramentas.
- O fallback XML continua estritamente sequencial.
- Respostas conceituais não são forçadas a produzir tool calls artificiais.

## Evidências de validação

- Compilação Java debug concluída com sucesso.
- 18 testes unitários executados, sem falhas ou erros.
- APK debug produzido e verificado.
- `git diff --check` sem erros nos arquivos publicados.
- Testes cobrem prompt, compactação, classificação de intenção, plano, sequência, retry, finalização e resultado de ferramenta.

## Conclusão

Os problemas críticos registrados na auditoria original não permanecem pendentes. O comportamento agora combina instruções claras ao modelo com verificações determinísticas no aplicativo. A descrição consolidada da arquitetura está em `ARQUITETURA_AGENT_CHAT_VOID.md`.

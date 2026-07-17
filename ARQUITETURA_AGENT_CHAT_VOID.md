# Arquitetura do Agent e Chat baseada no Void

## Escopo

Esta refatoração cobre somente o fluxo de Agent/Chat do Sketchware-IA. A referência foi a versão final do repositório `voideditor/void`, commit `b3166e7ef2aefbdfeb139445fdf248a561b85d4d`, com foco em:

- `common/prompt/prompts.ts`;
- `browser/chatThreadService.ts`;
- `browser/convertToLLMMessageService.ts`;
- conversão de ferramentas para OpenAI, Anthropic, Gemini e XML;
- retry de provedor, cancelamento, checkpoints e poda de contexto.

O objetivo não é copiar TypeScript para Java literalmente. O Android tem ciclo de vida, aprovação e execução sequencial próprios. Foram preservadas as invariantes úteis do Void e adicionadas validações determinísticas para os problemas observados no Sketchware-IA.

## Problemas corrigidos

### Contradições do prompt

O prompt anterior dizia simultaneamente que o Agent deveria sempre usar ferramentas e que não deveria usá-las quando julgasse desnecessário. Também limitava todos os provedores a uma ferramenta, embora o runtime aceitasse várias.

Política nova:

- Agent usa ferramentas para fatos do workspace, leitura, comandos e mudanças reais.
- Saudação, pergunta conceitual ou esclarecimento indispensável pode ser respondido sem ferramenta.
- Gather é somente leitura e busca.
- Normal não possui ferramentas.
- OpenAI, Anthropic e Gemini podem solicitar várias ferramentas independentes.
- Fallback XML emite exatamente uma ferramenta no fim da resposta e aguarda o resultado.
- Aprovação é responsabilidade do aplicativo; o modelo não presume que uma ação foi aprovada.
- Arquivo existente deve ser lido antes de `edit_file` ou `rewrite_file`.
- Mudança deve ser verificada antes de o Agent declarar conclusão.

### Encerramento prematuro

`FinishChecker` decide se um turno de Agent pode terminar. Ele considera apenas ferramentas da execução atual; chamadas antigas nunca satisfazem a solicitação nova.

O encerramento é adiado quando:

- há etapa crítica pendente;
- uma ferramenta obrigatória não foi usada;
- um arquivo mencionado não foi acessado;
- a resposta é somente textual para uma solicitação que exige ação.

Para evitar consumo infinito, o Agent devolve feedback ao modelo no máximo três vezes. Depois disso, encerra com erro explícito em vez de afirmar sucesso falso.

### Memória de intenção

`AgentMemory` mantém a solicitação original completa, arquivos centrais, fase e progresso. Essa memória é injetada somente em modo Agent.

Na compactação de histórico:

- a primeira mensagem do usuário é preservada explicitamente;
- o resumo substitui somente o miolo antigo;
- a mensagem mais recente é preservada;
- pares de chamada/resultado de ferramenta são removidos como grupo para não gerar histórico inválido no provedor.

### Planejamento

`PatternMatcher` classifica solicitações em português e inglês. `TaskPlanner` cria etapas determinísticas para leitura, busca, edição, criação, remoção, comando, correção, refatoração e análise.

Mutações possuem etapa de verificação. `edit_file` e `rewrite_file` são alternativas válidas quando a intenção é modificar um arquivo.

### Sequência de ferramentas

`ToolSequenceValidator` aplica estas dependências:

- `edit_file`: `read_file` bem-sucedido no mesmo arquivo;
- `rewrite_file`: leitura do mesmo arquivo ou criação prévia;
- `delete_file_or_folder`: busca ou leitura relacionada ao mesmo alvo.

Uma busca qualquer em outro arquivo não libera uma exclusão.

### Retry

Como no Void, falhas do provedor são repetidas até três tentativas, com intervalo de 2,5 segundos. Cancelamento invalida retries pendentes pelo `runVersion`.

Retries automáticos de ferramentas são limitados a alternativas sem mutação:

- arquivo não encontrado: buscar pelo nome;
- contexto de edição desatualizado: reler o arquivo;
- busca vazia: tentar outra leitura da estrutura.

Não há retry automático para comando com timeout, permissão negada, argumentos inválidos ou arquivo já existente. Em especial, nunca se troca “arquivo já existe” por `rewrite_file` com conteúdo vazio.

### Cache de contexto

A árvore de diretórios tem cache de cinco segundos para reduzir reconstruções durante loops curtos. Qualquer mutação de arquivo invalida imediatamente o cache do projeto.

### Erros de ferramentas

`ToolExecResult` reconhece os prefixos de falha realmente produzidos pelas ferramentas portadas do Void, incluindo diretório inexistente, bloco SEARCH/REPLACE inválido e edição não aplicada. Conteúdo comum que apenas menciona as palavras “error” ou “exception” continua sendo sucesso.

## Fluxo de execução

1. A mensagem do usuário cria `AgentMemory`, `PatternMatcher.Result` e, quando necessário, `TaskPlanner.Plan`.
2. `ContextBuilder` monta o prompt por modo e formato do provedor.
3. O provedor responde com texto e/ou chamadas de ferramenta.
4. Chamadas são sanitizadas, validadas e executadas sequencialmente.
5. Cada resultado atualiza histórico, plano, memória e cache.
6. Falhas seguras podem inserir uma alternativa de leitura/busca na frente da fila.
7. Sem ferramentas pendentes, `FinishChecker` valida a conclusão.
8. Se faltar trabalho, o feedback entra no próximo contexto; se tudo estiver completo, o turno termina.

## Testes

A suíte cobre:

- saudação sem ferramenta;
- intenção de correção em português;
- pergunta conceitual sem inspeção indevida;
- análise de projeto com leitura obrigatória;
- progressão e verificação de planos;
- leitura do mesmo arquivo antes de editar;
- bloqueio de sobrescrita cega;
- ausência de retry destrutivo;
- validação limitada ao turno atual;
- preservação da primeira mensagem;
- remoção conjunta de chamada e resultado de ferramenta;
- regra de quantidade de ferramentas por formato;
- classificação correta de falhas retornadas pelas ferramentas.

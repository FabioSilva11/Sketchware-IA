# Especificação atual do system prompt do Chat

**Revisão:** 17 de julho de 2026

**Fonte de verdade:** `ContextBuilder.java`

**Status:** validado após a refatoração Agent/Chat

## Natureza dinâmica

Não existe um único texto estático enviado a todos os modelos. `ContextBuilder` combina seções conforme:

- modo `agent`, `gather` ou `normal`;
- formato OpenAI, Anthropic, Gemini ou XML fallback;
- provedor/modelo selecionado;
- projeto e arquivos abertos;
- ferramentas registradas;
- terminais persistentes;
- histórico disponível e resumo compactado;
- memória, intenção e plano da execução atual.

Este documento descreve as invariantes do prompt. O Java continua sendo a fonte de verdade para o texto literal.

## Estrutura

O system context é montado nesta ordem:

1. cabeçalho e papel correspondente ao modo;
2. informações do sistema e workspace;
3. definições XML, apenas quando o provedor não possui tool calling nativo;
4. regras importantes por modo e formato;
5. visão geral de arquivos dentro do budget;
6. orientação determinística do Agent, quando existente.

## Regras comuns

- Respeitar o escopo solicitado pelo usuário.
- Diante de bloqueio, explicar a causa concreta e continuar o trabalho seguro possível.
- Não inventar fatos ausentes do pedido, contexto ou resultado de ferramentas.
- Usar Markdown para estruturar a resposta.
- Incluir linguagem e caminho conhecido em blocos de código.
- Usar a data produzida por `PromptConstants.todayDateForPrompt()`.

A antiga regra absoluta “NEVER reject the user's query” foi removida. Ela escondia bloqueios reais e entrava em conflito com segurança e disponibilidade de ferramentas.

## Agent mode

O papel do Agent é executar trabalho real no workspace.

Regras:

- fatos do projeto, inspeções, comandos e alterações exigem ferramentas;
- mudança solicitada deve ser realizada, não apenas sugerida em texto;
- arquivo existente deve ser lido antes de edição ou sobrescrita;
- caminho desconhecido deve ser pesquisado antes de ser presumido;
- toda mutação deve receber a verificação mais estreita relevante;
- aprovação é controlada pelo aplicativo;
- uma ação ainda não aprovada nunca pode ser descrita como executada;
- arquivo fora do workspace não pode ser modificado sem autorização;
- saudações, perguntas conceituais e esclarecimentos indispensáveis podem ser respondidos sem tool call artificial.

A memória e o plano produzidos pelo runtime são anexados apenas neste modo.

## Gather mode

Gather é somente leitura.

- Usa leitura e busca para afirmações sobre o workspace.
- Não usa ferramentas mutáveis.
- Não executa terminal.
- Pode responder diretamente a uma saudação ou questão conceitual alheia ao workspace.
- Sugestões de edição são entregues em blocos de código, sem aplicar a mudança.

## Normal mode

Normal não recebe ferramentas.

- Trabalha apenas com o contexto fornecido.
- Solicita informação ausente quando necessário.
- Pode sugerir referências `@` para arquivos específicos.
- Sugestões de alteração são textuais e condensadas.

## Formatos de provider

### OpenAI, Anthropic e Gemini

Os schemas são enviados no formato nativo. O modelo pode solicitar várias ferramentas independentes na mesma resposta. O aplicativo as executa sequencialmente e retorna todos os resultados antes do próximo ciclo.

### XML fallback

As definições são incluídas no system prompt. O modelo deve:

1. emitir exatamente uma chamada XML;
2. colocá-la no fim da resposta;
3. parar;
4. aguardar o resultado antes de continuar.

Essa limitação não é aplicada aos formatos nativos.

## Categorias de ferramentas

As definições concretas vêm do `ToolManager` e dos wrappers do Void. O conjunto padrão inclui:

### Leitura e busca

- `read_file`;
- `ls_dir`;
- `get_dir_tree`;
- `search_pathnames_only`;
- `search_for_files`;
- `search_in_file`;
- `read_lint_errors`.

Essas operações não exigem aprovação.

### Mutação

- `create_file_or_folder`;
- `delete_file_or_folder`;
- `edit_file`;
- `rewrite_file`.

Elas passam pela política de aprovação e pelas dependências do `ToolSequenceValidator`.

### Planejamento

- `update_plan`.

O plano completo é enviado a cada atualização para manter a interface sincronizada.

### Terminal

- `run_command`;
- `run_persistent_command`;
- `open_persistent_terminal`;
- `kill_persistent_terminal`.

Essas operações exigem aprovação. O prompt proíbe usar terminal como substituto das ferramentas próprias de edição.

### Serviços locais de IA

O registro também pode expor autocomplete, geração/busca de regex, preview de substituição e atualização de modelos locais. Como o registro é dinâmico, a lista efetiva no runtime prevalece sobre este resumo.

## Memória e histórico

Quando ocorre compactação:

- a primeira mensagem real do usuário é reinspecionada e preservada;
- o resumo representa o miolo antigo;
- a cauda recente permanece no formato original;
- a mensagem mais recente é protegida;
- solicitação e resultado de ferramenta são podados em grupo;
- mensagens são convertidas conforme o formato do provider.

Isso impede perda de intenção e históricos inválidos com resultados de ferramenta sem chamada correspondente.

## Budgets e cache

Valores padrão atuais:

- budget total: 6.000 tokens;
- system context: 2.400 tokens;
- histórico: 3.000 tokens;
- erros de compilação: 500 tokens;
- limite Android de contexto: 128.000 tokens;
- cache da árvore do projeto: cinco segundos.

Uma mutação de arquivo invalida o cache imediatamente.

## Encerramento fora do prompt

O prompt orienta o modelo, mas não é a única proteção. `FinishChecker` impede conclusão prematura, `ToolSequenceValidator` aplica pré-condições e `RetryManager` restringe recuperações automáticas. Assim, a correção não depende apenas de o modelo interpretar instruções perfeitamente.

## Casos validados

- saudação sem ferramenta;
- pergunta conceitual sem inspeção indevida;
- análise de projeto com leitura obrigatória;
- pedido amplo de implementação com plano e verificação;
- preservação da primeira mensagem;
- poda conjunta de chamadas e resultados;
- uma chamada por vez no XML;
- múltiplas chamadas permitidas em formatos nativos.

Esta especificação substitui o snapshot anterior, que ainda documentava regras contraditórias removidas do código.

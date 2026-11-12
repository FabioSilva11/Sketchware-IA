# Project Templates Implementation Plan

**Goal:** fornecer exemplos editáveis por blocos no lugar da loja e corrigir o exemplo Unity Ads 602.

**Architecture:** templates SWB locais, restauração com novo ID pelo BackupFactory e interface nativa na posição da loja. Blocos Unity usam o cliente gerado existente com setters seguros para placement.

**Spec:** docs/superpowers/specs/2026-10-28-project-templates-design.md

## Tarefas

- [ ] Blocos: adicionar `unityInterstitialSetPlacement` e `unityRewardedSetPlacement`, setters no cliente gerado e proteção contra callbacks de carregamentos anteriores; testar geração/compilação.
- [ ] Catálogo/importação: criar `ProjectTemplate`, `TemplateCatalog` e `TemplateImporter`; incluir três arquivos `assets/templates/<id>.swb` sem código Java livre.
- [ ] Interface: substituir ProjectsStoreFragment por TemplatesFragment, cards com ação de criar cópia; abrir o projeto criado e atualizar a lista. Remover toda a implementação P2P e dependências/entradas exclusivas.
- [ ] Exemplo 602: guardar original, substituir os dois blocos addSource por blocos próprios e conferir o resultado no dispositivo quando a versão atualizada estiver disponível.
- [ ] Validar recursos, blocos, cópias e build; enviar à main com o fluxo original após conferir as alterações.

## Interfaces entre tarefas

`ProjectTemplate`: campos finais públicos `String id`, `int titleRes`, `int descriptionRes`, `int iconRes`; `assetPath()` retorna `templates/<id>.swb`.

`TemplateCatalog.all()`: `List<ProjectTemplate>` com IDs `unity_ads`, `calculator`, `todo`.

`TemplateImporter.createCopy(Context, ProjectTemplate) throws IOException`: restaura uma cópia independente e retorna `String scId`. A interface chama fora da thread principal e abre DesignActivity na conclusão. Recursos do catálogo: `template_unity_ads_title`, `template_unity_ads_description`, `template_calculator_title`, `template_calculator_description`, `template_todo_title`, `template_todo_description`.

## Divisão de arquivos

Blocos e cliente Unity são independentes da substituição da loja. A implementação da interface não altera `pro/sketchware/unityads` nem `ExtraBlocks`. A criação dos assets e importador não altera MainActivity nem arquivos da loja. Todos os trabalhadores compartilham o checkout, sem commits individuais e sem builds Gradle simultâneos neste PC de 4 GB.

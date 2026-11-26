# Auditoria Técnica, Bugs, Duplicações e Plano de Melhorias — Sketchware-IA (Game Engine 2D)

> **Base da Análise:** Consolidação dos 8 relatórios técnicos produzidos durante a engenharia do protótipo [`Sketchware-GameEngine-HTML5`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-GameEngine-HTML5/analysis/00-master-report.md) (`00-master-report.md` a `07-logic-editor-ux.md` e `00-hypotheses.json`) cruzada com verificação direta nos **21 arquivos Java** de `pro.sketchware.activities.game` (**27.657 linhas**) e sua suíte de testes [`GameProjectAndCatalogTest.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/test/java/pro/sketchware/activities/game/GameProjectAndCatalogTest.java).

---

## Resumo Executivo

O módulo **Game Engine 2D** do `Sketchware-IA` já entrega uma base impressionante: 31 tipos de objetos (21 de mundo/gameplay + 10 de HUD), 131 opCodes de blocos visuais com 100% de cobertura tanto no Preview quanto no APK, física 2D, iluminação com recorte `DST_OUT`, partículas, transições de cena e edição visual estilo Scratch.

Porém, a auditoria linha a linha revelou que o crescimento acelerado do módulo deixou **dívidas técnicas estruturais graves** que hoje geram bugs visíveis ao usuário, travamentos, perda de dados e alto custo de manutenção:

| Indicador Auditado | Situação Atual no `Sketchware-IA` | Impacto Direto |
|---|---|---|
| **Volume total do módulo 2D** | **27.657 linhas** em 21 arquivos Java | Alta complexidade cognitiva e manutenção lenta |
| **Código 100% morto (não referenciado)** | **5.213 linhas** em [`GameBlocksEditorView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/blocks/GameBlocksEditorView.java) (**18,8%** do módulo) | Peso morto no repositório/compilação com recursos úteis presos fora do app |
| **Duplicação completa de runtime** | [`GameRuntimeView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java) (**4.686 linhas**) vs [`GameApkSourceGenerator.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java) (**4.803 linhas** em 7 strings `PART1..PART5`) | **~36% do módulo** somando código morto + fork de runtime; causa **20+ bugs de divergência** Preview × APK |
| **Bugs críticos e médios verificados** | **24 grupos de bugs** (perda silenciosa de JSON, crash `NumberFormatException`, clones/prefabs quebrados no APK, física circular ignorada, spam de partículas, etc.) | Jogos funcionam no Preview e quebram no APK; risco real de perder o projeto inteiro |
| **Cobertura de testes automatizados** | Apenas **3 testes unitários** ([`GameProjectAndCatalogTest.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/test/java/pro/sketchware/activities/game/GameProjectAndCatalogTest.java)) | Regressões no conversor de blocos, física e compilação passam despercebidas |

---

## Parte 1 — Bugs Verificados no Código

Cada item abaixo foi verificado diretamente no código-fonte atual do `Sketchware-IA` e classificado por severidade, explicando **o problema exato** e **por que a correção deve ser priorizada**.

### 1.1 Severidade Crítica — Perda de Dados, Crashes e Corrupção de Estado

#### B01. Perda silenciosa do projeto inteiro em erro de JSON ou I/O
- **Onde:** [`GameProjectRepository.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/model/GameProjectRepository.java#L38-L96), [`GameProjectRepository.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/model/GameProjectRepository.java#L306-L312) e [`GameEngineActivity.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameEngineActivity.java#L231-L237).
- **O que acontece:** `fromJson()` captura `Throwable` e retorna `GameProject.createDefault(safeId)` sem avisar o usuário. Como `loadOrCreate()` vê `parsed != null`, ele devolve um projeto em branco. No próximo `onPause()` da `GameEngineActivity`, `save()` grava esse projeto vazio por cima do `game_project.json` original. Além disso, `writeUtf8File()` grava direto no arquivo final (sem arquivo temporário + rename atômico e sem `.bak`) e engole `IOException` silenciosamente.
- **Por que considerar:** Um único fechamento inesperado do app durante um save (falta de bateria/memória ou crash) ou um caractere corrompido no JSON pode **apagar horas de trabalho do usuário sem nenhum aviso ou chance de recuperação**.

#### B02. Crash por `NumberFormatException` e corrupção quando texto literal começa com `"@"`
- **Onde:** [`GameLogicEditorActivity.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameLogicEditorActivity.java#L912-L913), [`GameLogicEditorActivity.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameLogicEditorActivity.java#L1395-L1396), [`GameLogicEditorActivity.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameLogicEditorActivity.java#L2374-L2375) e [`GameBlockBeanConverter.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/blocks/GameBlockBeanConverter.java#L198-L205), [`GameBlockBeanConverter.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/blocks/GameBlockBeanConverter.java#L337-L349).
- **O que acontece:** O formato `BlockBean` usa o prefixo `"@<id>"` sem escape para referenciar blocos aninhados.
  - Em `GameLogicEditorActivity` (`duplicateSubtree`, `insertNestedBlock` e `restoreFloatingStack`), `Integer.parseInt(val.substring(1))` **não tem `try/catch`**: se o usuário escrever `"@player"` ou `"@usuario"` em um bloco `sayText`/`setHudText` e acionar Undo em pilha flutuante ou duplicar o bloco, **o app fecha imediatamente com crash**.
  - Em `GameBlockBeanConverter.populatePane`, `"@player"` cai no `catch` e o texto some da UI; em `singleFromBean`, se o usuário escrever `"@12"` e existir um bloco com ID `12`, o conversor sequestra aquele bloco para dentro do parâmetro e apaga o texto.
- **Por que considerar:** É um crash reproduzível com texto comum de jogos (menções, emails, créditos `"@dev"`) e corrupção silenciosa de AST.

#### B03. Perda permanente de blocos soltos (`floatingStacks`) ao sair ou rodar Preview
- **Onde:** [`GameScriptNode.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/model/GameScriptNode.java#L23) e [`GameBlockBeanConverter.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/blocks/GameBlockBeanConverter.java#L263-L285).
- **O que acontece:** `floatingStacks` é declarado `transient` (Gson ignora completamente ao salvar/carregar). Além disso, `GameBlockBeanConverter.fromBlockPane()` percorre apenas a cadeia presa ao chapéu raiz (`root.ha`). Qualquer rascunho de blocos deixado solto no canvas desaparece permanentemente ao fechar o Logic Editor ou clicar em Preview.
- **Por que considerar:** Em editores estilo Scratch, desconectar temporariamente um trecho de blocos para testar uma mudança é parte essencial do fluxo de trabalho. Perder esses blocos ao testar o jogo gera frustração imediata.

#### B04. Prefabs instanciados no Editor perdem todos os scripts + exclusão de prefab não limpa scripts nem salva
- **Onde:** [`GameHierarchyDialog.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameHierarchyDialog.java#L488-L510).
- **O que acontece:**
  1. Ao clicar em **Spawn** de um prefab em `GameHierarchyDialog`, `cloneWithUniqueIdentity()` gera um novo `id`, não grava nenhum `prefabId` no `GameObjectBean` e não copia `project.prefabScripts` para `scene.scripts`. Resultado: o prefab colocado na cena durante a edição **perde toda a sua programação**, diferentemente do `spawnPrefab` em runtime.
  2. Ao clicar em **Remove prefab** (`L508`), o código faz `project.prefabs.remove(prefab)` sem remover os scripts correspondentes em `project.prefabScripts` e **sem chamar `callback.onSceneUpdated()`**, logo a exclusão nem sequer é salva em disco se o usuário fechar o app em seguida — e scripts mortos continuam sendo carregados em toda cena.
- **Por que considerar:** Quebra completamente a promessa de reutilização de Prefabs (inimigos, moedas, projéteis) e acumula scripts fantasmas indefinidamente no JSON.

#### B05. Excluir um objeto em qualquer tela deixa scripts órfãos (`"Removed object"`)
- **Onde:** [`GameHierarchyDialog.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameHierarchyDialog.java#L410), [`GameObjectsTabView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameObjectsTabView.java#L326), [`GamePropertyPanelView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GamePropertyPanelView.java#L1965) e [`GameStageEditorView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameStageEditorView.java#L1390).
- **O que acontece:** Nenhum dos 4 pontos de exclusão remove os `GameScriptNode` vinculados ao `obj.id` em `scene.scripts`. Os scripts permanecem no JSON, aparecem em vermelho como `"Removed object"` na aba Event e continuam sendo clonados na carga da cena em runtime.
- **Por que considerar:** Polui o projeto, confunde iniciantes com entradas vermelhas inexplicáveis na aba de eventos e desperdiça memória/tempo de serialização.

#### B06. Corrupções de round-trip em `GameBlockBeanConverter` (`getVar`, cores Material You e multi-aridade)
- **Onde:** [`GameBlockBeanConverter.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/blocks/GameBlockBeanConverter.java#L94-L127) e [`GameBlockBeanConverter.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/blocks/GameBlockBeanConverter.java#L318-L333).
- **O que acontece:**
  1. `getVar` grava o nome da variável em `bean.spec` na ida e, na volta (`singleFromBean`), sobrescreve `item.spec` com `"coins"` em vez de `"%s"`.
  2. `emitBlock` (`L126`) aplica `MaterialColors.harmonizeWithPrimary()` em `bean.color`, e `singleFromBean` (`L323`) persiste essa cor dependente do tema do celular diretamente dentro do `game_project.json`.
  3. `emitBlock` (`L100-103`) força `spec = def.spec` e só lê `countPlaceholders(def.spec)` parâmetros, descartando silenciosamente argumentos extras de blocos legados suportados no runtime (`setPos` 3 args, `setHudBar` 3 args, `spawnParticles` 4 args, `if` 3 args).
- **Por que considerar:** Um JSON de projeto deveria ser determinístico e independente do wallpaper/tema Material You do aparelho do autor, e abrir/salvar um script nunca deveria corromper `spec` ou amputar parâmetros válidos.

---

### 1.2 Severidade Crítica — Quebras de Paridade Preview (`GameRuntimeView`) vs APK (`GameApkSourceGenerator`)

#### B07. Clones e prefabs quebram silenciosamente no APK (`isClone` e `matchKey()`)
- **Onde:** [`GameApkSourceGenerator.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java#L1415-L1416) e [`GameApkSourceGenerator.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java#L2849-L2858).
- **O que acontece:**
  1. `spawnPrefabAt` no APK nunca seta `spawned.isClone = true` (ao contrário de [`GameRuntimeView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L2469)). Consequência: `deleteClone` dentro de um script de prefab **funciona no Preview e não faz nada no APK** (vazando inimigos/balas até travar).
  2. Ao despachar `pendingEvents`, o APK envia `qe.targetObjectId` bruto (`"obj_..."`/`"clone_..."`) em vez de `targetObj.matchKey()`. Consequência: eventos `on_touch` e `on_button` em clones ou prefabs **funcionam no Preview e nunca disparam no APK**.
- **Por que considerar:** É o pior tipo de bug em uma IDE de jogos: o criador testa no Preview, vê tudo funcionando perfeitamente, gera o APK, publica/compartilha e o jogo chega quebrado.

#### B08. `saveGameData` e `setVar` corrompem textos literais em `"0"` via `ExprParser`
- **Onde:** [`GameApkSourceGenerator.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java#L2765), [`GameApkSourceGenerator.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java#L2777) e [`GameRuntimeView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L2373).
- **O que acontece:** No APK, `saveGameData` passa o valor por `evalSlotString -> ExprParser` (enquanto o Preview usa `evalStringOrRaw`), fazendo `saveGameData("biome", "forest")` sem aspas internas salvar `"0"` no APK e `"forest"` no Preview. O mesmo bug ocorre em **ambos** os runtimes no bloco `setVar`: fazer `setVar("estado", "idle")` avalia `idle` como identificador desconhecido e grava `"0"`.
- **Por que considerar:** Impede criadores de usar variáveis de texto/estado (`"menu"`, `"idle"`, `"fase_2"`) ou salvar strings persistentes sem saber que precisariam envolver manualmente o texto em aspas.

#### B09. `"rot"` ausente no APK e variáveis do usuário sombreadas por built-ins (`getVar`)
- **Onde:** [`GameApkSourceGenerator.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java#L3241), [`GameBlockCatalog.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/blocks/GameBlockCatalog.java#L582-L583) e [`GameRuntimeView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L2533-L2536), [`GameRuntimeView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L2879-L2972).
- **O que acontece:**
  1. `GameBlockCatalog.toExpression("getDirection")` emite `"rot"`, mas `GameApkSourceGenerator.resolveBuiltInOrVar` aceita apenas `"selfRot"`, `"rotation"` e `"direction"`, retornando `0.0` no APK.
  2. Em **ambos** os runtimes, `case "getVar"` delega para `resolveBuiltInOrVar()`, que verifica os ~35 nomes built-in **antes** de `variables.containsKey(name)`. Se um criador criar uma variável global chamada `timer` (ex.: contagem regressiva de 60s), `size`, `volume`, `x`, `y` ou `fps`, `setVar("timer", 60)` grava no mapa `variables`, mas `getVar("timer")` ignora o mapa e devolve `gameTimerSeconds`!
- **Por que considerar:** `timer` e `size` são nomes extremamente comuns escolhidos por iniciantes ao criar variáveis. O comportamento silencioso é quase impossível de depurar sem olhar o código-fonte do motor.

#### B10. `activeOnlyNearCamera` anula `destroyOffscreen` e `moving_platform` parada se move no APK
- **Onde:** [`GameApkSourceGenerator.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java#L1507-L1591).
- **O que acontece:** No APK, `stepObjectBehaviors` dá `continue` cedo quando `activeOnlyNearCamera == true` e o objeto sai da margem de `360px` — e a checagem de `destroyOffscreen` está *depois* desse `continue`. Projéteis que saem rápido da tela nunca mais são destruídos. Na mesma função (`L1510-1540`), `moving_platform` faz `Math.max(1f, b.speed)` (movendo plataformas com `speed = 0`) e ignora `obj.glideActive` (brigando com blocos `glideToXY`).
- **Por que considerar:** Causa vazamento contínuo de entidades em jogos de tiro/ação no APK e quebra puzzles com plataformas inicialmente paradas.

#### B11. Quatro divergências visuais/entrada críticas no Preview vs APK
- **Onde:** [`GameRuntimeView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L559), [`L2081-L2093`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L2081-L2093), [`L2611-L2621`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L2611-L2621), [`L3790-L3792`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L3790-L3792).
- **O que acontece:**
  1. `ACTION_CANCEL` no Preview chama `pressedButtons.clear()`, apagando visualmente todos os `hud_toggle` ativos (no APK chama `rebuildPressedButtons()`).
  2. `drawSpeechBubble` roda *antes* de `drawLighting2DPass` no Preview (escurecendo diálogos em cenas noturnas) e não suporta cauda de balão nem `thinkForSecs`.
  3. `setZIndex("front")` no Preview ignora objetos HUD (`!o.isHud()`), impedindo elevar um widget HUD acima de outro (`hud_dialog` tem `zIndex = 200`).
  4. `touchingObject("edge")` e colisor circular dentro de `touchingObject` existem no APK mas foram esquecidos no Preview (embora o editor ofereça `"edge"` na lista!).
- **Por que considerar:** Destrói a previsibilidade WYSIWYG entre editar, testar e exportar.

#### B12. Outras divergências de opCodes e schema entre Preview e APK
- **Onde:** [`GameApkSourceGenerator.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java#L579-L580), [`L627-L628`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java#L627-L628), [`L2115-L2193`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java#L2115-L2193), [`L2258-L2277`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java#L2258-L2277), [`L2427-L2451`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java#L2427-L2451), [`L2517-L2526`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java#L2517-L2526), [`L2580-L2582`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java#L2580-L2582).
- **Resumo das quebras:**
  - `setBackdrop` troca de cena no APK se houver cena com o mesmo nome da imagem (no Preview troca só o fundo).
  - `goToTarget("joystick")` e `pointToward("joystick"|"random")` faltam no APK.
  - `clearEffects` não reseta `scaleX/scaleY` no APK.
  - `repeatUntil` com corpo vazio trava o script indefinidamente no APK (sem cap de 10.000 iterações).
  - `gameTimerSeconds` nunca reseta em `restartScene` no Preview e continua contando durante transição `"out"`, enquanto pausa e reseta no APK.
  - `parallaxX/Y` tem fallback `0.5f` no APK vs `1.0f` em `GameObjectBean`; `worldWidth/Height` é `int` no modelo e `float` no APK; `playBgm` ignora `paramBlock(1)` no Preview (`hasParam` em vez de `hasSlot`).
- **Por que considerar:** Cada divergência isolada parece pequena, mas juntas tornam o APK um "terceiro motor" imprevisível. Todas desaparecem de uma vez se o runtime for unificado (ver Parte 3).

---

### 1.3 Severidade Alta — Física, Runtime e Interpretador de Blocos

#### B13. Colisor circular (`physics_circle` / `circleCollider`) é ignorado na física sólida
- **Onde:** [`GameRuntimeView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L1055-L1115) e [`L1319-L1331`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L1319-L1331).
- **O que acontece:** A matemática circular só foi implementada em `aabbTouchingOrOverlapping` (para disparar eventos de colisão). Já a resolução sólida (`overlapsXPass`/`overlapsYPass`) trata **todo corpo como retângulo AABB fixo**, ignorando `circleCollider`, `scaleX/scaleY` e `rotation`. Bolas travam em quinas invisíveis de plataformas e objetos escalados têm hitboxes completamente desalinhadas do visual.
- **Por que considerar:** O editor oferece explicitamente o objeto `Circle Body` (`physics_circle`) e o bloco `setCircleCollider`, mas eles não se comportam fisicamente como círculos.

#### B14. Spam infinito de partículas em Checkpoints (saturação do pool de 320 partículas)
- **Onde:** [`GameRuntimeView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L1209-L1216).
- **O que acontece:** `actor.checkpointX` salva a posição centralizada `other.x + (other.w - actor.w) * 0.5f`, mas a guarda de disparo testa `Math.abs(actor.checkpointX - other.bean.x) > 4f` (comparando centro com borda esquerda!). Para um Sprite padrão (`96px`) em cima de um Checkpoint padrão (`64px`), a diferença é sempre `16px > 4px` — disparando **10 partículas por tick (600 partículas/segundo)** enquanto o jogador estiver parado no checkpoint, expulsando todas as outras partículas do jogo do limite global de 320.
- **Por que considerar:** Acontece com os tamanhos padrão da própria paleta; basta o jogador encostar em um checkpoint para inundar a tela e derrubar a performance.

#### B15. Voltar à cena inicial apaga todas as `globalVariables` em runtime
- **Onde:** [`GameRuntimeView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L425-L430) e [`GameApkSourceGenerator.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java#L1084-L1089).
- **O que acontece:** A condição verifica `pendingSceneLoadId == null && transitionTargetSceneId.isEmpty()` para decidir se deve resetar `variables`, mas ambos os campos são limpos **uma linha antes** de chamar `loadSceneInternal()` (`L891` e `L902`). Portanto, transitar de um subnível ou loja de volta para a cena inicial (ex.: mapa/hub) **zera pontuação, moedas e inventário**; por outro lado, reiniciar a Cena 2 não zera nada.
- **Por que considerar:** Quebra qualquer jogo com hub central, loja em cena separada ou retorno ao menu mantendo progresso em memória.

#### B16. Interpolação `{x}`, `{y}`, `{vx}`, `{vy}`, `{onGround}` sempre avalia para `0`
- **Onde:** [`GameRuntimeView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L2804-L2807) e [`L2859`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L2859).
- **O que acontece:** `interpolateTemplate(raw)` chama `resolveBuiltInOrVarString(key, null)` passando `fiber = null`. Qualquer propriedade dependente de `self` dentro de chaves (ex.: `"X: {x}, Y: {y}"`) cai no fallback `0`.
- **Por que considerar:** Correção trivial (passar `fiber` como parâmetro) que desbloqueia formatação dinâmica de HUD e balões de fala.

#### B17. Incompatibilidades entre `GameBlockCatalog.toExpression()` e `ExprParser`
- **Onde:** [`GameBlockCatalog.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/blocks/GameBlockCatalog.java#L654-L667) e [`GameRuntimeView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L3190-L3391).
- **O que acontece:**
  1. `opEq` usa `slotExpr` em vez de `slotTextOrExpr`, gerando `(sceneName == Level 2)` sem aspas, o que falha no parser.
  2. `opMathFn` emite `"ceiling(x)"`, `"log(x)"`, `"e ^(x)"` e `"10 ^(x)"`, enquanto `ExprParser` só reconhece `ceil`, `log10`, `exp` e quebra com o espaço em `"e ^("` / `"10 ^("`.
  3. `parseFunctionCall` (`isSingleBareIdentifierArg`, `L3199-3210`) verifica apenas `variables.containsKey(rawIdent)` — logo `round(selfX)` trata `selfX` como string literal `"selfX"` e retorna `0.0`.
- **Por que considerar:** Quebra blocos matemáticos e comparações de texto padrão do próprio catálogo sempre que a expressão sincronizada em string é avaliada.

#### B18. `sendMessageWait` nunca espera e problemas cinemáticos na física
- **Onde:** [`GameRuntimeView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L317-L378), [`L843-L868`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L843-L868), [`L1046-L1148`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L1046-L1148), [`L2360-L2365`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L2360-L2365).
- **O que acontece:**
  - `sendMessageWait` retorna `false` imediatamente, executando idêntico a `sendMessage` (em ambos Preview e APK).
  - **Tunneling:** A `MAX_FALL_SPEED = 1600 px/s`, o objeto anda `26,67 px` por tick, atravessando paredes finas e a tolerância de `6 px` de plataformas `oneway_platform`.
  - **Lag de `onGround` (1 frame):** `stepFibers()` roda *antes* de `stepPhysics()`, então `isOnGround` / `ifOnGround` lê o estado do frame anterior.
  - **`glideToXY` × gravidade:** Durante um glide em objeto com física ativa, a gravidade continua acumulando `velocityY`, entortando o percurso e derrubando o sprite ao final.
  - **Plataformas móveis:** Não empurram jogadores lateralmente (causam teleporte/snap súbito ao invadir um jogador parado).
  - **Colisão `static-vs-static`:** O laço `O(W^2)` testa pares de objetos estáticos entre si, disparando `on_collision` no frame 1 entre blocos de cenário encostados.
  - **Judder em 120 FPS:** `FIXED_DT` fixo em `1/60s` sem interpolação de render (`alpha`) alterna 1 e 0 steps por frame em telas/projetos de 120 Hz.
- **Por que considerar:** São exatamente as falhas que fazem jogos 2D parecerem "duros", "bugados" ou "atravessando o chão".

---

### 1.4 Severidade Média — Editor Visual (Stage, Inspector, Logic Editor e Assets)

#### B19. Botão "Duplicate" de blocos está morto (`View.GONE`) no `GameLogicEditorActivity`
- **Onde:** [`GameLogicEditorActivity.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameLogicEditorActivity.java#L670), [`L692`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameLogicEditorActivity.java#L692) e [`L725`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameLogicEditorActivity.java#L725).
- **O que acontece:** `startBlockDrag()` chama `topMenu.toggleLayoutVisibility(false)`, que oculta `binding.layoutCopy` (`View.GONE`). O método `duplicateSubtree()` (`L828`) está implementado e pronto, mas `topMenu.isInsideCopyArea()` sempre retorna `false` porque o container está `GONE`.
- **Por que considerar:** Uma linha impede os usuários de duplicar blocos no editor lógico, obrigando-os a reconstruir árvores de blocos manualmente.

#### B20. Busca de blocos só pesquisa os 10 nomes de categoria e hitboxes de snap não escalam por DPI
- **Onde:** [`GameLogicEditorActivity.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameLogicEditorActivity.java), `PaletteSelector.showSearchDialog()`, `BlockPane.java:663-687`, [`GameBlockCatalog.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/blocks/GameBlockCatalog.java#L232), [`L539`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/blocks/GameBlockCatalog.java#L539).
- **O que acontece:**
  - Pesquisar `"jump"`, `"particles"` ou `"clamp"` retorna *"Not found"* porque a busca só olha os 10 títulos das categorias.
  - O snap de encaixe usa `40px`/`60px` brutos sem multiplicar por densidade — em celulares modernos `xxhdpi`/`xxxhdpi`, `40px` equivale a apenas **`10–13dp`**, dificultando muito encaixar blocos aninhados com o dedo.
  - `forever` tem `type = "c"` em vez de cap terminal, permitindo encaixar código inalcançável abaixo do loop infinito.
  - `isReporterBlock()` omite `"s"` (blocos de texto), impedindo aninhamento caso `"s"` seja usado.
- **Por que considerar:** São os maiores pontos de atrito diário para quem programa blocos em tela touchscreen.

#### B21. Deriva geométrica, inversão de camadas HUD/World e bugs visuais no Stage Editor
- **Onde:** [`GameStageEditorView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameStageEditorView.java#L435), [`L582-L620`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameStageEditorView.java#L582-L620), [`L636`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameStageEditorView.java#L636), [`L778-L783`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameStageEditorView.java#L778-L783), [`L1328-L1351`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameStageEditorView.java#L1328-L1351), [`L1607-L1615`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameStageEditorView.java#L1607-L1615).
- **O que acontece:**
  1. Ao redimensionar um objeto com `rotation != 0`, `x` e `y` nunca são compensados (`origObjX`/`origObjY` são salvos e ignorados), fazendo o canto superior esquerdo derivar pelo canvas.
  2. Com `scaleY < 0` (flip vertical), o botão de rotação entra para dentro do corpo do sprite.
  3. `computeTopZIndex() + 1` mistura World e HUD: criar um botão HUD (`z=100`) e depois um Sprite coloca o Sprite em `z=101`, desenhando-o *acima* do HUD no editor (enquanto no runtime HUD fica sempre acima).
  4. Adicionar um elemento HUD enquanto a câmera do editor está arrastada para `x = 2400` coloca o HUD fora de `[0..virtualW]`, invisível em jogo.
  5. A área de lixeira (`TrashZone`) pisca em qualquer toque rápido (`L636` não checa `dragMovedBeyondSlop`).
  6. O vetor de trajeto de `moving_platform` é desenhado dentro da matriz de rotação/escala (divergindo do runtime em espaço de mundo) e desaparece se o objeto tiver sprite.
- **Por que considerar:** Faz o editor mentir para o designer sobre onde os objetos realmente estão e como o jogo vai aparecer no celular.

#### B22. Bugs e inconsistências de UI no Inspector, Hierarquia e Aba Objetos
- **Onde:** [`GamePropertyPanelView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GamePropertyPanelView.java#L515-L536), [`L1265`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GamePropertyPanelView.java#L1265), [`L1316`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GamePropertyPanelView.java#L1316), [`L1411-L1438`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GamePropertyPanelView.java#L1411-L1438), [`GameObjectsTabView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameObjectsTabView.java#L188), [`GameHierarchyDialog.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameHierarchyDialog.java#L410), [`activity_game_engine.xml`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/res/layout/activity_game_engine.xml#L194-L199).
- **O que acontece:**
  - O `GamePropertyPanelView` (`170dp`) sobrepõe completamente `#bottom_bar` (escondendo seletor de Cena, ferramentas de Canvas e botão `Run`).
  - Clicar em `Static` ou `Sensor` (`L518, L526`) força `selected.physicsEnabled = true` **mesmo quando o usuário está desligando a opção**.
  - Rótulo com typo `"q="` em vez de `"b="` para bounce (`L534`).
  - `hud_dialog` exibe o rótulo `"ActionKey (e.g., jump, fire)"` para o campo que no runtime é o **Nome do Falante (Speaker)**.
  - `projectile.damage` existe no modelo mas não é editável; `death_zone.damage` é editável mas ignorado no runtime.
  - `showGoalTargetDialog` grava `scene.id`, mas `GameProject.renameScene()` compara `targetScene` contra `oldName` (mistura de chave por ID vs Nome).
  - Criar uma cena com o preset `"Vertical Shooter"` muda `project.landscape = false` e `720×1280` **para o projeto inteiro** sem avisar que vai quebrar as outras cenas horizontais.
  - `GameObjectsTabView` (`L188`) concatena duas tabelas de nomes, exibindo `"Sprite (Sprite)"`, `"Platform (Platform)"`, `"Camera (Camera)"`.
  - `showMotionConfigDialog(true)` instancia `etSpeed` sem anexá-lo à tela e depois ainda sobrescreve `selected.speed` com o texto dele (`L1438`).
  - `GameHierarchyDialog` exclui objetos **sem diálogo de confirmação** e sem empurrar estado para o Undo do Stage.
- **Por que considerar:** São dezenas de "armadilhas" pequenas que minam a confiança do usuário na ferramenta e são rápidas de corrigir.

#### B23. Assimetria de Assets (Preview × APK), `Lottie` fantasma e risco de OOM no APK
- **Onde:** [`GameProjectRepository.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/model/GameProjectRepository.java#L146-L207), [`GameEngineActivity.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameEngineActivity.java#L1451-L1455), [`GameApkSourceGenerator.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java#L4323), [`GameDrawerView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameDrawerView.java#L127).
- **O que acontece:**
  - `GameProjectRepository` acha arquivos soltos na pasta do workspace no Preview, mas `BuildGameApkTask` só empacota itens registrados em `ProjectResourceManager` (arquivos somem no APK). Inversamente, o APK faz fallback em `files/assets/`, mas o Preview nunca lê `files/assets/`.
  - `GameApkSourceGenerator` (`L4323`) decodifica imagens de `assets/` com `BitmapFactory.decodeStream(is)` **sem `inSampleSize`** (risco de `OutOfMemoryError` com texturas grandes).
  - `GameDrawerView` lista **Lottie**, **Strings & Colors** e **Assets**, mas o motor 2D não tem suporte a Lottie em nenhum objeto.
- **Por que considerar:** Evita que imagens funcionem no Preview e sumam no APK (ou vice-versa), elimina crashes por falta de memória em aparelhos básicos e evita oferecer menus sem efeito prático.

#### B24. Riscos de concorrência/I/O síncrono e Undo dessincronizado
- **Onde:** [`GameEngineActivity.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameEngineActivity.java#L970), [`L1038-L1044`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameEngineActivity.java#L1038-L1044), [`GameStageEditorView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameStageEditorView.java#L321-L380).
- **O que acontece:** Todo arrasto ou clique de propriedade serializa o projeto inteiro em JSON *pretty-printed* **na UI thread** (`markGameModified()`). Além disso, o Undo do Stage salva apenas `scene.objects` (ignorando `scene.scripts`, propriedades da cena e edições feitas na Hierarquia/Aba Objetos). Ao renomear cena, o Inspector chama `pushUndo()`, criando uma entrada fantasma que não desfaz o nome.
- **Por que considerar:** Projetos grandes começam a engasgar a cada toque na tela, e o Undo quebra a expectativa básica de "voltar exatamente ao estado anterior".

---

## Parte 2 — Itens Duplicados e Código Morto

Abaixo está o inventário completo de duplicações encontradas na análise e verificadas no código do `Sketchware-IA`.

| # | Item Duplicado / Código Morto | Locais Exatos no Código | Por que Deve Ser Considerado |
|---|---|---|---|
| **D01** | **Editor de blocos duplicado e 100% morto (5.213 linhas)** | [`GameBlocksEditorView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/blocks/GameBlocksEditorView.java) (0 referências externas em todo `app/src`) vs [`GameLogicEditorActivity.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameLogicEditorActivity.java) (2.686 linhas) | Representa **18,8% de todo o módulo 2D** em código não utilizado! Aumenta o APK do Sketchware-IA, o tempo de build e confunde contribuidores. Antes de apagar, vale portar seus 4 recursos superiores: busca por bloco, clipboard `SWGAME_BLOCKS:`, seletor rápido de Target/Event e base de exportação de código. |
| **D02** | **Motor de runtime duplicado em strings Java (9.489 linhas somadas)** | [`GameRuntimeView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java) (4.686 linhas) vs [`GameApkSourceGenerator.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java) (4.803 linhas em `PART1..PART5`) | É a **causa raiz de todos os bugs B07–B12**. Manter ~4.500 linhas de Java dentro de literais `"""..."""` desativa autocompletar, verificação de tipos e refatoração da IDE, e garante que toda correção futura esquecida em um dos lados crie uma nova divergência Preview × APK. |
| **D03** | **4 cópias do montador `BlockBean -> BlockView` + linking `@<id>`** | [`GameBlockBeanConverter.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/blocks/GameBlockBeanConverter.java#L134-L261) e [`GameLogicEditorActivity.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameLogicEditorActivity.java#L828-L948) (`duplicateSubtree`), [`L1330-L1422`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameLogicEditorActivity.java#L1330-L1422) (`insertNestedBlock`), [`L2344-L2410`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameLogicEditorActivity.java#L2344-L2410) (`restoreFloatingStack`) | ~300 linhas repetidas com lógica quase idêntica de instanciar `BlockView`, ligar `subStack1/2`, `nextBlock`, `@childId` e rodar 2 passadas de `bv.o()`. Foi exatamente essa duplicação que deixou o crash **B02** (`NumberFormatException`) em 3 dos 4 lugares enquanto 1 tinha `try/catch`. |
| **D04** | **6 tabelas paralelas de avaliação de reporters e built-ins** | `evaluateBlockValue`, `resolveBuiltInOrVar` e `ExprParser.parseFunctionCall` em [`GameRuntimeView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/runtime/GameRuntimeView.java#L2527-L3392) + cópia tripla em [`GameApkSourceGenerator.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java#L2920-L3540) | Qualquer novo sensor ou função matemática precisa ser adicionado em 6 `switch` diferentes. Isso causou os bugs `"rot"`, `"log"`, `"ceiling"` e `joyAngle`. |
| **D05** | **4 fluxos independentes de CRUD/Clonagem/Deleção de objetos** | [`GameStageEditorView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameStageEditorView.java#L406-L485), [`GamePropertyPanelView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GamePropertyPanelView.java#L1871-L1970), [`GameHierarchyDialog.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameHierarchyDialog.java#L399-L415) (`L847`), [`GameObjectsTabView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameObjectsTabView.java#L221-L238) (`L326`) | Cada lugar gera IDs/nomes com padrão diferente (`_1` vs `_copy` vs sem checagem), uns empurram Undo e outros não, uns pedem confirmação e outros deletam direto, e **nenhum** limpa scripts órfãos. |
| **D06** | **2 superfícies concorrentes para listar objetos da cena** | [`GameHierarchyDialog.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameHierarchyDialog.java#L290-L525) (modal Z-ordenado com Eye/Lock/Prefabs) vs [`GameObjectsTabView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameObjectsTabView.java) (aba fixa em ordem de inserção com chips de evento) | Fragmenta o modelo mental do usuário: para travar ou mudar Z-order precisa abrir modal; para ver eventos por objeto vai na aba Component. Unificar reduz código e melhora a usabilidade. |
| **D07** | **Pickers de Imagem, Som, Fonte e Cor duplicados com qualidades diferentes** | [`GamePropertyPanelView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GamePropertyPanelView.java#L1552-L1778) (3 métodos copy-paste sem busca/áudio + color picker manual) vs [`GameLogicEditorActivity.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameLogicEditorActivity.java#L1026-L1150) (busca, miniaturas, `SoundPool` preview e `ColorPickerDialog` nativo) | O usuário tem busca e preview de áudio ao escolher um som num bloco, mas perde esses recursos ao escolher o som de um objeto ou BGM da cena no Inspector. |
| **D08** | **Cards e controles duplicados na UI** | `Physics` e `Body / Friction` abrem o mesmo `showPhysicsDialog()` ([`GamePropertyPanelView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GamePropertyPanelView.java#L512-L536)); `showDebugHitboxes` existe em Engine Settings e na aba Physics da Cena; `Scene Manager`, `Hierarchy` e `Engine Settings` se repetem no Drawer, no menu `btnOptions` e em `btnCanvasTools` | Ocupa espaço precioso na barra horizontal do Inspector e nos menus enquanto ações usadas o tempo todo (`Undo`, `Redo`, `Grid`) ficam escondidas em popups. |
| **D09** | **2 tabelas paralelas de metadados dos 31 tipos + 4 mapeamentos de títulos de eventos** | [`GameObjectBean.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/model/GameObjectBean.java#L204-L587) vs [`GamePaletteView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GamePaletteView.java#L56-L115); e títulos de eventos em [`GameEventsView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameEventsView.java#L868), [`GameEngineActivity.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameEngineActivity.java#L1053), [`GameLogicEditorActivity.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameLogicEditorActivity.java#L2596) e [`GamePropertyPanelView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GamePropertyPanelView.java#L542-L586) | Gera rótulos inconsistentes para o mesmo evento (`onCreate` / `On Start` / `On Scene Start`; `onClick` / `On Button Pressed`) e o bug `"Sprite (Sprite)"` na Aba Objetos. |
| **D10** | **Helpers utilitários e I/O copiados em vários arquivos** | `dp()` em **9 arquivos** (mesmo `SketchwareUtil.dpToPx` já existindo); decodificação de bitmap `inJustDecodeBounds` em **5 arquivos**; `computeTopZIndex`/`maxZ` em **6 locais**; `clamp()` em **4**; `nonEmpty()` em **4**; `formatTypeName()` em **3**; `readUtf8File`/`writeUtf8File` em [`GameProjectRepository.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/model/GameProjectRepository.java#L294-L312) e [`GameApkSourceGenerator.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/compiler/GameApkSourceGenerator.java#L143-L167); 6 métodos repetitivos de recursos em [`GameProjectRepository.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/model/GameProjectRepository.java#L98-L207) | Boilerplate repetitivo que cria inconsistências sutis (ex.: `Stage` decodifica em `1024px`, `Runtime` em `2048px`, `APK assets` sem limite nenhum) e dificulta corrigir bugs de I/O num único ponto. |
| **D11** | **Resíduos de código morto pontuais** | `CLIPBOARD_PREFIX` não usado ([`GameLogicEditorActivity.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/GameLogicEditorActivity.java#L122)); loop `for` vazio ([`GameEventsView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameEventsView.java#L252-L254)); `KIND_EXPR` e `KIND_OP` sem uso no catálogo visível ([`GameBlockCatalog.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/blocks/GameBlockCatalog.java)); menus `"direction"` e `"effect"` órfãos em `BlockOpcodeUtils`; `syntax_check_container` forçado para `GONE` (`L330`); `Lottie` sem implementação no [`GameDrawerView.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/main/java/pro/sketchware/activities/game/ui/GameDrawerView.java#L127) | Gera falsas expectativas de recursos inexistentes e ruído na leitura do código. |

---

## Parte 3 — Coisas que Podem Ser Simplificadas

Cada simplificação abaixo ataca diretamente a raiz arquitetural dos bugs e duplicações das Partes 1 e 2.

### S01. Unificar Preview e APK em um único Kernel de Runtime (fim das strings `PART1..PART5`)
- **Como é hoje:** `GameRuntimeView` (4.686 linhas) roda no Preview usando Gson e `pro.sketchware.activities.game.model.*`, enquanto `GameApkSourceGenerator` (4.803 linhas) mantém uma cópia manual reescrita dentro de 7 blocos de texto Java usando `org.json`.
- **Como simplificar:** Criar um módulo/pacote puro Android (`game-runtime-core`, sem dependências externas — apenas `android.*`, `org.json.*`, `java.*`) com uma pequena interface `GameAssetProvider` (2 implementações de ~60 linhas: `WorkspaceAssetProvider` para Preview e `ApkAssetProvider` para APK). No Preview, `GamePreviewActivity` passa o JSON gerado por `GameProjectRepository.toJson()` para esse núcleo; no APK, o compilador empacota o mesmo JAR/fontes `.java` reais (sem concatenação de strings!).
- **Por que deve ser considerada:**
  - Elimina **~4.500 linhas** duplicadas de uma só vez.
  - Garante **100% de paridade por construção** entre o que o usuário vê no Preview e o APK instalado.
  - Restaura checagem de compilação, refatoração segura e testes unitários no runtime do APK.

### S02. Aposentar `GameBlocksEditorView.java` após resgatar suas 4 boas ideias
- **Como é hoje:** `GameBlocksEditorView` tem 5.213 linhas completamente desconectadas da `GameEngineActivity`, mas contém a única busca por bloco individual, clipboard JSON (`SWGAME_BLOCKS:`), seletor rápido Target/Event e protótipo de exportação de código.
- **Como simplificar:** Portar a busca de blocos (~80 linhas), o clipboard JSON (~100 linhas) e o seletor rápido de evento/alvo para `GameLogicEditorActivity`, e **excluir `GameBlocksEditorView.java`**.
- **Por que deve ser considerada:** Reduz o módulo em **5.213 linhas (~19%)** mantendo apenas um editor de blocos oficial, sem perder nenhum recurso já escrito.

### S03. Centralizar todas as mutações em um `GameProjectCommandStore` (CRUD + Cascade + Undo Único)
- **Como é hoje:** Quatro telas diferentes manipulam `scene.objects` diretamente; cada uma clona, renomeia, ordena Z e deleta à sua maneira, e apenas o Stage tem Undo (e só de `scene.objects`).
- **Como simplificar:** Criar um único controlador de comandos (`addObject`, `updateObject`, `deleteObjects`, `duplicateObjects`, `instantiatePrefab`, `deletePrefab`, `renameScene`, `deleteScene`) que:
  1. Garante unicidade de ID/nome e Z-index por camada (World vs HUD).
  2. Faz limpeza em cascata de `scene.scripts` / `project.prefabScripts` (guardando os scripts removidos no comando de Undo para restaurá-los intactos ao pressionar Desfazer).
  3. Mantém uma única pilha de Undo/Redo compartilhada entre Stage, Inspector, Hierarquia e Aba Objetos.
- **Por que deve ser considerada:** Elimina de uma vez os bugs **B04**, **B05**, **B22**, **B24** e **D05**, reduzindo centenas de linhas repetidas nas 4 telas.

### S04. Um único registro declarativo para os 31 tipos (`GameObjectTypeRegistry`)
- **Como é hoje:** Informações de cada tipo estão espalhadas em `GameObjectBean.getTypeDisplayName`, `GameObjectBean.createDefaultForType`, `GamePaletteView` (`ALL_WORLD/GAMEPLAY/HUD`), `GamePropertyPanelView.buildTypeSpecificCards` e nos renderizadores.
- **Como simplificar:** Criar um registro único (`ObjectTypeSpec` por tipo) declarando: `type`, `displayName`, `category`, `iconRes`, `accentColor`, `defaultSize`, `defaultZ`, `isHud`, flags de física padrão e **lista de seções/campos editáveis no Inspector** (exatamente como provado em [`Sketchware-GameEngine-HTML5/src/core/model.js`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-GameEngine-HTML5/src/core/model.js)).
- **Por que deve ser considerada:** Adicionar um novo tipo de objeto (ex.: `TileMap` ou `Rope`) passa de editar 6 arquivos diferentes para adicionar **1 entrada declarativa**, além de eliminar automaticamente cards irrelevantes no Inspector (ex.: Física em `hud_text`).

### S05. Extrair `GameBlockViewBuilder` e `GameResourcePickers` compartilhados
- **Como é hoje:** 4 cópias do montador `BlockBean -> BlockView` + 2 famílias de pickers de Imagem/Som/Fonte/Cor.
- **Como simplificar:**
  1. Unificar `populatePane`, `duplicateSubtree`, `insertNestedBlock` e `restoreFloatingStack` em um único `GameBlockViewBuilder.materialize(pane, beans, offsetX, offsetY)` com escape seguro para literais `"@"` (ex.: `"\\@"` ou separação explícita em campo dedicado) — acabando com o crash **B02**.
  2. Reutilizar os pickers buscáveis de `GameLogicEditorActivity` (com miniaturas `48dp`, preview `SoundPool` e `ColorPickerDialog` nativo) também em `GamePropertyPanelView`.
- **Por que deve ser considerada:** Corta ~400 linhas duplicadas e eleva imediatamente a UX do Inspector ao mesmo nível do Logic Editor.

### S06. Referências por ID estável (`targetSceneId` e `prefabId`)
- **Como é hoje:** `Goal.targetScene` grava `scene.id`, blocos `loadScene` gravam `scene.name`, `renameScene` faz busca textual *case-insensitive* por toda a árvore de blocos, e instâncias de prefab não sabem de qual prefab vieram.
- **Como simplificar:**
  - Guardar `sceneId` imutável em `Goal` e nos blocos `loadScene*` (mantendo fallback por nome só para compatibilidade com projetos antigos) e exibir o `scene.name` dinamicamente na UI.
  - Adicionar `public String prefabId = "";` em `GameObjectBean`. Assim, instâncias colocadas no editor herdam `project.prefabScripts` automaticamente no runtime, sem precisar clonar scripts por instância, e habilitam sincronização futura (*Aplicar ao Prefab* / *Reverter*).
- **Por que deve ser considerada:** Simplifica drasticamente `renameScene`/`deleteScene` e resolve o bug estrutural de prefabs no editor (**B04**) com apenas 1 campo no modelo.

### S07. Simplificar a avaliação de blocos e expressões (AST direta sem ida-e-volta em string)
- **Como é hoje:** `syncNestedExpressions()` achata blocos aninhados em string com `toExpression()`, e o runtime mantém 3 switches (`evaluateBlockValue`, `resolveBuiltInOrVar`, `ExprParser.parseFunctionCall`) que frequentemente divergem.
- **Como simplificar:**
  - Avaliar sempre `paramBlocks[i]` diretamente via recursão tipada (`Object` = `Double | String | Boolean`), mantendo `toExpression()` apenas para texto legível/busca.
  - Em `getVar`, consultar `variables` **antes** de qualquer fallback, evitando sombrear variáveis criadas pelo usuário (**B09**).
  - Para slots de texto (`setVar`, `saveGameData`, `sayText`, `setHudText`), tratar strings literais sem bloco aninhado como texto puro (interpolando `{var}` com o `fiber` atual), sem tentar parsear `"idle"` ou `"forest"` como matemática.
- **Por que deve ser considerada:** Remove uma classe inteira de bugs sutis de parsing (**B08**, **B09**, **B16**, **B17**) e torna o interpretador mais rápido.

### S08. Simplificar `GameProjectRepository` e tornar o salvamento atômico + assíncrono
- **Como é hoje:** 6 métodos quase idênticos de lookup de imagem/som/fonte, escrita direta não-atômica engolindo `IOException`, e `save()` síncrono na UI thread a cada edição.
- **Como simplificar:**
  - Parametrizar os 6 métodos em `listAssets(scId, AssetKind)` e `resolveAssetFile(scId, AssetKind, name)` + adicionar um `GameBitmapUtils.decodeSampled(source, maxDim)` único para todo o módulo.
  - Adicionar `schemaVersion = 1` ao `GameProject`, salvar via `game_project.json.tmp` -> `renameTo` + backup `game_project.json.bak` em background (com *debounce* de ~300ms + flush síncrono em `onPause`/`launchPlayPreview`), e retornar `LoadResult(project, warnings, recoveredFromBackup)` em vez de silenciar erros.
- **Por que deve ser considerada:** Elimina o risco de corrupção/perda total do projeto (**B01**), deixa a interface mais fluida em projetos grandes e prepara o JSON para evoluções futuras sem quebrar projetos antigos.

### S09. Unificar `GameHierarchyDialog` + `GameObjectsTabView` e enxugar o build de APK 2D
- **Como é hoje:** Duas UIs diferentes para a lista de objetos da cena; e o build de APK 2D executa `targetWorkspace.a/b` gerando layouts XML e dependências AppCompat padrão do Sketchware antes de sobrescrever `MainActivity.java` com uma `android.app.Activity` pura que não usa nada disso.
- **Como simplificar:**
  1. Transformar a aba `Component/Objects` na **Árvore de Camadas (HUD / World)** definitiva com Eye/Lock/Z/Prefabs/Eventos, eliminando a duplicidade do `GameHierarchyDialog` modal (ou mantendo o modal apenas como atalho da mesma view reutilizável).
  2. No `BuildGameApkTask`, pular a injeção de layouts/bibliotecas AppCompat desnecessárias quando o projeto é do tipo Game 2D puro.
- **Por que deve ser considerada:** Menos código de UI para manter, fluxo mais direto para o criador, e **APKs menores e mais rápidos de compilar**.

---

## Parte 4 — Melhorias de Código, Física, Performance e UX (Validadas no Protótipo HTML5)

As melhorias abaixo correspondem às **20 hipóteses testáveis** implementadas e comprovadas em [`Sketchware-GameEngine-HTML5`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-GameEngine-HTML5/analysis/00-hypotheses.json), adaptadas para aplicação direta no `Sketchware-IA`.

### 4.1 Física, Colisão e "Game Feel"

| Ideia de Melhoria | O que Resolve no `Sketchware-IA` | Por que Deve Ser Considerada |
|---|---|---|
| **M01. Broadphase com Spatial Hash Grid (`128px`) + pular `static-vs-static`** | Substitui os 4 laços `O(N^2)` / `O(D·N)` em `GameRuntimeView:1013-1253` e faz `stepWorldBoundsAndBuiltins` iterar `actor.currentColliders` em vez de varrer todos os objetos. | Em cenas com 300+ tiles/moedas/projéteis, reduz em **>75% as checagens por frame**, mantendo 60 FPS estáveis em celulares intermediários e eliminando falsos `on_collision` no frame 1 entre plataformas paradas. |
| **M02. Física sólida analítica Círculo-AABB / Círculo-Círculo + escala na hitbox** | Resolve `physics_circle` usando distância analítica ao ponto mais próximo + projeção de normal e multiplica bounds por `|scaleX|`/`|scaleY|`. | Faz bolas rolarem naturalmente em quinas/rampas e garante que `setSize`/`setScale` alterem também a área física de colisão. |
| **M03. Anti-tunneling (Swept AABB / Substepping adaptativo) + divisão de massa** | Subdivide o passo quando `max(|vx|,|vy|)*dt > 0.5*min(w,h)` e divide separação dinâmico-dinâmico de forma simétrica. | Acaba com personagens/projéteis rápidos atravessando paredes finas ou plataformas *one-way* a `1600 px/s` e elimina esmagamentos dependentes da ordem no array. |
| **M04. Ordem coerente de `onGround`, suspensão de gravidade no `glideToXY` e interpolação `alpha`** | Atualiza contato/plataformas móveis dentro do solver antes da leitura de `onGround`, pausa gravidade durante `glideToXY`, empurra riders lateralmente em plataformas móveis e interpola `(prevX/Y -> x/y)` no render. | Remove o atraso de 1 frame ao pular, impede o sprite de "cair" durante um `glideToXY`, elimina teleportes laterais em plataformas móveis e acaba com *judder* em telas de 90/120 Hz. |
| **M05. Coyote Time (`~85ms`) e Jump Input Buffer (`~110ms`)** | Rastreia `timeSinceGrounded` e janela curta de pré-toque do botão de pulo (configurável por projeto ou cena). | É o padrão da indústria em jogos 2D (Celeste, Hollow Knight): faz jogos de plataforma criados no Sketchware parecerem imediatamente mais responsivos em telas touch, sem exigir lógica complexa em blocos. |
| **M06. Câmera com Lookahead, Deadzone e Trauma Shake** | Evolui `stepCamera()` para olhar à frente na direção da velocidade, ignorar micro-oscilações numa zona morta central e decair tremor de forma quadrática (`trauma^2`). | Melhora drasticamente a visibilidade de obstáculos à frente em fases de corrida/plataforma e eleva o acabamento visual sem custo significativo de CPU. |

### 4.2 Performance de Runtime e Uso de Memória

| Ideia de Melhoria | O que Resolve no `Sketchware-IA` | Por que Deve Ser Considerada |
|---|---|---|
| **M07. Zero-Allocation no loop de 60 FPS (Lighting, Partículas e Animação)** | Reutilizar `PorterDuffXfermode` e cachear `RadialGradient`/bitmap de luz em `drawLighting2DPass` (`L3558-3619`); pré-parsear `spriteFrames.split(",")` ao carregar a cena (`L957`); trocar `ArrayList.remove(0)` por *ring buffer* fixo de 320 partículas (`L1379-1426`); pré-carregar assets fora do `stateLock`. | No Android, alocar objetos a 60 FPS aciona o Garbage Collector e provoca travadinhas (*GC jank*) visíveis mesmo com FPS médio alto. Corrigir esses 4 pontos deixa o frame-time liso. |
| **M08. PRNG determinístico opcional e Reset explícito de `globalVariables` / `timer`** | Passar um `boolean resetGlobals` explícito em `loadSceneInternal()` e unificar a pausa/reset de `gameTimerSeconds`. | Corrige **B15**, permite reproduzir bugs com precisão e prepara o motor para replays ou testes de regressão determinísticos. |

### 4.3 Ergonomia do Stage Editor e Inspector

| Ideia de Melhoria | O que Resolve no `Sketchware-IA` | Por que Deve Ser Considerada |
|---|---|---|
| **M09. Inspetor contextual por schema e não-oclusivo** | Exibir apenas campos relevantes a cada um dos 31 tipos, mover `Scale`/`Alpha`/`Rotation` para **Transformação & Aparência**, adicionar campos dedicados para `hud_bar` (`value`/`max`), `hud_dialog` (`Speaker`), `light_2d` (Raio/Intensidade) e `projectile.damage`, e ajustar o layout para **não cobrir `#bottom_bar`**. | Reduz em mais de 50% a quantidade de toques e modais necessários para configurar uma cena, além de manter `Undo`, `Redo`, `Cena` e `Run` sempre acessíveis. |
| **M10. Gizmo de 8 alças sem deriva + Rota arrastável de `moving_platform`** | Substituir o knob único bottom-right por alças com compensação da âncora oposta em objetos rotacionados, corrigir a haste de rotação para `scaleY < 0`, mostrar `TrashZone` só após `dragMovedBeyondSlop`, e desenhar/arrastar o ponto final de `moving_platform` em espaço de mundo. | Elimina erros geométricos (**B21**) e permite ajustar visualmente o trajeto de plataformas móveis e raios de luz direto no palco sem tentativa e erro no Preview. |
| **M11. Separação real World/HUD + HUD pinado na viewport + Safe-Area** | Renderizar HUD num segundo passe no editor (igual ao runtime), calcular `computeTopZIndex` separadamente por camada, oferecer toggle para manter o HUD visível sobre a viewport ao navegar em fases largas (`3840×720`) e manter guias de *safe-area* acessíveis em qualquer filtro. | Impede criar HUD fora da tela e garante que o designer veja exatamente onde botões touch ficarão sobre o cenário. |
| **M12. Guias inteligentes de alinhamento e Seleção Múltipla** | Adicionar guias magnéticas de borda/centro entre objetos irmãos + grade configurável (`8/16/32/64/Off`) e seleção múltipla (alinhar, distribuir, mover e deletar em lote). | Construir fileiras de moedas, plataformas simétricas ou grupos de botões HUD passa de dezenas de ajustes manuais para 1–2 gestos. |

### 4.4 Ergonomia do Logic Editor, Observabilidade e Testes

| Ideia de Melhoria | O que Resolve no `Sketchware-IA` | Por que Deve Ser Considerada |
|---|---|---|
| **M13. Busca fuzzy real de blocos + hitboxes de snap em `dp` + preview fantasma** | Pesquisar nos 131 opCodes, specs, sinônimos e variáveis; converter `40px`/`60px` do `BlockPane` para `dp` (`~20–28dp`); mostrar silhueta translúcida do bloco real durante o arrasto com feedback de slot compatível/incompatível; e corrigir `forever` para cap terminal + habilitar `Duplicate` (`layoutCopy`). | Transforma a programação visual no celular de uma experiência trabalhosa em algo ágil e preciso, reduzindo erros de encaixe na primeira tentativa. |
| **M14. Alternador rápido `Alvo ▾ > Evento ▾`, Zoom e persistência de `floatingStacks`** | Permitir trocar de evento/objeto na barra superior do `GameLogicEditorActivity` sem fechar a Activity, adicionar pinch-to-zoom (`0.5x–1.5x`) no `ViewLogicEditor`, e salvar `floatingStacks` no JSON. | Hoje, editar `onCreate`, `onUpdate` e `onCollision` do mesmo jogador exige abrir e fechar a Activity 3 vezes (recarregando o JSON inteiro do disco a cada volta). |
| **M15. Lint estático (reativar `syntax_check_container`) e Telemetria no Preview** | Ativar diagnósticos leves antes do Play (condição `< >` vazia, bloco após `forever`/`stopScript`, sprite/som/cena inexistente, scripts órfãos) e enriquecer a pílula de FPS do `GamePreviewActivity` com um painel expansível de variáveis ao vivo, contagem de objetos/fibers/partículas e log de eventos. | Reduz drasticamente o tempo de depuração: o usuário descobre em segundos se uma tag de colisão foi digitada errado ou se um loop travou, em vez de adivinhar no escuro. |
| **M16. Suíte automatizada de paridade e regressão** | Expandir [`GameProjectAndCatalogTest.java`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-IA/app/src/test/java/pro/sketchware/activities/game/GameProjectAndCatalogTest.java) (hoje com apenas 3 testes) com testes de round-trip do `GameBlockBeanConverter` (incluindo `"@texto"`, `getVar`, cores), `GameProjectRepository` (recuperação de backup), `ExprParser`, cascata de exclusão e paridade de execução de cenários de referência (como validado em [`engine.test.mjs`](file:///c:/Users/kirit/AndroidStudioProjects/Sketchware/Sketchware-GameEngine-HTML5/tests/engine.test.mjs)). | Garante que cada bug corrigido nunca mais volte em futuras atualizações do Sketchware-IA. |

---

## Parte 5 — Roadmap Sugerido (Priorizado por Impacto × Esforço)

Para evoluir o código sem quebrar usuários atuais, a recomendação é atacar em **4 fases incrementais**:

### Fase 0 — Correções Cirúrgicas de Alto Impacto (Risco Mínimo, Ganho Imediato)
1. Envolver `Integer.parseInt` em `GameLogicEditorActivity` (`L913, L1396, L2375`) e escapar `"@"` no `GameBlockBeanConverter` (**B02**).
2. Corrigir no APK: `spawned.isClone = true` (`L2853`), `targetObj.matchKey()` em `pendingEvents` (`L1415`), alias `"rot"` (`L3241`), `saveGameData` com texto literal (`L2765`), `destroyOffscreen` fora do `activeOnlyNearCamera` (`L1507`) e `speed > 0 && !glideActive` em `moving_platform` (**B07–B10**).
3. Corrigir no Preview: `rebuildPressedButtons()` no `ACTION_CANCEL` (`L559`), ordem de `drawSpeechBubble` após iluminação (`L3790`), `touchingObject("edge")`, spam de partículas no Checkpoint (`L1210`), `interpolateTemplate(raw, fiber)` (`L2805`), reset de `globalVariables` por flag explícita (`L425`), e prioridade de `variables` em `getVar` (**B09, B11, B14–B16**).
4. Destravar `layoutCopy` no `GameLogicEditorActivity` (`L670`), corrigir typo `"q="` e `Static`/`Sensor` toggle no Inspector, remover loop vazio em `GameEventsView` (`L252-254`), e adicionar `inSampleSize` ao `decodeStream` do APK (**B19, B22, B23**).

### Fase 1 — Integridade de Dados & Desduplicação Estrutural
1. Portar busca de blocos + clipboard JSON de `GameBlocksEditorView` para `GameLogicEditorActivity` e **deletar `GameBlocksEditorView.java`** (**-5.213 linhas**).
2. Implementar gravação atômica + `.bak` + `schemaVersion` + salvamento assíncrono em `GameProjectRepository` (**B01, S08**).
3. Criar `GameProjectCommandStore` (CRUD + cascata de scripts + Undo unificado) + adicionar `prefabId` e remover `transient` de `floatingStacks` (**B03–B05, S03, S06**).
4. Extrair `GameBlockViewBuilder`, `GameResourcePickers`, `GameObjectTypeRegistry`, `GameBitmapUtils` e remover os 9 `dp()` / helpers duplicados (**D03, D07, D09, D10, S04, S05**).

### Fase 2 — Runtime Único & Física Profissional
1. Extrair `game-runtime-core` compartilhado entre `GamePreviewActivity` e o APK gerado, eliminando os 7 blocos de string `PART1..PART5` de `GameApkSourceGenerator` (**-4.500 linhas** e paridade definitiva **S01**).
2. Implementar avaliação direta de AST (`paramBlocks`) sem achatamento destrutivo em string (**B17, S07**).
3. Evoluir física e performance: Spatial Hash `128px`, colisor circular sólido + escala, anti-tunneling, `onGround` sem lag, suspensão de gravidade no `glideToXY`, Coyote Time / Jump Buffer, e render/partículas/animação *zero-allocation* (**M01–M08**).
4. Enxugar dependências desnecessárias do template no `BuildGameApkTask` (**S09**).

### Fase 3 — Evolução de UX do Editor & Observabilidade
1. Reformular `GamePropertyPanelView` para ser dirigido por schema (31 tipos) sem cobrir `#bottom_bar`, e unificar `GameHierarchyDialog` + `GameObjectsTabView` em uma Árvore de Camadas HUD/World (**M09, S09**).
2. Evoluir `GameStageEditorView`: 8 alças sem deriva, rota arrastável de `moving_platform`, passe separado de HUD + pin de viewport, guias magnéticas e seleção múltipla (**M10–M12**).
3. Evoluir `GameLogicEditorActivity` e `GamePreviewActivity`: busca fuzzy real, snap em `dp` + preview fantasma, alternador `Alvo > Evento`, zoom, lint estático em `syntax_check_container`, painel expansível de telemetria/variáveis e suíte completa de testes unitários (**M13–M16**).

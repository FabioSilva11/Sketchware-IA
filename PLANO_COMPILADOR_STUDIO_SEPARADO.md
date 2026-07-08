# Plano — Compilador/Bibliotecas separados só para o modo Android Studio

## 1. Causa raiz do problema

O modo Android Studio (`AndroidStudioProjectActivity.runStudioBuild()`) **reutiliza integralmente o pipeline de build do Sketchware normal**:

- `ProjectBuilder` (a.a.a.ProjectBuilder) — o mesmo compilador (ECJ + D8/AAPT2) dos projetos Sketchware.
- `yq` workspace — layout de pastas de projeto Sketchware.
- `BuiltInLibraries.extractCompileAssets()` — injeta **androidx/material em versões fixas** empacotadas no APK (`filesDir/libs`, ex.: `material-1.13.0`, androidx.* fixos).
- `LocalLibrariesUtil` / `syncGradleDependenciesForStudioBuild()` — baixa as dependências do `build.gradle` do projeto e as mistura às built-in.

**Por que dá conflito:** um projeto Android Studio real declara suas próprias versões no `build.gradle` (ex.: `material:1.11.0`, `appcompat:1.6.1`). O build injeta **também** as built-in fixas (material 1.13.0 etc.). Resultado: duas versões da mesma lib no classpath → `Duplicate class`, `NoSuchMethodError` em runtime, divergência de API compilada vs. empacotada, e R.java conflitante. É exatamente o sintoma que você descreve.

O modo Sketchware **depende** dessas built-in (os projetos Sketchware não têm `build.gradle` com versões próprias). Então não dá para simplesmente remover — precisa **bifurcar** o pipeline.

## 2. Objetivo

Criar um caminho de compilação isolado (`StudioProjectBuilder` + `StudioLibraryResolver`) usado **apenas** pelo modo Android Studio, que:

- Respeita as versões declaradas no `build.gradle` do projeto (fonte da verdade).
- **Não** injeta as built-in fixas por padrão — só as usa como fallback quando o projeto não declara aquela lib.
- Mantém o modo Sketchware 100% intocado (zero regressão).

## 3. Arquitetura proposta

```
buildProject()
  ├─ if (isAndroidStudioProject)  → runStudioBuildIsolated()  [NOVO]
  └─ else                          → runStudioBuild()          [atual, projetos Sketchware]
```

Novos componentes (pacote `pro.sketchware.studio.build`):

1. **`StudioLibraryResolver`** — lê `app/build.gradle` (+ `settings.gradle`, version catalogs `libs.versions.toml` se houver), resolve o grafo de dependências com o `DependencyResolver`/`org.cosmic.ide` que o app já usa, e produz **uma única lista de artefatos resolvidos com conflito de versão resolvido** (estratégia "highest version wins", como o Gradle). Sem tocar em `BuiltInLibraries`.

2. **`StudioClasspathPolicy`** — decide, para cada coordenada `group:artifact`:
   - Se o projeto declara versão → usa a do projeto.
   - Se não declara mas é transitiva → usa a resolvida pelo grafo.
   - Se falta e existe built-in → usa built-in **só como último recurso** (com aviso no output).
   - Deduplica por `group:artifact` mantendo 1 versão (nunca 2).

3. **`StudioProjectBuilder`** — cópia enxuta do fluxo de `runStudioBuild()`, mas:
   - Substitui `BuiltInLibraries.extractCompileAssets(receiver)` por `StudioLibraryResolver.extractResolvedClasspath(receiver)`.
   - Passa esse classpath isolado ao `ProjectBuilder` (reaproveita AAPT2/ECJ/D8 — o **compilador** pode ser o mesmo; o que precisa isolar são as **bibliotecas/classpath**).
   - Usa um diretório de libs próprio: `filesDir/studio_libs/<scId>/` (nunca `filesDir/libs`, que é do modo Sketchware).

4. **Cache por projeto** — `studio_libs/<scId>/resolved.json` com o grafo resolvido + hash do `build.gradle`. Só re-resolve quando o `build.gradle` muda (evita rebaixar toda hora).

## 4. Pontos de mudança (mínimos e localizados)

| Arquivo | Mudança |
|---|---|
| `AndroidStudioProjectActivity.buildProject()` | rotear para `runStudioBuildIsolated()` quando o projeto tem `build.gradle` real (detectar por presença de `app/build.gradle` com bloco `dependencies`). |
| `AndroidStudioProjectActivity.runStudioBuild()` | permanece para retrocompatibilidade / projetos sem gradle. |
| **novo** `studio/build/StudioLibraryResolver.java` | resolução de dependências isolada. |
| **novo** `studio/build/StudioClasspathPolicy.java` | dedupe + estratégia de versão. |
| **novo** `studio/build/StudioProjectBuilder.java` | fluxo de build usando o classpath isolado. |
| `ProjectBuilder` | **não alterar**; apenas receber o classpath via parâmetro/campo já existente (verificar `buildBuiltInLibraryInformation()` — expor override do classpath). |

Ponto de atenção: `ProjectBuilder.buildBuiltInLibraryInformation()` provavelmente lê de `BuiltInLibraries` internamente. É preciso adicionar um setter/override de classpath (ex.: `builder.setExternalCompileLibraries(List<File> jars)`) que, quando presente, **substitui** as built-in em vez de somar. Essa é a única mudança invasiva — e é aditiva (não quebra o modo Sketchware, que simplesmente não chama o setter).

## 5. Estratégia de resolução de conflitos (o coração)

1. Parsear todas as dependências declaradas (já existe `detectProjectDependencies()` / regex de gradle no arquivo).
2. Expandir transitivas via `DependencyResolver`.
3. Agrupar por `group:artifact`; para cada grupo escolher **a maior versão** (semver). Logar quando houver rebaixamento/conflito.
4. Baixar/cachear os AARs/JARs em `studio_libs/<scId>/`.
5. Montar classpath final = artefatos resolvidos (1 por coordenada). Built-in entra **apenas** para coordenadas ausentes que o compilador exige (ex.: `androidx.annotation` que quase todo projeto precisa mas nem sempre declara).
6. Passar ao `ProjectBuilder` via o setter novo.

## 6. Roteiro de implementação

1. **Fase A (isolamento de libs)** — `StudioLibraryResolver` + `StudioClasspathPolicy` + diretório `studio_libs/<scId>`; setter de classpath no `ProjectBuilder`. Teste: projeto que declara `material:1.11.0` compila com 1.11.0 (não 1.13.0).
2. **Fase B (rota separada)** — `StudioProjectBuilder` + roteamento em `buildProject()`; modo Sketchware inalterado. Teste de não-regressão: build de projeto Sketchware normal continua idêntico.
3. **Fase C (cache + conflitos)** — `resolved.json` por hash do gradle; logs de conflito/rebaixamento no painel de output.
4. **Fase D (opcional)** — permitir estratégias configuráveis (`highest` / `strict`/ `prefer-project`) num diálogo de "Build settings" do modo Studio.

## 7. Riscos e mitigação

- **`ProjectBuilder` acoplado a `BuiltInLibraries`**: se não houver ponto de injeção, criar subclasse/wrapper que sobrescreve a etapa de classpath. Mitigação já prevista no item 4.
- **Resolução transitiva lenta/offline**: cache por hash (Fase C) + reuso dos AARs já baixados via `LocalLibrariesUtil`.
- **AAPT2 e recursos duplicados**: ao usar 1 versão por lib, o conflito de `R`/recursos desaparece naturalmente; ainda assim, logar recursos duplicados.
- **Kotlin**: `KotlinCompilerBridge` continua igual, só recebendo o classpath isolado.

---

Resumo: o compilador (ECJ/AAPT2/D8) pode continuar sendo o mesmo — o que causa a incompatibilidade é o **classpath de bibliotecas** compartilhado e de versão fixa. A solução é bifurcar o build do modo Android Studio para usar um **resolvedor de bibliotecas isolado por projeto**, com uma única versão por coordenada vinda do `build.gradle`, deixando o modo Sketchware exatamente como está.

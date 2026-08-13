# Análise de risco — atualização de bibliotecas, JARs e Gradle

Data da inspeção: 13 de agosto de 2026
Escopo: `Sketchware-IA`, módulo `:app`. Este documento **não altera** versões nem remove arquivos.

## Resumo executivo

Atualizar tudo diretamente para a versão mais recente é uma operação de risco **alto** neste projeto. O aplicativo não é apenas um cliente Android: ele incorpora partes do toolchain que gera, compila, reduz, alinha e assina APKs de projetos Sketchware. Portanto, uma biblioteca pode compilar o aplicativo principal e ainda quebrar a geração de APKs feita pelos usuários.

O Gradle de infraestrutura já está relativamente atualizado:

| Camada | Versão encontrada | Avaliação |
|---|---:|---|
| Android Gradle Plugin | 8.13.2 | Não atualizar junto com todas as bibliotecas sem uma etapa própria. |
| Gradle Wrapper | 8.13 | Deve continuar compatível com o AGP; atualizar isoladamente. |
| Kotlin Gradle Plugin | 2.3.21 | Há risco por não coincidir com o compilador Kotlin embutido. |
| `compileSdk` | 36 | Bom ponto de partida; testar APIs e empacotamento após cada lote. |
| `targetSdk` | 28 | Separar da atualização de bibliotecas; elevar o target muda comportamento do Android. |

## Inventário que exige cuidado

### JARs locais em `app/libs`

Estes artefatos entram por `fileTree("libs")`; não são atualizados pelo catálogo Gradle e vários não têm metadados de dependências transitivas.

| Arquivo | Papel inferido | Risco ao substituir |
|---|---|---|
| `a.a.a-important-classes.jar`, `a.a.a-notimportant-classes.jar` | Classes internas/ofuscadas do Sketchware | **Crítico**: não há contrato de API legível; substituição pode causar erros só ao criar/editar projetos. |
| `com.besome.sketch-classes.jar` | Compatibilidade com classes históricas do Sketchware | **Crítico**: risco de `ClassNotFoundException` em projetos legados. |
| `build-tools_apksigner_32.0.0.jar` | Assinatura de APK dentro do app | **Crítico**: API e esquemas de assinatura podem mudar; validar APK final com `apksigner verify`. |
| `proguard-base-7.2.2.jar` | Redução/otimização de código gerado | **Crítico**: regras, atributos e APIs diferem entre versões. |
| `base_libs.jar` | ASM e utilitários de bytecode | **Alto**: incompatibilidade de bytecode e classes duplicadas. |
| `play-services-location-21.0.1.jar` | Localização do Google Play Services | **Alto**: mistura um JAR manual com dependências Google/Firebase modernas. |
| `android-svg.jar` | Renderização SVG | **Médio**: troca pode alterar parsing/renderização de imagens existentes. |
| `com.github.megatronking.stringfog-classes.jar` | Ofuscação/decodificação de strings | **Alto**: pode impedir leitura de código/projetos ou quebrar classes ofuscadas. |

Também existe `app/src/main/assets/libs/core-lambda-stubs.jar`. Ele parece ser distribuído/consumido como parte do ambiente de compilação do Sketchware, não uma dependência normal do app. Não deve ser atualizado junto com as dependências de execução.

### Dependências Gradle com maior probabilidade de quebra

| Grupo | Situação observada | Risco |
|---|---|---|
| Kotlin | Plugin `2.3.21`, mas `kotlin-compiler` `2.1.21` e `kotlinc-for-sketchware` `2.1.21_rc3` | **Crítico**: metadata, APIs do compilador e geração de código podem divergir. |
| Toolchain de build interno | `bundletool`, `sdklib`, `r8`, `proguard-core`, `zipalign-java`, `ecj`, `nb-javac-android` | **Crítico**: estes componentes produzem APKs dentro do aplicativo. Atualizar em conjunto torna a causa de uma falha difícil de isolar. |
| Editor de código | Sora Editor BOM, editor, Java e TextMate | **Alto**: alterações de API podem afetar realce, edição e abertura de arquivos. |
| Firebase/Google | Firebase BoM declarado diretamente em `app/build.gradle`, Google Services, Crashlytics, Ads, UMP e Play Services local | **Alto**: versões devem ser alinhadas; não misturar JAR manual de Play Services com módulos Maven sem conferir classes duplicadas. |
| Rede e parsing | OkHttp/Okio, Gson, Retrofit transitivo, JavaParser, Woodstox/StAX | **Médio/alto**: mudanças de API e conflitos transitivos podem atingir importação, IA e analisador de código. |
| Interface | AndroidX, Material alpha, Coil/Glide, Lottie, Markwon, Insetter | **Médio**: regressões visuais, de tema, recursos ou minSdk. |

## Inconsistências a resolver antes da atualização

1. O catálogo declara `firebaseBom = 34.3.0`, mas o módulo usa diretamente `firebase-bom:34.16.0`. Há duas fontes de verdade.
2. O catálogo usa Kotlin Compiler `2.1.21`, enquanto o plugin Kotlin do projeto está em `2.3.21`. Isso só deve ser alterado após testes reais de compilação de projetos Kotlin pelo próprio Sketchware.
3. Há JARs locais antigos ao lado de bibliotecas Maven recentes. Gradle não resolve conflitos internos de classes presentes nos JARs como resolveria versões de módulos Maven.
4. `targetSdk 28` é uma mudança de comportamento independente. Não incluí-lo no mesmo PR/lote da modernização das bibliotecas.

## Estratégia de atualização segura

1. Criar uma branch e registrar um baseline: `assembleDebug`, abertura do app, criação/abertura de um projeto Java e um Kotlin, compilação, assinatura e instalação dos APKs gerados.
2. Congelar os JARs locais inicialmente. Atualizar primeiro dependências de UI, rede e Firebase que não participam da ferramenta interna de build.
3. Fazer um pequeno lote por vez: uma família (por exemplo AndroidX) e um único commit. Nunca usar atualização global automática em todo o catálogo.
4. Atualizar Gradle Wrapper, AGP e Kotlin Gradle Plugin em etapas separadas, mantendo a matriz de compatibilidade oficial de cada um.
5. Atualizar o compilador Kotlin embutido somente como uma migração própria, com projetos de teste Java/Kotlin e bibliotecas comuns.
6. Para cada JAR local, identificar origem, licença, código-fonte e APIs usadas antes de substituí-lo. Preferir migrar para um artefato Maven somente se for possível reproduzir o comportamento e excluir duplicidades.
7. Deixar `targetSdk` para a última fase, depois das bibliotecas estarem estáveis; testar permissões, armazenamento, notificações, instalação de APK e fluxos em Android recente.

## Matriz de validação obrigatória

| Alteração | Validação mínima antes de avançar |
|---|---|
| Biblioteca comum | `:app:assembleDebug`, inicialização e fluxo da tela que a usa. |
| Firebase/Google/Ads | Build com e sem `google-services.json`, inicialização, Crashlytics/Analytics sem falha e consentimento UMP. |
| Editor/JavaParser/Kotlin | Abrir, editar e salvar Java/Kotlin/XML; realce e análise sem crash. |
| R8/ProGuard/ASM/JAR de compilação | Gerar APK de amostra, instalar, abrir e executar a função gerada. |
| Bundletool/zipalign/apksigner | Gerar APK/AAB quando aplicável; conferir integridade, alinhamento e assinatura (`apksigner verify --verbose`). |
| AGP/Gradle/Kotlin Plugin | Build limpo e incremental; repetir todas as validações acima. |
| `targetSdk` | Teste físico em Android recente: permissões, arquivos, notificações, câmera/localização se usadas, instalação e execução de APK gerado. |

## Critério de aceite e reversão

Uma atualização só é aceita quando houver `BUILD SUCCESSFUL`, artefato novo e íntegro, e os APKs **gerados pelo Sketchware** forem instalados e executados em dispositivo/emulador. A compilação do app Sketchware por si só não é evidência suficiente.

Se um lote falhar, reverter apenas o commit daquele lote, preservar o log completo e identificar a primeira incompatibilidade. Não compensar uma falha com `force`, exclusões arbitrárias ou downgrade silencioso de dependências transitivas.

## Ordem recomendada de trabalho

1. Documentar origem e uso de todos os JARs locais.
2. Corrigir as duas fontes de versão do Firebase BoM.
3. Atualizar AndroidX/UI em lotes pequenos.
4. Atualizar rede, parsing e bibliotecas auxiliares.
5. Atualizar Firebase/Google com teste de consentimento e serviços.
6. Atualizar Gradle, AGP e plugin Kotlin em três etapas separadas.
7. Migrar o compilador Kotlin e, depois, o toolchain interno/JARs críticos.
8. Avaliar `targetSdk` por último.

Essa ordem reduz o risco de transformar uma falha de compilação de projetos Sketchware em um diagnóstico impossível, e mantém um ponto de reversão claro para cada família de dependências.

# Migração dos anúncios do Sketchware IA para Unity Ads

**Objetivo:** substituir a monetização do próprio aplicativo usando Game ID `800408466` e banner `BP_Banner_Android`.

**Escopo:** manter o suporte AdMob e Unity Ads dos projetos gerados. Não alterar os IDs dos projetos dos usuários. Não criar novos momentos de exibição de anúncios.

**Decisões:** usar Unity Ads 4.21.0, já usado pelas bibliotecas internas; usar a API atual verificada no SDK. Centralizar também os placements `BP_Interstitial_Android` e `BP_Rewarded_Android`, sem novos gatilhos de tela cheia, pois o aplicativo só usava banners e nativos. Remover os nativos do chat e das bibliotecas, que não têm equivalente direto entre os formatos fornecidos. Solicitar apenas anúncios contextuais, sem presumir consentimento para personalização; remover o fluxo Google UMP e o App ID AdMob do aplicativo.

## Execução

- [x] Testar o intervalo progressivo entre tentativas e a recuperação após sucesso.
- [x] Migrar AdManager, dependência e todos os banners próprios para Unity Ads; liberar anúncios ao destruir a tela ou sua view e ignorar callbacks antigos.
- [x] Remover carregadores e espaços nativos; preservar a indexação das mensagens e bibliotecas.
- [x] Compilar o gerenciador com os SDKs reais, executar testes isolados e conferir os fontes do manifesto e dependências.
- [x] Montar o APK completo e testar a entrega de banner de teste em dispositivo (entrega em produção descrita abaixo).

## Verificação

Verificar inicialização assíncrona, falhas sem loop de solicitações, views desanexadas durante carregamento, contêineres ainda sem medidas, centralização do banner e modo de teste apenas em debug. Executar os testes do aplicativo e montar o APK debug. A entrega real depende da validação do Game ID/placements no painel Unity e de teste em dispositivo com rede.

## Resultado da validação

- Os 15 pontos de banner usam o placement central, sem IDs próprios AdMob remanescentes.
- `AdManager` e `AdRequestBackoff` compilados com `javac --release 17` contra Android 37.0 e Unity Ads 4.21.0 reais.
- Oito testes JUnit passaram: quatro para o intervalo progressivo, limite, recuperação e tempo restante; quatro para a ordenação das mensagens do chat, incluindo conversa longa sem linhas nativas.
- Revisão independente encontrou uma falha de recuperação após erro na inicialização. Corrigida: tentativas agendadas com intervalo progressivo, canceladas quando não há banners em telas ativas. O carregamento de banner também se recupera dessa forma.
- `git diff --check` passou. Inspeção do AAR confirmou o inicializador AndroidX Startup do Unity e a compatibilidade da API usada.
- A execução completa `:app:testDebugUnitTest :app:assembleDebug` não terminou: a JVM falhou por falta de memória nativa na etapa `mergeDebugResources`, inclusive com um único worker e heap de 768 MB. Não foi produzido um APK novo nem confirmada a exibição em dispositivo. O APK antigo em `app/build/outputs` não representa esta migração.

## Diagnóstico no aparelho e correção do Protobuf

- Reproduzido no aparelho `2312DRA50G`: o SDK abortava a inicialização com `VerifyError` em `gatewayprotocol.v1.TimestampsOuterClass$Timestamps.mergeTimestamp`. O `com.google.protobuf.Timestamp` completo não herda de `GeneratedMessageLite`, como o código da Unity exige. Excluir apenas `protobuf-javalite` permite compilar, mas produz esse erro em execução.
- `gradle/unity-protobuf.gradle` gera um AAR da Unity com referências e runtimes Protobuf lite realocados para `pro.sketchware.unity.protobuf`. Preserva manifesto, recursos e regras do SDK, bem como suas dependências Android declaradas no POM. O Protobuf completo do bundletool permanece no namespace original. A transformação ocorre antes de entregar o AAR ao AGP.
- O teste `UnityProtobufCompatibilityTest` reproduziu o mesmo `VerifyError` com os binários originais. Após o isolamento, passou a serialização e leitura de timestamps da Unity junto com a consulta de descritores da versão completa.
- Executar a verificação pequena com `gradlew :app:verifyUnityProtobufCompatibility`; o teste também está disponível na suíte JUnit do app.
- Os dez recursos `.proto` compartilhados entre as versões completas/lite foram comparados byte a byte: são idênticos. O empacotamento mantém uma cópia de cada esquema.
- APK release de diagnóstico gerado e instalado com `adb install -r`, preservando os dados. Para a compilação local, foi usada temporariamente a configuração Firebase de debug; o arquivo temporário foi removido. A análise estática global `lintVital` foi excluída desta compilação de diagnóstico; compilação Java/Kotlin, verificação de duplicatas, teste Protobuf e empacotamento passaram. A junção das dependências exigiu heap de 2304 MB; as etapas seguintes aproveitaram o cache com heap de 1024 MB.
- No aparelho, o SDK registrou `Initializing SDK 4.21.0 ... with game id 800408466 in production mode`, `Initialized successfully` e `unity_initialized`. O `VerifyError` original desapareceu.
- Após desbloqueio, o servidor respondeu `52100 / PUBLIC_ERROR_CODE_LOAD_NO_FILL` em produção. Os IDs e o placement ativo foram conferidos no painel; o Store ID permanece sem cadastro, conforme a decisão original de não inventar uma URL de loja. Não foi comprovada entrega de anúncio pago.
- A instrumentação temporária, assinada com a mesma chave, inicializou o SDK em modo de teste dentro do processo do APK instalado. Sem substituir o APK de produção, abriu `pro.sketchware.activities.about.AboutActivity`: o banner real de teste foi carregado e exibido, com callbacks `unity_banner_loaded` e `unity_banner_impression`. A captura `app/build/unity-diagnostics/about-banner-test.png` mostra o banner; o contêiner mediu 1220x150 px e a WebView, 960x150 px (320x50 dp), ambos visíveis. O usuário também confirmou anúncios de teste na tela de edição e em outras telas.
- Encerramento: instrumento auxiliar desinstalado; cadastro temporário do aparelho removido do painel (total de dispositivos de teste retornou a zero); modo geral do projeto não foi alterado; aplicativo reaberto normalmente. Os registros confirmaram novamente inicialização em produção e resposta de ausência de inventário. Nenhuma alteração visual experimental do teste foi aplicada aos fontes ou persistida no aplicativo.

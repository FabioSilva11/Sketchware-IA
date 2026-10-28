# Migração dos anúncios do Sketchware IA para Unity Ads

**Objetivo:** substituir a monetização do próprio aplicativo usando Game ID `800408466` e banner `BP_Banner_Android`.

**Escopo:** manter o suporte AdMob e Unity Ads dos projetos gerados. Não alterar os IDs dos projetos dos usuários. Não criar novos momentos de exibição de anúncios.

**Decisões:** usar Unity Ads 4.21.0, já usado pelas bibliotecas internas; usar a API atual verificada no SDK. Centralizar também os placements `BP_Interstitial_Android` e `BP_Rewarded_Android`, sem novos gatilhos de tela cheia, pois o aplicativo só usava banners e nativos. Remover os nativos do chat e das bibliotecas, que não têm equivalente direto entre os formatos fornecidos. Solicitar apenas anúncios contextuais, sem presumir consentimento para personalização; remover o fluxo Google UMP e o App ID AdMob do aplicativo.

## Execução

- [x] Testar o intervalo progressivo entre tentativas e a recuperação após sucesso.
- [x] Migrar AdManager, dependência e todos os banners próprios para Unity Ads; liberar anúncios ao destruir a tela ou sua view e ignorar callbacks antigos.
- [x] Remover carregadores e espaços nativos; preservar a indexação das mensagens e bibliotecas.
- [x] Compilar o gerenciador com os SDKs reais, executar testes isolados e conferir os fontes do manifesto e dependências.
- [ ] Montar o APK completo e testar a entrega em dispositivo.

## Verificação

Verificar inicialização assíncrona, falhas sem loop de solicitações, views desanexadas durante carregamento, contêineres ainda sem medidas, centralização do banner e modo de teste apenas em debug. Executar os testes do aplicativo e montar o APK debug. A entrega real depende da validação do Game ID/placements no painel Unity e de teste em dispositivo com rede.

## Resultado da validação

- Os 15 pontos de banner usam o placement central, sem IDs próprios AdMob remanescentes.
- `AdManager` e `AdRequestBackoff` compilados com `javac --release 17` contra Android 37.0 e Unity Ads 4.21.0 reais.
- Oito testes JUnit passaram: quatro para o intervalo progressivo, limite, recuperação e tempo restante; quatro para a ordenação das mensagens do chat, incluindo conversa longa sem linhas nativas.
- Revisão independente encontrou uma falha de recuperação após erro na inicialização. Corrigida: tentativas agendadas com intervalo progressivo, canceladas quando não há banners em telas ativas. O carregamento de banner também se recupera dessa forma.
- `git diff --check` passou. Inspeção do AAR confirmou o inicializador AndroidX Startup do Unity e a compatibilidade da API usada.
- A execução completa `:app:testDebugUnitTest :app:assembleDebug` não terminou: a JVM falhou por falta de memória nativa na etapa `mergeDebugResources`, inclusive com um único worker e heap de 768 MB. Não foi produzido um APK novo nem confirmada a exibição em dispositivo. O APK antigo em `app/build/outputs` não representa esta migração.

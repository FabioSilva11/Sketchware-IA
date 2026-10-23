# Migrar a monetização do Sketchware IA para Unity Ads

## Objetivo
Substituir os anúncios do próprio Sketchware IA por Unity Ads, usando os IDs cadastrados na conta do usuário. A versão anterior está preservada em origin/main, commit c7f4ea78efe69439de3783499fe07a1cf3168a69.

## Abordagens
Foram propostas a integração direta com banners nas telas e no chat, ou banners apenas nas telas, retirando anúncios do chat. O usuário escolheu banners Unity também dentro do chat.

## Escopo e comportamento
- Cadastrar Sketchware IA para Android e público geral, conforme informado pelo usuário.
- Usar SDK Unity Ads diretamente, com IDs reais do painel e anúncios de teste em builds de desenvolvimento.
- Substituir banners atuais e o anúncio nativo do chat por banners Unity, preservando os locais e a frequência existentes.
- Tratar inicialização assíncrona, falhas de carregamento, reciclagem do chat e encerramento das telas sem duplicar anúncios ou reter Activities.
- Adaptar os controles de privacidade ao SDK escolhido, sem declarar consentimento em nome do usuário.
- Preservar os componentes AdMob e Unity oferecidos pelo editor para os projetos criados pelos usuários; a mudança é na monetização do próprio aplicativo.

## Verificação
Compilar o aplicativo e executar verificações relevantes de inicialização e ciclo de vida. A entrega distinguirá compilação verificada de exibição real em dispositivo, caso esta não possa ser testada.

## Decisões de implementação
Centralizar IDs e carregamento no gerenciador de anúncios existente. Não introduzir anúncios de tela cheia nem recompensas. O cadastro é inicialmente sem URL de loja, pois não há uma URL confirmada para este aplicativo.

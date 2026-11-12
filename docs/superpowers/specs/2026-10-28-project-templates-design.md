# Blocos Unity Ads e projetos modelo

## Requisitos

O projeto de teste 602 deve demonstrar a integração por blocos próprios, sem exigir Java em `add source directly`. Substituir a loja de projetos por uma aba Templates: tocar em um exemplo cria uma cópia independente, editável no editor normal.

## Decisões de implementação

- O 602 contém dois trechos Java: recriação do componente intersticial com placement digitado e teste da API antiga. Acrescentar blocos de alteração do placement para intersticial/recompensado e usar os blocos existentes para carregar, exibir e consultar estado. Remover o teste da API antiga do exemplo. Preservar uma cópia dos arquivos originais antes de editar o projeto no dispositivo.
- Templates serão exemplos incluídos no APK, disponíveis sem rede, no formato `.swb` já restaurado pelo Sketchware. Cada criação recebe um novo ID e abre uma cópia; nunca modifica o arquivo modelo ou outro projeto.
- Coleção inicial proposta na ausência de outra preferência: Unity Ads, calculadora e lista de tarefas. Toda lógica dos exemplos será representada por blocos visuais.
- Remover a implementação da loja P2P, telas de perfil/publicação, inicialização de rede, dependências nativas exclusivas da loja e seus testes/recursos exclusivos. A aba reutiliza a posição de navegação da loja e passa a se chamar Templates.
- Manter o workflow original do GitHub. A correção Unity Ads anterior já está na main e seu build é acompanhado separadamente.

## Abordagens consideradas

Exemplos incluídos no APK dispensam backend e dão um catálogo previsível para iniciantes. Um catálogo remoto exigiria hospedagem e distribuição ainda não solicitadas. Gerar Java pronto impediria a edição por blocos; por isso os exemplos usam dados reais de projeto e restauração existente.

## Validação

Testar mudança de placement durante carregamento e descarte de callbacks antigos; validar os arquivos dos três exemplos, suas conexões de blocos e ausência de addSourceDirectly. Conferir IDs distintos em criações repetidas, edição das cópias e ausência da inicialização da antiga loja. Compilar e acompanhar o CI.

# Testes pendentes no celular: chat Axion, manifesto e gerenciadores

Correções enviadas sem teste no aparelho (o celular estava fora da rede). Os testes unitários passam no PC.
Marque cada item ao testar. Se algo falhar, anote o passo e tire um print.

## Preparação

1. Instale o APK sobre o atual (mesma chave, os dados ficam):
   ```
   adb connect 192.168.1.231:5555
   adb install -r "%USERPROFILE%\Downloads\Sketchware-IA-9.0-chat-axion.apk"
   ```
2. Modelo de teste local, que não gasta tokens: no PC, rode o servidor e libere a porta para o celular.
   ```
   python scripts/chat_mock_llm.py
   adb reverse tcp:8090 tcp:8090
   ```
   No app: aba **Chat** > escolha um projeto nativo > drawer > engrenagem > **OpenAI-Compatible**. Use qualquer chave
   (por exemplo `teste`), a URL `http://127.0.0.1:8090/v1` e ative. Depois escolha o modelo `mock-agent`.
   Cada palavra-chave abaixo dispara um cenário. A resposta final cita o que o app entregou ao modelo, e tudo fica
   em `scripts/chat_mock_llm.log`. Também dá para usar um provedor real com a sua chave: basta pedir as mesmas
   coisas em texto livre.
3. Para comparar as pastas de um projeto antes e depois de uma ação:
   ```
   adb push scripts/sketchware_project_snapshot.sh /data/local/tmp/snap.sh
   adb shell sh /data/local/tmp/snap.sh 605 > antes.txt
   ```

## A. Interface do chat

- [ ] **A1 Ícones dos provedores.** Em *Choose a provider*, cada provedor mostra o ícone da marca. OpenAI-Compatible,
      LiteLLM e os personalizados mostram o ícone genérico (antes aparecia um quadrado roxo).
- [ ] **A2 Engrenagem do drawer.** Ela abre as configurações de IA (antes não fazia nada).
- [ ] **A2b Skills.** O novo ícone ao lado da engrenagem abre a tela de Skills. Antes ela existia, mas nada a
      abria. Crie uma skill, volte ao chat e confira que ela é aplicada.
- [ ] **A3 Aprovação.** Envie `editar`. Deve aparecer **um** balão por ferramenta. Quando o app pedir aprovação,
      os botões somem depois de Aprovar ou Negar, e o balão mostra o resultado. Role a lista, saia e
      volte ao chat: nada de balões duplicados nem de botão "Aprovar" sobrando.
- [ ] **A4 Anúncio na lista.** Depois de várias mensagens, o anúncio nativo só aparece antes de uma mensagem sua,
      nunca entre a ferramenta e a resposta. Role para cima e para baixo e reabra o chat: a ordem não muda.
- [ ] **A5 Compilar pelo chat.** O botão de compilar no cabeçalho abre a página de log, mostra o progresso e gera
      o APK.
- [ ] **A6 Sem shell.** Pergunte "rode um comando no terminal". O modelo não tem ferramenta de shell, e o app não
      mostra nenhuma opção de terminal.
- [ ] **A7 Recusa aparece como erro.** Nos cenários recusados da seção B (`fora`, `apagar`, `gerado`, `recurso`,
      `mover`, `aberto`), o balão da ferramenta fica com **erro**. Antes ele aparecia como "Concluído" mesmo sem
      ter mudado nada.
- [ ] **A8 Escrita sem espera.** `editar` responde na hora. Antes cada escrita esperava 2 s por uma checagem de
      lint que não existia, e o modelo era informado de "No lint errors found".

## B. Arquivos do projeto nativo (criptografados)

Envie cada palavra-chave no chat de um projeto nativo.

| Palavra | Esperado |
|---|---|
| `leitura`, `logica`, `projeto` | O conteúdo vem legível (linhas `@...` e JSON), não binário |
| `busca` | Encontra texto dentro dos arquivos criptografados |
| `lista`, `arvore` | Mostra só as pastas deste projeto |
| `fora` | É recusado: outro projeto não existe para o chat |
| `absoluto` | O caminho `/storage/emulated/0/.sketchware/data/<id>/library` funciona |
| `criar` | Cria `data/<id>/files/chat_test/Nota.java` em texto puro |
| `editar` e depois `desfazer` | O tema muda e depois volta. O projeto continua abrindo normalmente no Sketchware, ou seja, continua criptografado |
| `apagar` | É recusado com "Sketchware needs this file" |
| `plano` | A aba de plano mostra as duas etapas |
| `gerado` (novo) | A listagem de `mysc/<id>` **não** mostra `bin/` nem `gen/`. A escrita em `mysc/<id>/...` é recusada, e a resposta final do mock mostra a mensagem apontando para `data/<id>/view`, `logic`, `file` ou `files/java` (antes o modelo recebia só "Cannot write to file") |
| `recurso` (novo) | Lista `resources/images/<id>`, e a escrita ali é recusada (imagens só entram pelos gerenciadores) |
| `copiar` (novo) | Cria `data/<id>/files/chat_test/view_copia.txt` com o **texto** do view, não com bytes criptografados |
| `mover` (novo, depois de `copiar`) | É recusado: mover um arquivo comum por cima de `data/<id>/view` quebraria o projeto. O projeto continua abrindo |
| `aberto` (novo) | Veja o roteiro abaixo |

**Roteiro do `aberto`:**

1. Abra o projeto no editor e mude algo sem salvar.
2. Feche o Sketchware pelos recentes, deslizando o app.
3. Confira que a pasta `bak/<id>` existe: `adb shell ls /storage/emulated/0/.sketchware/bak`.
4. Abra o Sketchware > aba Chat > o mesmo projeto e envie `aberto`.
5. Esperado: a edição é recusada, e a resposta mostra o pedido de "Save & exit" primeiro.
6. Depois, abra o projeto, recupere ou descarte e use **Salvar e sair**. Envie `editar`: agora funciona.

## C. Manifesto editado à mão (`Undefined Prefix: tools`)

- [ ] **C1 Projeto nativo.**
  1. No gerenciador do AndroidManifest do projeto, use o manifesto personalizado (editar personalizado) e adicione
     um atributo `tools:` **sem** declarar `xmlns:tools`, por
     exemplo `tools:replace="android:theme"` na `<application>` ou
     `<uses-permission android:name="android.permission.INTERNET" tools:node="remove"/>`.
  2. Compile. Esperado: compila sem "Failed to merge library manifests ... Undefined Prefix: tools".
- [ ] **C2 Projeto Android Studio.** Faça o mesmo em `app/src/main/AndroidManifest.xml` e compile. Esperado: OK.
- [ ] **C3 Prefixo desconhecido** (por exemplo `foo:bar="x"`) no manifesto personalizado. Esperado: o cartão de
      status do gerenciador e o botão Validar dizem que `foo:` não foi declarado, em vez do erro do parser.
      Com `tools:`, `app:` ou `dist:`, o manifesto continua válido, porque esses prefixos são declarados
      automaticamente na compilação.

## D. Gerenciadores e projetos

- [ ] **D1 Nome da fonte.** Em Gerenciador de fontes > adicionar, escolha `Fonte Teste.ttf`. O nome sugerido deve
      ser `fonte_teste` (antes saía `onte_teste.`). Um arquivo `2024.otf` deve sugerir `font_2024`.
- [ ] **D2 Botão acima do banner.** Nos gerenciadores de Som, Fonte e Lottie, o botão "+" e a barra de
      importar/apagar ficam **acima** do banner. Confira também depois que o banner carrega (a altura muda) e ao
      girar a tela.
- [ ] **D3 ID de projeto reaproveitado.** Use projetos de teste.
  1. Crie o projeto "Teste ID" e anote o id (`adb shell ls /storage/emulated/0/.sketchware/mysc/list`).
  2. Apague, por um gerenciador de arquivos, **só** `mysc/list/<id>`, deixando `data/<id>` e `mysc/<id>`.
  3. Crie outro projeto. Esperado: ele recebe um id novo e não herda o código gerado nem os arquivos de build do
     antigo. Antes, ele reaproveitava o id e herdava `mysc/<id>`.

## E. O que o experimento de pastas mostrou (referência)

Projeto nativo novo > imagens, som e fonte adicionados pelos gerenciadores > Salvar > Compilar > Salvar e sair:

- `data/<id>/{file,view,logic,library,resource}`: a fonte do projeto, criptografada. `resource` só é escrito ao
  **Salvar e sair**. Enquanto o editor está aberto, o trabalho fica em `bak/<id>`.
- `resources/{images,sounds,fonts}/<id>`: os arquivos binários adicionados pelos gerenciadores.
- `mysc/<id>`: **gerado a cada build**. Contém o Java convertido de view/logic, cópias dos recursos
  (`res/drawable-xhdpi`, `res/raw`, `assets/fonts`), `bin/` e `gen/` (`R.java` com cerca de 2,6 MB cada).
  Antes o chat tratava `mysc/<id>/app/src/main/java` como editável. Agora tudo em `mysc/<id>` é só leitura, e
  `bin/`, `gen/` e `build/` ficam fora das listagens e das buscas.
- A compilação cria arquivos de configuração em `data/<id>` (`project_config`, `proguard`, `stringfog`,
  `permission`), em texto puro.

## Arquivos de teste no celular

O projeto 602 ("Teste Pastas") e a pasta `/sdcard/Download/sketchware-teste/` foram criados nos testes. Apague-os
quando quiser. Não apaguei porque são dados no seu aparelho.

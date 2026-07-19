# Plano — Serviço de IA por Assinatura (Mercado Pago)

Objetivo: vender acesso à IA do app como assinatura própria ("SketchIA Pro"), sem o usuário precisar de API key, cobrando via Mercado Pago e liberando o uso **sem abuso**.

---

## 1. Arquitetura obrigatória: backend proxy

**Regra de ouro: nenhuma API key de provedor (OpenAI/Anthropic/Gemini/Groq) pode estar no APK.** APK é descompilável; qualquer chave embutida vaza em horas. Todo o faturamento e controle de uso também precisam ser server-side — checagem de assinatura no cliente é trivialmente burlável.

```
App (Android) ──JWT──▶ Backend (seu servidor) ──chave secreta──▶ Provedor LLM
                          │
                          ├─ valida assinatura (status no banco)
                          ├─ mede tokens e aplica cotas
                          └─ Webhooks ◀── Mercado Pago (pagamentos)
```

Componentes do backend (Node/Go/Python, hospedado em Railway/Fly/VPS ~R$30–80/mês no início):
- **Auth**: login (e-mail+senha ou Google Sign-In) emitindo JWT curto (15 min) + refresh token.
- **Proxy LLM**: endpoint `/v1/chat` compatível com OpenAI (streaming SSE). Vantagem enorme: o app já suporta provedores OpenAI-compatíveis — o serviço vira só um **novo provedor** (`sketchia`) no `VoidPortLlmMessage`/`IaSettingsActivity` apontando para seu endpoint, com o JWT no lugar da API key. Quase zero mudança no cliente.
- **Medidor de uso**: registra tokens de entrada/saída por usuário (o campo `usage` das respostas dos provedores) e bloqueia ao estourar a cota.
- **Banco**: Postgres com `users`, `subscriptions`, `usage_daily`, `payments`.

## 2. Cobrança com Mercado Pago

Usar a **API de Assinaturas (`/preapproval`)**: uma assinatura une um plano a um cliente com meio de pagamento salvo, gerando faturas automáticas ([docs](https://www.mercadopago.com.br/developers/pt/docs/subscriptions/overview), [criar assinatura](https://www.mercadopago.com.br/developers/pt/reference/subscriptions/_preapproval/post)).

Fluxo:
1. App abre o **checkout do Mercado Pago via navegador/WebView** (link de `init_point` da preapproval). Nunca processe cartão dentro do app.
2. Usuário paga (cartão recorrente; para **Pix recorrente** use o Pix Automático — obrigatório desde 01/2026 para cobranças recorrentes via Pix).
3. Mercado Pago chama seu **webhook** (`/webhooks/mp`). Valide o header `x-signature` com a chave secreta ([docs webhooks](https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks)) — sem isso qualquer um forja um "pagamento aprovado".
4. Webhook `authorized/approved` → ativa `subscriptions.status=active` no banco. `cancelled/paused/payment_failed` → desativa (com tolerância de 2–3 dias para falha de cartão).
5. O app consulta `/me/subscription` para exibir o status; a **decisão de liberar** é sempre do backend a cada request de chat.

Importante: como o app não é distribuído pela Play Store (targetSdk 28, distribuição própria), você **não é obrigado** ao Google Play Billing — Mercado Pago é permitido. Se um dia for para a Play Store, isso muda.

## 3. Planos sugeridos

| Plano | Preço (ref.) | Cota | Modelo |
|---|---|---|---|
| Grátis | R$0 | 30 mensagens/dia, modelo barato (ex. Llama/Haiku) | isca de conversão |
| Pro | R$19,90/mês | ~2M tokens/mês, modelos médios | principal |
| Max | R$49,90/mês | ~8M tokens/mês + modelos top (Sonnet/GPT-4o) | power users |

Regra de margem: preço ≥ 2,5× o custo estimado de tokens do plano (a compactação de contexto e o prompt caching já implementados reduzem muito o custo por turno). Comece com preços altos e cotas conservadoras; é fácil aumentar cota, impossível reduzir sem revolta.

## 4. Anti-abuso (as 7 camadas)

1. **Cota dura server-side** por usuário (tokens/dia e tokens/mês), com resposta 429 + mensagem clara no app. Nunca confie em contagem local.
2. **Rate limit por usuário e por IP**: ex. 10 req/min, 3 streams simultâneos. Corta scripts e compartilhamento de conta.
3. **1 assinatura = poucos dispositivos**: registre `device_id` no login; máximo 2–3 dispositivos ativos; novo device desloga o mais antigo.
4. **Play Integrity / SafetyNet opcional** no login para dificultar emuladores/farms (não bloqueie totalmente — só marque para revisão).
5. **Limite de tamanho de request**: máx. ~150k chars de contexto por chamada; rejeite payloads absurdos (alguém usando seu proxy como API genérica barata).
6. **Verificação de e-mail + captcha no cadastro** para frear criação em massa de contas grátis; cota grátis por device_id além de por conta.
7. **Telemetria de anomalia**: alerta quando um usuário consome >3× a média diária; suspensão automática temporária acima de X, com revisão manual. Logue `user_id → tokens` por request para auditoria.

Anti-fraude de pagamento: só ative pelo webhook validado (nunca pelo retorno do checkout no app); trate estorno/chargeback (webhook `charged_back`) desativando na hora.

## 5. Mudanças no projeto (cliente)

Pequenas — o grosso é backend:
1. Novo provedor fixo `sketchia` (OpenAI-compatível) em `VoidPortLlmMessage`/`AiChatSettingsHelper`, baseUrl do seu backend, "API key" = JWT do login.
2. Tela de conta em `IaSettingsActivity`: login, plano atual, uso do mês (barra de progresso), botão "Assinar" que abre o `init_point` do MP no navegador.
3. Tratamento do 429 de cota: mensagem amigável + botão de upgrade (em vez do retry automático — adicionar exceção no retry para o provedor `sketchia`).
4. Refresh de JWT transparente no `AiProviderService` (401 → renovar token → repetir 1×).

## 6. Roteiro de execução

1. **Semana 1–2**: backend mínimo (auth + proxy OpenAI-compatível + medição de tokens) apontando para 1 provedor barato; provedor `sketchia` no app; teste interno.
2. **Semana 3**: integração Mercado Pago sandbox (preapproval + webhook assinado + tabela subscriptions); tela de conta no app.
3. **Semana 4**: cotas, rate limit, limite de devices, tratamento de 429/401 no app; testes de carga.
4. **Lançamento beta fechado** (20–50 usuários, plano Pro com desconto vitalício de fundador) → medir custo real de tokens/usuário → ajustar preços → lançamento.
5. Pós-lançamento: Pix Automático, painel admin de uso/abuso, cupons.

## Riscos principais

Custo de token descontrolado (mitigado por cota dura + caching), chargeback (webhook + desativação imediata), vazamento de JWT (expiração curta + binding por device), e dependência de 1 provedor LLM (o backend proxy permite trocar de provedor sem tocar no app).

---

Sources: [Assinaturas — visão geral](https://www.mercadopago.com.br/developers/pt/docs/subscriptions/overview) · [POST /preapproval](https://www.mercadopago.com.br/developers/pt/reference/subscriptions/_preapproval/post) · [Webhooks e validação x-signature](https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks) · [Pix / Checkout API](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/payment-integration/pix)

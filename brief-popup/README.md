# Brief Pop-up

Substitui o heads-up do AOSP por uma cápsula estreita no topo, estilo *pop-up breve* da One UI.

## Por que este app existe

Comprovado por `dumpsys notification --noredact` no aparelho alvo (Motorola, Android 16):

| | tag | flags | template | channel | groupAlertBehavior |
|---|---|---|---|---|---|
| summary | `null` | `SHOW_LIGHTS\|GROUP_SUMMARY` | InboxStyle | `individual_chat_defaults_7` | **1** |
| filha | `<hash>` | `ONLY_ALERT_ONCE` | MessagingStyle | `individual_chat_defaults_7` | **1** |

- `groupAlertBehavior = 1` = `GROUP_ALERT_SUMMARY`: **quem alerta é a summary**, e a summary é InboxStyle
  (`android.text = "3 mensagens de 3 conversas"`). Daí o banner gigante.
- Summary e filhas usam **o mesmo canal** → não dá para separar rebaixando canal.
- As filhas ainda levam `ONLY_ALERT_ONCE`.

Ou seja: **não existe solução por configuração**. Só interceptando e redesenhando.

## Dependências operacionais

1. **Pop-up Control** (`com.jamworks.disablenotificationpopups`) precisa continuar ativo.
   Ele rebaixa os canais de importância 4 → 3 (`mUserLockedFields=4`), matando o heads-up nativo.
   Sem ele, você veria os dois pop-ups.
2. Suba `voip_notification_8` (WhatsApp) de volta para importância **4**.
   O Pop-up Control derrubou também as **chamadas** — com a tela desbloqueada você pode
   não ver ligação entrando. Isso é independente deste app.

## Permissões

- `BIND_NOTIFICATION_LISTENER_SERVICE` (Configurações → Acesso a notificações)
- `SYSTEM_ALERT_WINDOW` (Sobrepor a outros apps)

Ou via ADB / Shizuku:

```
adb shell cmd notification allow_listener com.sla.briefpopup/com.sla.briefpopup.BriefListener
adb shell appops set com.sla.briefpopup SYSTEM_ALERT_WINDOW allow
```

## Como funciona

`BriefListener` (é o próprio `Service`, não precisa de foreground service):

1. Tela apagada → ignora.
2. `flags & FLAG_GROUP_SUMMARY` → descarta. Mata o banner gigante.
3. Chave da conversa = `shortcutId` (ex.: `156538723369061@lid`), não o tag hasheado.
4. `MessagingStyle.extractMessagingStyleFromNotification()` → pega a **última** mensagem
   (remetente, texto, timestamp). Não usa `EXTRA_TEXT`, que vem como "Novas mensagens: 1".
5. **Dedupe**: se `timestamp <= último exibido para essa conversa`, ignora.
   Crítico — o WhatsApp re-posta *todas* as filhas + a summary a cada mensagem nova
   (4 callbacks para 1 mensagem, confirmado no dump).

`BriefOverlay` desenha a cápsula, some em 5 s, toque abre a conversa, swipe pra cima dispensa.

## Trade-off que você vai pagar

Enquanto o overlay estiver na tela, apps que usam `filterTouchesWhenObscured` (bancos, e as
próprias caixas de permissão do Android) **recusam toques**. São ~5 s por notificação.
Se incomodar: adicione `FLAG_NOT_TOUCHABLE` nos params e perca o toque-para-abrir.

## Próximos passos óbvios

- **Responder do overlay**: `Item.replyAction` já carrega a `Action` com `RemoteInput`
  (as filhas trazem `actions=3`). Falta só o campo de texto e o `send()`.
- Respeitar DND: checar `NotificationManager.getCurrentInterruptionFilter()`.
- Allowlist por app em `BriefListener.allowlist`.

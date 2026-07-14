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

## Toque para abrir a conversa (BAL)

Desde o Android 14+, `contentIntent.send()` chamado por um `NotificationListenerService`
terceiro é bloqueado por padrão pelo Background Activity Launch (BAL) — confirmado no
aparelho via `logcat -s ActivityTaskManager` com `Background activity launch blocked!
goo.gle/android-bal`, tanto pro WhatsApp (`targetSdk=36`) quanto pro Telegram
(`targetSdk=35`). Não é algo específico de app: é o Android bloqueando qualquer listener
terceiro que tente repassar o `PendingIntent` de conteúdo de outro app.

O log mostrava `balAllowedByPiSender: BSP.ALLOW_FGS` (nosso lado já tem privilégio, por ter
janela overlay visível + processo em foreground service) mas `balAllowedByPiCreator:
BSP.NONE` (o app dono da notificação nunca opta in). A regra é: `ALLOWED` se **ou** o
criador **ou** o remetente tiver privilégio **e** tiver optado in explicitamente. Como o
criador nunca vai optar in por nós, a solução é o remetente (nós) optar in em
`BriefOverlay.open()`:

```kotlin
val options = ActivityOptions.makeBasic()
options.setPendingIntentBackgroundActivityStartMode(
    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
)
intent.send(uiCtx, 0, null, null, null, null, options.toBundle())
```

Exige API 34+ (`Build.VERSION_CODES.UPSIDE_DOWN_CAKE`); abaixo disso cai para `intent.send()`
simples. Testado e confirmado funcionando no aparelho com WhatsApp e WhatsApp Business.

## Próximos passos óbvios

- **Responder do overlay**: `Item.replyAction` já carrega a `Action` com `RemoteInput`
  (as filhas trazem `actions=3`). Falta só o campo de texto e o `send()`. Como essa
  `PendingIntent` normalmente aponta pra um `BroadcastReceiver`/`Service` (não uma
  `Activity`), não deve esbarrar no mesmo bloqueio de BAL — mas só confirma testando.
- Respeitar DND: checar `NotificationManager.getCurrentInterruptionFilter()`.
- Allowlist por app em `BriefListener.allowlist`.

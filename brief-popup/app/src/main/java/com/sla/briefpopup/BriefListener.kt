package com.sla.briefpopup

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Recebe as notificacoes postadas, descarta ruido e entrega ao overlay
 * apenas a mensagem NOVA de cada conversa.
 *
 * Contexto (comprovado por dumpsys no aparelho alvo):
 *   - O WhatsApp posta N filhas (MessagingStyle, tag = JID hasheado) + 1 summary
 *     (InboxStyle, tag = null, flags = GROUP_SUMMARY).
 *   - groupAlertBehavior = 1 (GROUP_ALERT_SUMMARY): quem alerta e' a summary.
 *     Por isso o heads-up nativo mostra "3 mensagens de 3 conversas".
 *   - A cada mensagem nova, o app RE-POSTA todas as filhas + a summary.
 *     => 4 callbacks de onNotificationPosted para 1 mensagem.
 *
 * Logo: filtrar a summary NAO basta. Precisa de dedupe por conversa.
 */
class BriefListener : NotificationListenerService() {

    /** Apps que passam pelo filtro. Vazio = todos. */
    private val allowlist = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "com.google.android.apps.messaging"
    )

    /** chave da conversa -> timestamp da ultima mensagem ja' exibida */
    private val lastShown = HashMap<String, Long>()

    private val overlay by lazy { BriefOverlay(this) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return

        // 0. Nao mostra com a tela apagada (o heads-up nativo tambem nao mostraria).
        val pm = getSystemService(PowerManager::class.java)
        if (pm?.isInteractive != true) return

        // 1. Filtros baratos primeiro.
        if (sbn.packageName == packageName) return
        if (allowlist.isNotEmpty() && sbn.packageName !in allowlist) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return   // <- mata o banner gigante
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (!sbn.isClearable) return

        // 2. Chave estavel da conversa. shortcutId e' melhor que o tag hasheado:
        //    e' o mesmo valor que aparece no dump ("shortcut=156538723369061@lid").
        val convKey = sbn.notification.shortcutId
            ?: sbn.tag
            ?: sbn.key

        // 3. Extrai a ULTIMA mensagem direto de EXTRA_MESSAGES.
        //    Nao usar EXTRA_TEXT: em notificacao agrupada ele vem como
        //    "Novas mensagens: 1". O dump mostra o formato real:
        //    android.messages = Bundle[]{ sender, sender_person, text, time }
        val last = latestMessage(n)

        val timestamp = last?.getLong("time") ?: n.`when`
        val sender = last?.getCharSequence("sender")
            ?: n.extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: return
        val body = last?.getCharSequence("text")
            ?: n.extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: return

        // 4. Dedupe. Repost sem mensagem nova -> ignora.
        //    Isto e' o que transforma 4 callbacks em 1 pop-up.
        val previous = lastShown[convKey]
        if (previous != null && timestamp <= previous) return
        lastShown[convKey] = timestamp

        // 5. Avatar: largeIcon (o dump mostra BITMAP 135x135); cai para o smallIcon.
        val icon = n.getLargeIcon() ?: n.smallIcon

        overlay.show(
            BriefOverlay.Item(
                key = sbn.key,
                title = sender.toString(),
                text = body.toString(),
                icon = icon,
                isGroup = n.extras.getBoolean("android.isGroupConversation", false),
                conversationTitle = n.extras
                    .getCharSequence("android.conversationTitle")?.toString(),
                contentIntent = n.contentIntent,
                replyAction = findReplyAction(n)
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val convKey = sbn.notification?.shortcutId ?: sbn.tag ?: return
        // Conversa lida/dispensada: libera para alertar de novo do zero.
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0) {
            lastShown.remove(convKey)
        }
        overlay.dismissIfKey(sbn.key)
    }

    /**
     * Le android.messages (Bundle[]) e devolve a mensagem mais recente.
     * Chaves confirmadas no dumpsys do aparelho:
     *   Bundle[{ extras=..., sender_person=..., sender=..., text=..., time=... }]
     */
    private fun latestMessage(n: Notification): Bundle? {
        val arr = messagesArray(n.extras) ?: return null
        return arr.filterIsInstance<Bundle>().maxByOrNull { it.getLong("time") }
    }

    /** getParcelableArray(String) e' deprecated na API 33+; usa a variante tipada quando disponivel. */
    @Suppress("DEPRECATION")
    private fun messagesArray(extras: Bundle): Array<out Parcelable>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        } else {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        }

    /** A filha traz actions=3, uma delas com RemoteInput (responder). */
    private fun findReplyAction(n: Notification): Notification.Action? =
        n.actions?.firstOrNull { a ->
            a.remoteInputs?.isNotEmpty() == true
        }

    override fun onDestroy() {
        overlay.destroy()
        super.onDestroy()
    }
}

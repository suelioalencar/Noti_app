package com.sla.briefpopup

import android.app.Notification
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.net.Uri

/** Uma mensagem dentro da conversa (EXTRA_MESSAGES), usada pro banner expandido. */
data class MessageLine(
    val sender: CharSequence?,
    val text: CharSequence?,
    val timeMs: Long,
    val dataUri: Uri?,
    val dataMimeType: String?
)

/** Dados de uma notificacao de conversa, independente do estilo visual usado pra mostra-la. */
data class NotificationItem(
    val key: String,
    val conversationKey: String,
    val title: String,
    val text: String,
    val icon: Icon?,
    val isGroup: Boolean,
    val conversationTitle: String?,
    val contentIntent: PendingIntent?,
    val replyAction: Notification.Action?,
    val markAsReadAction: Notification.Action? = null,
    val messages: List<MessageLine> = emptyList()
)

/** Cada estilo visual (Samsung, iPhone, ...) implementa isso; BriefListener nao sabe qual esta' ativo. */
interface NotificationOverlay {
    fun show(item: NotificationItem)
    fun dismissIfKey(key: String)
    fun destroy()
}

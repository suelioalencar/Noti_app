package com.sla.briefpopup

import android.content.Context

enum class NotificationStyle(val label: String) {
    SAMSUNG("Samsung (capsula One UI)"),
    IPHONE("iPhone (card)")
}

/** Qual estilo visual mostra o pop-up - configuravel na MainActivity. */
object StyleStore {
    private const val PREFS = "brief_popup_prefs"
    private const val KEY_STYLE = "style"

    fun get(ctx: Context): NotificationStyle {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_STYLE, null) ?: return NotificationStyle.SAMSUNG
        return runCatching { NotificationStyle.valueOf(name) }.getOrDefault(NotificationStyle.SAMSUNG)
    }

    fun set(ctx: Context, style: NotificationStyle) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STYLE, style.name)
            .apply()
    }
}

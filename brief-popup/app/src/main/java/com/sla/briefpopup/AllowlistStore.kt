package com.sla.briefpopup

import android.content.Context

/**
 * Quais apps mostram o pop-up. Persistido em SharedPreferences pra ser
 * configuravel pela AppSelectionActivity, em vez de fixo no codigo.
 */
object AllowlistStore {
    private const val PREFS = "brief_popup_prefs"
    private const val KEY_ALLOWLIST = "allowlist"

    /** Mesmo conjunto que era fixo no codigo antes - preserva o comportamento pra quem ja usava o app. */
    private val defaultAllowlist = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "com.google.android.apps.messaging"
    )

    fun get(ctx: Context): Set<String> {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // getStringSet() nao deve ser mutado, e alguns storages devolvem a
        // instancia interna direto - copia defensiva.
        return (prefs.getStringSet(KEY_ALLOWLIST, null) ?: defaultAllowlist).toSet()
    }

    fun isEnabled(ctx: Context, packageName: String): Boolean = packageName in get(ctx)

    fun setEnabled(ctx: Context, packageName: String, enabled: Boolean) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = get(ctx).toMutableSet()
        if (enabled) updated.add(packageName) else updated.remove(packageName)
        prefs.edit().putStringSet(KEY_ALLOWLIST, updated).apply()
    }
}

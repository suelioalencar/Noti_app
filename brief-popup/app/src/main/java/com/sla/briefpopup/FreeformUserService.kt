package com.sla.briefpopup

import android.app.ActivityOptions
import android.app.PendingIntent
import android.graphics.Rect
import android.util.Log

/**
 * O Shizuku sobe essa classe num processo separado (app_process) rodando
 * com uid shell (ou root, se disponivel) - NAO no processo normal do app.
 *
 * E' por isso que ela existe: ActivityOptions.setLaunchWindowingMode e'
 * API oculta (@hide), e o hidden-API enforcement do Android bloqueia
 * reflection sobre ela no processo normal do app (confirmado por logcat:
 * NoSuchMethodException). Uid shell/root e' isento desse enforcement -
 * e' exatamente por isso que "adb shell am start --windowingMode 5"
 * funciona e a mesma chamada no processo do app nao funciona.
 *
 * Reusa o PendingIntent original (Parcelable, atravessa processo mantendo
 * o deep-link pra conversa exata) em vez de montar um Intent novo com
 * package/classe fixos - assim a conversa certa continua abrindo.
 */
class FreeformUserService : IFreeformService.Stub() {

    override fun launchFreeform(intent: PendingIntent, bounds: Rect): Boolean {
        return try {
            val options = ActivityOptions.makeBasic()
            options.launchBounds = bounds

            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Integer.TYPE)
                .invoke(options, 5)   // WINDOWING_MODE_FREEFORM

            intent.send(null, 0, null, null, null, null, options.toBundle())
            true
        } catch (e: Throwable) {
            Log.w("FreeformUserService", "launchFreeform() falhou mesmo com uid shell", e)
            false
        }
    }
}

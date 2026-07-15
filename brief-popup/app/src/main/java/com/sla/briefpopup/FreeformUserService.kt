package com.sla.briefpopup

import android.app.ActivityOptions
import android.app.PendingIntent
import android.graphics.Rect
import android.os.Build
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

            // Sem isso o launch e' aceito (intent.send() nao lanca) mas fica
            // "invisible launch ... result code=0" - BAL_BLOCK, confirmado
            // por logcat (balRequireOptInByPendingIntentCreator=true). Mesmo
            // opt-in que ja' faz open() funcionar hoje, so' que aqui do lado
            // do processo shell/root em vez do processo normal do app.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }

            // NOTA: um fillIn Intent (3o param) nao-nulo aqui exige um Context
            // real - com Context nulo da' NullPointerException dentro de
            // PendingIntent.sendAndReturnResult (confirmado por logcat).
            // FreeformUserService nao tem Context disponivel, entao por ora
            // fica sem fillIn/NEW_TASK/MULTIPLE_TASK (isolamento de task fica
            // pra depois, quando resolver o problema mais critico: fazer a
            // janela aparecer de verdade).
            intent.send(null, 0, null, null, null, null, options.toBundle())

            // O double-send funciona ATE' AS VEZES (confirmado por logcat: em
            // parte das tentativas o MotoFreeForm reage e a janela aparece
            // antes ate' do open() de seguranca disparar) - mas e' inconsistente:
            // testes mostraram tentativas onde nem o FreeformTaskListener nem o
            // MotoFreeForm reagem, como se o primeiro send() nao tivesse dado
            // tempo da task assentar antes do segundo. 150ms parece curto
            // demais as vezes; aumentando a folga.
            Thread.sleep(300)
            intent.send(null, 0, null, null, null, null, options.toBundle())
            true
        } catch (e: Throwable) {
            Log.w("FreeformUserService", "launchFreeform() falhou mesmo com uid shell", e)
            false
        }
    }
}

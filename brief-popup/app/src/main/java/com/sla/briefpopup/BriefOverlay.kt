package com.sla.briefpopup

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Outline
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.util.Log
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView

/**
 * Capsula fina no topo, estilo "pop-up breve" da One UI.
 * - some sozinha depois de DURATION_MS
 * - notificacao nova durante a exibicao: troca o conteudo e reinicia o timer
 * - toque: abre a conversa
 * - swipe para cima: dispensa
 */
class BriefOverlay(private val ctx: Context) {

    data class Item(
        val key: String,
        val title: String,
        val text: String,
        val icon: Icon?,
        val isGroup: Boolean,
        val conversationTitle: String?,
        val contentIntent: PendingIntent?,
        val replyAction: Notification.Action?
    )

    companion object {
        private const val DURATION_MS = 5000L
        private const val SWIPE_THRESHOLD_DP = 24
    }

    /**
     * O Context de um Service NAO e' um UI context. Pedir WindowManager dele
     * gera warning e resolve recursos com a configuracao errada no Android 12+.
     * O correto e' um window context amarrado ao display + tipo de janela.
     */
    private val uiCtx: Context = ctx
        .createDisplayContext(
            ctx.getSystemService(DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY)
        )
        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)

    private val wm = uiCtx.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private var view: View? = null
    private var current: Item? = null
    private val hideRunnable = Runnable { dismiss() }

    fun show(item: Item) = main.post {
        val v = view ?: inflate().also {
            wm.addView(it, params())
            it.alpha = 0f
            it.translationY = -dp(16).toFloat()
            it.animate().alpha(1f).translationY(0f).setDuration(180).start()
        }
        view = v
        current = item
        bind(v, item)

        main.removeCallbacks(hideRunnable)
        main.postDelayed(hideRunnable, DURATION_MS)
    }

    fun dismissIfKey(key: String) = main.post {
        if (current?.key == key) dismiss()
    }

    fun destroy() = main.post { dismiss() }

    // ---------------------------------------------------------------- interno

    private fun inflate(): View {
        val v = LayoutInflater.from(uiCtx).inflate(R.layout.brief_popup, null)

        v.findViewById<ImageView>(R.id.avatar).apply {
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }

        var downY = 0f
        var downX = 0f
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downY = e.rawY; downX = e.rawX
                    main.removeCallbacks(hideRunnable)   // pausa o timer enquanto toca
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dy = e.rawY - downY
                    val dx = Math.abs(e.rawX - downX)
                    when {
                        dy < -dp(SWIPE_THRESHOLD_DP) -> dismiss()          // swipe up
                        dx < dp(12) && Math.abs(dy) < dp(12) -> open()     // tap
                        else -> main.postDelayed(hideRunnable, DURATION_MS)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    main.postDelayed(hideRunnable, DURATION_MS); true
                }
                else -> true
            }
        }
        return v
    }

    private fun bind(v: View, item: Item) {
        val title = if (item.isGroup && item.conversationTitle != null)
            "${item.title} \u00B7 ${item.conversationTitle}"   // "Fulano · Grupo"
        else item.title

        v.findViewById<TextView>(R.id.title).text = title
        v.findViewById<TextView>(R.id.text).text = item.text
        v.findViewById<ImageView>(R.id.avatar).apply {
            item.icon?.let { setImageIcon(it) }
        }
    }

    private fun open() {
        val intent = current?.contentIntent
        dismiss()
        if (intent == null) {
            Log.w("BriefOverlay", "open(): contentIntent e' null, nada para abrir")
            return
        }
        try {
            intent.send()
            Log.d("BriefOverlay", "open(): send() ok, creator=${intent.creatorPackage}")
        } catch (e: PendingIntent.CanceledException) {
            Log.w("BriefOverlay", "open(): PendingIntent cancelado", e)
        }
    }

    private fun dismiss() {
        main.removeCallbacks(hideRunnable)
        val v = view ?: return
        v.animate().alpha(0f).translationY(-dp(16).toFloat()).setDuration(140)
            .withEndAction {
                runCatching { wm.removeView(v) }
                view = null
                current = null
            }.start()
    }

    private fun params(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarHeight() + dp(6)
        }

    private fun statusBarHeight(): Int {
        val id = uiCtx.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) uiCtx.resources.getDimensionPixelSize(id) else dp(28)
    }

    private fun dp(v: Int): Int =
        (v * uiCtx.resources.displayMetrics.density).toInt()
}

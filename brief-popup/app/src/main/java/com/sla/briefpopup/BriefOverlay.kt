package com.sla.briefpopup

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Outline
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.util.Log
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Capsula fina no topo, estilo "pop-up breve" da One UI.
 * - some sozinha depois de DURATION_MS
 * - notificacao nova durante a exibicao: troca o conteudo e reinicia o timer
 * - toque: abre a conversa
 * - swipe para cima: dispensa
 * - arrasta pra baixo: expande
 */
class BriefOverlay(private val ctx: Context) {

    data class MessageLine(
        val sender: CharSequence?,
        val text: CharSequence?,
        val timeMs: Long,
        val dataUri: Uri?,
        val dataMimeType: String?
    )

    data class Item(
        val key: String,
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

    companion object {
        private const val DURATION_MS = 5000L
        private const val SWIPE_THRESHOLD_DP = 24
        private const val EXPAND_DRAG_THRESHOLD_DP = 24
        private const val EXPANDED_SIDE_GUTTER_DP = 8
        private const val THUMB_SIZE_DP = 40
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
    private var expanded = false
    private val hideRunnable = Runnable { dismiss() }

    fun show(item: Item) = main.post {
        val v = view ?: inflate().also {
            wm.addView(it, params())
            it.alpha = 0f
            it.translationY = -dp(16).toFloat()
            it.animate().alpha(1f).translationY(0f).setDuration(180).start()
        }
        view = v
        if (expanded) setExpanded(v, false)   // conteudo novo: volta pra capsula compacta
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
                        dy > dp(EXPAND_DRAG_THRESHOLD_DP) && !expanded ->
                            setExpanded(v, true)                          // arrasta pra baixo: expande
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

        v.findViewById<ImageView>(R.id.expandChevron).setOnClickListener {
            setExpanded(v, !expanded)
        }

        v.findViewById<EditText>(R.id.replyInput).setOnEditorActionListener { tv, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val text = tv.text?.toString().orEmpty()
                if (text.isNotBlank()) {
                    sendReply(text)
                    dismiss()
                }
                true
            } else false
        }

        v.findViewById<TextView>(R.id.markReadButton).setOnClickListener {
            markAsRead()
            dismiss()
        }

        return v
    }

    private fun bind(v: View, item: Item) {
        val title = if (item.isGroup && item.conversationTitle != null)
            "${item.title} · ${item.conversationTitle}"   // "Fulano · Grupo"
        else item.title

        v.findViewById<TextView>(R.id.title).text = title
        v.findViewById<TextView>(R.id.text).text = item.text
        v.findViewById<ImageView>(R.id.avatar).apply {
            item.icon?.let { setImageIcon(it) }
        }
        v.findViewById<ImageView>(R.id.expandChevron).visibility =
            if (item.replyAction != null || item.markAsReadAction != null) View.VISIBLE else View.GONE

        bindMessages(v, item)
    }

    /** Preenche o banner expandido com ate 3 mensagens (bind() roda sempre, e' barato). */
    private fun bindMessages(v: View, item: Item) {
        val messageList = v.findViewById<LinearLayout>(R.id.messageList)
        messageList.removeAllViews()   // view e' reaproveitada entre notificacoes - critico
        for (m in item.messages) {
            val row = LayoutInflater.from(uiCtx)
                .inflate(R.layout.brief_message_row, messageList, false)
            val senderTv = row.findViewById<TextView>(R.id.msgSender)
            val textTv = row.findViewById<TextView>(R.id.msgText)
            val thumb = row.findViewById<ImageView>(R.id.msgThumb)

            val showSender = item.isGroup && m.sender != null && m.sender != item.conversationTitle
            senderTv.visibility = if (showSender) View.VISIBLE else View.GONE
            senderTv.text = m.sender
            textTv.text = m.text ?: ""

            when {
                m.dataUri != null && m.dataMimeType?.startsWith("image/") == true -> {
                    thumb.visibility = View.VISIBLE
                    loadThumbnail(thumb, m.dataUri, item.key)
                }
                m.dataUri != null -> {
                    thumb.visibility = View.GONE
                    textTv.text = "${textTv.text} [anexo]"
                }
                else -> thumb.visibility = View.GONE
            }
            messageList.addView(row)
        }
    }

    /** Thumbnail fora da main thread; descarta resultado se o popup ja mudou de conteudo. */
    private fun loadThumbnail(iv: ImageView, uri: Uri, forKey: String) {
        Thread {
            val bmp = runCatching {
                uiCtx.contentResolver.loadThumbnail(
                    uri, Size(dp(THUMB_SIZE_DP), dp(THUMB_SIZE_DP)), null
                )
            }.getOrNull()
            main.post {
                if (current?.key != forKey) return@post
                if (bmp != null) iv.setImageBitmap(bmp) else iv.visibility = View.GONE
            }
        }.start()
    }

    /** Expande em estilo banner full-width com as ultimas mensagens e resposta, ou volta a' capsula compacta. */
    private fun setExpanded(v: View, expand: Boolean) {
        expanded = expand
        val replyRow = v.findViewById<View>(R.id.replyRow)
        val markReadButton = v.findViewById<TextView>(R.id.markReadButton)
        val editText = v.findViewById<EditText>(R.id.replyInput)
        val avatar = v.findViewById<ImageView>(R.id.avatar)
        val title = v.findViewById<TextView>(R.id.title)
        val text = v.findViewById<TextView>(R.id.text)
        val messageList = v.findViewById<View>(R.id.messageList)
        val imm = uiCtx.getSystemService(InputMethodManager::class.java)

        v.findViewById<ImageView>(R.id.expandChevron).rotation = if (expand) 180f else 0f
        replyRow.visibility = if (expand && current?.replyAction != null) View.VISIBLE else View.GONE
        markReadButton.visibility = if (expand && current?.markAsReadAction != null) View.VISIBLE else View.GONE
        messageList.visibility = if (expand) View.VISIBLE else View.GONE

        val avatarSize = dp(if (expand) 48 else 32)
        avatar.layoutParams = avatar.layoutParams.apply {
            width = avatarSize
            height = avatarSize
        }

        val maxWidth = if (expand)
            wm.currentWindowMetrics.bounds.width() - dp(EXPANDED_SIDE_GUTTER_DP * 2 + 80)
        else dp(240)
        title.maxWidth = maxWidth
        text.maxWidth = maxWidth
        title.textSize = if (expand) 15f else 13f
        text.textSize = if (expand) 15f else 13f
        text.isSingleLine = !expand
        text.maxLines = if (expand) 6 else 1
        text.ellipsize = if (expand) null else android.text.TextUtils.TruncateAt.END

        // Janela precisa virar focavel pra o EditText aceitar teclado (do
        // contrario o toque nela nem chega no IME - janela overlay comum e'
        // FLAG_NOT_FOCUSABLE de proposito, pra nao roubar foco da tela por tras)
        // e virar full-width pro banner expandido; as duas coisas mudam
        // WindowManager.LayoutParams, entao ficam numa unica chamada a updateViewLayout.
        applyExpandedWindowState(v, expand)

        if (expand) {
            main.removeCallbacks(hideRunnable)
            if (current?.replyAction != null) {
                showKeyboardWhenFocused(v, editText, imm)
            }
        } else {
            imm?.hideSoftInputFromWindow(editText.windowToken, 0)
            editText.text?.clear()
            main.removeCallbacks(hideRunnable)
            main.postDelayed(hideRunnable, DURATION_MS)
        }
    }

    /**
     * Alterna foco (pro EditText aceitar teclado) e largura da janela
     * (capsula centralizada <-> banner quase full-width) numa unica
     * chamada a updateViewLayout.
     */
    private fun applyExpandedWindowState(v: View, expand: Boolean) {
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        lp.flags = if (expand) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (expand) {
            val screenW = wm.currentWindowMetrics.bounds.width()
            lp.width = screenW - dp(EXPANDED_SIDE_GUTTER_DP * 2)
            lp.gravity = Gravity.TOP
        } else {
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        runCatching { wm.updateViewLayout(v, lp) }
    }

    /**
     * Chamar requestFocus()/showSoftInput() logo depois de tirar
     * FLAG_NOT_FOCUSABLE nao funciona de forma confiavel: o
     * updateViewLayout() que muda a flag e' assincrono (round-trip ate' o
     * WindowManagerService), entao a janela ainda pode nao ter foco de
     * verdade no momento em que pedimos o teclado - o EditText fica com
     * foco "local" mas o IME nao aparece ate' o usuario tocar nele. Espera
     * o evento real de foco da janela antes de mostrar o teclado.
     */
    private fun showKeyboardWhenFocused(v: View, editText: EditText, imm: InputMethodManager?) {
        if (v.hasWindowFocus()) {
            editText.requestFocus()
            imm?.showSoftInput(editText, InputMethodManager.SHOW_FORCED)
            return
        }
        val vto = v.viewTreeObserver
        vto.addOnWindowFocusChangeListener(object : ViewTreeObserver.OnWindowFocusChangeListener {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (!hasFocus) return
                editText.requestFocus()
                imm?.showSoftInput(editText, InputMethodManager.SHOW_FORCED)
                vto.removeOnWindowFocusChangeListener(this)
            }
        })
    }

    /** Preenche o RemoteInput da action e dispara - normalmente um Broadcast/Service
     * do proprio app (nao uma Activity), entao nao esbarra no bloqueio de BAL. */
    private fun sendReply(text: CharSequence) {
        val action = current?.replyAction ?: return
        val remoteInputs = action.remoteInputs
        if (remoteInputs == null || remoteInputs.isEmpty()) {
            Log.w("BriefOverlay", "sendReply(): action sem RemoteInput")
            return
        }
        val results = Bundle().apply {
            for (ri in remoteInputs) putCharSequence(ri.resultKey, text)
        }
        val fillIn = Intent()
        RemoteInput.addResultsToIntent(remoteInputs, fillIn, results)
        try {
            action.actionIntent.send(uiCtx, 0, fillIn)
        } catch (e: PendingIntent.CanceledException) {
            Log.w("BriefOverlay", "sendReply(): PendingIntent cancelado", e)
        }
    }

    /** Dispara a action de "marcar como lida" da notificacao (sem RemoteInput). */
    private fun markAsRead() {
        val action = current?.markAsReadAction ?: return
        try {
            action.actionIntent.send()
        } catch (e: PendingIntent.CanceledException) {
            Log.w("BriefOverlay", "markAsRead(): PendingIntent cancelado", e)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Opt-in do lado do remetente (nos). Sem isso, o BAL so' considera
                // o lado do criador do PendingIntent (ex: WhatsApp), que nunca
                // opta in - e' exatamente o bloqueio que vimos no dumpsys/logcat.
                val options = ActivityOptions.makeBasic()
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                intent.send(uiCtx, 0, null, null, null, null, options.toBundle())
            } else {
                intent.send()
            }
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

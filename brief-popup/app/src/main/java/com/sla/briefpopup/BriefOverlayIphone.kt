package com.sla.briefpopup

import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Outline
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Card full-width estilo banner de notificacao do iOS - ja' chega "aberto"
 * (sem capsula colapsada), caindo de cima pra baixo com uma leve quicada.
 * - some sozinha depois de DURATION_MS
 * - toque: abre a conversa
 * - swipe para cima: dispensa
 * - chevron/arrasta pra baixo: revela historico + resposta
 */
class BriefOverlayIphone(private val ctx: Context) : NotificationOverlay {

    companion object {
        private const val DURATION_MS = 5000L
        private const val SWIPE_THRESHOLD_DP = 24
        private const val EXPAND_DRAG_THRESHOLD_DP = 24
        private const val SIDE_GUTTER_DP = 8
        private const val THUMB_SIZE_DP = 40
        private const val DROP_START_OFFSET_DP = 300
    }

    private val uiCtx: Context = ctx
        .createDisplayContext(
            ctx.getSystemService(DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY)
        )
        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)

    private val wm = uiCtx.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private var view: View? = null
    private var current: NotificationItem? = null
    private var expanded = false
    private val hideRunnable = Runnable { dismiss() }

    override fun show(item: NotificationItem) {
        main.post {
            val v = view ?: inflate().also {
                wm.addView(it, params())
                it.translationY = -dp(DROP_START_OFFSET_DP).toFloat()
                it.animate()
                    .translationY(0f)
                    .setInterpolator(OvershootInterpolator(1.1f))
                    .setDuration(420)
                    .start()
            }
            view = v

            val sameConversationExpanded = expanded && current?.conversationKey == item.conversationKey
            if (expanded && !sameConversationExpanded) setExpanded(v, false)
            current = item
            bind(v, item)

            if (sameConversationExpanded) {
                v.findViewById<View>(R.id.replyRow).visibility =
                    if (item.replyAction != null) View.VISIBLE else View.GONE
                v.findViewById<View>(R.id.markReadButton).visibility =
                    if (item.markAsReadAction != null) View.VISIBLE else View.GONE
            } else {
                main.removeCallbacks(hideRunnable)
                main.postDelayed(hideRunnable, DURATION_MS)
            }
        }
    }

    override fun dismissIfKey(key: String) {
        main.post { if (current?.key == key) dismiss() }
    }

    override fun destroy() {
        main.post { dismiss() }
    }

    // ---------------------------------------------------------------- interno

    private fun inflate(): View {
        val v = LayoutInflater.from(uiCtx).inflate(R.layout.brief_popup_iphone, null)

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
                    main.removeCallbacks(hideRunnable)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dy = e.rawY - downY
                    val dx = Math.abs(e.rawX - downX)
                    when {
                        dy < -dp(SWIPE_THRESHOLD_DP) -> dismiss()
                        dy > dp(EXPAND_DRAG_THRESHOLD_DP) && !expanded -> setExpanded(v, true)
                        dx < dp(12) && Math.abs(dy) < dp(12) -> open()
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

    private fun bind(v: View, item: NotificationItem) {
        val title = if (item.isGroup && item.conversationTitle != null)
            "${item.title} · ${item.conversationTitle}"
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

    private fun bindMessages(v: View, item: NotificationItem) {
        val messageList = v.findViewById<LinearLayout>(R.id.messageList)
        messageList.removeAllViews()
        for (m in item.messages) {
            val row = LayoutInflater.from(uiCtx)
                .inflate(R.layout.brief_message_row_iphone, messageList, false)
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

    /** Ja' e' full-width desde o inicio - expandir so' revela historico/resposta, nao muda largura. */
    private fun setExpanded(v: View, expand: Boolean) {
        expanded = expand
        val replyRow = v.findViewById<View>(R.id.replyRow)
        val markReadButton = v.findViewById<TextView>(R.id.markReadButton)
        val editText = v.findViewById<EditText>(R.id.replyInput)
        val messageList = v.findViewById<View>(R.id.messageList)
        val imm = uiCtx.getSystemService(InputMethodManager::class.java)

        v.findViewById<ImageView>(R.id.expandChevron).rotation = if (expand) 180f else 0f
        replyRow.visibility = if (expand && current?.replyAction != null) View.VISIBLE else View.GONE
        markReadButton.visibility = if (expand && current?.markAsReadAction != null) View.VISIBLE else View.GONE
        messageList.visibility = if (expand) View.VISIBLE else View.GONE

        applyFocusable(v, expand)

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

    private fun applyFocusable(v: View, focusable: Boolean) {
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        lp.flags = if (focusable) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { wm.updateViewLayout(v, lp) }
    }

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

    private fun sendReply(text: CharSequence) {
        val action = current?.replyAction ?: return
        val remoteInputs = action.remoteInputs
        if (remoteInputs == null || remoteInputs.isEmpty()) {
            Log.w("BriefOverlayIphone", "sendReply(): action sem RemoteInput")
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
            Log.w("BriefOverlayIphone", "sendReply(): PendingIntent cancelado", e)
        }
    }

    private fun markAsRead() {
        val action = current?.markAsReadAction ?: return
        try {
            action.actionIntent.send()
        } catch (e: PendingIntent.CanceledException) {
            Log.w("BriefOverlayIphone", "markAsRead(): PendingIntent cancelado", e)
        }
    }

    private fun open() {
        val intent = current?.contentIntent
        dismiss()
        if (intent == null) {
            Log.w("BriefOverlayIphone", "open(): contentIntent e' null, nada para abrir")
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic()
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                intent.send(uiCtx, 0, null, null, null, null, options.toBundle())
            } else {
                intent.send()
            }
        } catch (e: PendingIntent.CanceledException) {
            Log.w("BriefOverlayIphone", "open(): PendingIntent cancelado", e)
        }
    }

    private fun dismiss() {
        main.removeCallbacks(hideRunnable)
        val v = view ?: return
        v.animate().alpha(0f).translationY(-dp(40).toFloat()).setDuration(180)
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
            gravity = Gravity.TOP
            width = wm.currentWindowMetrics.bounds.width() - dp(SIDE_GUTTER_DP * 2)
            y = statusBarHeight() + dp(6)
        }

    private fun statusBarHeight(): Int {
        val id = uiCtx.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) uiCtx.resources.getDimensionPixelSize(id) else dp(28)
    }

    private fun dp(v: Int): Int =
        (v * uiCtx.resources.displayMetrics.density).toInt()
}

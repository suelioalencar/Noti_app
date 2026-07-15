package com.sla.briefpopup

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Tela unica: leva as permissoes necessarias + a selecao de apps + estilo visual. */
class MainActivity : Activity() {

    private var testOverlay: NotificationOverlay? = null
    private var testOverlayStyle: NotificationStyle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(40), dp(28), dp(28))
        }

        root.addView(TextView(this).apply {
            text = "Brief Pop-up"
            textSize = 24f
            setTextColor(resources.getColor(R.color.text_primary, theme))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "Configure as permissoes e escolha quais apps mostram o pop-up."
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_secondary, theme))
            setPadding(0, dp(8), 0, dp(28))
        })

        root.addView(primaryButton("1. Acesso as notificacoes") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })

        root.addView(primaryButton("2. Sobrepor a outros apps") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        })

        root.addView(primaryButton("3. Escolher apps") {
            startActivity(Intent(this, AppSelectionActivity::class.java))
        })

        root.addView(primaryButton("4. Estilo de notificacao") {
            showStylePicker()
        })

        root.addView(secondaryButton("Testar notificacao") {
            showTestNotification()
        })

        root.addView(secondaryButton("Verificar") {
            val overlay = Settings.canDrawOverlays(this@MainActivity)
            val listener = Settings.Secure.getString(
                contentResolver, "enabled_notification_listeners"
            )?.contains(packageName) == true
            Toast.makeText(
                this@MainActivity,
                "overlay=$overlay  listener=$listener",
                Toast.LENGTH_LONG
            ).show()
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showStylePicker() {
        val styles = NotificationStyle.entries.toTypedArray()
        val current = StyleStore.get(this)
        val labels = styles.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Estilo de notificacao")
            .setSingleChoiceItems(labels, styles.indexOf(current)) { dialog, which ->
                StyleStore.set(this, styles[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** So' preview visual/de animacao - sem contentIntent/reply de verdade, ja' que nao ha' notificacao real. */
    private fun showTestNotification() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Ative a permissao de sobrepor a outros apps primeiro", Toast.LENGTH_LONG).show()
            return
        }
        resolveTestOverlay().show(
            NotificationItem(
                key = "test",
                conversationKey = "test",
                title = "Fulano de Tal",
                text = "Oi! Essa e' uma notificacao de teste - e' assim que o pop-up vai aparecer.",
                icon = Icon.createWithResource(this, android.R.drawable.ic_dialog_email),
                isGroup = false,
                conversationTitle = null,
                contentIntent = null,
                replyAction = null,
                markAsReadAction = null,
                messages = emptyList()
            )
        )
    }

    private fun resolveTestOverlay(): NotificationOverlay {
        val style = StyleStore.get(this)
        if (testOverlay == null || testOverlayStyle != style) {
            testOverlay?.destroy()
            testOverlay = when (style) {
                NotificationStyle.SAMSUNG -> BriefOverlay(this)
                NotificationStyle.IPHONE -> BriefOverlayIphone(this)
            }
            testOverlayStyle = style
        }
        return testOverlay!!
    }

    private fun primaryButton(label: String, action: () -> Unit) =
        styledButton(label, R.drawable.bg_button_primary, R.color.text_primary, action)

    private fun secondaryButton(label: String, action: () -> Unit) =
        styledButton(label, R.drawable.bg_button_secondary, R.color.accent, action)

    private fun styledButton(label: String, bg: Int, textColorRes: Int, action: () -> Unit) =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTextColor(resources.getColor(textColorRes, theme))
            background = resources.getDrawable(bg, theme)
            setPadding(dp(20), dp(16), dp(20), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener { action() }
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

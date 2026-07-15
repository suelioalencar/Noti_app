package com.sla.briefpopup

import android.app.Activity
import android.content.Intent
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

/** Tela unica: leva as permissoes necessarias + a selecao de apps. */
class MainActivity : Activity() {

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

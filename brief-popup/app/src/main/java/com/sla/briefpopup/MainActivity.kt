package com.sla.briefpopup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

/** Tela unica: so' leva as duas permissoes necessarias. */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        root.addView(Button(this).apply {
            text = "1. Acesso as notificacoes"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        })

        root.addView(Button(this).apply {
            text = "2. Sobrepor a outros apps"
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        })

        root.addView(Button(this).apply {
            text = "3. Escolher apps"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, AppSelectionActivity::class.java))
            }
        })

        root.addView(Button(this).apply {
            text = "Verificar"
            setOnClickListener {
                val overlay = Settings.canDrawOverlays(this@MainActivity)
                val listener = Settings.Secure.getString(
                    contentResolver, "enabled_notification_listeners"
                )?.contains(packageName) == true
                Toast.makeText(
                    this@MainActivity,
                    "overlay=$overlay  listener=$listener",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        setContentView(root)
    }
}

package com.sla.briefpopup

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import rikka.shizuku.Shizuku

/**
 * Tela unica: leva as permissoes necessarias + ativacao do Shizuku
 * (usado so' pro recurso de abrir em Freeform de verdade - sem ele o
 * arrastar-a-alca cai no comportamento normal de abrir o app).
 */
class MainActivity : Activity() {

    companion object {
        private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
        private const val SHIZUKU_RELEASES_URL = "https://github.com/RikkaApps/Shizuku/releases"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
    }

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            Toast.makeText(this, "Shizuku: permissao ${if (granted) "concedida" else "negada"}", Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

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
            text = "3. Ativar Shizuku (freeform de verdade)"
            setOnClickListener { onShizukuButtonClick() }
        })

        root.addView(Button(this).apply {
            text = "Verificar"
            setOnClickListener {
                val overlay = Settings.canDrawOverlays(this@MainActivity)
                val listener = Settings.Secure.getString(
                    contentResolver, "enabled_notification_listeners"
                )?.contains(packageName) == true
                val shizukuAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
                val shizukuGranted = shizukuAlive &&
                    runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
                        .getOrDefault(false)
                Toast.makeText(
                    this@MainActivity,
                    "overlay=$overlay  listener=$listener  shizuku=$shizukuAlive/$shizukuGranted",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        setContentView(root)
    }

    private fun onShizukuButtonClick() {
        val shizukuInstalled = packageManager.getLaunchIntentForPackage(SHIZUKU_PKG) != null
        if (!shizukuInstalled) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_RELEASES_URL)))
            return
        }
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            // Instalado mas nao rodando - abre o proprio Shizuku pro usuario ativar
            // (parear via ADB wireless, ou root se disponivel).
            packageManager.getLaunchIntentForPackage(SHIZUKU_PKG)?.let { startActivity(it) }
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Shizuku ja esta ativo e com permissao", Toast.LENGTH_LONG).show()
        } else {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }
}

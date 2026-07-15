package com.sla.briefpopup

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/**
 * Lista os apps instalados (com icone de launcher, igual a gaveta de apps)
 * pra escolher quais mostram o pop-up. Tocar num app (fora do checkbox)
 * abre direto a tela nativa de configuracao de notificacao dele.
 */
class AppSelectionActivity : Activity() {

    private data class AppEntry(
        val packageName: String,
        val label: String,
        val icon: Drawable
    )

    private lateinit var apps: List<AppEntry>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = pm.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
            .filter { it != packageName }
            .mapNotNull { pkg ->
                runCatching {
                    val info = pm.getApplicationInfo(pkg, 0)
                    AppEntry(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }

        setContentView(ListView(this).apply {
            adapter = AppAdapter()
            divider = ColorDrawable(resources.getColor(R.color.surface, theme))
            dividerHeight = dp(1)
            setBackgroundColor(resources.getColor(R.color.bg, theme))
        })
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int): AppEntry = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val entry = apps[position]

            val icon = ImageView(this@AppSelectionActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                    marginEnd = dp(16)
                }
                setImageDrawable(entry.icon)
            }
            val label = TextView(this@AppSelectionActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = entry.label
                textSize = 15f
                setTextColor(resources.getColor(R.color.text_primary, theme))
            }
            val checkBox = CheckBox(this@AppSelectionActivity).apply {
                isChecked = AllowlistStore.isEnabled(this@AppSelectionActivity, entry.packageName)
                buttonTintList = android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.accent, theme)
                )
                setOnCheckedChangeListener { _, checked ->
                    AllowlistStore.setEnabled(this@AppSelectionActivity, entry.packageName, checked)
                }
            }

            val rippleRes = android.util.TypedValue().also {
                theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
            }.resourceId

            return LinearLayout(this@AppSelectionActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                foreground = resources.getDrawable(rippleRes, theme)   // feedback de toque por cima do conteudo
                addView(icon)
                addView(label)
                addView(checkBox)
                setOnClickListener {
                    // Abre a config nativa de notificacao do app - o checkbox
                    // (liga/desliga o pop-up) tem seu proprio listener e
                    // consome o toque antes de chegar aqui.
                    startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, entry.packageName)
                    )
                }
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

package com.tihon.shtorka

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.tihon.shtorka.core.Hub
import com.tihon.shtorka.core.Prefs
import com.tihon.shtorka.service.A11y
import com.tihon.shtorka.system.Launch
import com.tihon.shtorka.ui.Fonts
import com.tihon.shtorka.ui.IosSwitch
import com.tihon.shtorka.ui.Palette
import com.tihon.shtorka.ui.PanelHost
import com.tihon.shtorka.ui.dp
import com.tihon.shtorka.ui.text
import kotlin.math.roundToInt
import kotlin.reflect.KMutableProperty0

/** Настройки в стиле приложения «Настройки» iOS: доступы, жесты, островок, проверка. */
class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private val refreshers = ArrayList<() -> Unit>()

    private fun d(v: Number) = dp(v).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Palette.BLACK)
            isVerticalScrollBarEnabled = false
        }
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(16), d(24), d(16), d(40))
        }
        scroll.addView(list)
        setContentView(scroll)
        build()
    }

    override fun onResume() {
        super.onResume()
        refreshers.forEach { it() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshers.forEach { it() }
    }

    private fun build() {
        list.addView(text(34f, Fonts.bold).apply { text = "Шторка" }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(12) })
        list.addView(
            text(15f, Fonts.regular, Palette.TEXT_SECONDARY, lines = 4).apply {
                text = "Пункт управления, Центр уведомлений и островок у капли камеры — как на iPhone."
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(6); bottomMargin = d(18) },
        )

        hero()

        section("Доступы")
        group {
            status(
                "Специальные возможности", "Без неё шторка не появится",
                { A11y.enabled(this) },
            ) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            status(
                "Доступ к уведомлениям", "Уведомления, музыка, «Не беспокоить»",
                { A11y.notificationAccess(this) },
            ) {
                val i = if (Build.VERSION.SDK_INT >= 30) {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        android.content.ComponentName(this, com.tihon.shtorka.service.NotifListener::class.java).flattenToString(),
                    )
                } else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                if (!Launch.start(this, i)) Launch.start(this, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            status(
                "Изменение настроек", "Яркость, автояркость и поворот экрана",
                { Settings.System.canWrite(this) },
            ) { Launch.start(this, Launch.writeSettings(this)) }
            status(
                "Работа без ограничений", "Чтобы Samsung не усыплял шторку",
                { getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName) },
            ) {
                @SuppressLint("BatteryLife")
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                if (!Launch.start(this, i)) Launch.start(this, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            status(
                "Название сети Wi-Fi", "Необязательно: Android отдаёт его только с геолокацией",
                { checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED },
            ) { requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1) }
        }
        note(
            "Если переключатель службы серый или пишет «Ограниченная настройка»: " +
                "Настройки → Приложения → Шторка → ⋮ (вверху справа) → «Разрешить ограниченные настройки». " +
                "После этого включи службу ещё раз. Так Android защищает приложения, установленные не из магазина.",
        )
        group {
            button("Открыть сведения о приложении") {
                Launch.start(this, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }

        section("Жесты")
        group {
            toggle("Пункт управления", "Свайп вниз справа от капли", Prefs::controlCenter)
            toggle("Центр уведомлений", "Свайп вниз слева от капли", Prefs::notificationCenter)
            toggle("Нижний ряд кнопок", "Энергосбережение, тёмная тема, запись экрана, будильник", Prefs::extraRow)
            toggle("Виброотклик", "Лёгкие щелчки при нажатиях", Prefs::haptics)
        }
        note(
            "Обычная шторка Samsung никуда не делась: свайп вниз от верхнего края двумя пальцами открывает её. " +
                "А если выключить один из свайпов, на его стороне она будет открываться и одним пальцем.",
        )

        section("Островок у камеры")
        group {
            toggle("Островок", "Капля камеры оживает и показывает события", Prefs::island)
            toggle("Музыка", "Обложка и волна; тап — плеер", Prefs::islandMusic)
            toggle("Зарядка", "Процент заряда при подключении кабеля", Prefs::islandCharging)
            toggle("Режим звонка", "Звонок, вибрация или без звука", Prefs::islandRinger)
        }

        section("Проверка")
        group {
            button("Открыть Пункт управления") { withService { it.panels.open(PanelHost.Mode.CC) } }
            button("Открыть Центр уведомлений") { withService { it.panels.open(PanelHost.Mode.NC) } }
            button("Показать островок") { withService { it.demoIsland() } }
        }

        section("Автозапуск")
        group {
            status(
                "Самовосстановление службы", "Включит шторку, если её выключит «оптимизатор»",
                { A11y.canSelfEnable(this) }, okText = "Есть", noText = "Нет",
            ) {
                Toast.makeText(this, "Нужна одна команда с компьютера — она написана ниже", Toast.LENGTH_LONG).show()
            }
        }
        note(
            "Служба специальных возможностей запускается сама при каждом включении телефона — " +
                "ничего делать не нужно. Для полной надёжности можно один раз выполнить на компьютере:\n\n" +
                "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS\n\n" +
                "Тогда шторка сама включит службу обратно, если система или чистильщик её отключат.",
        )

        list.addView(
            text(13f, Fonts.regular, 0x66FFFFFF, gravity = Gravity.CENTER_HORIZONTAL).apply {
                text = "Шторка ${BuildConfigCompat.version(this@MainActivity)}"
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(28) },
        )
    }

    private fun withService(block: (com.tihon.shtorka.service.ShadeService) -> Unit) {
        val s = Hub.service
        if (s == null) {
            Toast.makeText(this, "Сначала включи службу «Шторка» в специальных возможностях", Toast.LENGTH_LONG).show()
            return
        }
        block(s)
    }

    // ---------- строительные блоки ----------

    private fun hero() {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(d(16), d(16), d(16), d(16))
            background = GradientDrawable().apply { cornerRadius = dp(14); setColor(0xFF1C1C1E.toInt()) }
        }
        val icon = ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) }
        card.addView(icon, LinearLayout.LayoutParams(d(60), d(60)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(17f, Fonts.semibold).apply { text = "Как пользоваться" })
        col.addView(
            text(14f, Fonts.regular, Palette.TEXT_SECONDARY, lines = 6).apply {
                text = "Свайп вниз справа от камеры — Пункт управления. Слева — уведомления. " +
                    "Долгое нажатие на блок раскрывает его. Закрыть — свайп вверх или тап мимо кнопок."
                setLineSpacing(0f, 1.1f)
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(4) },
        )
        card.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = d(14) })
        list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = d(8) })
    }

    private fun section(title: String) {
        list.addView(
            text(13f, Fonts.regular, 0x8CFFFFFF.toInt()).apply { text = title.uppercase() },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(26); bottomMargin = d(7); marginStart = d(16) },
        )
    }

    private fun note(t: String) {
        list.addView(
            text(13f, Fonts.regular, 0x8CFFFFFF.toInt(), lines = 20).apply { text = t; setLineSpacing(0f, 1.12f) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(7); marginStart = d(16); marginEnd = d(16) },
        )
    }

    private var current: LinearLayout? = null

    private fun group(block: () -> Unit) {
        val g = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(12); setColor(0xFF1C1C1E.toInt()) }
            clipToOutline = true
        }
        current = g
        block()
        current = null
        list.addView(g, LinearLayout.LayoutParams(-1, -2))
    }

    private fun addRow(row: View) {
        val g = current ?: return
        if (g.childCount > 0) {
            g.addView(View(this).apply { setBackgroundColor(0xFF38383A.toInt()) }, LinearLayout.LayoutParams(-1, 1).apply { marginStart = d(16) })
        }
        g.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun rowBase(title: String, sub: String?): Pair<LinearLayout, LinearLayout> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = d(52)
            setPadding(d(16), d(11), d(14), d(11))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(17f, Fonts.regular, lines = 2).apply { text = title })
        if (sub != null) col.addView(
            text(13f, Fonts.regular, Palette.TEXT_SECONDARY, lines = 3).apply { text = sub },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(2) },
        )
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        return row to col
    }

    private fun status(
        title: String, sub: String, ok: () -> Boolean,
        okText: String = "Включено", noText: String = "Включить",
        action: () -> Unit,
    ) {
        val (row, _) = rowBase(title, sub)
        val state = text(15f, Fonts.semibold)
        row.addView(state, LinearLayout.LayoutParams(-2, -2).apply { marginStart = d(10) })
        row.setOnClickListener { action() }
        row.background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x22FFFFFF), null, null,
        )
        refreshers += {
            val good = runCatching(ok).getOrDefault(false)
            state.text = if (good) "✓ $okText" else "$noText ›"
            state.setTextColor(if (good) Palette.GREEN else Palette.BLUE)
        }
        addRow(row)
    }

    private fun toggle(title: String, sub: String, prop: KMutableProperty0<Boolean>) {
        val (row, _) = rowBase(title, sub)
        val sw = IosSwitch(this)
        sw.set(prop.get())
        sw.onChange = { prop.set(it) }
        row.addView(sw, LinearLayout.LayoutParams(-2, -2).apply { marginStart = d(10) })
        row.setOnClickListener { sw.set(!sw.checked, animate = true); prop.set(sw.checked) }
        addRow(row)
    }

    private fun button(title: String, action: () -> Unit) {
        val row = TextView(this).apply {
            text = title
            typeface = Fonts.regular
            textSize = 17f
            setTextColor(Palette.BLUE)
            minHeight = d(48)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(d(16), d(10), d(16), d(10))
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22FFFFFF), null, null,
            )
            setOnClickListener { action() }
        }
        addRow(row)
    }
}

/** Версия без BuildConfig (он у модуля выключен). */
object BuildConfigCompat {
    fun version(a: Activity): String = runCatching {
        a.packageManager.getPackageInfo(a.packageName, 0).versionName
    }.getOrNull() ?: ""
}

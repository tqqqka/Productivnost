package com.tihon.shtorka

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
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
import com.tihon.shtorka.service.IslandService
import com.tihon.shtorka.service.NotifListener
import com.tihon.shtorka.system.Kind
import com.tihon.shtorka.system.Launch
import com.tihon.shtorka.ui.Fonts
import com.tihon.shtorka.ui.IosSwitch
import com.tihon.shtorka.ui.Palette
import com.tihon.shtorka.ui.dp
import com.tihon.shtorka.ui.text
import kotlin.math.roundToInt
import kotlin.reflect.KMutableProperty0

/** Настройки в стиле приложения «Настройки» iOS. */
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

    private fun build() {
        list.addView(text(34f, Fonts.bold).apply { text = "Островок" }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(12) })
        list.addView(
            text(15f, Fonts.regular, Palette.TEXT_SECONDARY, lines = 4).apply {
                text = "Dynamic Island вокруг капли камеры — как на iPhone."
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(6); bottomMargin = d(18) },
        )
        hero()
        group {
            row("Тест звонка", "Входящий с мелодией и вибрацией: ответь или сбрось прямо в островке") {
                withService { svc ->
                    moveTaskToBack(true)
                    svc.main.postDelayed({ svc.island.testCall() }, 450)
                }
            }
        }

        section("Доступы")
        group {
            status("Специальные возможности", "Без неё островок не появится", { A11y.enabled(this) }) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            status("Доступ к уведомлениям", "Музыка, звонки, таймеры, уведомления", { A11y.notificationAccess(this) }) {
                val i = if (Build.VERSION.SDK_INT >= 30) {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        ComponentName(this, NotifListener::class.java).flattenToString(),
                    )
                } else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                if (!Launch.start(this, i)) Launch.start(this, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            status(
                "Работа без ограничений", "Чтобы Samsung не усыплял островок",
                { getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName) },
            ) {
                @SuppressLint("BatteryLife")
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                if (!Launch.start(this, i)) Launch.start(this, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        note(
            "Если Android пишет «Доступ к приложению запрещён» или переключатель серый:\n" +
                "1. Нажми «Открыть сведения о приложении» ниже.\n" +
                "2. Вверху справа ⋮ → «Разрешить ограниченные настройки», подтверди отпечатком или PIN.\n" +
                "3. Вернись сюда и включи доступы ещё раз.\n" +
                "Так Android защищает приложения, установленные не из магазина.",
        )
        group {
            button("Открыть сведения о приложении") {
                Launch.start(this, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }

        section("Что показывать")
        group {
            toggle("Музыка", "Обложка и волна, раскрывается в плеер", Prefs::music)
            toggle("Звонки", "Входящий — кнопки «Ответить» и «Отклонить», идущий — длительность", Prefs::calls)
            toggle("Таймеры и секундомеры", "Обратный отсчёт оранжевым, как в iOS", Prefs::timers)
            toggle("Запись экрана", "Красная точка и время записи", Prefs::recording)
            toggle("Навигация", "Стрелка и расстояние из Карт и Навигатора", Prefs::navigation)
            toggle("Загрузки", "Кольцо прогресса", Prefs::downloads)
            toggle("Уведомления", "Выезжают из островка и прячутся обратно", Prefs::banners)
            toggle("Зарядка", "Процент при подключении кабеля", Prefs::charging)
            toggle("Низкий заряд", "Предупреждение на 20, 10 и 5 %", Prefs::lowBattery)
            toggle("Режим звонка", "Звонок, вибрация или без звука", Prefs::ringer)
            toggle("Разблокировка", "Замок открывается, как Face ID", Prefs::unlock)
            toggle("Наушники", "Подключение и заряд", Prefs::headphones)
        }

        section("Вид")
        group {
            segmented("Размер", listOf("Маленький", "Обычный", "Крупный"), Prefs.size) { Prefs.size = it }
            toggle("Пилюля в покое", "Как на iPhone: чёрная пилюля видна всегда. Выключено — прячется в каплю", Prefs::idlePill)
            toggle("Прятать в своём приложении", "Музыка не показывается, пока открыт сам плеер — как в iOS", Prefs::hideInOwnerApp)
            toggle("Виброотклик", "Лёгкие щелчки при нажатиях", Prefs::haptics)
        }

        section("Посмотреть")
        group {
            demo("Входящий звонок", Kind.CALL_INCOMING)
            demo("Таймер", Kind.TIMER)
            demo("Уведомление", Kind.BANNER)
            demo("Зарядка", Kind.CHARGING)
            demo("Разблокировка", Kind.UNLOCK)
            demo("Наушники", Kind.HEADPHONES)
            demo("Навигация", Kind.NAVIGATION)
            demo("Загрузка", Kind.PROGRESS)
            demo("Запись экрана", Kind.RECORDING)
            demo("Низкий заряд", Kind.LOW_BATTERY)
        }
        note("Тап по островку открывает приложение, долгое нажатие раскрывает, свайп вбок прячет, свайп вверх сворачивает.")

        section("Диагностика")
        group {
            button("Что видит островок") { showDump() }
        }
        note(
            "Если таймер, навигация или загрузка из какого-то приложения не появляется в островке — " +
                "запусти её, открой этот экран и пришли скриншот: там видно, что именно присылает приложение.",
        )

        section("Автозапуск")
        group {
            status(
                "Самовосстановление службы", "Включит островок, если его выключит «оптимизатор»",
                { A11y.canSelfEnable(this) }, okText = "Есть", noText = "Нет",
            ) {
                Toast.makeText(this, "Нужна одна команда с компьютера — она ниже", Toast.LENGTH_LONG).show()
            }
        }
        note(
            "Служба специальных возможностей запускается сама при каждом включении телефона — " +
                "ничего делать не нужно. Для полной надёжности можно один раз выполнить на компьютере:\n\n" +
                "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS",
        )

        list.addView(
            text(13f, Fonts.regular, 0x66FFFFFF, gravity = Gravity.CENTER_HORIZONTAL).apply {
                text = "Островок " + (runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "")
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(28) },
        )
    }

    /** Сырые поля текущих уведомлений и как островок их понял. */
    private fun showDump() {
        val l = Hub.listener
        if (l == null) {
            Toast.makeText(this, "Сначала включи «Доступ к уведомлениям»", Toast.LENGTH_LONG).show()
            return
        }
        val text = runCatching { com.tihon.shtorka.system.LiveRepo.dump(this, l.activeNotifications ?: emptyArray()) }
            .getOrElse { "Не удалось прочитать уведомления: ${it.message}" }
        val tv = TextView(this).apply {
            this.text = text
            textSize = 12.5f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Palette.WHITE)
            setTextIsSelectable(true)
            setPadding(d(18), d(12), d(18), d(12))
        }
        val sv = ScrollView(this).apply { addView(tv) }
        android.app.AlertDialog.Builder(this)
            .setTitle("Что видит островок")
            .setView(sv)
            .setPositiveButton("Скопировать") { _, _ ->
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("island", text))
                Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun withService(block: (IslandService) -> Unit) {
        val s = Hub.service
        if (s == null) {
            Toast.makeText(this, "Сначала включи службу «Островок» в специальных возможностях", Toast.LENGTH_LONG).show()
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
        card.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) }, LinearLayout.LayoutParams(d(60), d(60)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(17f, Fonts.semibold).apply { text = "Как пользоваться" })
        col.addView(
            text(14f, Fonts.regular, Palette.TEXT_SECONDARY, lines = 8).apply {
                text = "Включи два доступа ниже — и островок сам оживает: музыка, звонки, таймеры, запись, " +
                    "навигация, загрузки, уведомления. Если активностей две, вторая — капелькой справа."
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

    private fun ripple() = android.graphics.drawable.RippleDrawable(
        android.content.res.ColorStateList.valueOf(0x22FFFFFF), null, null,
    )

    private fun rowBase(title: String, sub: String?): LinearLayout {
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
        return row
    }

    private fun status(
        title: String, sub: String, ok: () -> Boolean,
        okText: String = "Включено", noText: String = "Включить",
        action: () -> Unit,
    ) {
        val row = rowBase(title, sub)
        val state = text(15f, Fonts.semibold)
        row.addView(state, LinearLayout.LayoutParams(-2, -2).apply { marginStart = d(10) })
        row.setOnClickListener { action() }
        row.background = ripple()
        refreshers += {
            val good = runCatching(ok).getOrDefault(false)
            state.text = if (good) "✓ $okText" else "$noText ›"
            state.setTextColor(if (good) Palette.GREEN else Palette.BLUE)
        }
        addRow(row)
    }

    private fun toggle(title: String, sub: String, prop: KMutableProperty0<Boolean>) {
        val row = rowBase(title, sub)
        val sw = IosSwitch(this)
        sw.set(prop.get())
        sw.onChange = { prop.set(it) }
        row.addView(sw, LinearLayout.LayoutParams(-2, -2).apply { marginStart = d(10) })
        row.setOnClickListener { sw.set(!sw.checked, animate = true); prop.set(sw.checked) }
        addRow(row)
    }

    private fun segmented(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(16), d(12), d(16), d(14))
        }
        box.addView(text(17f, Fonts.regular).apply { text = title })
        val seg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { cornerRadius = dp(9); setColor(0xFF2C2C2E.toInt()) }
            setPadding(d(2), d(2), d(2), d(2))
        }
        val items = ArrayList<TextView>()
        fun paint(sel: Int) = items.forEachIndexed { i, t ->
            t.background = if (i == sel) GradientDrawable().apply { cornerRadius = dp(7); setColor(0xFF636366.toInt()) } else null
        }
        options.forEachIndexed { i, o ->
            val t = text(13.5f, Fonts.semibold, gravity = Gravity.CENTER).apply {
                text = o
                setPadding(0, d(7), 0, d(7))
                setOnClickListener { paint(i); onPick(i) }
            }
            items += t
            seg.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
        paint(selected)
        box.addView(seg, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(10) })
        addRow(box)
    }

    /** Строка-кнопка с подписью, как «Тест звонка». */
    private fun row(title: String, sub: String, action: () -> Unit) {
        val r = rowBase(title, sub)
        r.addView(GlyphCircleIcon(), LinearLayout.LayoutParams(d(40), d(40)).apply { marginStart = d(10) })
        r.setOnClickListener { action() }
        r.background = ripple()
        addRow(r)
    }

    private fun GlyphCircleIcon() = com.tihon.shtorka.ui.island.GlyphCircle(this).apply {
        solid = true
        set(R.drawable.g_call, Palette.GREEN)
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
            background = ripple()
            setOnClickListener { action() }
        }
        addRow(row)
    }

    private fun demo(title: String, kind: Kind) = button(title) {
        withService { s ->
            // Свернуть настройки, чтобы было видно островок поверх рабочего стола.
            moveTaskToBack(true)
            s.main.postDelayed({ s.island.demo(kind) }, 450)
        }
    }
}

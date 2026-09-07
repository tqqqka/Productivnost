package com.tihon.productivnost.voxel

import com.tihon.productivnost.core.Plant
import com.tihon.productivnost.core.PlantKind
import java.util.Random
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Палитра одного вида растения. */
data class Palette(
    val name: String,
    val bark: IntArray,
    val leaves: IntArray,
    val soil: IntArray,
    val grass: IntArray,
    val accent: Int = 0,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = name.hashCode()
}

/**
 * Процедурная генерация воксельных растений.
 * Одна и та же семечка (`seed`) всегда даёт одно и то же дерево.
 */
object PlantGenerator {

    private val soilDark = intArrayOf(0xFF4A3728.toInt(), 0xFF54402E.toInt(), 0xFF43301F.toInt())
    private val grassGreen = intArrayOf(0xFF5AA95A.toInt(), 0xFF4E9B52.toInt(), 0xFF67B663.toInt())

    val focusPalettes = listOf(
        Palette(
            "Дуб",
            bark = intArrayOf(0xFF6E4B31.toInt(), 0xFF5C3E27.toInt(), 0xFF7A553A.toInt()),
            leaves = intArrayOf(0xFF4FA24C.toInt(), 0xFF418B45.toInt(), 0xFF62B45C.toInt(), 0xFF37773C.toInt()),
            soil = soilDark, grass = grassGreen,
        ),
        Palette(
            "Сосна",
            bark = intArrayOf(0xFF5A4130.toInt(), 0xFF4A3527.toInt(), 0xFF664B36.toInt()),
            leaves = intArrayOf(0xFF2F7A55.toInt(), 0xFF27664A.toInt(), 0xFF3A8F63.toInt(), 0xFF1F5540.toInt()),
            soil = soilDark, grass = grassGreen,
        ),
        Palette(
            "Сакура",
            bark = intArrayOf(0xFF7A5A45.toInt(), 0xFF684A38.toInt(), 0xFF8A6851.toInt()),
            leaves = intArrayOf(0xFFF3A9C4.toInt(), 0xFFE98FB2.toInt(), 0xFFFFC4DA.toInt(), 0xFFDD7BA2.toInt()),
            soil = soilDark, grass = grassGreen,
        ),
        Palette(
            "Клён",
            bark = intArrayOf(0xFF6A4A33.toInt(), 0xFF573C29.toInt(), 0xFF77553B.toInt()),
            leaves = intArrayOf(0xFFE2803A.toInt(), 0xFFD2632E.toInt(), 0xFFF0A24B.toInt(), 0xFFB94F26.toInt()),
            soil = soilDark, grass = grassGreen,
        ),
        Palette(
            "Берёза",
            bark = intArrayOf(0xFFE8E3DA.toInt(), 0xFFD6D0C6.toInt(), 0xFF3A3A38.toInt()),
            leaves = intArrayOf(0xFF8CC65C.toInt(), 0xFF79B44E.toInt(), 0xFF9FD46F.toInt(), 0xFF66A043.toInt()),
            soil = soilDark, grass = grassGreen,
        ),
    )

    val studyPalettes = listOf(
        Palette(
            "Древо знаний",
            bark = intArrayOf(0xFF5E4B63.toInt(), 0xFF4E3E54.toInt(), 0xFF6D5873.toInt()),
            leaves = intArrayOf(0xFF3FA9A0.toInt(), 0xFF33908A.toInt(), 0xFF56C0B6.toInt(), 0xFF27756F.toInt()),
            soil = soilDark, grass = grassGreen, accent = 1,
        ),
        Palette(
            "Библиотечное",
            bark = intArrayOf(0xFF5A4636.toInt(), 0xFF4A3A2C.toInt(), 0xFF6A5442.toInt()),
            leaves = intArrayOf(0xFF6C7BD8.toInt(), 0xFF5A69C4.toInt(), 0xFF8A97E6.toInt(), 0xFF48549F.toInt()),
            soil = soilDark, grass = grassGreen, accent = 1,
        ),
        Palette(
            "Янтарное",
            bark = intArrayOf(0xFF6B5233.toInt(), 0xFF57422A.toInt(), 0xFF7C603E.toInt()),
            leaves = intArrayOf(0xFFE0B341.toInt(), 0xFFCB9C2F.toInt(), 0xFFF2CB63.toInt(), 0xFFA87D22.toInt()),
            soil = soilDark, grass = grassGreen, accent = 1,
        ),
    )

    private val bookColors = intArrayOf(
        0xFFD9534F.toInt(), 0xFF4A90D9.toInt(), 0xFFE8B84B.toInt(),
        0xFF52A96B.toInt(), 0xFFB06AC9.toInt(), 0xFFE0764B.toInt(),
    )

    fun palettesFor(kind: PlantKind) = if (kind == PlantKind.STUDY) studyPalettes else focusPalettes

    fun speciesCount(kind: PlantKind) = palettesFor(kind).size

    fun paletteName(kind: PlantKind, species: Int): String {
        val list = palettesFor(kind)
        return list[((species % list.size) + list.size) % list.size].name
    }

    fun build(plant: Plant): VoxelModel = build(plant.seed, plant.kind, plant.species)

    fun build(seed: Long, kind: PlantKind, species: Int): VoxelModel {
        val list = palettesFor(kind)
        val palette = list[((species % list.size) + list.size) % list.size]
        val rnd = Random(seed)
        val b = VoxelBuilder()

        ground(b, rnd, palette)
        b.sealGround()

        // Семечко — оно видно уже в самом начале роста.
        b.add(0, 1, 0, palette.bark[1])
        b.add(0, 1, -1, palette.bark[0])
        b.add(-1, 1, 0, palette.bark[0])
        b.add(-1, 1, -1, palette.bark[1])

        when {
            kind == PlantKind.STUDY -> studyTree(b, rnd, palette)
            palette.name == "Сосна" -> conifer(b, rnd, palette)
            else -> broadleaf(b, rnd, palette)
        }

        b.sortGrowth(Random(seed xor 0x5DEECE66DL))
        return b.build()
    }

    // ------------------------------------------------------------------ земля

    private fun ground(b: VoxelBuilder, rnd: Random, p: Palette) {
        val r = 5
        for (x in -r..r) for (z in -r..r) {
            val d = sqrt((x * x + z * z).toDouble())
            if (d > r - 0.2 + rnd.nextDouble() * 0.9) continue
            b.add(x, 0, z, pick(p.grass, rnd))
            b.add(x, -1, z, pick(p.soil, rnd))
            if (d < r - 2.0) b.add(x, -2, z, pick(p.soil, rnd))
        }
        // Пара камешков и травинок для живости.
        repeat(3) {
            val a = rnd.nextDouble() * Math.PI * 2
            val rr = 2.9 + rnd.nextDouble() * 1.5
            val x = (cos(a) * rr).roundToInt()
            val z = (sin(a) * rr).roundToInt()
            b.add(x, 1, z, if (it % 2 == 0) 0xFF8E8E88.toInt() else p.grass[2])
        }
    }

    // ------------------------------------------------------- лиственное дерево

    private fun broadleaf(b: VoxelBuilder, rnd: Random, p: Palette) {
        val trunkH = 9.5f + rnd.nextFloat() * 2.5f
        limb(b, 0f, 1f, 0f, 0f, trunkH, 0f, 2.2f, 1.35f, p.bark, rnd, curve = 0.1f) { _, _, _ -> }

        // Ветви уходят вверх и в стороны, но недалеко: кроны должны сомкнуться
        // в одну шапку, а не рассыпаться на отдельные кусты.
        val tips = ArrayList<FloatArray>()
        val branches = 4 + rnd.nextInt(2)
        val a0 = rnd.nextFloat() * 6.28f
        for (i in 0 until branches) {
            val a = a0 + i * (6.28f / branches) + (rnd.nextFloat() - 0.5f) * 0.4f
            val len = 3.4f + rnd.nextFloat() * 1.8f
            val ex = cos(a.toDouble()).toFloat() * len * 0.85f
            val ez = sin(a.toDouble()).toFloat() * len * 0.85f
            val ey = trunkH + len * 0.45f + rnd.nextFloat() * 0.6f
            limb(b, 0f, trunkH - 2.2f, 0f, ex, ey, ez, 1.35f, 0.8f, p.bark, rnd, curve = 0.16f) { x, y, z ->
                tips.add(floatArrayOf(x, y, z))
            }
        }

        // Сначала общая шапка, затем шапочки на концах ветвей.
        blob(b, 0f, trunkH + 2.4f, 0f, 4.4f + rnd.nextFloat() * 0.7f, p.leaves, rnd)
        for (t in tips) {
            blob(b, t[0], t[1] + 0.6f, t[2], 3.0f + rnd.nextFloat() * 1.0f, p.leaves, rnd)
        }
    }

    // --------------------------------------------------------------- хвойное

    private fun conifer(b: VoxelBuilder, rnd: Random, p: Palette) {
        val h = 17f + rnd.nextFloat() * 4f
        limb(b, 0f, 1f, 0f, 0f, h, 0f, 1.7f, 0.8f, p.bark, rnd, curve = 0.05f) { _, _, _ -> }
        val layers = 5 + rnd.nextInt(2)
        for (i in 0 until layers) {
            val t = i / (layers - 1f)
            val y = 4.5f + t * (h - 3.5f)
            val r = (5.4f - t * 4.1f) + rnd.nextFloat() * 0.5f
            cone(b, y, r, p.leaves, rnd)
        }
        blob(b, 0f, h + 0.8f, 0f, 1.5f, p.leaves, rnd)
    }

    private fun cone(b: VoxelBuilder, y: Float, r: Float, colors: IntArray, rnd: Random) {
        val ri = r.roundToInt()
        for (x in -ri - 1..ri + 1) for (z in -ri - 1..ri + 1) {
            val d = sqrt((x * x + z * z).toDouble()).toFloat()
            if (d <= r + rnd.nextFloat() * 0.7f - 0.3f) {
                val yy = (y + (1f - d / max(r, 0.5f)) * 1.7f).roundToInt()
                b.add(x, yy, z, pick(colors, rnd))
                if (d < r * 0.55f) b.add(x, yy + 1, z, pick(colors, rnd))
            }
        }
    }

    // ------------------------------------------------- дерево учёбы с книгами

    private fun studyTree(b: VoxelBuilder, rnd: Random, p: Palette) {
        val trunkH = 8.5f + rnd.nextFloat() * 2f
        limb(b, 0f, 1f, 0f, 0f, trunkH, 0f, 2.1f, 1.3f, p.bark, rnd, curve = 0.08f) { _, _, _ -> }

        val tips = ArrayList<FloatArray>()
        val branches = 4 + rnd.nextInt(2)
        val a0 = rnd.nextFloat() * 6.28f
        for (i in 0 until branches) {
            val a = a0 + i * (6.28f / branches)
            val len = 3.2f + rnd.nextFloat() * 1.6f
            val ex = cos(a.toDouble()).toFloat() * len * 0.8f
            val ez = sin(a.toDouble()).toFloat() * len * 0.8f
            val ey = trunkH + len * 0.5f
            limb(b, 0f, trunkH - 2f, 0f, ex, ey, ez, 1.25f, 0.75f, p.bark, rnd, curve = 0.14f) { x, y, z ->
                tips.add(floatArrayOf(x, y, z))
            }
        }
        blob(b, 0f, trunkH + 2.2f, 0f, 4.2f + rnd.nextFloat() * 0.6f, p.leaves, rnd)
        for (t in tips) blob(b, t[0], t[1] + 0.5f, t[2], 2.8f + rnd.nextFloat() * 0.9f, p.leaves, rnd)

        // Стопки книг у корней и пара томов, забытых в ветвях.
        bookStack(b, -4, 1, 1, 3 + rnd.nextInt(2), rnd)
        bookStack(b, 2, 1, -3, 2 + rnd.nextInt(2), rnd)
        for (t in tips.take(2)) {
            book(b, t[0].roundToInt(), (t[1] - 1).roundToInt(), t[2].roundToInt(), rnd)
        }
    }

    private fun bookStack(b: VoxelBuilder, x: Int, y: Int, z: Int, count: Int, rnd: Random) {
        var yy = y
        for (i in 0 until count) {
            val c = bookColors[rnd.nextInt(bookColors.size)]
            val off = rnd.nextInt(2) - 1
            for (dx in 0..3) for (dz in 0..2) b.add(x + dx + off, yy, z + dz, c)
            for (dz in 0..2) b.add(x + off, yy, z + dz, 0xFFF2EDE0.toInt())
            yy++
        }
    }

    private fun book(b: VoxelBuilder, x: Int, y: Int, z: Int, rnd: Random) {
        val c = bookColors[rnd.nextInt(bookColors.size)]
        for (dx in 0..2) for (dz in 0..1) b.add(x + dx, y, z + dz, c)
        b.add(x, y, z, 0xFFF2EDE0.toInt())
        b.add(x, y, z + 1, 0xFFF2EDE0.toInt())
    }

    // ---------------------------------------------------------------- примитивы

    private inline fun limb(
        b: VoxelBuilder,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        r0: Float, r1: Float,
        colors: IntArray, rnd: Random,
        curve: Float,
        onTip: (Float, Float, Float) -> Unit,
    ) {
        val dx = x1 - x0; val dy = y1 - y0; val dz = z1 - z0
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        val steps = max(2, (len * 1.7f).roundToInt())
        val cx = (rnd.nextFloat() - 0.5f) * curve * len
        val cz = (rnd.nextFloat() - 0.5f) * curve * len
        var lx = 0f; var ly = 0f; var lz = 0f
        for (s in 0..steps) {
            val t = s / steps.toFloat()
            val bend = sin((t * Math.PI).toDouble()).toFloat()
            val px = x0 + dx * t + cx * bend
            val py = y0 + dy * t
            val pz = z0 + dz * t + cz * bend
            sphere(b, px, py, pz, r0 + (r1 - r0) * t, colors, rnd)
            lx = px; ly = py; lz = pz
        }
        onTip(lx, ly, lz)
    }

    private fun sphere(b: VoxelBuilder, cx: Float, cy: Float, cz: Float, r: Float, colors: IntArray, rnd: Random) {
        val ri = max(1, (r + 0.6f).roundToInt())
        for (x in -ri..ri) for (y in -ri..ri) for (z in -ri..ri) {
            if (x * x + y * y + z * z <= r * r + 0.25f) {
                b.add((cx + x).roundToInt(), (cy + y).roundToInt(), (cz + z).roundToInt(), pick(colors, rnd))
            }
        }
    }

    /** Крона: сплюснутый эллипсоид с рваным краем. */
    private fun blob(b: VoxelBuilder, cx: Float, cy: Float, cz: Float, r: Float, colors: IntArray, rnd: Random) {
        val ry = r * 0.82f
        val ri = (r + 1.2f).roundToInt()
        val ryi = (ry + 1.2f).roundToInt()
        for (x in -ri..ri) for (y in -ryi..ryi) for (z in -ri..ri) {
            val d = (x * x + z * z) / (r * r) + (y * y) / (ry * ry)
            if (d <= 1f + (rnd.nextFloat() - 0.55f) * 0.34f) {
                val yy = (cy + y).roundToInt()
                if (yy < 1) continue
                b.add((cx + x).roundToInt(), yy, (cz + z).roundToInt(), pick(colors, rnd))
            }
        }
    }

    private fun pick(colors: IntArray, rnd: Random) = colors[rnd.nextInt(colors.size)]
}

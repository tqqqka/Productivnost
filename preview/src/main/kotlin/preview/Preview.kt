package preview

import com.tihon.productivnost.core.PlantKind
import com.tihon.productivnost.voxel.PlantGenerator
import com.tihon.productivnost.voxel.VoxelModel
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private fun shade(color: Int, k: Float): Color {
    fun c(v: Int) = (v * k).toInt().coerceIn(0, 255)
    return Color(c((color shr 16) and 0xFF), c((color shr 8) and 0xFF), c(color and 0xFF))
}

/** Повторяет проекцию и порядок отрисовки из VoxelRenderer. */
private fun render(
    g: Graphics2D,
    ox: Float, oy: Float, w: Float, h: Float,
    model: VoxelModel, count: Int, yaw: Float, pitch: Float = 0.62f,
): Int {
    val cosY = cos(yaw); val sinY = sin(yaw)
    val cosP = cos(pitch); val sinP = sin(pitch)
    val ux = cosY; val uy = sinP * sinY
    val vy = -cosP
    val wx = -sinY; val wy = sinP * cosY
    val cox = FloatArray(8); val coy = FloatArray(8)
    for (i in 0 until 8) {
        val dx = ((i shr 2) and 1).toFloat()
        val dy = ((i shr 1) and 1).toFloat()
        val dz = (i and 1).toFloat()
        cox[i] = dx * ux + dz * wx
        coy[i] = dx * uy + dy * vy + dz * wy
    }
    var minSx = Float.MAX_VALUE; var maxSx = -Float.MAX_VALUE
    var minSy = Float.MAX_VALUE; var maxSy = -Float.MAX_VALUE
    for (c in 0 until 8) {
        val x = (if ((c shr 2) and 1 == 1) model.maxX + 1 else model.minX).toFloat()
        val y = (if ((c shr 1) and 1 == 1) model.maxY + 1 else model.minY).toFloat()
        val z = (if (c and 1 == 1) model.maxZ + 1 else model.minZ).toFloat()
        val sx = x * ux + z * wx
        val sy = x * uy + y * vy + z * wy
        minSx = min(minSx, sx); maxSx = max(maxSx, sx)
        minSy = min(minSy, sy); maxSy = max(maxSy, sy)
    }
    val pad = min(w, h) * 0.06f
    val spanX = maxSx - minSx; val spanY = maxSy - minSy
    val scale = min((w - 2 * pad) / spanX, (h - 2 * pad) / spanY)
    val originX = ox + pad + (w - 2 * pad - spanX * scale) / 2f - minSx * scale
    val originY = oy + pad + (h - 2 * pad - spanY * scale) * 0.55f - minSy * scale

    val order = ArrayList<Pair<Float, Int>>(count)
    for (i in 0 until count) {
        if (model.hideFrom[i] <= count) continue
        order += (model.xs[i] * sinY * cosP + model.ys[i] * sinP + model.zs[i] * cosY * cosP) to i
    }
    order.sortBy { it.first }

    val faceTop = intArrayOf(2, 6, 7, 3)
    val faceX = if (sinY > 0f) intArrayOf(4, 6, 7, 5) else intArrayOf(0, 2, 3, 1)
    val faceZ = if (cosY > 0f) intArrayOf(1, 5, 7, 3) else intArrayOf(0, 4, 6, 2)

    fun quad(bx: Float, by: Float, f: IntArray, col: Color) {
        g.color = col
        g.fillPolygon(
            IntArray(4) { (bx + cox[f[it]] * scale).roundToInt() },
            IntArray(4) { (by + coy[f[it]] * scale).roundToInt() },
            4,
        )
    }
    for ((_, i) in order) {
        val bx = originX + (model.xs[i] * ux + model.zs[i] * wx) * scale
        val by = originY + (model.xs[i] * uy + model.ys[i] * vy + model.zs[i] * wy) * scale
        val base = model.colors[i]; val j = model.shade[i]
        quad(bx, by, faceTop, shade(base, 1.00f * j))
        quad(bx, by, faceX, shade(base, 0.70f * j))
        quad(bx, by, faceZ, shade(base, 0.87f * j))
    }
    return order.size
}

fun main() {
    val cell = 300
    val cols = 5
    val rows = 3
    val img = BufferedImage(cell * cols, cell * rows, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color(12, 18, 16)
    g.fillRect(0, 0, img.width, img.height)

    var row = 0
    var col = 0
    fun draw(kind: PlantKind, species: Int, growth: Float, seed: Long) {
        val t0 = System.nanoTime()
        val model = PlantGenerator.build(seed, kind, species)
        val genMs = (System.nanoTime() - t0) / 1e6
        val count = model.countFor(growth)
        val t1 = System.nanoTime()
        val drawn = render(g, (col * cell).toFloat(), (row * cell).toFloat(), cell.toFloat(), cell.toFloat(), model, count, 0.62f)
        val drawMs = (System.nanoTime() - t1) / 1e6
        println(
            "%-16s рост %.2f | вокселей %5d | рисуем %4d кубиков (%d граней) | генерация %.1f мс, отрисовка %.1f мс"
                .format(PlantGenerator.paletteName(kind, species), growth, model.size, drawn, drawn * 3, genMs, drawMs),
        )
        col++
        if (col == cols) { col = 0; row++ }
    }

    repeat(PlantGenerator.speciesCount(PlantKind.FOCUS)) { draw(PlantKind.FOCUS, it, 1f, 100L + it) }
    repeat(PlantGenerator.speciesCount(PlantKind.STUDY)) { draw(PlantKind.STUDY, it, 1f, 500L + it) }
    draw(PlantKind.FOCUS, 0, 0.03f, 100L)
    listOf(0.3f, 0.6f, 0.85f).forEach { draw(PlantKind.FOCUS, 0, it, 100L) }
    draw(PlantKind.STUDY, 0, 0.5f, 500L)

    g.dispose()
    ImageIO.write(img, "png", File("/tmp/voxel_preview.png"))
    println("Записано /tmp/voxel_preview.png")

    renderGardenPreview("/tmp/garden_preview.png", 13, 1000, 900)
    renderGardenPreview("/tmp/garden_full.png", 36, 1000, 900)
}

// --------------------------------------------------------------------- сад

private const val G_YAW = (Math.PI / 4).toFloat()
private const val G_PITCH = 0.62f
private const val CELL = 14f
private const val DEPTH = 3f
private const val ISO = 0.70710678f

private fun shadeInt(color: Int, k: Float): Color {
    fun c(v: Int) = (v * k).toInt().coerceIn(0, 255)
    return Color(c((color shr 16) and 0xFF), c((color shr 8) and 0xFF), c(color and 0xFF))
}

/** Спрайт растения: растр плюс место мировой точки (0,0,0) внутри него. */
private class Spr(val img: BufferedImage, val ox: Float, val oy: Float)

private fun spriteOf(seed: Long, kind: PlantKind, species: Int, scale: Float): Spr {
    val model = PlantGenerator.build(seed, kind, species, withGround = false)
    val cosY = cos(G_YAW); val sinY = sin(G_YAW)
    val cosP = cos(G_PITCH); val sinP = sin(G_PITCH)
    val ux = cosY; val uy = sinP * sinY
    val vy = -cosP
    val wx = -sinY; val wy = sinP * cosY
    var minSx = Float.MAX_VALUE; var maxSx = -Float.MAX_VALUE
    var minSy = Float.MAX_VALUE; var maxSy = -Float.MAX_VALUE
    for (c in 0 until 8) {
        val x = (if ((c shr 2) and 1 == 1) model.maxX + 1 else model.minX).toFloat()
        val y = (if ((c shr 1) and 1 == 1) model.maxY + 1 else model.minY).toFloat()
        val z = (if (c and 1 == 1) model.maxZ + 1 else model.minZ).toFloat()
        val sx = x * ux + z * wx
        val sy = x * uy + y * vy + z * wy
        minSx = min(minSx, sx); maxSx = max(maxSx, sx)
        minSy = min(minSy, sy); maxSy = max(maxSy, sy)
    }
    val w = ((maxSx - minSx) * scale + 2).roundToInt().coerceAtLeast(1)
    val h = ((maxSy - minSy) * scale + 2).roundToInt().coerceAtLeast(1)
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val ox = 1f - minSx * scale
    val oy = 1f - minSy * scale
    render(g, ox - (0f), oy - (0f), 0f, 0f, model, model.size, G_YAW, G_PITCH, scale, ox, oy)
    g.dispose()
    return Spr(img, ox, oy)
}

/** Рисует модель так, чтобы мировая точка (0,0,0) попала в (ox, oy). */
private fun render(
    g: Graphics2D, unusedA: Float, unusedB: Float, unusedC: Float, unusedD: Float,
    model: VoxelModel, count: Int, yaw: Float, pitch: Float, scale: Float, ox: Float, oy: Float,
) {
    val cosY = cos(yaw); val sinY = sin(yaw)
    val cosP = cos(pitch); val sinP = sin(pitch)
    val ux = cosY; val uy = sinP * sinY
    val vy = -cosP
    val wx = -sinY; val wy = sinP * cosY
    val cox = FloatArray(8); val coy = FloatArray(8)
    for (i in 0 until 8) {
        val dx = ((i shr 2) and 1).toFloat()
        val dy = ((i shr 1) and 1).toFloat()
        val dz = (i and 1).toFloat()
        cox[i] = dx * ux + dz * wx
        coy[i] = dx * uy + dy * vy + dz * wy
    }
    val order = ArrayList<Pair<Float, Int>>(count)
    for (i in 0 until count) {
        if (model.hideFrom[i] <= count) continue
        order += (model.xs[i] * sinY * cosP + model.ys[i] * sinP + model.zs[i] * cosY * cosP) to i
    }
    order.sortBy { it.first }
    val fTop = intArrayOf(2, 6, 7, 3)
    val fX = if (sinY > 0f) intArrayOf(4, 6, 7, 5) else intArrayOf(0, 2, 3, 1)
    val fZ = if (cosY > 0f) intArrayOf(1, 5, 7, 3) else intArrayOf(0, 4, 6, 2)
    fun quad(bx: Float, by: Float, f: IntArray, col: Color) {
        g.color = col
        g.fillPolygon(
            IntArray(4) { (bx + cox[f[it]] * scale).roundToInt() },
            IntArray(4) { (by + coy[f[it]] * scale).roundToInt() },
            4,
        )
    }
    for ((_, i) in order) {
        val bx = ox + (model.xs[i] * ux + model.zs[i] * wx) * scale
        val by = oy + (model.xs[i] * uy + model.ys[i] * vy + model.zs[i] * wy) * scale
        val base = model.colors[i]; val j = model.shade[i]
        quad(bx, by, fTop, shadeInt(base, 1.00f * j))
        quad(bx, by, fX, shadeInt(base, 0.70f * j))
        quad(bx, by, fZ, shadeInt(base, 0.87f * j))
    }
}

fun renderGardenPreview(path: String, count: Int, w: Int, h: Int) {
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color(12, 18, 16)
    g.fillRect(0, 0, w, h)

    val n = maxOf(4, kotlin.math.ceil(kotlin.math.sqrt(count.toFloat())).toInt())
    val sinP = sin(G_PITCH); val cosP = cos(G_PITCH)
    val treeH = 30f
    val heightPerEx = treeH * cosP / (ISO * CELL) + 2f * n * sinP + DEPTH * cosP / (ISO * CELL)
    val ex = minOf(w * 0.94f / (2f * n), h * 0.94f / heightPerEx).coerceIn(14f, 96f)
    val ey = ex * sinP
    val voxelScale = ex / (ISO * CELL)
    val blockPx = DEPTH * cosP * voxelScale
    val treePx = treeH * cosP * voxelScale
    val cx0 = w / 2f
    val cy0 = h / 2f - ((2f * n - 2f) * ey + blockPx - treePx) / 2f

    val kinds = PlantKind.entries
    val sprites = (0 until count).map { k ->
        val kind = if (k % 4 == 3) PlantKind.STUDY else PlantKind.FOCUS
        val species = k % PlantGenerator.speciesCount(kind)
        spriteOf(1000L + k * 37L, kind, species, voxelScale)
    }
    println("сад: клеток ${n}x$n, растений $count, клетка ${ex.roundToInt()} px, воксель %.2f px".format(voxelScale))
    println("спрайт: ${sprites[0].img.width}x${sprites[0].img.height} px, ${kinds.size} типа")

    val grass = intArrayOf(0xFF5AA95A.toInt(), 0xFF529E54.toInt())
    val tilled = intArrayOf(0xFF6B5236.toInt(), 0xFF614A31.toInt())
    val soil = 0xFF4A3728.toInt()

    fun poly(pts: List<Pair<Float, Float>>, c: Color) {
        g.color = c
        g.fillPolygon(IntArray(pts.size) { pts[it].first.roundToInt() }, IntArray(pts.size) { pts[it].second.roundToInt() }, pts.size)
    }

    for (d in 0..(2 * n - 2)) {
        for (i in 0 until n) {
            val j = d - i
            if (j < 0 || j >= n) continue
            val x = cx0 + (j - i) * ex
            val y = cy0 + (j + i) * ey
            val index = i * n + j
            val has = index < count
            val even = (i + j) % 2 == 0
            val top = if (has) (if (even) grass[0] else grass[1]) else (if (even) tilled[0] else tilled[1])
            poly(listOf(x to (y - ey), (x + ex) to y, x to (y + ey), (x - ex) to y), shadeInt(top, 1.0f))
            poly(listOf((x + ex) to y, x to (y + ey), x to (y + ey + blockPx), (x + ex) to (y + blockPx)), shadeInt(soil, 0.70f))
            poly(listOf((x - ex) to y, x to (y + ey), x to (y + ey + blockPx), (x - ex) to (y + blockPx)), shadeInt(soil, 0.87f))
            if (!has) continue
            val s = sprites[index]
            val anchorY = y + cosP * voxelScale
            g.drawImage(s.img, (x - s.ox).roundToInt(), (anchorY - s.oy).roundToInt(), null)
        }
    }
    g.dispose()
    ImageIO.write(img, "png", File(path))
    println("Записано $path")
}

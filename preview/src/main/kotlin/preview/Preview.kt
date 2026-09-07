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
}

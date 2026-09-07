package com.tihon.productivnost.voxel

/**
 * Воксельная модель: параллельные массивы координат и цветов.
 *
 * [hideFrom] — индекс роста, начиная с которого воксель полностью закрыт соседями
 * и его можно не рисовать. Значение вычисляется по финальному порядку, поэтому
 * отсечение остаётся корректным на любой стадии роста.
 */
class VoxelModel(
    val xs: IntArray,
    val ys: IntArray,
    val zs: IntArray,
    val colors: IntArray,
    val shade: FloatArray,
    val hideFrom: IntArray,
    /** Первые [alwaysCount] вокселей (земля) видны всегда. */
    val alwaysCount: Int,
    val minX: Int, val maxX: Int,
    val minY: Int, val maxY: Int,
    val minZ: Int, val maxZ: Int,
) {
    val size: Int get() = xs.size
    val growable: Int get() = size - alwaysCount

    /** Сколько вокселей показывать при степени роста [t] в диапазоне 0..1. */
    fun countFor(t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return alwaysCount + Math.round(growable * f)
    }
}

class VoxelBuilder {
    private val index = HashMap<Int, Int>(4096)
    private val xs = ArrayList<Int>(2048)
    private val ys = ArrayList<Int>(2048)
    private val zs = ArrayList<Int>(2048)
    private val cs = ArrayList<Int>(2048)
    var alwaysCount = 0
        private set

    private fun key(x: Int, y: Int, z: Int) = ((x + 128) shl 20) or ((y + 128) shl 10) or (z + 128)

    fun add(x: Int, y: Int, z: Int, color: Int) {
        if (x < -120 || x > 120 || y < -120 || y > 120 || z < -120 || z > 120) return
        val k = key(x, y, z)
        if (index.containsKey(k)) return
        index[k] = xs.size
        xs.add(x); ys.add(y); zs.add(z); cs.add(color)
    }

    fun has(x: Int, y: Int, z: Int) = index.containsKey(key(x, y, z))

    /** Помечает всё добавленное к этому моменту как «всегда видимое» (земля). */
    fun sealGround() {
        alwaysCount = xs.size
    }

    /**
     * Сортирует растущую часть так, чтобы растение раскрывалось снизу вверх
     * и от центра наружу.
     */
    fun sortGrowth(rnd: java.util.Random) {
        val n = xs.size
        if (n <= alwaysCount) return
        // Ключ считаем заранее: вызывать генератор случайных чисел внутри
        // компаратора нельзя — порядок перестаёт быть согласованным.
        val keys = DoubleArray(n)
        for (i in alwaysCount until n) {
            val r = Math.hypot(xs[i].toDouble(), zs[i].toDouble())
            keys[i] = ys[i] * 2.6 + r * 0.55 + rnd.nextDouble() * 1.4
        }
        val idx = (alwaysCount until n).sortedBy { keys[it] }
        val nx = IntArray(n); val ny = IntArray(n); val nz = IntArray(n); val nc = IntArray(n)
        for (i in 0 until alwaysCount) {
            nx[i] = xs[i]; ny[i] = ys[i]; nz[i] = zs[i]; nc[i] = cs[i]
        }
        idx.forEachIndexed { off, src ->
            val i = alwaysCount + off
            nx[i] = xs[src]; ny[i] = ys[src]; nz[i] = zs[src]; nc[i] = cs[src]
        }
        for (i in 0 until n) {
            xs[i] = nx[i]; ys[i] = ny[i]; zs[i] = nz[i]; cs[i] = nc[i]
        }
        index.clear()
        for (i in 0 until n) index[key(xs[i], ys[i], zs[i])] = i
    }

    fun build(): VoxelModel {
        val n = xs.size
        val ax = IntArray(n) { xs[it] }
        val ay = IntArray(n) { ys[it] }
        val az = IntArray(n) { zs[it] }
        val ac = IntArray(n) { cs[it] }
        val shade = FloatArray(n)
        val hide = IntArray(n)
        for (i in 0 until n) {
            val x = ax[i]; val y = ay[i]; val z = az[i]
            // Лёгкий шум яркости — воксели перестают выглядеть заливкой.
            val h = (x * 73856093) xor (y * 19349663) xor (z * 83492791)
            shade[i] = 0.94f + ((h ushr 8) and 0xFF) / 255f * 0.12f
            var maxN = -1
            var closed = true
            for (d in 0 until 6) {
                val nx = x + if (d == 0) 1 else if (d == 1) -1 else 0
                val ny = y + if (d == 2) 1 else if (d == 3) -1 else 0
                val nz = z + if (d == 4) 1 else if (d == 5) -1 else 0
                val ni = index[key(nx, ny, nz)]
                if (ni == null) { closed = false; break }
                if (ni > maxN) maxN = ni
            }
            hide[i] = if (closed) maxN + 1 else Int.MAX_VALUE
        }
        return VoxelModel(
            ax, ay, az, ac, shade, hide, alwaysCount,
            ax.minOrNull() ?: 0, ax.maxOrNull() ?: 0,
            ay.minOrNull() ?: 0, ay.maxOrNull() ?: 0,
            az.minOrNull() ?: 0, az.maxOrNull() ?: 0,
        )
    }
}

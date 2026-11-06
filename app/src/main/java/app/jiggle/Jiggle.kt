package app.jiggle

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Желе-эффект: закрашенная область колышется на месте, края «привязаны» к фото. */
class Jiggle(val bmp: Bitmap, mask: Bitmap, private val sens: Int, private val spr: Int, edge: Int) {
    val mw: Int
    val mh: Int
    private val nw: Int
    private val nh: Int
    private val cw: Float
    private val ch: Float
    private val orig: FloatArray
    val verts: FloatArray
    private val wt: FloatArray
    private var bx0 = 0
    private var bx1 = -1
    private var by0 = 0
    private var by1 = -1
    private var cx = 0f
    private var cy = 0f
    private val sc = bmp.width / 800f
    private var x = 0f; private var y = 0f; private var vx = 0f; private var vy = 0f
    private var q = 0f; private var vq = 0f
    private var x2 = 0f; private var y2 = 0f; private var vx2 = 0f; private var vy2 = 0f

    init {
        val w = bmp.width
        val h = bmp.height
        val cell = max(w, h) / 48f
        mw = max(2, (w / cell).roundToInt())
        mh = max(2, (h / cell).roundToInt())
        nw = mw + 1
        nh = mh + 1
        cw = w.toFloat() / mw
        ch = h.toFloat() / mh
        orig = FloatArray(nw * nh * 2)
        for (j in 0 until nh) for (i in 0 until nw) {
            val k = (j * nw + i) * 2
            orig[k] = i * cw
            orig[k + 1] = j * ch
        }
        verts = orig.copyOf()

        val mk = if (mask.width == w && mask.height == h) mask else Bitmap.createScaledBitmap(mask, w, h, true)
        val px = IntArray(w * h)
        mk.getPixels(px, 0, w, 0, 0, w, h)
        var a = FloatArray(nw * nh)
        val step = max(1, (min(cw, ch) / 5).toInt())
        val hx = (cw / 2).toInt()
        val hy = (ch / 2).toInt()
        for (j in 0 until nh) for (i in 0 until nw) {
            val cxp = (i * cw).toInt()
            val cyp = (j * ch).toInt()
            var s = 0f
            var n = 0
            var yy = max(0, cyp - hy)
            while (yy <= min(h - 1, cyp + hy)) {
                var xx = max(0, cxp - hx)
                while (xx <= min(w - 1, cxp + hx)) {
                    s += (px[yy * w + xx] ushr 24).toFloat()
                    n++
                    xx += step
                }
                yy += step
            }
            a[j * nw + i] = if (n > 0) s / n / 255f else 0f
        }
        val r = max(1, ((edge * .8f + 1f) * 10f / 800f * w / min(cw, ch)).roundToInt())
        a = blur(a, nw, nh, r)
        a = blur(a, nw, nh, r)

        wt = FloatArray(nw * nh)
        var x0 = Int.MAX_VALUE; var x1 = -1; var y0 = Int.MAX_VALUE; var y1 = -1
        var sx = 0f; var sy = 0f; var sw = 0f
        for (j in 0 until nh) for (i in 0 until nw) {
            var v = min(1f, a[j * nw + i] * 1.7f)
            v = v * v * (3f - 2f * v)
            wt[j * nw + i] = v
            if (v > .02f) {
                if (i < x0) x0 = i
                if (i > x1) x1 = i
                if (j < y0) y0 = j
                if (j > y1) y1 = j
                sx += i * v; sy += j * v; sw += v
            }
        }
        if (sw > 0f) {
            bx0 = x0; bx1 = x1; by0 = y0; by1 = y1
            cx = sx / sw * cw
            cy = sy / sw * ch
        }
    }

    /** ax, ay — ускорение телефона; содержимое по инерции едет в обратную сторону. */
    fun kick(ax: Float, ay: Float) {
        val m = sens * .18f * sc
        vx += ax * m
        vy += ay * m
        vq += (abs(ax) + abs(ay)) * sens * .18f * .0009f
    }

    /** Шаг физики. Возвращает true, пока что-то движется. */
    fun step(): Boolean {
        if (bx1 < 0) return false
        val k = .03f + spr * .012f
        val d = .965f - spr * .004f
        vx += -x * k; vy += -y * k; vq += -q * k * 1.3f
        vx *= d; vy *= d; vq *= d
        x += vx; y += vy; q += vq
        vx2 += (x - x2) * k * .6f; vy2 += (y - y2) * k * .6f
        vx2 *= d; vy2 *= d
        x2 += vx2; y2 += vy2
        val lim = bmp.width * .06f
        x = x.coerceIn(-lim, lim); y = y.coerceIn(-lim, lim); q = q.coerceIn(-.12f, .12f)
        val still = (abs(x) + abs(y) + abs(vx) + abs(vy) + abs(q * 100) + abs(vq * 100) + abs(x2) + abs(vx2)) < .08f * sc
        if (still) {
            x = 0f; y = 0f; vx = 0f; vy = 0f; q = 0f; vq = 0f
            x2 = 0f; y2 = 0f; vx2 = 0f; vy2 = 0f
        }
        compute()
        return !still
    }

    private fun compute() {
        val span = max(1f, (by1 - by0) * ch)
        val ja = max(0, by0 - 1)
        val jb = min(nh - 1, by1 + 1)
        val ia = max(0, bx0 - 1)
        val ib = min(nw - 1, bx1 + 1)
        for (j in ja..jb) {
            val py = j * ch
            val t = ((py - by0 * ch) / span).coerceIn(0f, 1f)
            val ox = x * (1 - t) + x2 * t
            val oy = y * (1 - t) + y2 * t
            for (i in ia..ib) {
                val kk = j * nw + i
                val w = wt[kk]
                val px = i * cw
                val dx = if (w < .003f) 0f else w * (ox + (px - cx) * q)
                val dy = if (w < .003f) 0f else w * (oy - (py - cy) * q)
                verts[kk * 2] = orig[kk * 2] + dx
                verts[kk * 2 + 1] = orig[kk * 2 + 1] + dy
            }
        }
    }

    private fun blur(a: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val t = FloatArray(a.size)
        val o = FloatArray(a.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f
            for (k in -r..r) { val xx = x + k; if (xx in 0 until w) s += a[y * w + xx] }
            t[y * w + x] = s / (2 * r + 1)
        }
        for (x in 0 until w) for (y in 0 until h) {
            var s = 0f
            for (k in -r..r) { val yy = y + k; if (yy in 0 until h) s += t[yy * w + x] }
            o[y * w + x] = s / (2 * r + 1)
        }
        return o
    }
}

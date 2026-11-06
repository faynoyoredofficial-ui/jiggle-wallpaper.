package app.jiggle

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

class MaskView(c: Context) : View(c) {
    var photo: Bitmap? = null
    var mask: Bitmap? = null
    private var mc: Canvas? = null
    var brush = 60f
    var erase = false
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val tint = Paint().apply {
        colorFilter = PorterDuffColorFilter(0x99FF4060.toInt(), PorterDuff.Mode.SRC_IN)
    }
    private val m = Matrix()
    private val inv = Matrix()
    private var scale = 1f
    private var lx = 0f
    private var ly = 0f

    fun setPhoto(b: Bitmap) {
        photo = b
        val mk = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        mask = mk
        mc = Canvas(mk)
        invalidate()
    }

    fun clearMask() { mask?.eraseColor(0); invalidate() }

    private fun calc() {
        val p = photo ?: return
        val s = minOf(width / p.width.toFloat(), height / p.height.toFloat())
        scale = s
        m.setScale(s, s)
        m.postTranslate((width - p.width * s) / 2f, (height - p.height * s) / 2f)
        m.invert(inv)
    }

    override fun onDraw(c: Canvas) {
        val p = photo ?: return
        val mk = mask ?: return
        calc()
        c.drawBitmap(p, m, null)
        c.drawBitmap(mk, m, tint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val cvs = mc ?: return false
        calc()
        val pt = floatArrayOf(e.x, e.y)
        inv.mapPoints(pt)
        val xf = if (erase) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
        line.xfermode = xf
        dot.xfermode = xf
        val bw = brush / scale
        line.strokeWidth = bw
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cvs.drawCircle(pt[0], pt[1], bw / 2, dot)
                lx = pt[0]; ly = pt[1]
            }
            MotionEvent.ACTION_MOVE -> {
                cvs.drawLine(lx, ly, pt[0], pt[1], line)
                cvs.drawCircle(pt[0], pt[1], bw / 2, dot)
                lx = pt[0]; ly = pt[1]
            }
        }
        invalidate()
        return true
    }
}

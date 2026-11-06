package app.jiggle

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.io.File
import kotlin.math.abs
import kotlin.math.max

class JiggleWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = E()

    inner class E : Engine(), SensorEventListener {
        private val h = Handler(Looper.getMainLooper())
        private val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private var jig: Jiggle? = null
        private var ver = -1L
        private var sw = 0
        private var sh = 0
        private var vis = false
        private var looping = false
        private var gx = 0f
        private var gy = 0f

        private val tick = object : Runnable {
            override fun run() {
                looping = false
                val j = jig ?: return
                val active = j.step()
                draw()
                if (active && vis) {
                    looping = true
                    h.postDelayed(this, 16)
                }
            }
        }

        private fun load() {
            val prefs = getSharedPreferences("jiggle", MODE_PRIVATE)
            val v = prefs.getLong("ver", 0)
            if (v == ver && jig != null) return
            ver = v
            val f = File(filesDir, "photo.jpg")
            val m = File(filesDir, "mask.png")
            if (!f.exists() || !m.exists()) { jig = null; return }
            val photo = BitmapFactory.decodeFile(f.path) ?: return
            val mask = BitmapFactory.decodeFile(m.path) ?: return
            jig = Jiggle(photo, mask, prefs.getInt("sens", 5), prefs.getInt("spr", 5), prefs.getInt("edge", 5))
        }

        private fun draw() {
            val holder = surfaceHolder
            val c = holder.lockCanvas() ?: return
            try {
                c.drawColor(Color.BLACK)
                val j = jig
                if (j != null && sw > 0) {
                    val s = max(sw / j.bmp.width.toFloat(), sh / j.bmp.height.toFloat())
                    c.save()
                    c.translate((sw - j.bmp.width * s) / 2f, (sh - j.bmp.height * s) / 2f)
                    c.scale(s, s)
                    c.drawBitmapMesh(j.bmp, j.mw, j.mh, j.verts, 0, null, 0, null)
                    c.restore()
                }
            } finally {
                holder.unlockCanvasAndPost(c)
            }
        }

        private fun register() {
            val lin = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
                ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            sm.registerListener(this, lin, SensorManager.SENSOR_DELAY_GAME)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            vis = visible
            if (visible) {
                load()
                register()
                draw()
            } else {
                sm.unregisterListener(this)
                h.removeCallbacks(tick)
                looping = false
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            sw = width
            sh = height
            draw()
        }

        override fun onDestroy() {
            sm.unregisterListener(this)
            h.removeCallbacks(tick)
            super.onDestroy()
        }

        override fun onSensorChanged(e: SensorEvent) {
            var ax = e.values[0]
            var ay = e.values[1]
            if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                gx = gx * .9f + ax * .1f
                gy = gy * .9f + ay * .1f
                ax -= gx
                ay -= gy
            }
            if (abs(ax) + abs(ay) < 1.2f) return
            jig?.kick(-ax, ay)
            if (!looping) { looping = true; h.post(tick) }
        }

        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }
}

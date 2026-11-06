package app.jiggle

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class MainActivity : Activity() {
    private lateinit var mv: MaskView
    private var sens = 5
    private var spr = 5
    private var edge = 5

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val prefs = getSharedPreferences("jiggle", MODE_PRIVATE)
        sens = prefs.getInt("sens", 5); spr = prefs.getInt("spr", 5); edge = prefs.getInt("edge", 5)
        val d = resources.displayMetrics.density
        val pad = (12 * d).toInt()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad) }
        mv = MaskView(this)
        mv.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)

        val top = LinearLayout(this)
        top.addView(btn("Выбрать фото") {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 1)
        })
        val er = btn("Ластик") {}
        er.setOnClickListener {
            mv.erase = !mv.erase
            er.text = if (mv.erase) "Маркер" else "Ластик"
        }
        top.addView(er)
        top.addView(btn("Очистить") { mv.clearMask() })

        root.addView(top)
        root.addView(mv)
        root.addView(bar("Маркер", 140, 60) { mv.brush = max(8, it).toFloat() })
        root.addView(bar("Сила", 9, sens - 1) { sens = it + 1 })
        root.addView(bar("Упругость", 9, spr - 1) { spr = it + 1 })
        root.addView(bar("Мягкость краёв", 9, edge - 1) { edge = it + 1 })
        root.addView(btn("Поставить обои") { apply() })
        setContentView(root)
        Toast.makeText(this, "Выберите фото и закрасьте то, что должно колыхаться", Toast.LENGTH_LONG).show()
    }

    private fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }

    private fun bar(label: String, max: Int, init: Int, on: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val t = TextView(this).apply { text = label; minWidth = (130 * resources.displayMetrics.density).toInt() }
        val s = SeekBar(this).apply {
            this.max = max; progress = init
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { on(p) }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        row.addView(t); row.addView(s)
        return row
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if (req != 1 || res != RESULT_OK) return
        try { mv.setPhoto(loadBitmap(uri)) } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть фото", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (max(o.outWidth, o.outHeight) / s > 1800) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s }
        var bmp = contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, o2)!! }
        val ori = try {
            contentResolver.openInputStream(uri)!!.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (e: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val deg = when (ori) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (deg != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
        return bmp
    }

    private fun apply() {
        val p = mv.photo
        val m = mv.mask
        if (p == null || m == null) {
            Toast.makeText(this, "Сначала выберите фото", Toast.LENGTH_SHORT).show(); return
        }
        FileOutputStream(File(filesDir, "photo.jpg")).use { p.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        FileOutputStream(File(filesDir, "mask.png")).use { m.compress(Bitmap.CompressFormat.PNG, 100, it) }
        getSharedPreferences("jiggle", MODE_PRIVATE).edit()
            .putInt("sens", sens).putInt("spr", spr).putInt("edge", edge)
            .putLong("ver", System.currentTimeMillis()).apply()
        try {
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this, JiggleWallpaperService::class.java)
                )
            )
        } catch (e: Exception) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }
}

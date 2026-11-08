package com.example.receiptprinter

import android.content.Context
import android.graphics.*
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

object Ocr {
    private fun dataRoot(ctx: Context): String {
        val dir = File(ctx.filesDir, "tessdata").apply { mkdirs() }
        for (n in listOf("ara", "eng")) {
            val f = File(dir, "$n.traineddata")
            if (!f.exists()) ctx.assets.open("tessdata/$n.traineddata").use { i ->
                f.outputStream().use { i.copyTo(it) }
            }
        }
        return ctx.filesDir.absolutePath
    }

    private fun prep(src: Bitmap): Bitmap {
        val scale = if (src.width < 1400) 1400f / src.width else 1f
        val w = (src.width * scale).toInt(); val h = (src.height * scale).toInt()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val cm = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.4f; val t = (-0.5f * contrast + 0.5f) * 255f
        cm.postConcat(ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, t, 0f, contrast, 0f, 0f, t,
            0f, 0f, contrast, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) }
        c.drawBitmap(src, null, Rect(0, 0, w, h), p)
        return out
    }

    fun run(ctx: Context, bmp: Bitmap): String {
        val api = TessBaseAPI()
        try {
            if (!api.init(dataRoot(ctx), "ara+eng")) throw Exception("فشل تحميل بيانات اللغة")
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
            api.setImage(prep(bmp))
            return api.utF8Text.lines().map { it.trim().replace(Regex("\\s{2,}"), " ") }
                .filter { it.isNotEmpty() }.joinToString("\n")
        } finally { api.recycle() }
    }
}

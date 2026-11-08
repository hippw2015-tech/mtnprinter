package com.example.receiptprinter

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream

object EscPos {

    /** اختبار نص عادي (ASCII) للتأكد من سلامة الاتصال */
    fun textTest(): List<ByteArray> = listOf(
        byteArrayOf(0x1B, 0x40),
        "\n== TEST ==\nHello 12345\nABCDEFG abcdefg\n\n\n\n".toByteArray(Charsets.US_ASCII)
    )

    /** يعيد الطباعة كشرائح صغيرة؛ كل شريحة تُرسل ثم يُنتظر قليلاً */
    fun build(bmp: Bitmap, s: PrintSettings): List<ByteArray> {
        val units = ArrayList<ByteArray>()
        units.add(byteArrayOf(0x1B, 0x40, 0x1B, 0x61, 0x00)) // reset + محاذاة يسار

        val w = bmp.width
        val h = bmp.height
        val bpr = w / 8
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)

        fun bit(x: Int, y: Int): Boolean {
            if (y >= h) return false
            val p = px[y * w + x]
            val lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
            return lum < s.threshold
        }

        when (s.mode) {
            0 -> { // GS v 0 بشرائح 24 صفاً
                var y = 0
                while (y < h) {
                    val bh = minOf(24, h - y)
                    val o = ByteArrayOutputStream()
                    o.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00,
                        (bpr and 255).toByte(), (bpr shr 8).toByte(),
                        (bh and 255).toByte(), (bh shr 8).toByte()))
                    for (r in 0 until bh) for (bx in 0 until bpr) {
                        var b = 0
                        for (i in 0..7) if (bit(bx * 8 + i, y + r)) b = b or (0x80 shr i)
                        o.write(b)
                    }
                    units.add(o.toByteArray())
                    y += bh
                }
            }
            1 -> { // ESC * 33 : 24 نقطة، كثافة مزدوجة (حتى 384 عموداً)
                units.add(byteArrayOf(0x1B, 0x33, 24))
                var y = 0
                while (y < h) {
                    val o = ByteArrayOutputStream()
                    o.write(byteArrayOf(0x1B, 0x2A, 33, (w and 255).toByte(), (w shr 8).toByte()))
                    for (x in 0 until w) for (k in 0..2) {
                        var b = 0
                        for (i in 0..7) if (bit(x, y + k * 8 + i)) b = b or (0x80 shr i)
                        o.write(b)
                    }
                    o.write(0x0A)
                    units.add(o.toByteArray())
                    y += 24
                }
                units.add(byteArrayOf(0x1B, 0x32))
            }
            else -> { // ESC * 1 : 8 نقاط، كثافة مزدوجة (الأقدم والأكثر توافقاً)
                units.add(byteArrayOf(0x1B, 0x33, 8))
                var y = 0
                while (y < h) {
                    val o = ByteArrayOutputStream()
                    o.write(byteArrayOf(0x1B, 0x2A, 1, (w and 255).toByte(), (w shr 8).toByte()))
                    for (x in 0 until w) {
                        var b = 0
                        for (i in 0..7) if (bit(x, y + i)) b = b or (0x80 shr i)
                        o.write(b)
                    }
                    o.write(0x0A)
                    units.add(o.toByteArray())
                    y += 8
                }
                units.add(byteArrayOf(0x1B, 0x32))
            }
        }

        units.add(byteArrayOf(0x1B, 0x64, s.feedLines.toByte()))
        if (s.cut) units.add(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
        return units
    }
}

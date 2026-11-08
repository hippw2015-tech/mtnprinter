package com.example.receiptprinter

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream

object EscPos {
    private const val BAND = 96 // عدد الصفوف في كل أمر طباعة

    fun build(bmp: Bitmap, s: PrintSettings): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x1B, 0x40))        // reset
        out.write(byteArrayOf(0x1B, 0x61, 0x00))  // محاذاة يسار
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
        if (!s.compat) {
            var y = 0
            while (y < h) {
                val bh = minOf(BAND, h - y)
                out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00,
                    (bpr and 255).toByte(), (bpr shr 8).toByte(),
                    (bh and 255).toByte(), (bh shr 8).toByte()))
                for (r in 0 until bh) for (bx in 0 until bpr) {
                    var b = 0
                    for (i in 0..7) if (bit(bx * 8 + i, y + r)) b = b or (0x80 shr i)
                    out.write(b)
                }
                y += bh
            }
        } else {
            out.write(byteArrayOf(0x1B, 0x33, 24))
            var y = 0
            while (y < h) {
                out.write(byteArrayOf(0x1B, 0x2A, 33, (w and 255).toByte(), (w shr 8).toByte()))
                for (x in 0 until w) for (k in 0..2) {
                    var b = 0
                    for (i in 0..7) if (bit(x, y + k * 8 + i)) b = b or (0x80 shr i)
                    out.write(b)
                }
                out.write(0x0A)
                y += 24
            }
            out.write(byteArrayOf(0x1B, 0x32))
        }
        out.write(byteArrayOf(0x1B, 0x64, s.feedLines.toByte()))
        if (s.cut) out.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
        return out.toByteArray()
    }
}

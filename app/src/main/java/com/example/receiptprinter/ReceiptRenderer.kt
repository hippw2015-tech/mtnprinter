package com.example.receiptprinter

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint

/** يرسم النص نفسه كصورة بعرض الورقة بالضبط، فلا حاجة لدعم الطابعة للعربية. */
object ReceiptRenderer {
    fun render(text: String, s: PrintSettings): Bitmap {
        val width = s.paperDots
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = s.textSize
            typeface = if (s.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (s.extraBold) {
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = s.textSize * 0.045f
            }
        }
        val inner = width - s.margin * 2
        val layouts = text.lines().map { line ->
            val l = if (line.isEmpty()) " " else line
            val dir = when (s.align) {
                1 -> TextDirectionHeuristics.RTL
                3 -> TextDirectionHeuristics.LTR
                else -> TextDirectionHeuristics.FIRSTSTRONG_LTR
            }
            val al = if (s.align == 2) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
            StaticLayout.Builder.obtain(l, 0, l.length, paint, inner)
                .setAlignment(al).setTextDirection(dir)
                .setLineSpacing(0f, s.lineSpacing).setIncludePad(false).build()
        }
        val h = layouts.sumOf { it.height } + s.margin * 2
        val bmp = Bitmap.createBitmap(width, h.coerceAtLeast(8), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        var y = s.margin.toFloat()
        for (l in layouts) {
            c.save(); c.translate(s.margin.toFloat(), y); l.draw(c); c.restore()
            y += l.height
        }
        return bmp
    }
}

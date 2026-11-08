package com.example.receiptprinter

import android.content.SharedPreferences

data class PrintSettings(
    val paperDots: Int = 384,      // 58mm = 384 , 80mm = 576
    val textSize: Float = 26f,
    val bold: Boolean = true,
    val extraBold: Boolean = false,
    val lineSpacing: Float = 1.15f,
    val align: Int = 0,            // 0 تلقائي 1 يمين 2 وسط 3 يسار
    val threshold: Int = 150,      // أعلى = أغمق
    val digits: Int = 0,           // 0 كما هي 1 إنجليزية 2 عربية
    val margin: Int = 6,
    val feedLines: Int = 4,
    val cut: Boolean = false,
    val mode: Int = 1,             // 0 = GS v 0 ، 1 = ESC * 24 نقطة ، 2 = ESC * 8 نقاط
    val chunkDelay: Int = 8,       // تأخير بين الحزم الصغيرة (ms)
    val unitDelay: Int = 60        // تأخير بين شرائح الصورة (ms)
)

object Store {
    fun load(p: SharedPreferences) = PrintSettings(
        paperDots = p.getInt("paper", 384),
        textSize = p.getFloat("size", 26f),
        bold = p.getBoolean("bold", true),
        extraBold = p.getBoolean("xbold", false),
        lineSpacing = p.getFloat("ls", 1.15f),
        align = p.getInt("align", 0),
        threshold = p.getInt("thr", 150),
        digits = p.getInt("digits", 0),
        margin = p.getInt("margin", 6),
        feedLines = p.getInt("feed", 4),
        cut = p.getBoolean("cut", false),
        mode = p.getInt("mode2", 1),
        chunkDelay = p.getInt("cdelay", 8),
        unitDelay = p.getInt("udelay", 60)
    )

    fun save(p: SharedPreferences, s: PrintSettings) {
        p.edit().putInt("paper", s.paperDots).putFloat("size", s.textSize)
            .putBoolean("bold", s.bold).putBoolean("xbold", s.extraBold)
            .putFloat("ls", s.lineSpacing).putInt("align", s.align)
            .putInt("thr", s.threshold).putInt("digits", s.digits)
            .putInt("margin", s.margin).putInt("feed", s.feedLines)
            .putBoolean("cut", s.cut).putInt("mode2", s.mode)
            .putInt("cdelay", s.chunkDelay).putInt("udelay", s.unitDelay).apply()
    }
}

fun convertDigits(s: String, mode: Int): String = buildString {
    for (c in s) when {
        mode == 1 && c in '\u0660'..'\u0669' -> append('0' + (c - '\u0660'))
        mode == 1 && c in '\u06F0'..'\u06F9' -> append('0' + (c - '\u06F0'))
        mode == 2 && c in '0'..'9' -> append('\u0660' + (c - '0'))
        else -> append(c)
    }
}

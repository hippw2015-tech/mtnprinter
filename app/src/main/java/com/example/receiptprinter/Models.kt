package com.example.receiptprinter

import android.content.SharedPreferences

data class PrintSettings(
    val paperDots: Int = 384,      // 58mm = 384 , 80mm = 576
    val textSize: Float = 26f,     // بالنقاط (dots)
    val bold: Boolean = true,
    val extraBold: Boolean = false,
    val lineSpacing: Float = 1.15f,
    val align: Int = 0,            // 0 تلقائي 1 يمين 2 وسط 3 يسار
    val threshold: Int = 150,      // أعلى = أغمق
    val digits: Int = 0,           // 0 كما هي 1 أرقام إنجليزية 2 أرقام عربية
    val margin: Int = 6,
    val feedLines: Int = 4,
    val cut: Boolean = false,
    val delayMs: Int = 25,         // تأخير بين الحزم لمنع الرموز الصينية
    val compat: Boolean = false    // وضع ESC * للطابعات القديمة
)

object Store {
    fun load(p: SharedPreferences) = PrintSettings(
        p.getInt("paper", 384), p.getFloat("size", 26f), p.getBoolean("bold", true),
        p.getBoolean("xbold", false), p.getFloat("ls", 1.15f), p.getInt("align", 0),
        p.getInt("thr", 150), p.getInt("digits", 0), p.getInt("margin", 6),
        p.getInt("feed", 4), p.getBoolean("cut", false), p.getInt("delay", 25),
        p.getBoolean("compat", false)
    )

    fun save(p: SharedPreferences, s: PrintSettings) {
        p.edit().putInt("paper", s.paperDots).putFloat("size", s.textSize)
            .putBoolean("bold", s.bold).putBoolean("xbold", s.extraBold)
            .putFloat("ls", s.lineSpacing).putInt("align", s.align)
            .putInt("thr", s.threshold).putInt("digits", s.digits)
            .putInt("margin", s.margin).putInt("feed", s.feedLines)
            .putBoolean("cut", s.cut).putInt("delay", s.delayMs)
            .putBoolean("compat", s.compat).apply()
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

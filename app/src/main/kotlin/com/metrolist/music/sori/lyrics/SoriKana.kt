package com.metrolist.music.sori.lyrics

/**
 * Japanese readings (kana) written in Hangul, so Korean listeners can sing along: 愛してる is shown
 * as 아이시테루 rather than "aishiteru". Used in place of romaji under Japanese lyrics.
 *
 * Spelling follows how the kana sound (カ 카, ツ 쓰, ス 스); ン and ッ become ㄴ and ㅅ
 * final consonants, ー is dropped, and small ァィゥェォャュョ change the vowel of the kana before.
 */
object SoriKana {
    /** Replaces upstream's romaji for Japanese lines. */
    val ENABLED = true

    private const val A = 0
    private const val YA = 2
    private const val E = 5
    private const val O = 8
    private const val WA = 9
    private const val YO = 12
    private const val U = 13
    private const val WO = 14
    private const val WE = 15
    private const val WI = 16
    private const val YU = 17
    private const val EU = 18
    private const val I = 20

    // Initial consonants (Hangul choseong index).
    private const val G = 0
    private const val N = 2
    private const val D = 3
    private const val R = 5
    private const val M = 6
    private const val B = 7
    private const val S = 9
    private const val SS = 10
    private const val NG = 11
    private const val J = 12
    private const val CH = 14
    private const val K = 15
    private const val T = 16
    private const val P = 17
    private const val H = 18

    private const val JONG_N = 4
    private const val JONG_S = 19

    private val BASE: Map<Char, Pair<Int, Int>> = buildMap {
        fun row(kana: String, vararg syllables: Pair<Int, Int>) =
            kana.forEachIndexed { i, c -> put(c, syllables[i]) }
        row("アイウエオ", NG to A, NG to I, NG to U, NG to E, NG to O)
        row("カキクケコ", K to A, K to I, K to U, K to E, K to O)
        row("ガギグゲゴ", G to A, G to I, G to U, G to E, G to O)
        row("サシスセソ", S to A, S to I, S to EU, S to E, S to O)
        row("ザジズゼゾ", J to A, J to I, J to EU, J to E, J to O)
        row("タチツテト", T to A, CH to I, SS to EU, T to E, T to O)
        row("ダヂヅデド", D to A, J to I, J to EU, D to E, D to O)
        row("ナニヌネノ", N to A, N to I, N to U, N to E, N to O)
        row("ハヒフヘホ", H to A, H to I, H to U, H to E, H to O)
        row("バビブベボ", B to A, B to I, B to U, B to E, B to O)
        row("パピプペポ", P to A, P to I, P to U, P to E, P to O)
        row("マミムメモ", M to A, M to I, M to U, M to E, M to O)
        row("ヤユヨ", NG to YA, NG to YU, NG to YO)
        row("ラリルレロ", R to A, R to I, R to U, R to E, R to O)
        row("ワヲヴ", NG to WA, NG to O, B to U)
    }

    // Small kana that only change the vowel of the syllable before them.
    private val SMALL_Y = mapOf('ャ' to YA, 'ュ' to YU, 'ョ' to YO)
    private val SMALL_V = mapOf('ァ' to A, 'ィ' to I, 'ゥ' to U, 'ェ' to E, 'ォ' to O)

    private class Syllable(var cho: Int, var jung: Int, var jong: Int = 0) {
        fun toChar() = (0xAC00 + (cho * 21 + jung) * 28 + jong).toChar()
    }

    fun toHangul(reading: String?): String {
        if (reading.isNullOrEmpty()) return ""
        val out = StringBuilder()
        var last: Syllable? = null
        fun flush() {
            last?.let { out.append(it.toChar()) }
            last = null
        }
        for (raw in reading) {
            val c = if (raw in 'ぁ'..'ゖ') raw + 0x60 else raw // hiragana to katakana
            val prev = last
            when {
                c in BASE -> {
                    flush()
                    val (cho, jung) = BASE.getValue(c)
                    last = Syllable(cho, jung)
                }
                c in SMALL_Y && prev != null -> {
                    // シャ 샤, but チャ 차 and ジャ 자: ㅈ/ㅊ/ㅉ already carry the y sound.
                    val y = SMALL_Y.getValue(c)
                    prev.jung = if (prev.cho == J || prev.cho == CH || prev.cho == SS) {
                        mapOf(YA to A, YU to U, YO to O).getValue(y)
                    } else {
                        y
                    }
                }
                c in SMALL_V && prev != null -> {
                    val v = SMALL_V.getValue(c)
                    // ウィ 위, ウェ 웨, ウォ 워, ファ 화 (u-sounds glide); otherwise the vowel is replaced.
                    prev.jung = if (prev.jung == U && (prev.cho == NG || prev.cho == H)) {
                        when (v) {
                            A -> WA
                            I -> WI
                            E -> WE
                            O -> WO
                            else -> U
                        }
                    } else {
                        v
                    }
                }
                c == 'ン' -> if (prev != null && prev.jong == 0) prev.jong = JONG_N else {
                    flush()
                    out.append('응')
                }
                c == 'ッ' -> if (prev != null && prev.jong == 0) prev.jong = JONG_S
                c == 'ー' -> Unit
                else -> {
                    flush()
                    out.append(raw)
                }
            }
        }
        flush()
        return out.toString()
    }
}

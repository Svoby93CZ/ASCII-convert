package cz.svoby93.asciistudio.ui.studio

import kotlin.math.floor

/**
 * A word blinked in Morse code, like the signal lamp of a ship: a dot is one unit of light, a dash
 * three, with one dark unit between them, three between letters and seven before the word comes
 * again. One unit lasts [unitSeconds].
 */
class MorseLight(word: String, private val unitSeconds: Float = UNIT_SECONDS) {

    /** Light on or off, unit by unit, for one round of the word and the pause after it. */
    internal val units: BooleanArray = buildList {
        word.uppercase().forEachIndexed { index, letter ->
            if (index > 0) repeat(LETTER_GAP) { add(false) }
            val code = requireNotNull(CODES[letter]) { "No Morse code for $letter" }
            code.forEachIndexed { mark, symbol ->
                if (mark > 0) add(false)
                repeat(if (symbol == '-') DASH else DOT) { add(true) }
            }
        }
        repeat(WORD_GAP) { add(false) }
    }.toBooleanArray()

    /** Whether the light is on [seconds] after the word began for the first time. */
    fun isOn(seconds: Float): Boolean = units[floor(seconds / unitSeconds).toInt().mod(units.size)]

    private companion object {
        /** Five words a minute, the pace of a beginner, so that the letters can be read by eye. */
        const val UNIT_SECONDS = 0.24f
        const val DOT = 1
        const val DASH = 3
        const val LETTER_GAP = 3
        const val WORD_GAP = 7

        val CODES = mapOf(
            'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.",
            'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
            'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.",
            'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
            'Y' to "-.--", 'Z' to "--..",
        )
    }
}

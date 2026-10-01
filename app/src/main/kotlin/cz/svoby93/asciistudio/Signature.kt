package cz.svoby93.asciistudio

/**
 * The nickname of the author, hidden in the app as easter eggs:
 * - the light in the border of the start screen blinks it in Morse code,
 * - the About dialog is signed with it in Braille,
 * - now and then the rain background spells it,
 * - five quick taps turn the donut into the name in 3D, which also unlocks a look drawn with its
 *   letters; typing the name as custom characters unlocks the look too.
 *
 * Seven taps on the version in the About dialog switch on a developer mode, which shows how fast
 * the art is converted and recorded.
 */
object Signature {
    const val NAME = "SVOBY"

    /** [NAME] in Braille: the letters s, v, o, b and y. */
    const val BRAILLE = "⠎⠧⠕⠃⠽"
}

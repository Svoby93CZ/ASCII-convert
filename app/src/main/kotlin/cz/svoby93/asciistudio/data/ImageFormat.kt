package cz.svoby93.asciistudio.data

import androidx.annotation.StringRes
import cz.svoby93.asciistudio.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Sizes of exported pictures: the art's own, or a frame for a purpose with the art centred in it. */
enum class ImageFormat(@StringRes val label: Int) {
    /** The art at a fixed size per character, with a small margin. */
    ORIGINAL(R.string.format_original),

    /** 1080 × 1080, for posts. */
    SQUARE(R.string.format_square),

    /** 1080 × 1350, the tallest post that Instagram shows uncropped. */
    PORTRAIT(R.string.format_portrait),

    /** 1080 × 1920, for stories and reels; their buttons cover the top and the bottom. */
    STORY(R.string.format_story),

    /** The phone's screen, clear of the clock above and the dock below. */
    WALLPAPER(R.string.format_wallpaper),

    /** A4 at 300 dpi with margins of 10 mm, on its side for wide art. */
    PRINT_A4(R.string.format_print),
    ;

    /**
     * The picture for art of [artWidth] × [artHeight] in any unit, or `null` for [ORIGINAL].
     * [screenWidth] × [screenHeight] is the phone's screen in pixels, in either orientation.
     */
    fun frame(artWidth: Float, artHeight: Float, screenWidth: Int, screenHeight: Int): Frame? = when (this) {
        ORIGINAL -> null
        SQUARE -> Frame(POST_WIDTH, POST_WIDTH, POST_MARGIN, POST_MARGIN)
        PORTRAIT -> Frame(POST_WIDTH, PORTRAIT_HEIGHT, POST_MARGIN, POST_MARGIN)
        STORY -> Frame(POST_WIDTH, STORY_HEIGHT, POST_MARGIN, STORY_MARGIN)
        WALLPAPER -> {
            val width = min(screenWidth, screenHeight)
            val height = max(screenWidth, screenHeight)
            Frame(width, height, (width * WALLPAPER_SIDE).roundToInt(), (height * WALLPAPER_TOP).roundToInt())
        }
        PRINT_A4 -> if (artWidth > artHeight) {
            Frame(A4_LONG, A4_SHORT, A4_MARGIN, A4_MARGIN)
        } else {
            Frame(A4_SHORT, A4_LONG, A4_MARGIN, A4_MARGIN)
        }
    }

    private companion object {
        const val POST_WIDTH = 1080
        const val PORTRAIT_HEIGHT = 1350
        const val STORY_HEIGHT = 1920
        const val POST_MARGIN = 64

        /** Stories cover about 250 pixels at the top with the profile and at the bottom with the reply bar. */
        const val STORY_MARGIN = 250
        const val WALLPAPER_SIDE = 0.06f
        const val WALLPAPER_TOP = 0.12f

        /** A4 is 210 × 297 mm; at 300 dpi that is 2480 × 3508 pixels, and 10 mm are 118. */
        const val A4_SHORT = 2480
        const val A4_LONG = 3508
        const val A4_MARGIN = 118
    }
}

/**
 * A picture of [width] × [height] in any unit, whose art keeps [sideMargin] from the left and right
 * edge and [topMargin] from the top and bottom edge.
 */
class Frame(val width: Int, val height: Int, val sideMargin: Int, val topMargin: Int) {

    /** How to draw art of [artWidth] × [artHeight] as large as fits, centred within the margins. */
    fun place(artWidth: Float, artHeight: Float): Placement {
        val boxWidth = width - 2f * sideMargin
        val boxHeight = height - 2f * topMargin
        val scale = min(boxWidth / artWidth, boxHeight / artHeight)
        return Placement(
            scale = scale,
            left = sideMargin + (boxWidth - artWidth * scale) / 2f,
            top = topMargin + (boxHeight - artHeight * scale) / 2f,
        )
    }
}

/** Draw the art scaled by [scale] with its top left corner at [left], [top]. */
data class Placement(val scale: Float, val left: Float, val top: Float)

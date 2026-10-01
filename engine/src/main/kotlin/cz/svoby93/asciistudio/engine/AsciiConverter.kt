package cz.svoby93.asciistudio.engine

import kotlin.math.roundToInt

/**
 * Converts images into [AsciiArt].
 *
 * The converter is stateless and thread-safe; a conversion is a pure function of the image and
 * the options, which makes it trivial to run on a background dispatcher and to cancel by simply
 * discarding outdated results.
 *
 * A conversion has two halves. [sample] averages the image into the grids the options need; it
 * reads every pixel of the image and takes most of the time. The [convert] that takes [Samples]
 * turns the grids into glyphs, and settings that only change tones, dithering or the
 * sensitivity of outlines can repeat it on the same samples, as [CachingConverter] does.
 */
object AsciiConverter {

    fun convert(image: PixelImage, options: AsciiOptions): AsciiArt = convert(sample(image, options), options)

    /** Number of text rows needed to keep the image proportions for the given cell shape. */
    fun rowsFor(imageWidth: Int, imageHeight: Int, columns: Int, cellAspect: Float): Int =
        (columns * (imageHeight.toFloat() / imageWidth) * cellAspect).roundToInt().coerceAtLeast(1)

    /** The second half of a conversion: tones, dithering, glyphs and outlines from [samples]. */
    fun convert(samples: Samples, options: AsciiOptions): AsciiArt {
        require(samples.fit(options)) { "The samples were made for other options" }
        return when (val glyphs = options.glyphs) {
            is GlyphSet.Ramp -> convertRamp(samples, options, glyphs.ramp)
            GlyphSet.Braille -> convertBraille(samples, options)
        }
    }

    /** The first half of a conversion: [image] averaged into the grids that [options] need. */
    fun sample(image: PixelImage, options: AsciiOptions): Samples {
        val width = image.width
        val height = image.height
        val columns = options.columns
        return when (options.glyphs) {
            is GlyphSet.Ramp -> {
                val rows = rowsFor(width, height, columns, options.cellAspect)
                // Outlines need a finer view of the image; the cell grid is then derived from it.
                val fine = if (options.edgeMode == EdgeMode.OFF) {
                    null
                } else {
                    Sampler.sample(image, columns * EDGE_OVERSAMPLING, rows * EDGE_OVERSAMPLING)
                }
                Samples(
                    imageWidth = width,
                    imageHeight = height,
                    columns = columns,
                    rows = rows,
                    cellAspect = options.cellAspect,
                    braille = false,
                    grid = fine?.downsample(EDGE_OVERSAMPLING, EDGE_OVERSAMPLING)
                        ?: Sampler.sample(image, columns, rows),
                    fine = fine,
                )
            }
            GlyphSet.Braille -> {
                val rows = rowsFor(width, height, columns, AsciiOptions.BRAILLE_CELL_ASPECT)
                Samples(
                    imageWidth = width,
                    imageHeight = height,
                    columns = columns,
                    rows = rows,
                    cellAspect = AsciiOptions.BRAILLE_CELL_ASPECT,
                    braille = true,
                    grid = Sampler.sample(image, columns * Braille.DOTS_X, rows * Braille.DOTS_Y),
                    fine = null,
                )
            }
        }
    }

    private fun convertRamp(samples: Samples, options: AsciiOptions, ramp: CharRamp): AsciiArt {
        val columns = samples.columns
        val rows = samples.rows
        val grid = samples.grid

        val ink = ToneMapper.toInk(grid, options)
        val indices = Quantizer.quantize(ink, columns, rows, ramp.levels, options.dithering)
        val chars = CharArray(columns * rows) { ramp.chars[indices[it]] }

        val fine = samples.fine
        if (fine != null) {
            val gradients = EdgeDetector.sobel(
                ToneMapper.normalizedLuma(fine, options.autoLevels),
                fine.width,
                fine.height,
            )
            // Width / height of one gradient sample in image pixels.
            val yScale = (samples.imageWidth.toFloat() * fine.height) / (samples.imageHeight.toFloat() * fine.width)
            val outlines = EdgeDetector.cellOutlines(
                gradients,
                columns,
                rows,
                EDGE_OVERSAMPLING,
                EDGE_OVERSAMPLING,
                yScale,
                options.edgeSensitivity,
            )
            for (i in chars.indices) {
                val outline = outlines[i]
                when {
                    outline != NO_OUTLINE && grid.alpha[i] >= 0.5f -> chars[i] = outline
                    options.edgeMode == EdgeMode.ONLY -> chars[i] = ramp.blank
                }
            }
        }

        return AsciiArt(columns, rows, chars, grid.colors, options.cellAspect, isBraille = false)
    }

    private fun convertBraille(samples: Samples, options: AsciiOptions): AsciiArt {
        val columns = samples.columns
        val rows = samples.rows
        val dots = samples.grid
        val dotsWide = dots.width
        val dotsHigh = dots.height

        val ink = ToneMapper.toInk(dots, options)
        val lit = Quantizer.quantize(ink, dotsWide, dotsHigh, BINARY_LEVELS, options.dithering)
        val on = BooleanArray(lit.size) { lit[it] == 1 }

        if (options.edgeMode != EdgeMode.OFF) {
            val gradients = EdgeDetector.sobel(
                ToneMapper.normalizedLuma(dots, options.autoLevels),
                dotsWide,
                dotsHigh,
            )
            val outline = EdgeDetector.dotOutlines(gradients, options.edgeSensitivity)
            for (i in on.indices) {
                val edge = outline[i] && dots.alpha[i] >= 0.5f
                on[i] = if (options.edgeMode == EdgeMode.ONLY) edge else on[i] || edge
            }
        }

        val chars = CharArray(columns * rows)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                var bits = 0
                for (dy in 0 until Braille.DOTS_Y) {
                    val rowStart = (row * Braille.DOTS_Y + dy) * dotsWide + column * Braille.DOTS_X
                    for (dx in 0 until Braille.DOTS_X) {
                        if (on[rowStart + dx]) bits = bits or Braille.bit(dx, dy)
                    }
                }
                chars[row * columns + column] = (Braille.BLANK.code + bits).toChar()
            }
        }
        val colors = dots.downsample(Braille.DOTS_X, Braille.DOTS_Y).colors
        return AsciiArt(columns, rows, chars, colors, AsciiOptions.BRAILLE_CELL_ASPECT, isBraille = true)
    }

    private const val EDGE_OVERSAMPLING = 2
    private const val NO_OUTLINE = '\u0000'
    private val BINARY_LEVELS = floatArrayOf(0f, 1f)
}

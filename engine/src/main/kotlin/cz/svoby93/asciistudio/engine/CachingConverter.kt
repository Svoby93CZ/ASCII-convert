package cz.svoby93.asciistudio.engine

/**
 * Converts like [AsciiConverter.convert] and gives the same art, but keeps the [Samples] of the
 * last image. Sampling reads every pixel of the image and takes most of the time of a
 * conversion, so changing tones, dithering or the sensitivity of outlines becomes many times
 * quicker. A new image or a new grid (width, glyph kind, outline mode) is sampled again.
 *
 * Conversions run one after another; every conversion pipeline should have its own instance.
 */
class CachingConverter {
    private var image: PixelImage? = null
    private var samples: Samples? = null

    /** How often an image was sampled, for tests. */
    internal var samplings = 0
        private set

    @Synchronized
    fun convert(image: PixelImage, options: AsciiOptions): AsciiArt {
        val cached = samples?.takeIf { image === this.image && it.fit(options) }
        val samples = cached ?: AsciiConverter.sample(image, options).also {
            this.image = image
            samples = it
            samplings++
        }
        return AsciiConverter.convert(samples, options)
    }
}

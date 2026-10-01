package cz.svoby93.asciistudio.engine

import kotlin.math.roundToInt

/**
 * A raster image stored as packed `0xAARRGGBB` integers (non-premultiplied, row-major).
 *
 * This is exactly the layout produced by Android's `Bitmap.getPixels`, which keeps the engine
 * free of any platform dependency.
 */
class PixelImage(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(width > 0 && height > 0) { "Image must not be empty, was ${width}x$height" }
        require(pixels.size >= width * height) {
            "Pixel buffer holds ${pixels.size} values, ${width * height} required"
        }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]

    /**
     * A new image whose longer side has at most [maxSize] pixels, e.g. for small previews. Every
     * pixel is the average colour of its part of this image, with the average opacity.
     */
    fun thumbnail(maxSize: Int): PixelImage {
        require(maxSize > 0) { "maxSize must be positive, was $maxSize" }
        val scale = minOf(1f, maxSize.toFloat() / maxOf(width, height))
        val thumbnailWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val thumbnailHeight = (height * scale).roundToInt().coerceAtLeast(1)
        return PixelImage(thumbnailWidth, thumbnailHeight, Sampler.sample(this, thumbnailWidth, thumbnailHeight).colors)
    }
}

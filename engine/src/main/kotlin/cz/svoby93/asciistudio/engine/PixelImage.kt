package cz.svoby93.asciistudio.engine

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
}

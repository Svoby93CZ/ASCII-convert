package cz.svoby93.asciistudio.engine

internal object TestImages {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
    const val TRANSPARENT = 0x00000000

    fun solid(width: Int, height: Int, color: Int) = PixelImage(width, height, IntArray(width * height) { color })

    fun of(width: Int, height: Int, pixel: (x: Int, y: Int) -> Int) =
        PixelImage(width, height, IntArray(width * height) { pixel(it % width, it / width) })

    fun gray(level: Int): Int = 0xFF000000.toInt() or (level shl 16) or (level shl 8) or level
}

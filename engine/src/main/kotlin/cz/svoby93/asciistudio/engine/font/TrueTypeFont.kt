package cz.svoby93.asciistudio.engine.font

import kotlin.math.roundToInt

/**
 * A TrueType font read from its bytes: the character map, the horizontal metrics and the outline
 * of every glyph, which is what [FontSubsetter] and the SVG export need. Only fonts with TrueType
 * outlines (`glyf`) are supported, not CFF fonts or font collections.
 */
class TrueTypeFont(private val data: ByteArray) {

    private val tables: Map<String, Table>

    init {
        require(data.size >= HEADER_SIZE) { "Not a font: only ${data.size} bytes" }
        val version = data.i32(0)
        require(version == TRUETYPE || version == APPLE_TRUETYPE) { "Not a TrueType font" }
        val count = data.u16(4)
        tables = (0 until count).associate { index ->
            val record = HEADER_SIZE + index * RECORD_SIZE
            val tag = String(CharArray(4) { data.u8(record + it).toChar() })
            val table = Table(data.i32(record + 8), data.i32(record + 12))
            require(table.offset >= 0 && table.length >= 0 && table.offset + table.length <= data.size) {
                "Table $tag lies outside the font"
            }
            tag to table
        }
        for (tag in REQUIRED) require(tag in tables) { "The font has no $tag table" }
    }

    private val head = tables.getValue("head").offset
    private val loca = tables.getValue("loca").offset
    private val glyf = tables.getValue("glyf")
    private val hmtx = tables.getValue("hmtx").offset
    private val longLoca = data.i16(head + 50) == 1
    private val hMetricCount = data.u16(tables.getValue("hhea").offset + 34)

    val unitsPerEm: Int = data.u16(head + 18)
    val glyphCount: Int = data.u16(tables.getValue("maxp").offset + 4)

    /** Height above the baseline that lines reserve, in font units. */
    val ascender: Int = data.i16(tables.getValue("hhea").offset + 4)

    /** Depth below the baseline that lines reserve, in font units; negative. */
    val descender: Int = data.i16(tables.getValue("hhea").offset + 6)
    private val characterMap: Map<Int, Int> = readCharacterMap()

    /** The code points the font has glyphs for. */
    val codePoints: Set<Int> get() = characterMap.keys

    /** The glyph of [codePoint], or `null` when the font has none. */
    fun glyphOf(codePoint: Int): Int? = characterMap[codePoint]

    fun advanceWidth(glyph: Int): Int = data.u16(hmtx + 4 * minOf(glyph, hMetricCount - 1))

    fun leftSideBearing(glyph: Int): Int = if (glyph < hMetricCount) {
        data.i16(hmtx + 4 * glyph + 2)
    } else {
        data.i16(hmtx + 4 * hMetricCount + 2 * (glyph - hMetricCount))
    }

    /** The outline data of [glyph] as stored in the `glyf` table; empty for glyphs like the space. */
    fun glyphData(glyph: Int): ByteArray {
        require(glyph in 0 until glyphCount) { "No glyph $glyph" }
        val start = locaOffset(glyph)
        val end = locaOffset(glyph + 1)
        require(start <= end && glyf.offset + end <= glyf.offset + glyf.length) { "Glyph $glyph is damaged" }
        return data.copyOfRange(glyf.offset + start, glyf.offset + end)
    }

    /** A copy of the table [tag], or `null` when the font has none. */
    fun table(tag: String): ByteArray? = tables[tag]?.let { data.copyOfRange(it.offset, it.offset + it.length) }

    /** The English name record [id] for Windows, such as 0 for the copyright notice. */
    fun name(id: Int): String? {
        val table = tables["name"] ?: return null
        val base = table.offset
        val count = data.u16(base + 2)
        val strings = base + data.u16(base + 4)
        for (index in 0 until count) {
            val record = base + 6 + index * 12
            val platform = data.u16(record)
            val language = data.u16(record + 4)
            if (data.u16(record + 6) != id || platform != PLATFORM_WINDOWS || language != ENGLISH_US) continue
            val start = strings + data.u16(record + 10)
            val bytes = data.copyOfRange(start, start + data.u16(record + 8))
            return String(bytes, Charsets.UTF_16BE)
        }
        return null
    }

    /** The outline of [glyph] in font units with y up, with the parts of composite glyphs put together. */
    fun outline(glyph: Int): List<Contour> = outline(glyph, depth = 0)

    private fun outline(glyph: Int, depth: Int): List<Contour> {
        val glyphData = glyphData(glyph)
        if (glyphData.isEmpty()) return emptyList()
        val contours = glyphData.i16(0)
        if (contours >= 0) return simpleOutline(glyphData, contours)
        require(depth < MAX_COMPONENT_DEPTH) { "Glyph $glyph nests too deep" }
        val result = mutableListOf<Contour>()
        for (component in components(glyphData)) {
            val parts = outline(component.glyph, depth + 1)
            val (dx, dy) = component.offset(placed = result, parts = parts)
            parts.mapTo(result) { contour -> contour.transformed(component, dx, dy) }
        }
        return result
    }

    private fun locaOffset(glyph: Int): Int =
        if (longLoca) data.i32(loca + 4 * glyph) else data.u16(loca + 2 * glyph) * 2

    private fun readCharacterMap(): Map<Int, Int> {
        val base = tables.getValue("cmap").offset
        val subtables = (0 until data.u16(base + 2)).map { index ->
            val record = base + 4 + index * 8
            Triple(data.u16(record), data.u16(record + 2), base + data.i32(record + 4))
        }
        // A full Unicode table first, then one for the Basic Multilingual Plane.
        val best = subtables.firstOrNull { (_, _, offset) -> data.u16(offset) == 12 }
            ?: subtables.firstOrNull { (platform, encoding, offset) ->
                data.u16(offset) == 4 &&
                    (platform == PLATFORM_UNICODE || platform == PLATFORM_WINDOWS && encoding == ENCODING_UNICODE_BMP)
            }
            ?: throw IllegalArgumentException("The font has no Unicode character map")
        val offset = best.third
        return if (data.u16(offset) == 12) readFormat12(offset) else readFormat4(offset)
    }

    private fun readFormat4(offset: Int): Map<Int, Int> {
        val segments = data.u16(offset + 6) / 2
        val ends = offset + 14
        val starts = ends + 2 * segments + 2
        val deltas = starts + 2 * segments
        val rangeOffsets = deltas + 2 * segments
        val map = HashMap<Int, Int>()
        for (segment in 0 until segments) {
            val start = data.u16(starts + 2 * segment)
            val end = data.u16(ends + 2 * segment)
            val delta = data.u16(deltas + 2 * segment)
            val rangeOffsetAt = rangeOffsets + 2 * segment
            val rangeOffset = data.u16(rangeOffsetAt)
            for (codePoint in start..end) {
                if (codePoint == 0xFFFF) continue
                val glyph = if (rangeOffset == 0) {
                    (codePoint + delta) and 0xFFFF
                } else {
                    val stored = data.u16(rangeOffsetAt + rangeOffset + 2 * (codePoint - start))
                    if (stored == 0) 0 else (stored + delta) and 0xFFFF
                }
                if (glyph != 0) map[codePoint] = glyph
            }
        }
        return map
    }

    private fun readFormat12(offset: Int): Map<Int, Int> {
        val groups = data.i32(offset + 12)
        val map = HashMap<Int, Int>()
        for (group in 0 until groups) {
            val record = offset + 16 + group * 12
            val start = data.i32(record)
            val end = data.i32(record + 4)
            val firstGlyph = data.i32(record + 8)
            for (codePoint in start..end) map[codePoint] = firstGlyph + codePoint - start
        }
        return map
    }

    private class Table(val offset: Int, val length: Int)

    companion object {
        private const val TRUETYPE = 0x00010000
        private const val APPLE_TRUETYPE = 0x74727565 // "true"
        private const val HEADER_SIZE = 12
        private const val RECORD_SIZE = 16
        private const val PLATFORM_UNICODE = 0
        private const val PLATFORM_WINDOWS = 3
        private const val ENCODING_UNICODE_BMP = 1
        private const val ENGLISH_US = 0x409
        private const val MAX_COMPONENT_DEPTH = 8
        private val REQUIRED = listOf("head", "hhea", "maxp", "hmtx", "loca", "glyf", "cmap")
    }
}

/** A closed contour of a glyph outline in font units with y up; off-curve points are quadratic controls. */
class Contour(val x: IntArray, val y: IntArray, val onCurve: BooleanArray) {
    val size: Int get() = x.size
}

/** One part of a composite glyph, with where its bytes lie in the glyph data. */
internal class Component(
    val flags: Int,
    val glyph: Int,
    private val argument1: Int,
    private val argument2: Int,
    private val xx: Float,
    private val yx: Float,
    private val xy: Float,
    private val yy: Float,
    /** Offset of the flags of this component in the glyph data. */
    val start: Int,
    /** Offset just after the last byte of this component. */
    val end: Int,
) {
    fun transformX(x: Int, y: Int): Float = xx * x + xy * y

    fun transformY(x: Int, y: Int): Float = yx * x + yy * y

    /** Where the part goes: given as an offset, or by matching a point of [placed] with one of [parts]. */
    fun offset(placed: List<Contour>, parts: List<Contour>): Pair<Float, Float> {
        if (flags and ARGS_ARE_XY_VALUES != 0) {
            if (flags and SCALED_COMPONENT_OFFSET == 0) return argument1.toFloat() to argument2.toFloat()
            return transformX(argument1, argument2) to transformY(argument1, argument2)
        }
        val (parentX, parentY) = placed.point(argument1)
        val (partX, partY) = parts.point(argument2)
        return parentX - transformX(partX, partY) to parentY - transformY(partX, partY)
    }

    private fun List<Contour>.point(index: Int): Pair<Int, Int> {
        var remaining = index
        for (contour in this) {
            if (remaining < contour.size) return contour.x[remaining] to contour.y[remaining]
            remaining -= contour.size
        }
        throw IllegalArgumentException("No point $index to attach a component to")
    }

    companion object {
        const val ARG_1_AND_2_ARE_WORDS = 0x0001
        const val ARGS_ARE_XY_VALUES = 0x0002
        const val WE_HAVE_A_SCALE = 0x0008
        const val MORE_COMPONENTS = 0x0020
        const val WE_HAVE_AN_X_AND_Y_SCALE = 0x0040
        const val WE_HAVE_A_TWO_BY_TWO = 0x0080
        const val WE_HAVE_INSTRUCTIONS = 0x0100
        const val SCALED_COMPONENT_OFFSET = 0x0800
    }
}

/** The parts of the composite glyph [glyphData], in their order. */
internal fun components(glyphData: ByteArray): List<Component> {
    val result = mutableListOf<Component>()
    var position = GLYPH_HEADER_SIZE
    do {
        val start = position
        val flags = glyphData.u16(position)
        val glyph = glyphData.u16(position + 2)
        position += 4
        val xyValues = flags and Component.ARGS_ARE_XY_VALUES != 0
        val argument1: Int
        val argument2: Int
        if (flags and Component.ARG_1_AND_2_ARE_WORDS != 0) {
            argument1 = if (xyValues) glyphData.i16(position) else glyphData.u16(position)
            argument2 = if (xyValues) glyphData.i16(position + 2) else glyphData.u16(position + 2)
            position += 4
        } else {
            argument1 = if (xyValues) glyphData[position].toInt() else glyphData.u8(position)
            argument2 = if (xyValues) glyphData[position + 1].toInt() else glyphData.u8(position + 1)
            position += 2
        }
        var xx = 1f
        var yx = 0f
        var xy = 0f
        var yy = 1f
        when {
            flags and Component.WE_HAVE_A_SCALE != 0 -> {
                xx = glyphData.f2Dot14(position)
                yy = xx
                position += 2
            }
            flags and Component.WE_HAVE_AN_X_AND_Y_SCALE != 0 -> {
                xx = glyphData.f2Dot14(position)
                yy = glyphData.f2Dot14(position + 2)
                position += 4
            }
            flags and Component.WE_HAVE_A_TWO_BY_TWO != 0 -> {
                xx = glyphData.f2Dot14(position)
                yx = glyphData.f2Dot14(position + 2)
                xy = glyphData.f2Dot14(position + 4)
                yy = glyphData.f2Dot14(position + 6)
                position += 8
            }
        }
        result += Component(flags, glyph, argument1, argument2, xx, yx, xy, yy, start, position)
    } while (flags and Component.MORE_COMPONENTS != 0)
    return result
}

/** The contours of a simple glyph with [count] contours. */
private fun simpleOutline(glyphData: ByteArray, count: Int): List<Contour> {
    if (count == 0) return emptyList()
    val ends = IntArray(count) { glyphData.u16(GLYPH_HEADER_SIZE + 2 * it) }
    val points = ends.last() + 1
    var position = GLYPH_HEADER_SIZE + 2 * count
    position += 2 + glyphData.u16(position)
    val flags = IntArray(points)
    var index = 0
    while (index < points) {
        val flag = glyphData.u8(position++)
        flags[index++] = flag
        if (flag and REPEAT != 0) {
            repeat(glyphData.u8(position++)) { if (index < points) flags[index++] = flag }
        }
    }
    val xs = IntArray(points)
    var x = 0
    for (point in 0 until points) {
        val flag = flags[point]
        if (flag and X_SHORT != 0) {
            val delta = glyphData.u8(position++)
            x += if (flag and X_SAME_OR_POSITIVE != 0) delta else -delta
        } else if (flag and X_SAME_OR_POSITIVE == 0) {
            x += glyphData.i16(position)
            position += 2
        }
        xs[point] = x
    }
    val ys = IntArray(points)
    var y = 0
    for (point in 0 until points) {
        val flag = flags[point]
        if (flag and Y_SHORT != 0) {
            val delta = glyphData.u8(position++)
            y += if (flag and Y_SAME_OR_POSITIVE != 0) delta else -delta
        } else if (flag and Y_SAME_OR_POSITIVE == 0) {
            y += glyphData.i16(position)
            position += 2
        }
        ys[point] = y
    }
    var first = 0
    return ends.map { end ->
        val range = first..end
        first = end + 1
        Contour(
            x = xs.sliceArray(range),
            y = ys.sliceArray(range),
            onCurve = BooleanArray(range.count()) { flags[range.first + it] and ON_CURVE != 0 },
        )
    }
}

private fun Contour.transformed(component: Component, dx: Float, dy: Float): Contour = Contour(
    x = IntArray(size) { (component.transformX(x[it], y[it]) + dx).roundToInt() },
    y = IntArray(size) { (component.transformY(x[it], y[it]) + dy).roundToInt() },
    onCurve = onCurve,
)

internal const val GLYPH_HEADER_SIZE = 10
internal const val ON_CURVE = 0x01
internal const val X_SHORT = 0x02
internal const val Y_SHORT = 0x04
internal const val REPEAT = 0x08
internal const val X_SAME_OR_POSITIVE = 0x10
internal const val Y_SAME_OR_POSITIVE = 0x20

internal fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

internal fun ByteArray.u16(at: Int): Int = (u8(at) shl 8) or u8(at + 1)

internal fun ByteArray.i16(at: Int): Int = u16(at).toShort().toInt()

internal fun ByteArray.i32(at: Int): Int = (u16(at) shl 16) or u16(at + 2)

private fun ByteArray.f2Dot14(at: Int): Float = i16(at) / 16384f

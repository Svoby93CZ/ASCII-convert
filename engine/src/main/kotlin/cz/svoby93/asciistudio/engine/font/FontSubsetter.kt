package cz.svoby93.asciistudio.engine.font

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.SortedMap

/** A glyph of a new font: TrueType outline data without instructions, and its horizontal metrics. */
class GlyphRecord(val data: ByteArray, val advanceWidth: Int, val leftSideBearing: Int) {
    val hasOutline: Boolean get() = data.isNotEmpty()
    val xMin: Int get() = data.i16(2)
    val yMin: Int get() = data.i16(4)
    val xMax: Int get() = data.i16(6)
    val yMax: Int get() = data.i16(8)
}

/** Glyphs that a font lacks, made for the code points that need them. */
interface ExtraGlyphs {
    /** Glyphs that the made glyphs are composed of, and which no code point shows by itself. */
    val parts: List<GlyphRecord>

    /** The glyph for [codePoint], or `null`; [partGlyph] gives the glyph index of a part in the new font. */
    fun glyph(codePoint: Int, partGlyph: (Int) -> Int): GlyphRecord?
}

/**
 * Builds small TrueType fonts to embed in exported files: only the glyphs that one piece of art
 * uses, without hinting, ligatures and kerning, which monospaced art does not need.
 */
object FontSubsetter {

    /**
     * A font named [family] with the glyphs of [codePoints]: from [font], or from [extra] for code
     * points the font lacks. Code points that neither has are left out, so that a browser takes
     * them from another font. The copyright and licence notices of [font] are kept.
     */
    fun subset(font: TrueTypeFont, codePoints: Set<Int>, family: String, extra: ExtraGlyphs? = null): ByteArray {
        val fromFont = sortedMapOf<Int, Int>()
        val lacking = sortedSetOf<Int>()
        // The character map is written in format 4, which covers the Basic Multilingual Plane.
        for (codePoint in codePoints.filter { it in 1 until 0xFFFF }) {
            val glyph = font.glyphOf(codePoint)
            if (glyph != null) fromFont[codePoint] = glyph else lacking += codePoint
        }
        // Glyph 0 (.notdef) has to come first; composite glyphs bring their parts along.
        val needed = sortedSetOf<Int>()
        val pending = ArrayDeque(listOf(0) + fromFont.values)
        while (pending.isNotEmpty()) {
            val glyph = pending.removeFirst()
            if (!needed.add(glyph)) continue
            val data = font.glyphData(glyph)
            if (data.isNotEmpty() && data.i16(0) < 0) components(data).mapTo(pending) { it.glyph }
        }
        val newIndex = HashMap<Int, Int>()
        needed.forEachIndexed { index, glyph -> newIndex[glyph] = index }
        // Made glyphs follow the parts they are composed of, which follow the glyphs of the font.
        val made = sortedMapOf<Int, GlyphRecord>()
        if (extra != null) {
            for (codePoint in lacking) {
                extra.glyph(codePoint) { part -> needed.size + part }?.let { made[codePoint] = it }
            }
        }
        val parts = if (made.isEmpty()) emptyList() else extra!!.parts
        val glyphs = needed.map { glyph ->
            val data = withoutInstructions(font.glyphData(glyph), newIndex)
            GlyphRecord(data, font.advanceWidth(glyph), font.leftSideBearing(glyph))
        } + parts + made.values
        val characterMap = sortedMapOf<Int, Int>()
        fromFont.forEach { (codePoint, glyph) -> characterMap[codePoint] = newIndex.getValue(glyph) }
        made.keys.forEachIndexed { index, codePoint -> characterMap[codePoint] = needed.size + parts.size + index }

        val tables = sortedMapOf<String, ByteArray>()
        val (glyf, loca) = glyphTables(glyphs)
        tables["glyf"] = glyf
        tables["loca"] = loca
        tables["hmtx"] = horizontalMetrics(glyphs)
        tables["cmap"] = characterMapTable(characterMap)
        tables["head"] = head(font.table("head")!!, glyphs)
        tables["hhea"] = horizontalHeader(font.table("hhea")!!, glyphs)
        tables["maxp"] = maximumProfile(glyphs)
        tables["name"] = names(font, family)
        font.table("OS/2")?.let { tables["OS/2"] = os2(it, characterMap.keys) }
        font.table("post")?.let { tables["post"] = postScript(it) }
        return assemble(tables)
    }

    /** [data] with its hinting instructions removed and its parts renumbered. */
    private fun withoutInstructions(data: ByteArray, newIndex: Map<Int, Int>): ByteArray {
        if (data.isEmpty()) return data
        val contours = data.i16(0)
        val bytes = ByteArrayOutputStream(data.size)
        if (contours >= 0) {
            val instructionsAt = GLYPH_HEADER_SIZE + 2 * contours
            val rest = instructionsAt + 2 + data.u16(instructionsAt)
            bytes.write(data, 0, instructionsAt)
            bytes.write(0)
            bytes.write(0)
            // Without the padding of the source; glyphTables pads every glyph again.
            bytes.write(data, rest, simpleGlyphEnd(data, contours) - rest)
        } else {
            bytes.write(data, 0, GLYPH_HEADER_SIZE)
            val out = DataOutputStream(bytes)
            for (component in components(data)) {
                out.writeShort(component.flags and Component.WE_HAVE_INSTRUCTIONS.inv())
                out.writeShort(newIndex.getValue(component.glyph))
                out.write(data, component.start + 4, component.end - component.start - 4)
            }
        }
        return bytes.toByteArray()
    }

    /** Where the coordinates of a simple glyph end, before any padding. */
    private fun simpleGlyphEnd(data: ByteArray, contours: Int): Int {
        if (contours == 0) return GLYPH_HEADER_SIZE + 2
        val points = data.u16(GLYPH_HEADER_SIZE + 2 * (contours - 1)) + 1
        val instructionsAt = GLYPH_HEADER_SIZE + 2 * contours
        var position = instructionsAt + 2 + data.u16(instructionsAt)
        var coordinateBytes = 0
        var point = 0
        while (point < points) {
            val flag = data.u8(position++)
            val times = if (flag and REPEAT != 0) 1 + data.u8(position++) else 1
            val xBytes = if (flag and X_SHORT != 0) 1 else if (flag and X_SAME_OR_POSITIVE != 0) 0 else 2
            val yBytes = if (flag and Y_SHORT != 0) 1 else if (flag and Y_SAME_OR_POSITIVE != 0) 0 else 2
            coordinateBytes += times * (xBytes + yBytes)
            point += times
        }
        return position + coordinateBytes
    }

    private fun glyphTables(glyphs: List<GlyphRecord>): Pair<ByteArray, ByteArray> {
        val glyf = ByteArrayOutputStream()
        val loca = table { out ->
            for (glyph in glyphs) {
                out.writeInt(glyf.size())
                glyf.write(glyph.data)
                repeat(padding(glyph.data.size)) { glyf.write(0) }
            }
            out.writeInt(glyf.size())
        }
        return glyf.toByteArray() to loca
    }

    private fun horizontalMetrics(glyphs: List<GlyphRecord>): ByteArray = table { out ->
        for (glyph in glyphs) {
            out.writeShort(glyph.advanceWidth)
            out.writeShort(glyph.leftSideBearing)
        }
    }

    /** Format 4 for the Unicode and Windows platforms; neighbours with neighbouring glyphs share a segment. */
    private fun characterMapTable(map: SortedMap<Int, Int>): ByteArray {
        val segments = mutableListOf<IntArray>() // start, end, delta
        for ((codePoint, glyph) in map) {
            val last = segments.lastOrNull()
            if (last != null && codePoint == last[1] + 1 && glyph == (codePoint + last[2]) and 0xFFFF) {
                last[1] = codePoint
            } else {
                segments += intArrayOf(codePoint, codePoint, (glyph - codePoint) and 0xFFFF)
            }
        }
        segments += intArrayOf(0xFFFF, 0xFFFF, 1)
        val count = segments.size
        val searchRange = 2 * Integer.highestOneBit(count)
        return table { out ->
            out.writeShort(0) // version
            out.writeShort(2) // subtables
            for ((platform, encoding) in listOf(0 to 3, 3 to 1)) {
                out.writeShort(platform)
                out.writeShort(encoding)
                out.writeInt(CMAP_HEADER_SIZE)
            }
            out.writeShort(4) // format
            out.writeShort(16 + 8 * count)
            out.writeShort(0) // language
            out.writeShort(2 * count)
            out.writeShort(searchRange)
            out.writeShort(Integer.numberOfTrailingZeros(searchRange / 2))
            out.writeShort(2 * count - searchRange)
            segments.forEach { out.writeShort(it[1]) }
            out.writeShort(0) // reserved
            segments.forEach { out.writeShort(it[0]) }
            segments.forEach { out.writeShort(it[2]) }
            segments.forEach { _ -> out.writeShort(0) } // no range offsets
        }
    }

    private fun head(source: ByteArray, glyphs: List<GlyphRecord>): ByteArray {
        val head = source.copyOf(HEAD_SIZE)
        val outlined = glyphs.filter { it.hasOutline }
        head.putInt(8, 0) // checkSumAdjustment, filled in by assemble
        head.putShort(36, outlined.minOfOrNull { it.xMin } ?: 0)
        head.putShort(38, outlined.minOfOrNull { it.yMin } ?: 0)
        head.putShort(40, outlined.maxOfOrNull { it.xMax } ?: 0)
        head.putShort(42, outlined.maxOfOrNull { it.yMax } ?: 0)
        head.putShort(50, 1) // long loca offsets
        return head
    }

    private fun horizontalHeader(source: ByteArray, glyphs: List<GlyphRecord>): ByteArray {
        val hhea = source.copyOf(HHEA_SIZE)
        val outlined = glyphs.filter { it.hasOutline }
        hhea.putShort(10, glyphs.maxOf { it.advanceWidth })
        hhea.putShort(12, outlined.minOfOrNull { it.leftSideBearing } ?: 0)
        hhea.putShort(14, outlined.minOfOrNull { it.advanceWidth - it.leftSideBearing - (it.xMax - it.xMin) } ?: 0)
        hhea.putShort(16, outlined.maxOfOrNull { it.leftSideBearing + it.xMax - it.xMin } ?: 0)
        hhea.putShort(34, glyphs.size)
        return hhea
    }

    /** Version 1.0, with the sizes the glyphs need and no room for hinting. */
    private fun maximumProfile(glyphs: List<GlyphRecord>): ByteArray {
        val extents = HashMap<Int, Extent>()
        fun extentOf(index: Int, depth: Int = 0): Extent = extents.getOrPut(index) {
            val data = glyphs[index].data
            require(depth < MAX_DEPTH) { "Composite glyphs nest too deep" }
            when {
                data.isEmpty() -> Extent(points = 0, contours = 0, components = 0, depth = 0)
                data.i16(0) >= 0 -> {
                    val contours = data.i16(0)
                    val points = if (contours == 0) 0 else data.u16(GLYPH_HEADER_SIZE + 2 * (contours - 1)) + 1
                    Extent(points, contours, components = 0, depth = 0)
                }
                else -> {
                    val parts = components(data).map { extentOf(it.glyph, depth + 1) }
                    Extent(
                        points = parts.sumOf { it.points },
                        contours = parts.sumOf { it.contours },
                        components = parts.size,
                        depth = 1 + parts.maxOf { it.depth },
                    )
                }
            }
        }
        val all = glyphs.indices.map { extentOf(it) }
        val simple = all.filter { it.components == 0 }
        val composite = all.filter { it.components > 0 }
        return table { out ->
            out.writeInt(0x00010000)
            out.writeShort(glyphs.size)
            out.writeShort(simple.maxOfOrNull { it.points } ?: 0)
            out.writeShort(simple.maxOfOrNull { it.contours } ?: 0)
            out.writeShort(composite.maxOfOrNull { it.points } ?: 0)
            out.writeShort(composite.maxOfOrNull { it.contours } ?: 0)
            out.writeShort(1) // maxZones: no twilight zone without instructions
            // Twilight points, storage, functions, instruction definitions, stack and instructions.
            repeat(6) { out.writeShort(0) }
            out.writeShort(composite.maxOfOrNull { it.components } ?: 0)
            out.writeShort(composite.maxOfOrNull { it.depth } ?: 0)
        }
    }

    private class Extent(val points: Int, val contours: Int, val components: Int, val depth: Int)

    /** Names for the new family; the copyright and licence of the source stay with the glyphs. */
    private fun names(font: TrueTypeFont, family: String): ByteArray {
        val records = sortedMapOf<Int, String>()
        font.name(NAME_COPYRIGHT)?.let { records[NAME_COPYRIGHT] = it }
        records[NAME_FAMILY] = family
        records[NAME_SUBFAMILY] = "Regular"
        records[NAME_UNIQUE_ID] = "$family;${font.name(NAME_POSTSCRIPT) ?: "subset"}"
        records[NAME_FULL] = family
        font.name(NAME_VERSION)?.let { records[NAME_VERSION] = it }
        records[NAME_POSTSCRIPT] = family.filter { it.isLetterOrDigit() }
        records[NAME_DESCRIPTION] = "Subset of ${font.name(NAME_FULL) ?: "a font"} for one piece of ASCII art"
        font.name(NAME_LICENSE)?.let { records[NAME_LICENSE] = it }
        font.name(NAME_LICENSE_URL)?.let { records[NAME_LICENSE_URL] = it }
        val strings = records.mapValues { it.value.toByteArray(Charsets.UTF_16BE) }
        return table { out ->
            out.writeShort(0) // format
            out.writeShort(strings.size)
            out.writeShort(6 + 12 * strings.size)
            var offset = 0
            for ((id, bytes) in strings) {
                out.writeShort(3) // Windows
                out.writeShort(1) // Unicode BMP
                out.writeShort(0x409) // English (United States)
                out.writeShort(id)
                out.writeShort(bytes.size)
                out.writeShort(offset)
                offset += bytes.size
            }
            strings.values.forEach { out.write(it) }
        }
    }

    private fun os2(source: ByteArray, codePoints: Set<Int>): ByteArray {
        val os2 = source.copyOf()
        if (os2.size >= 68 && codePoints.isNotEmpty()) {
            os2.putShort(64, codePoints.min())
            os2.putShort(66, codePoints.max())
        }
        return os2
    }

    /** Format 3: the metrics of the source without glyph names. */
    private fun postScript(source: ByteArray): ByteArray = source.copyOf(POST_SIZE).also { it.putInt(0, 0x00030000) }

    private fun assemble(tables: SortedMap<String, ByteArray>): ByteArray {
        val count = tables.size
        val searchRange = 16 * Integer.highestOneBit(count)
        val directorySize = 12 + 16 * count
        var offset = directorySize
        var headOffset = 0
        val font = table { out ->
            out.writeInt(0x00010000)
            out.writeShort(count)
            out.writeShort(searchRange)
            out.writeShort(Integer.numberOfTrailingZeros(searchRange / 16))
            out.writeShort(16 * count - searchRange)
            for ((tag, data) in tables) {
                if (tag == "head") headOffset = offset
                out.writeBytes(tag)
                out.writeInt(checksum(data).toInt())
                out.writeInt(offset)
                out.writeInt(data.size)
                offset += data.size + padding(data.size)
            }
            for (data in tables.values) {
                out.write(data)
                repeat(padding(data.size)) { out.write(0) }
            }
        }
        font.putInt(headOffset + 8, (CHECKSUM_MAGIC - checksum(font)).toInt())
        return font
    }

    private fun checksum(data: ByteArray): Long {
        var sum = 0L
        for (index in data.indices step 4) {
            var word = 0L
            for (byte in 0 until 4) {
                word = (word shl 8) or (if (index + byte < data.size) data.u8(index + byte).toLong() else 0L)
            }
            sum = (sum + word) and 0xFFFFFFFFL
        }
        return sum
    }

    private fun padding(size: Int): Int = (4 - size % 4) % 4

    private fun table(write: (DataOutputStream) -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use(write)
        return bytes.toByteArray()
    }

    private fun ByteArray.putShort(at: Int, value: Int) {
        this[at] = (value shr 8).toByte()
        this[at + 1] = value.toByte()
    }

    private fun ByteArray.putInt(at: Int, value: Int) {
        putShort(at, value shr 16)
        putShort(at + 2, value)
    }

    private const val HEAD_SIZE = 54
    private const val HHEA_SIZE = 36
    private const val POST_SIZE = 32
    private const val CMAP_HEADER_SIZE = 4 + 2 * 8
    private const val MAX_DEPTH = 8
    private const val CHECKSUM_MAGIC = 0xB1B0AFBAL
    private const val NAME_COPYRIGHT = 0
    private const val NAME_FAMILY = 1
    private const val NAME_SUBFAMILY = 2
    private const val NAME_UNIQUE_ID = 3
    private const val NAME_FULL = 4
    private const val NAME_VERSION = 5
    private const val NAME_POSTSCRIPT = 6
    private const val NAME_DESCRIPTION = 10
    private const val NAME_LICENSE = 13
    private const val NAME_LICENSE_URL = 14
}

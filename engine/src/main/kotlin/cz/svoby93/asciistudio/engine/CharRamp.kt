package cz.svoby93.asciistudio.engine

/**
 * An ordered set of glyphs, from the one with the least ink to the one with the most.
 *
 * Every glyph carries its measured ink coverage normalised to `0..1`, so the converter can pick
 * the glyph whose visual density matches a pixel best, instead of assuming that the glyphs are
 * evenly spaced in tone (they almost never are).
 */
class CharRamp private constructor(
    val chars: String,
    val levels: FloatArray,
) {
    val size: Int get() = chars.length

    /** The glyph with the least ink, used as "paper". */
    val blank: Char get() = chars[0]

    override fun equals(other: Any?): Boolean =
        other is CharRamp && other.chars == chars && other.levels.contentEquals(levels)

    override fun hashCode(): Int = 31 * chars.hashCode() + levels.contentHashCode()

    override fun toString(): String = "CharRamp(\"$chars\")"

    companion object {
        /**
         * Creates a ramp from glyphs and their raw ink coverage (any scale). Glyphs are sorted by
         * coverage and duplicates are removed; the coverage is normalised so that the lightest
         * glyph maps to 0 and the densest one to 1.
         */
        fun of(chars: String, coverage: FloatArray): CharRamp {
            require(chars.length == coverage.size) { "Every glyph needs exactly one coverage value" }
            val unique = LinkedHashMap<Char, Float>()
            chars.forEachIndexed { index, c -> unique.putIfAbsent(c, coverage[index]) }
            require(unique.size >= 2) { "A ramp needs at least two distinct glyphs" }

            val sorted = unique.entries.sortedBy { it.value }
            val min = sorted.first().value
            val max = sorted.last().value
            val span = max - min
            val levels = FloatArray(sorted.size) { index ->
                if (span <= 0f) index / (sorted.size - 1f) else (sorted[index].value - min) / span
            }
            // Glyphs with identical coverage would be unreachable; spread them minimally.
            for (i in 1 until levels.size) {
                if (levels[i] <= levels[i - 1]) levels[i] = minOf(1f, levels[i - 1] + 1e-4f)
            }
            return CharRamp(String(CharArray(sorted.size) { sorted[it].key }), levels)
        }

        /** Creates a ramp that trusts the given order and spaces the glyphs evenly in tone. */
        fun uniform(chars: String): CharRamp {
            val distinct = chars.toCharArray().distinct()
            require(distinct.size >= 2) { "A ramp needs at least two distinct glyphs" }
            return CharRamp(
                String(distinct.toCharArray()),
                FloatArray(distinct.size) { it / (distinct.size - 1f) },
            )
        }
    }
}

/**
 * Built-in ramps. Coverage values were measured on JetBrains Mono (the font the app renders
 * with), so the tonal mapping is accurate on screen and in exported images.
 */
object CharRamps {
    val STANDARD: CharRamp = CharRamp.of(
        " .-:=+*#%@",
        floatArrayOf(0f, 0.0267f, 0.0323f, 0.0515f, 0.0869f, 0.0908f, 0.1231f, 0.1873f, 0.2112f, 0.2532f),
    )

    val DETAILED: CharRamp = CharRamp.of(
        " .-,:;~!=+r?LlcfoV3U#gRQN\$W@",
        floatArrayOf(
            0f, 0.0267f, 0.0323f, 0.0387f, 0.0515f, 0.0651f, 0.0685f, 0.0802f, 0.0869f, 0.0908f,
            0.1079f, 0.1107f, 0.1163f, 0.1216f, 0.1304f, 0.1403f, 0.1490f, 0.1597f, 0.1688f, 0.1783f,
            0.1873f, 0.1955f, 0.2083f, 0.2166f, 0.2247f, 0.2336f, 0.2493f, 0.2532f,
        ),
    )

    val BLOCKS: CharRamp = CharRamp.of(
        " ░▒▓█",
        floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    )

    val BINARY: CharRamp = CharRamp.of(
        " 10",
        floatArrayOf(0f, 0.1448f, 0.2034f),
    )
}

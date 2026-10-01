package cz.svoby93.asciistudio.engine.font

import java.io.File

internal object TestFonts {
    /** JetBrains Mono as the app ships it; the build passes its path. */
    val app: TrueTypeFont by lazy { TrueTypeFont(File(System.getProperty("appFont")).readBytes()) }

    /** The points of [outline] as text, which makes differences easy to read in a failed test. */
    fun points(outline: List<Contour>): List<String> = outline.map { contour ->
        (0 until contour.size).joinToString(" ") { index ->
            "${contour.x[index]},${contour.y[index]}" + if (contour.onCurve[index]) "" else "~"
        }
    }
}

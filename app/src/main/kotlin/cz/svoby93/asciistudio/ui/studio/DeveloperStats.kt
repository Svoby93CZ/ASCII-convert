package cz.svoby93.asciistudio.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * The measurements of the developer mode over the art, one per line. They are technical readouts
 * of numbers and units, like the size of the art in the border of its window, so they are not
 * translated (see [statsLine]). Screen readers skip them.
 */
@Composable
fun DeveloperStats(lines: List<String>, modifier: Modifier = Modifier) {
    if (lines.isEmpty()) return
    val colors = LocalStudioColors.current
    Column(
        modifier
            .clearAndSetSemantics { }
            .background(colors.paper.copy(alpha = 0.85f), StatsShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        lines.forEach { line ->
            Text(line, style = TerminalLabelStyle.copy(fontSize = 11.sp, lineHeight = 14.sp), color = colors.ink)
        }
    }
}

/** A line of [DeveloperStats], with a decimal point whatever the language. */
fun statsLine(pattern: String, vararg values: Any): String = String.format(Locale.ROOT, pattern, *values)

private val StatsShape = RoundedCornerShape(6.dp)

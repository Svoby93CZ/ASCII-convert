package cz.svoby93.asciistudio

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.res.ResourcesCompat
import cz.svoby93.asciistudio.data.ArtExporter
import cz.svoby93.asciistudio.data.ImageRepository
import cz.svoby93.asciistudio.data.SettingsRepository
import cz.svoby93.asciistudio.data.settingsDataStore
import cz.svoby93.asciistudio.render.AsciiOptionsFactory
import cz.svoby93.asciistudio.render.AsciiRenderer
import cz.svoby93.asciistudio.render.GlyphMeasurer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency injection: the app is small enough that a hand-written container is simpler
 * and faster to build than a DI framework, while still keeping every class testable.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** JetBrains Mono, used for every piece of rendered art. */
    val asciiTypeface: Typeface by lazy {
        ResourcesCompat.getFont(appContext, R.font.jetbrains_mono_regular) ?: Typeface.MONOSPACE
    }

    val optionsFactory: AsciiOptionsFactory by lazy {
        AsciiOptionsFactory(GlyphMeasurer(asciiTypeface), AsciiRenderer(asciiTypeface).textCellAspect)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext.settingsDataStore, applicationScope)
    }

    val imageRepository: ImageRepository by lazy { ImageRepository(appContext) }

    val exporter: ArtExporter by lazy { ArtExporter(appContext, asciiTypeface) }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is provided by MainActivity")
}

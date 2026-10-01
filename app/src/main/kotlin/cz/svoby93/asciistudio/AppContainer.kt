package cz.svoby93.asciistudio

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.res.ResourcesCompat
import cz.svoby93.asciistudio.data.ArtExporter
import cz.svoby93.asciistudio.data.GalleryRepository
import cz.svoby93.asciistudio.data.ImageRepository
import cz.svoby93.asciistudio.data.PresetRepository
import cz.svoby93.asciistudio.data.SettingsRepository
import cz.svoby93.asciistudio.data.settingsDataStore
import cz.svoby93.asciistudio.render.AsciiOptionsFactory
import cz.svoby93.asciistudio.render.AsciiRenderer
import cz.svoby93.asciistudio.render.GlyphMeasurer
import cz.svoby93.asciistudio.render.LookPreviewer
import java.io.File
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

    /** The presets the user saved, stored next to the settings. */
    val presetRepository: PresetRepository by lazy {
        PresetRepository(appContext.settingsDataStore, applicationScope)
    }

    val lookPreviewer: LookPreviewer by lazy { LookPreviewer(optionsFactory) }

    val imageRepository: ImageRepository by lazy { ImageRepository(appContext) }

    val exporter: ArtExporter by lazy { ArtExporter(appContext, asciiTypeface) }

    /** Private and left out of backups, like the image being edited. */
    val galleryRepository: GalleryRepository by lazy {
        GalleryRepository(File(appContext.noBackupFilesDir, "gallery"))
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is provided by MainActivity")
}

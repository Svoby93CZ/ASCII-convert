package cz.svoby93.asciistudio.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "studio_settings")

/**
 * Single source of truth for [StudioSettings].
 *
 * Changes are applied in memory immediately (sliders stay perfectly responsive) and written to
 * DataStore shortly after the user stops interacting.
 */
@OptIn(FlowPreview::class)
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    private val state = MutableStateFlow<StudioSettings?>(null)

    /** `null` until the stored settings have been read. */
    val settings: StateFlow<StudioSettings?> = state.asStateFlow()

    init {
        scope.launch {
            val stored = dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .first()
                .toSettings()
            state.compareAndSet(null, stored)
            var persisted = stored
            state.filterNotNull()
                .debounce(PERSIST_DELAY_MS)
                .collect { settings ->
                    if (settings != persisted) {
                        persist(settings)
                        persisted = settings
                    }
                }
        }
    }

    fun update(transform: (StudioSettings) -> StudioSettings) {
        state.update { current -> current?.let(transform) }
    }

    private suspend fun persist(settings: StudioSettings) {
        try {
            dataStore.edit { it.write(settings) }
        } catch (_: IOException) {
            // Losing a settings write is harmless; the in-memory state stays correct.
        }
    }

    private fun Preferences.toSettings(): StudioSettings {
        val defaults = StudioSettings()
        return StudioSettings(
            columns = (this[COLUMNS] ?: defaults.columns)
                .coerceIn(StudioSettings.MIN_COLUMNS, StudioSettings.MAX_COLUMNS),
            charset = enumOrDefault(this[CHARSET], defaults.charset),
            customChars = this[CUSTOM_CHARS] ?: defaults.customChars,
            brightness = this[BRIGHTNESS] ?: defaults.brightness,
            contrast = this[CONTRAST] ?: defaults.contrast,
            sharpness = this[SHARPNESS] ?: defaults.sharpness,
            autoLevels = this[AUTO_LEVELS] ?: defaults.autoLevels,
            invert = this[INVERT] ?: defaults.invert,
            dithering = enumOrDefault(this[DITHERING], defaults.dithering),
            edgeMode = enumOrDefault(this[EDGE_MODE], defaults.edgeMode),
            edgeSensitivity = this[EDGE_SENSITIVITY] ?: defaults.edgeSensitivity,
            colorMode = enumOrDefault(this[COLOR_MODE], defaults.colorMode),
            palette = enumOrDefault(this[PALETTE], defaults.palette),
            cameraBackground = enumOrDefault(this[CAMERA_BACKGROUND], defaults.cameraBackground),
        )
    }

    private fun MutablePreferences.write(settings: StudioSettings) {
        this[COLUMNS] = settings.columns
        this[CHARSET] = settings.charset.name
        this[CUSTOM_CHARS] = settings.customChars
        this[BRIGHTNESS] = settings.brightness
        this[CONTRAST] = settings.contrast
        this[SHARPNESS] = settings.sharpness
        this[AUTO_LEVELS] = settings.autoLevels
        this[INVERT] = settings.invert
        this[DITHERING] = settings.dithering.name
        this[EDGE_MODE] = settings.edgeMode.name
        this[EDGE_SENSITIVITY] = settings.edgeSensitivity
        this[COLOR_MODE] = settings.colorMode.name
        this[PALETTE] = settings.palette.name
        this[CAMERA_BACKGROUND] = settings.cameraBackground.name
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val PERSIST_DELAY_MS = 400L

        val COLUMNS = intPreferencesKey("columns")
        val CHARSET = stringPreferencesKey("charset")
        val CUSTOM_CHARS = stringPreferencesKey("custom_chars")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val CONTRAST = floatPreferencesKey("contrast")
        val SHARPNESS = floatPreferencesKey("sharpness")
        val AUTO_LEVELS = booleanPreferencesKey("auto_levels")
        val INVERT = booleanPreferencesKey("invert")
        val DITHERING = stringPreferencesKey("dithering")
        val EDGE_MODE = stringPreferencesKey("edge_mode")
        val EDGE_SENSITIVITY = floatPreferencesKey("edge_sensitivity")
        val COLOR_MODE = stringPreferencesKey("color_mode")
        val PALETTE = stringPreferencesKey("palette")
        val CAMERA_BACKGROUND = stringPreferencesKey("camera_background")
    }
}

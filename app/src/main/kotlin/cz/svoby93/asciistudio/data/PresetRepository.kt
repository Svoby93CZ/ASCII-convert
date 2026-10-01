package cz.svoby93.asciistudio.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The presets the user saved, as JSON next to the settings. Changes apply in memory at once and
 * reach DataStore one after another, so the last change always wins.
 */
class PresetRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    private val state = MutableStateFlow<List<UserPreset>?>(null)

    /** `null` until the stored presets have been read. */
    val presets: StateFlow<List<UserPreset>?> = state.asStateFlow()

    init {
        scope.launch {
            val stored = dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .first()[PRESETS]
                ?.let(::decode)
                .orEmpty()
            state.compareAndSet(null, stored)
            var persisted = stored
            state.filterNotNull().collect { presets ->
                if (presets != persisted) {
                    persist(presets)
                    persisted = presets
                }
            }
        }
    }

    /** Keeps the look of [settings] as a new preset; the width and the background do not belong to it. */
    fun add(name: String, settings: StudioSettings) {
        val look = settings.copy(columns = StudioSettings.DEFAULT_COLUMNS, backdrop = StudioSettings().backdrop)
        val preset = UserPreset(UUID.randomUUID().toString(), name.trim().take(MAX_NAME_LENGTH), look)
        state.update { current -> current?.plus(preset) }
    }

    fun delete(id: String) {
        state.update { current -> current?.filterNot { it.id == id } }
    }

    private suspend fun persist(presets: List<UserPreset>) {
        try {
            dataStore.edit { it[PRESETS] = json.encodeToString(serializer, presets) }
        } catch (_: IOException) {
            // The presets stay correct in memory; the next change tries again.
        }
    }

    private fun decode(text: String): List<UserPreset> = try {
        json.decodeFromString(serializer, text)
    } catch (_: SerializationException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    }

    companion object {
        const val MAX_NAME_LENGTH = 30

        private val PRESETS = stringPreferencesKey("user_presets")
        private val serializer = ListSerializer(UserPreset.serializer())

        /** Unknown properties are skipped, unknown enum values fall back to their defaults. */
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }
    }
}

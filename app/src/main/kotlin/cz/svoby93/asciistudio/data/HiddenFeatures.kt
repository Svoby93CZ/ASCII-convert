package cz.svoby93.asciistudio.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The hidden features the user has found (see `Signature`): the look drawn with the letters of the
 * author's nickname, and the developer mode. They are stored next to the settings, but not in
 * [StudioSettings], because they are not part of the art that gallery items and presets keep.
 */
class HiddenFeatures(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    /** What was stored, `null` until it has been read. */
    private val state = MutableStateFlow<Preferences?>(null)

    /** Whether the look and the palette of the author's nickname are offered. */
    val signatureLook: StateFlow<Boolean> = flag(SIGNATURE_LOOK)

    /** Whether the editor and the camera show how fast they convert and record. */
    val developerMode: StateFlow<Boolean> = flag(DEVELOPER_MODE)

    init {
        scope.launch {
            val stored = dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .first()
            // A write that finished first holds the newer values.
            state.compareAndSet(null, stored)
        }
    }

    fun unlockSignatureLook() = write { it[SIGNATURE_LOOK] = true }

    fun setDeveloperMode(enabled: Boolean) = write { it[DEVELOPER_MODE] = enabled }

    private fun write(change: (MutablePreferences) -> Unit) {
        scope.launch {
            state.value = try {
                dataStore.edit(change)
            } catch (_: IOException) {
                // Kept for this session at least.
                (state.value ?: emptyPreferences()).toMutablePreferences().apply(change)
            }
        }
    }

    private fun flag(key: Preferences.Key<Boolean>): StateFlow<Boolean> =
        state.map { it?.get(key) == true }.stateIn(scope, SharingStarted.Eagerly, false)

    private companion object {
        val SIGNATURE_LOOK = booleanPreferencesKey("signature_look")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
    }
}

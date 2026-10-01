package cz.svoby93.asciistudio.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Exercises the real DataStore file, like [SettingsRepositoryTest]. */
class HiddenFeaturesTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `everything is hidden at first`() = runBlocking {
        session(newFile()) { features, _ ->
            assertFalse(features.signatureLook.value)
            assertFalse(features.developerMode.value)
        }
    }

    @Test
    fun `the signature look stays unlocked after a restart`() = runBlocking {
        val file = newFile()
        session(file) { features, _ ->
            features.unlockSignatureLook()
            features.signatureLook.first { it }
        }

        session(file) { features, _ ->
            // Times out unless the stored flag is read.
            features.signatureLook.first { it }
            assertFalse(features.developerMode.value)
        }
    }

    @Test
    fun `the developer mode stays as the user leaves it`() = runBlocking {
        val file = newFile()
        session(file) { features, _ ->
            features.setDeveloperMode(true)
            features.developerMode.first { it }
        }

        session(file) { features, store ->
            features.developerMode.first { it }
            features.setDeveloperMode(false)
            features.developerMode.first { !it }
            assertEquals(false, store.data.first()[booleanPreferencesKey("developer_mode")])
        }
    }

    @Test
    fun `the settings next to the flags stay as they are`() = runBlocking {
        val file = newFile()
        val palette = stringPreferencesKey("palette")
        session(file) { features, store ->
            store.edit { it[palette] = ArtPalette.RUBY.name }
            features.unlockSignatureLook()
            features.signatureLook.first { it }
            assertEquals(ArtPalette.RUBY.name, store.data.first()[palette])
        }
    }

    private suspend fun session(
        file: File,
        block: suspend (HiddenFeatures, DataStore<Preferences>) -> Unit,
    ) {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            withTimeout(TIMEOUT_MS) { block(HiddenFeatures(store, scope), store) }
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    private fun newFile() = File(folder.newFolder(), "settings.preferences_pb")

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}

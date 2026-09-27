package cz.svoby93.asciistudio.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import cz.svoby93.asciistudio.engine.Dithering
import cz.svoby93.asciistudio.engine.EdgeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `defaults are used when nothing is stored`() = runTest {
        val store = openStore(newFile())
        val repository = SettingsRepository(store.dataStore, backgroundScope)

        assertEquals(StudioSettings(), repository.loaded())
    }

    @Test
    fun `changes are applied immediately and survive a restart`() = runTest {
        val file = newFile()
        val changed = StudioSettings(
            columns = 180,
            charset = CharsetPreset.CUSTOM,
            customChars = " .oO@",
            brightness = 0.25f,
            invert = true,
            dithering = Dithering.ATKINSON,
            edgeMode = EdgeMode.MIXED,
            colorMode = ColorMode.PHOTO,
            palette = ArtPalette.PAPER,
        )

        writeThenClose(file) { changed }

        val repository = SettingsRepository(openStore(file).dataStore, backgroundScope)
        assertEquals(changed, repository.loaded())
    }

    @Test
    fun `out of range values are clamped when read`() = runTest {
        val file = newFile()

        writeThenClose(file) { it.copy(columns = 5_000) }

        val repository = SettingsRepository(openStore(file).dataStore, backgroundScope)
        assertEquals(StudioSettings.MAX_COLUMNS, repository.loaded().columns)
    }

    /** Applies [change] through a repository, waits for the debounced write and closes the file. */
    private suspend fun TestScope.writeThenClose(file: File, change: (StudioSettings) -> StudioSettings) {
        val store = openStore(file)
        val repository = SettingsRepository(store.dataStore, backgroundScope)
        val expected = change(repository.loaded())

        repository.update(change)
        assertEquals("Changes apply in memory right away", expected, repository.settings.value)

        // Let the debounce elapse (virtual time), then wait for DataStore's real file write.
        advanceTimeBy(DEBOUNCE_MARGIN_MS)
        store.dataStore.data.first { it.asMap().isNotEmpty() }
        store.scope.coroutineContext.job.cancelAndJoin()
    }

    private suspend fun SettingsRepository.loaded(): StudioSettings = settings.filterNotNull().first()

    private fun newFile() = File(folder.newFolder(), "settings.preferences_pb")

    /** DataStore does real file I/O, so it gets a real dispatcher and a scope we can close. */
    private fun openStore(file: File): OpenStore {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        return OpenStore(PreferenceDataStoreFactory.create(scope = scope) { file }, scope)
    }

    private class OpenStore(val dataStore: DataStore<Preferences>, val scope: CoroutineScope)

    private companion object {
        const val DEBOUNCE_MARGIN_MS = 5_000L
    }
}

package cz.svoby93.asciistudio.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import cz.svoby93.asciistudio.engine.Dithering
import cz.svoby93.asciistudio.engine.EdgeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `defaults are used when nothing is stored`() = runTest {
        val file = File(folder.newFolder(), "settings.preferences_pb")
        val (repository, _) = open(file)

        assertNull(repository.settings.value)
        advanceUntilIdle()

        assertEquals(StudioSettings(), repository.settings.value)
    }

    @Test
    fun `changes are applied immediately and survive a restart`() = runTest {
        val file = File(folder.newFolder(), "settings.preferences_pb")
        val (first, firstStore) = open(file)
        advanceUntilIdle()

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
        first.update { changed }
        assertEquals(changed, first.settings.value)

        // Writes are debounced; let them happen, then "restart" with a fresh repository.
        advanceTimeBy(5_000)
        advanceUntilIdle()
        firstStore.cancel()

        val (second, _) = open(file)
        advanceUntilIdle()
        assertEquals(changed, second.settings.value)
    }

    @Test
    fun `out of range values are clamped when read`() = runTest {
        val file = File(folder.newFolder(), "settings.preferences_pb")
        val (first, firstStore) = open(file)
        advanceUntilIdle()
        first.update { it.copy(columns = 5_000) }
        advanceTimeBy(5_000)
        advanceUntilIdle()
        firstStore.cancel()

        val (second, _) = open(file)
        advanceUntilIdle()
        assertEquals(StudioSettings.MAX_COLUMNS, second.settings.value?.columns)
    }

    /** A repository plus the scope of its DataStore, which must be cancelled before reopening the file. */
    private fun TestScope.open(file: File): Pair<SettingsRepository, CoroutineScope> {
        val storeScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = storeScope) { file }
        return SettingsRepository(store, backgroundScope) to storeScope
    }
}

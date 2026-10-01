package cz.svoby93.asciistudio.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import cz.svoby93.asciistudio.engine.Dithering
import cz.svoby93.asciistudio.engine.EdgeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Exercises the real DataStore file format. Everything runs in real time: DataStore does real
 * file I/O and the repository's debounce is short, so the tests stay fast and deterministic.
 */
class SettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `defaults are used when nothing is stored`() = runBlocking {
        session(newFile()) { repository, _ ->
            assertEquals(StudioSettings(), repository.loaded())
        }
    }

    @Test
    fun `changes are applied immediately and survive a restart`() = runBlocking {
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
            colorTiles = true,
            palette = ArtPalette.PAPER,
            backdrop = Backdrop.RAIN,
        )

        session(file) { repository, store ->
            repository.loaded()
            repository.update { changed }
            assertEquals(changed, repository.settings.value)
            store.awaitWrite()
        }

        session(file) { repository, _ ->
            assertEquals(changed, repository.loaded())
        }
    }

    @Test
    fun `out of range values are clamped when read`() = runBlocking {
        val file = newFile()

        session(file) { repository, store ->
            repository.loaded()
            repository.update { it.copy(columns = 5_000) }
            store.awaitWrite()
        }

        session(file) { repository, _ ->
            assertEquals(StudioSettings.MAX_COLUMNS, repository.loaded().columns)
        }
    }

    /** Opens [file] with a fresh DataStore and repository, runs [block], then closes both again. */
    private suspend fun session(
        file: File,
        block: suspend (SettingsRepository, DataStore<Preferences>) -> Unit,
    ) {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            withTimeout(TIMEOUT_MS) { block(SettingsRepository(store, scope), store) }
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    private suspend fun SettingsRepository.loaded(): StudioSettings = settings.filterNotNull().first()

    /** Waits until the repository's debounced write has reached the file. */
    private suspend fun DataStore<Preferences>.awaitWrite() {
        data.first { it.asMap().isNotEmpty() }
    }

    private fun newFile() = File(folder.newFolder(), "settings.preferences_pb")

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}

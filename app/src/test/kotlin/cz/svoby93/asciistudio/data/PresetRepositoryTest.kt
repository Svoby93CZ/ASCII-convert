package cz.svoby93.asciistudio.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cz.svoby93.asciistudio.engine.Dithering
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Exercises the real DataStore file, like [SettingsRepositoryTest]. */
class PresetRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `there are no presets at first`() = runBlocking {
        session(newFile()) { repository, _ ->
            assertEquals(emptyList<UserPreset>(), repository.loaded())
        }
    }

    @Test
    fun `saved presets survive a restart, without the width and the background`() = runBlocking {
        val file = newFile()
        val settings = StudioSettings(
            columns = 60,
            charset = CharsetPreset.BLOCKS,
            dithering = Dithering.BAYER,
            palette = ArtPalette.LCD,
            backdrop = Backdrop.CRT,
        )

        session(file) { repository, store ->
            repository.loaded()
            repository.add("  Handheld  ", settings)
            repository.add("Second", StudioSettings())
            store.awaitPresets(2)
        }

        session(file) { repository, _ ->
            val presets = repository.loaded()
            assertEquals(listOf("Handheld", "Second"), presets.map { it.name })
            val look = presets.first().look
            assertEquals(StudioSettings.DEFAULT_COLUMNS, look.columns)
            assertEquals(StudioSettings().backdrop, look.backdrop)
            assertTrue(settings.hasLookOf(look))
        }
    }

    @Test
    fun `deleted presets stay deleted`() = runBlocking {
        val file = newFile()
        session(file) { repository, store ->
            repository.loaded()
            repository.add("Keep", StudioSettings())
            repository.add("Drop", StudioSettings(palette = ArtPalette.RUBY))
            store.awaitPresets(2)
            repository.delete(repository.loaded().last().id)
            store.awaitPresets(1)
        }

        session(file) { repository, _ ->
            assertEquals(listOf("Keep"), repository.loaded().map { it.name })
        }
    }

    @Test
    fun `presets written by a newer version keep what this one knows`() = runBlocking {
        val file = newFile()
        session(file) { _, store ->
            store.edit {
                it[stringPreferencesKey("user_presets")] =
                    """[{"id":"1","name":"Future","look":{"palette":"AURORA","contrast":0.5,"glow":true},"pinned":true}]"""
            }
        }

        session(file) { repository, _ ->
            val preset = repository.loaded().single()
            assertEquals("Future", preset.name)
            assertEquals(0.5f, preset.look.contrast)
            assertEquals(StudioSettings().palette, preset.look.palette)
        }
    }

    @Test
    fun `unreadable presets are left out`() = runBlocking {
        val file = newFile()
        session(file) { _, store ->
            store.edit { it[stringPreferencesKey("user_presets")] = "not json" }
        }

        session(file) { repository, _ ->
            assertEquals(emptyList<UserPreset>(), repository.loaded())
        }
    }

    private suspend fun session(
        file: File,
        block: suspend (PresetRepository, DataStore<Preferences>) -> Unit,
    ) {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            withTimeout(TIMEOUT_MS) { block(PresetRepository(store, scope), store) }
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    private suspend fun PresetRepository.loaded(): List<UserPreset> = presets.filterNotNull().first()

    /**
     * Waits until the file holds [count] presets. Reads again and again, because a collector of
     * `data` that starts while a write is under way can miss that write (DataStore 1.2.1).
     */
    private suspend fun DataStore<Preferences>.awaitPresets(count: Int) {
        while (storedPresets() != count) delay(POLL_MS)
    }

    private suspend fun DataStore<Preferences>.storedPresets(): Int {
        val text = data.first()[stringPreferencesKey("user_presets")] ?: return 0
        return Regex("\"id\"").findAll(text).count()
    }

    private fun newFile() = File(folder.newFolder(), "settings.preferences_pb")

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 10L
    }
}

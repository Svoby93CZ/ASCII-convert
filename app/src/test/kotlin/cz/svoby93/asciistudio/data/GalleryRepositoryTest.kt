package cz.svoby93.asciistudio.data

import cz.svoby93.asciistudio.engine.Dithering
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GalleryRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val directory: File by lazy { folder.newFolder("gallery") }

    @Test
    fun `saved items come back after a restart, newest first`() = runBlocking {
        val settings = StudioSettings(
            columns = 180,
            charset = CharsetPreset.CUSTOM,
            customChars = " .░▒▓█",
            dithering = Dithering.ATKINSON,
            palette = ArtPalette.AMBER,
            backdrop = Backdrop.RAIN,
        )
        val repository = GalleryRepository(directory)
        val older = repository.add(StudioSettings(), 110, 60, bytes(1), bytes(2), createdAt = 1_000)
        val newer = repository.add(settings, 180, 97, bytes(3), bytes(4), createdAt = 2_000)
        assertEquals(listOf(newer, older), repository.items.value)

        val restarted = GalleryRepository(directory)
        assertNull(restarted.items.value)
        assertEquals(listOf(newer, older), restarted.load())
        assertEquals(settings, restarted.find(newer.id)?.settings)
        assertEquals(listOf<Byte>(3), newer.source.readBytes().toList())
        assertEquals(listOf<Byte>(4), newer.preview.readBytes().toList())
    }

    @Test
    fun `deleting removes the item and its files`() = runBlocking {
        val repository = GalleryRepository(directory)
        val kept = repository.add(StudioSettings(), 110, 60, bytes(1), bytes(2), createdAt = 1_000)
        val deleted = repository.add(StudioSettings(), 110, 60, bytes(1), bytes(2), createdAt = 2_000)

        repository.delete(deleted.id)

        assertEquals(listOf(kept), repository.items.value)
        assertFalse(deleted.source.parentFile!!.exists())
        assertEquals(listOf(kept), GalleryRepository(directory).load())
    }

    @Test
    fun `a failed save leaves nothing behind`() = runBlocking {
        val repository = GalleryRepository(directory)
        try {
            repository.add(StudioSettings(), 110, 60, bytes(1), { throw IOException("disk full") })
            fail("The error should reach the caller")
        } catch (_: IOException) {
        }
        assertEquals(emptyList<GalleryItem>(), repository.items.value)
        assertEquals(emptyList<File>(), directory.listFiles()!!.toList())
    }

    @Test
    fun `broken items are skipped and interrupted saves are cleaned up`() = runBlocking {
        val repository = GalleryRepository(directory)
        val good = repository.add(StudioSettings(), 110, 60, bytes(1), bytes(2))
        val interrupted = File(directory, "${good.id.dropLast(1)}0.tmp").apply { mkdirs() }
        val noMetadata = File(directory, good.id.replaceFirstChar { if (it == 'a') 'b' else 'a' }).apply { mkdirs() }
        File(noMetadata, "photo.img").writeBytes(byteArrayOf(1))
        File(noMetadata, "preview.img").writeBytes(byteArrayOf(2))
        File(directory, "notes.txt").writeText("not an item")

        assertEquals(listOf(good), GalleryRepository(directory).load())
        assertFalse(interrupted.exists())
    }

    @Test
    fun `unknown values in stored settings fall back to defaults`() = runBlocking {
        val item = GalleryRepository(directory).add(StudioSettings(), 110, 60, bytes(1), bytes(2))
        File(item.source.parentFile, "item.json").writeText(
            """{"createdAt":5,"columns":110,"rows":60,"future":true,""" +
                """"settings":{"columns":9000,"palette":"NEON","dithering":"BAYER","sparkle":1}}""",
        )

        val settings = GalleryRepository(directory).load().single().settings

        assertEquals(StudioSettings(columns = StudioSettings.MAX_COLUMNS, dithering = Dithering.BAYER), settings)
    }

    @Test
    fun `settings that only differ in the background are the same art`() {
        val art = StudioSettings(palette = ArtPalette.LCD, backdrop = Backdrop.GRID)
        assertTrue(art.sameArtAs(art.copy(backdrop = Backdrop.NONE)))
        assertFalse(art.sameArtAs(art.copy(palette = ArtPalette.PAPER)))
    }

    private fun bytes(value: Int): (OutputStream) -> Unit = { it.write(value) }
}

package cz.svoby93.asciistudio.ui.editor

import cz.svoby93.asciistudio.data.ArtPalette
import cz.svoby93.asciistudio.data.StudioSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHistoryTest {

    private val start = StudioSettings()

    @Test
    fun `undo and redo walk through the steps`() {
        val history = SettingsHistory(mergeMillis = 100)
        val amber = start.copy(palette = ArtPalette.AMBER)
        val wide = amber.copy(columns = 200)
        history.changed(start, timeMillis = 0)
        history.changed(amber, timeMillis = 1_000)

        assertEquals(amber, history.undo(wide))
        assertEquals(start, history.undo(amber))
        assertFalse(history.canUndo)
        assertEquals(amber, history.redo(start))
        assertEquals(wide, history.redo(amber))
        assertFalse(history.canRedo)
    }

    @Test
    fun `changes in quick succession are one step`() {
        val history = SettingsHistory(mergeMillis = 100)
        // One drag of a slider: many small changes, each soon after the previous one.
        var settings = start
        for (step in 1..20) {
            history.changed(settings, timeMillis = step * 30L)
            settings = settings.copy(brightness = step / 20f)
        }

        assertEquals(start, history.undo(settings))
        assertFalse(history.canUndo)
    }

    @Test
    fun `a pause starts a new step, and so does close`() {
        val history = SettingsHistory(mergeMillis = 100)
        val first = start.copy(contrast = 0.5f)
        val second = first.copy(sharpness = 0.9f)
        val third = second.copy(invert = true)
        history.changed(start, timeMillis = 0)
        history.changed(first, timeMillis = 500)
        history.close()
        history.changed(second, timeMillis = 550)

        assertEquals(second, history.undo(third))
        assertEquals(first, history.undo(second))
        assertEquals(start, history.undo(first))
    }

    @Test
    fun `a new change clears what could be redone`() {
        val history = SettingsHistory(mergeMillis = 100)
        val amber = start.copy(palette = ArtPalette.AMBER)
        history.changed(start, timeMillis = 0)
        history.undo(amber)
        assertTrue(history.canRedo)

        history.changed(start, timeMillis = 1_000)

        assertFalse(history.canRedo)
        assertNull(history.redo(start))
    }

    @Test
    fun `a step that ended where it began is skipped`() {
        val history = SettingsHistory(mergeMillis = 100)
        val amber = start.copy(palette = ArtPalette.AMBER)
        history.changed(start, timeMillis = 0)
        // A slider dragged away and back to where it was.
        history.changed(amber, timeMillis = 1_000)

        assertEquals(start, history.undo(amber))
        assertFalse(history.canUndo)
    }

    @Test
    fun `only the latest steps are kept`() {
        val history = SettingsHistory(mergeMillis = 100, limit = 3)
        var settings = start
        for (step in 1..5) {
            history.changed(settings, timeMillis = step * 1_000L)
            settings = settings.copy(columns = 100 + step)
        }

        repeat(3) { settings = history.undo(settings)!! }

        assertEquals(102, settings.columns)
        assertNull(history.undo(settings))
    }
}

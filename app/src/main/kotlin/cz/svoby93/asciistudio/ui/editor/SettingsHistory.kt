package cz.svoby93.asciistudio.ui.editor

import cz.svoby93.asciistudio.data.StudioSettings

/**
 * Undo and redo for the settings of the editor. Changes that follow each other closely, like the
 * many small steps of one drag of a slider, make one step of the history.
 *
 * @param mergeMillis a change within this time of the previous one joins its step.
 * @param limit the number of steps that can be undone.
 */
class SettingsHistory(
    private val mergeMillis: Long = MERGE_MILLIS,
    private val limit: Int = LIMIT,
) {
    private val past = ArrayDeque<StudioSettings>()
    private val future = ArrayDeque<StudioSettings>()
    private var lastChange = 0L
    private var open = false

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    /** Notes that the settings were [before] until a change at [timeMillis]. */
    fun changed(before: StudioSettings, timeMillis: Long) {
        if (!open || timeMillis - lastChange >= mergeMillis) {
            past.addLast(before)
            if (past.size > limit) past.removeFirst()
            future.clear()
        }
        lastChange = timeMillis
        open = true
    }

    /** Lets the next change start a step of its own, e.g. after a change made in one tap. */
    fun close() {
        open = false
    }

    /** The settings before the last step, or `null` when there is none; [current] can be redone. */
    fun undo(current: StudioSettings): StudioSettings? {
        // A step that ended where it started changed nothing to undo.
        while (past.lastOrNull() == current) past.removeLast()
        val previous = past.removeLastOrNull() ?: return null
        future.addLast(current)
        open = false
        return previous
    }

    /** The settings of the last undone step, or `null` when there is none. */
    fun redo(current: StudioSettings): StudioSettings? {
        val next = future.removeLastOrNull() ?: return null
        past.addLast(current)
        open = false
        return next
    }

    private companion object {
        const val MERGE_MILLIS = 600L
        const val LIMIT = 100
    }
}

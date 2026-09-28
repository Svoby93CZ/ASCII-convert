package cz.svoby93.asciistudio.ui.editor

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import cz.svoby93.asciistudio.data.StudioSettings
import cz.svoby93.asciistudio.engine.AsciiArt

sealed interface EditorLoadState {
    data object Loading : EditorLoadState

    /** [preview] is the photo itself, shown when the user compares the art with the original. */
    class Ready(val preview: ImageBitmap) : EditorLoadState

    data class Failed(@StringRes val message: Int) : EditorLoadState
}

data class EditorUiState(
    val load: EditorLoadState = EditorLoadState.Loading,
    val settings: StudioSettings? = null,
    val art: AsciiArt? = null,
    /** An export or a save to the gallery is running. */
    val isBusy: Boolean = false,
    /** The gallery holds this photo with the current settings already. */
    val isSaved: Boolean = false,
)

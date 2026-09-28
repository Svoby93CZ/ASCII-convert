package cz.svoby93.asciistudio.ui.home

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.svoby93.asciistudio.data.GalleryRepository
import cz.svoby93.asciistudio.data.ImageRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(images: ImageRepository, gallery: GalleryRepository) : ViewModel() {

    /** The image from the last session, offered as "continue editing". */
    val recentImage: StateFlow<ImageBitmap?> = images.image
        .map { it?.bitmap?.asImageBitmap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val galleryCount: StateFlow<Int> = gallery.items
        .map { it?.size ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch { images.restore() }
        viewModelScope.launch { gallery.load() }
    }
}

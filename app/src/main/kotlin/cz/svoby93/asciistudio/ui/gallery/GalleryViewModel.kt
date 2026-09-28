package cz.svoby93.asciistudio.ui.gallery

import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.svoby93.asciistudio.data.GalleryItem
import cz.svoby93.asciistudio.data.GalleryRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryViewModel(private val gallery: GalleryRepository) : ViewModel() {

    /** Newest first; `null` while the gallery is being read. */
    val items: StateFlow<List<GalleryItem>?> = gallery.items

    /** Decoded previews, so that scrolling back does not decode them again. */
    private val previews = object : LruCache<String, ImageBitmap>(PREVIEW_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * BYTES_PER_PIXEL
    }

    init {
        viewModelScope.launch { gallery.load() }
    }

    fun cachedPreview(item: GalleryItem): ImageBitmap? = previews[item.id]

    suspend fun loadPreview(item: GalleryItem): ImageBitmap? = previews[item.id] ?: withContext(Dispatchers.IO) {
        BitmapFactory.decodeFile(item.preview.path)?.asImageBitmap()?.also { previews.put(item.id, it) }
    }

    fun delete(item: GalleryItem) {
        viewModelScope.launch {
            try {
                gallery.delete(item.id)
                previews.remove(item.id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                // The item stays in the list, so the user can simply try again.
                Log.w(TAG, "Cannot delete ${item.id}", error)
            }
        }
    }

    private companion object {
        const val TAG = "AsciiStudio"
        const val BYTES_PER_PIXEL = 4
        const val PREVIEW_CACHE_BYTES = 32 * 1024 * 1024
    }
}

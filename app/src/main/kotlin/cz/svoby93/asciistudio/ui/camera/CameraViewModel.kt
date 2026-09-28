package cz.svoby93.asciistudio.ui.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.svoby93.asciistudio.data.ImageRepository
import cz.svoby93.asciistudio.data.SettingsRepository
import cz.svoby93.asciistudio.data.StudioSettings
import cz.svoby93.asciistudio.engine.AsciiArt
import cz.svoby93.asciistudio.engine.AsciiConverter
import cz.svoby93.asciistudio.engine.PixelImage
import cz.svoby93.asciistudio.render.AsciiOptionsFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CameraEvent {
    data object PhotoReady : CameraEvent
    data object CaptureFailed : CameraEvent
}

/**
 * Owns the CameraX use cases: a low resolution analysis stream that is converted to ASCII art
 * on every frame, and a still capture for the photo that is then opened in the editor.
 */
class CameraViewModel(
    private val images: ImageRepository,
    private val settingsRepository: SettingsRepository,
    private val optionsFactory: AsciiOptionsFactory,
) : ViewModel() {

    val settings: StateFlow<StudioSettings?> = settingsRepository.settings

    private val liveArt = MutableStateFlow<AsciiArt?>(null)
    val art: StateFlow<AsciiArt?> = liveArt.asStateFlow()

    private val lens = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing: StateFlow<Int> = lens.asStateFlow()

    private val capturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = capturing.asStateFlow()

    private val eventChannel = Channel<CameraEvent>(Channel.BUFFERED)
    val events: Flow<CameraEvent> = eventChannel.receiveAsFlow()

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
        .setResolutionSelector(resolutionSelector(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()
        .also { analysis -> analysis.setAnalyzer(analysisExecutor, ::analyze) }

    val imageCapture: ImageCapture = ImageCapture.Builder()
        .setResolutionSelector(resolutionSelector(Size(2048, 1536), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

    fun switchCamera() {
        lens.update { if (it == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }
    }

    fun updateSettings(transform: (StudioSettings) -> StudioSettings) = settingsRepository.update(transform)

    fun capture() {
        if (!capturing.compareAndSet(expect = false, update = true)) return
        val mirror = lens.value == CameraSelector.LENS_FACING_FRONT
        imageCapture.takePicture(
            analysisExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val photo = image.use { upright(it.toBitmap(), it.imageInfo.rotationDegrees, mirror, MAX_PHOTO_SIZE) }
                    viewModelScope.launch {
                        try {
                            images.importBitmap(photo)
                            eventChannel.send(CameraEvent.PhotoReady)
                        } finally {
                            capturing.value = false
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.w(TAG, "Capture failed", exception)
                    capturing.value = false
                    eventChannel.trySend(CameraEvent.CaptureFailed)
                }
            },
        )
    }

    private fun analyze(image: ImageProxy) {
        image.use { frame ->
            val settings = settingsRepository.settings.value ?: return
            val columns = min(settings.columns, StudioSettings.MAX_LIVE_COLUMNS)
            val mirror = lens.value == CameraSelector.LENS_FACING_FRONT
            val source = frame.toBitmap()
            val small = upright(source, frame.imageInfo.rotationDegrees, mirror, columns * SAMPLES_PER_COLUMN)
            if (small !== source) source.recycle()
            val pixels = IntArray(small.width * small.height)
            small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
            small.recycle()
            liveArt.value = AsciiConverter.convert(
                PixelImage(small.width, small.height, pixels),
                optionsFactory.create(settings, columns),
            )
        }
    }

    /** Rotates the sensor image upright, mirrors selfies and shrinks it to [maxSize] pixels. */
    private fun upright(bitmap: Bitmap, rotationDegrees: Int, mirror: Boolean, maxSize: Int): Bitmap {
        val scale = min(1f, maxSize.toFloat() / max(bitmap.width, bitmap.height))
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            if (mirror) postScale(-1f, 1f)
            postScale(scale, scale)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    override fun onCleared() {
        imageAnalysis.clearAnalyzer()
        analysisExecutor.shutdown()
    }

    private companion object {
        const val TAG = "AsciiStudio"

        /** Source pixels per text column for the live preview: enough for good averaging. */
        const val SAMPLES_PER_COLUMN = 4
        const val MAX_PHOTO_SIZE = 1600

        fun resolutionSelector(size: Size, fallbackRule: Int): ResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(size, fallbackRule))
            .build()
    }
}

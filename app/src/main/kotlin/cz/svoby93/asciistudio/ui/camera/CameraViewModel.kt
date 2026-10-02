package cz.svoby93.asciistudio.ui.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
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
import cz.svoby93.asciistudio.data.ArtExporter
import cz.svoby93.asciistudio.data.HiddenFeatures
import cz.svoby93.asciistudio.data.ImageRepository
import cz.svoby93.asciistudio.data.PresetRepository
import cz.svoby93.asciistudio.data.SettingsRepository
import cz.svoby93.asciistudio.data.StudioSettings
import cz.svoby93.asciistudio.data.UserPreset
import cz.svoby93.asciistudio.data.VideoFile
import cz.svoby93.asciistudio.engine.AsciiArt
import cz.svoby93.asciistudio.engine.LiveConverter
import cz.svoby93.asciistudio.engine.PixelImage
import cz.svoby93.asciistudio.render.ArtRecorder
import cz.svoby93.asciistudio.render.AsciiOptionsFactory
import cz.svoby93.asciistudio.render.LookPreviewer
import cz.svoby93.asciistudio.render.artStyle
import cz.svoby93.asciistudio.ui.studio.DeveloperStats
import cz.svoby93.asciistudio.ui.studio.statsLine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CameraEvent {
    data object PhotoReady : CameraEvent
    data object CaptureFailed : CameraEvent

    /** A recording was saved; [uri] shares it, and [limitReached] says that it stopped by itself. */
    data class VideoSaved(val uri: Uri?, val limitReached: Boolean) : CameraEvent

    data object VideoFailed : CameraEvent
}

/** How fast the live picture goes, shown in the developer mode. */
data class LiveStats(
    /** Frames converted in a second, and how long one took to read and convert on average. */
    val framesPerSecond: Float,
    val convertMillis: Float,
    /** The encoder while a recording runs, and the frames it got in a second. */
    val recorder: ArtRecorder.Info?,
    val recordedPerSecond: Float,
) {
    /** The lines of [DeveloperStats]: the conversion, then the video while it records. */
    fun lines(): List<String> = buildList {
        add(statsLine("%.1f fps · %.1f ms", framesPerSecond, convertMillis))
        if (recorder != null) {
            val megabits = recorder.bitRate / BITS_PER_MEGABIT
            val size = "${recorder.width}×${recorder.height}"
            add(statsLine("REC %.1f fps · %s · %.1f Mb/s", recordedPerSecond, size, megabits))
            val kind = when (recorder.hardware) {
                true -> "HW"
                false -> "SW"
                null -> "?"
            }
            add("${recorder.name} · $kind")
        }
    }

    private companion object {
        const val BITS_PER_MEGABIT = 1_000_000f
    }
}

/**
 * Owns the CameraX use cases: a low resolution analysis stream that is converted to ASCII art
 * on every frame, and a still capture for the photo that is then opened in the editor. The art of
 * the stream can be recorded as a video.
 */
class CameraViewModel(
    private val images: ImageRepository,
    private val settingsRepository: SettingsRepository,
    private val presetRepository: PresetRepository,
    private val optionsFactory: AsciiOptionsFactory,
    private val exporter: ArtExporter,
    private val hiddenFeatures: HiddenFeatures,
) : ViewModel() {

    val settings: StateFlow<StudioSettings?> = settingsRepository.settings

    /** The presets the user saved; empty while they are being read. */
    val userPresets: StateFlow<List<UserPreset>> = presetRepository.presets
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val liveThumbnail = MutableStateFlow<PixelImage?>(null)

    /** A small copy of the live picture for the previews of the presets, renewed about once a second. */
    val thumbnail: StateFlow<PixelImage?> = liveThumbnail.asStateFlow()
    private var thumbnailTime = 0L

    private val liveArt = MutableStateFlow<AsciiArt?>(null)
    val art: StateFlow<AsciiArt?> = liveArt.asStateFlow()

    private val lens = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing: StateFlow<Int> = lens.asStateFlow()

    private val capturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = capturing.asStateFlow()

    private val recordingStart = MutableStateFlow<Long?>(null)

    /** When the current recording started, as [SystemClock.elapsedRealtime], or `null`. */
    val recordingSince: StateFlow<Long?> = recordingStart.asStateFlow()

    /** The recording in progress, on the analysis thread only. */
    private var recording: Recording? = null

    private val eventChannel = Channel<CameraEvent>(Channel.BUFFERED)
    val events: Flow<CameraEvent> = eventChannel.receiveAsFlow()

    private val liveStats = MutableStateFlow<LiveStats?>(null)

    /** Measured only in the developer mode, a few times a second; `null` until then. */
    val stats: StateFlow<LiveStats?> = liveStats.asStateFlow()

    /** Counts the frames for [stats], on the analysis thread only. */
    private val meter = StatsMeter { liveStats.value = it }

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /** Reads the frames of the analysis stream, on its thread only. */
    private val frames = RgbaFrameReader()

    /** Keeps the live picture calm from frame to frame, on the analysis thread only. */
    private val converter = LiveConverter()

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
            val started = System.nanoTime()
            val columns = min(settings.columns, StudioSettings.MAX_LIVE_COLUMNS)
            val plane = frame.planes[0]
            val crop = frame.cropRect
            val picture = frames.read(
                plane.buffer,
                plane.rowStride,
                plane.pixelStride,
                crop.left,
                crop.top,
                crop.width(),
                crop.height(),
                frame.imageInfo.rotationDegrees,
                mirror = lens.value == CameraSelector.LENS_FACING_FRONT,
                maxSize = columns * SAMPLES_PER_COLUMN,
            )
            val art = converter.convert(picture, optionsFactory.create(settings, columns))
            val converted = System.nanoTime()
            liveArt.value = art
            recording?.let { record(it, art, settings) }
            if (hiddenFeatures.developerMode.value) {
                meter.frame(converted, converted - started, recording?.recorder?.info)
            } else {
                meter.reset()
            }
            val now = SystemClock.uptimeMillis()
            if (now - thumbnailTime >= THUMBNAIL_INTERVAL_MS) {
                thumbnailTime = now
                // A copy: the reader reuses the pixels of the picture for the next frame.
                liveThumbnail.value = picture.thumbnail(LookPreviewer.THUMBNAIL_SIZE)
            }
        }
    }

    fun savePreset(name: String) {
        val current = settingsRepository.settings.value ?: return
        presetRepository.add(name, current)
    }

    /** Starts recording the live art, which needs art to take the shape of the video from. */
    fun startRecording() {
        val since = SystemClock.elapsedRealtime()
        if (!recordingStart.compareAndSet(expect = null, update = since)) return
        analysisExecutor.execute {
            val art = liveArt.value
            var video: VideoFile? = null
            try {
                checkNotNull(art) { "No art to record yet" }
                video = exporter.createVideo()
                val recorder = exporter.recorder(video)
                recorder.start(art)
                recording = Recording(recorder, video)
            } catch (error: Exception) {
                Log.w(TAG, "Recording failed to start", error)
                video?.discard()
                recordingStart.value = null
                eventChannel.trySend(CameraEvent.VideoFailed)
            }
        }
    }

    /** Stops recording and saves the video; it is safe to call when nothing is being recorded. */
    fun stopRecording() {
        if (recordingStart.getAndUpdate { null } == null) return
        analysisExecutor.execute { finishRecording(limitReached = false) }
    }

    /** Adds a frame to [current], on the analysis thread; a recording ends by itself after a while. */
    private fun record(current: Recording, art: AsciiArt, settings: StudioSettings) {
        try {
            current.recorder.write(art, settings.artStyle())
            meter.recorded()
        } catch (error: Exception) {
            Log.w(TAG, "Recording failed", error)
            recording = null
            current.recorder.abort()
            current.video.discard()
            recordingStart.value = null
            eventChannel.trySend(CameraEvent.VideoFailed)
            return
        }
        val since = recordingStart.value ?: return
        if (SystemClock.elapsedRealtime() - since >= MAX_RECORDING_MS) {
            recordingStart.value = null
            finishRecording(limitReached = true)
        }
    }

    private fun finishRecording(limitReached: Boolean) {
        val current = recording ?: return
        recording = null
        try {
            if (current.recorder.finish()) {
                eventChannel.trySend(CameraEvent.VideoSaved(current.video.publish(), limitReached))
            } else {
                current.video.discard()
                eventChannel.trySend(CameraEvent.VideoFailed)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Recording failed to finish", error)
            current.video.discard()
            eventChannel.trySend(CameraEvent.VideoFailed)
        }
    }

    private class Recording(val recorder: ArtRecorder, val video: VideoFile)

    /** Adds up frames and their times, and hands on [LiveStats] about twice a second. */
    private class StatsMeter(private val publish: (LiveStats) -> Unit) {
        private var since = 0L
        private var frames = 0
        private var recorded = 0
        private var convertNanos = 0L

        fun frame(now: Long, nanos: Long, recorder: ArtRecorder.Info?) {
            if (since == 0L) since = now
            frames++
            convertNanos += nanos
            val elapsed = now - since
            if (elapsed < STATS_INTERVAL_NANOS) return
            val seconds = elapsed / NANOS_PER_SECOND
            publish(LiveStats(frames / seconds, convertNanos / NANOS_PER_MILLI / frames, recorder, recorded / seconds))
            reset()
            since = now
        }

        fun recorded() {
            recorded++
        }

        /** Starts counting afresh, e.g. after the developer mode was off for a while. */
        fun reset() {
            since = 0L
            frames = 0
            recorded = 0
            convertNanos = 0L
        }
    }

    fun deletePreset(preset: UserPreset) = presetRepository.delete(preset.id)

    /** Rotates the photo upright, mirrors selfies and shrinks it to [maxSize] pixels. */
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
        // Queued before the shutdown, so a recording is still saved when the user leaves.
        stopRecording()
        imageAnalysis.clearAnalyzer()
        analysisExecutor.shutdown()
    }

    private companion object {
        const val TAG = "AsciiStudio"

        /** Source pixels per text column for the live preview: enough for good averaging. */
        const val SAMPLES_PER_COLUMN = 4
        const val MAX_PHOTO_SIZE = 1600
        const val THUMBNAIL_INTERVAL_MS = 1_000L
        const val STOP_TIMEOUT_MS = 5_000L

        /** Three minutes are plenty for a clip to share, and keep a video under 150 MB. */
        const val MAX_RECORDING_MS = 3 * 60 * 1_000L

        const val STATS_INTERVAL_NANOS = 500_000_000L
        const val NANOS_PER_SECOND = 1e9f
        const val NANOS_PER_MILLI = 1e6f

        fun resolutionSelector(size: Size, fallbackRule: Int): ResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(size, fallbackRule))
            .build()
    }
}

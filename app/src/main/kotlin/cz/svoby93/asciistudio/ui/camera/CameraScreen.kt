package cz.svoby93.asciistudio.ui.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.svoby93.asciistudio.LocalAppContainer
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.ui.studio.LocalStudioColors
import cz.svoby93.asciistudio.ui.studio.TerminalFrame
import cz.svoby93.asciistudio.ui.studio.findActivity
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
fun CameraScreen(onBack: () -> Unit, onCaptured: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        askedOnce = true
    }

    // The user may grant the permission in system settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = context.hasCameraPermission() }
    LaunchedEffect(Unit) {
        if (!granted && !askedOnce) requestPermission.launch(Manifest.permission.CAMERA)
    }

    if (granted) {
        LiveCamera(onBack = onBack, onCaptured = onCaptured)
    } else {
        val activity = context.findActivity()
        // Once the user said "don't ask again" only the settings screen can help.
        val canAskAgain = !askedOnce ||
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
        PermissionRationale(
            canAskAgain = canAskAgain,
            onRequest = { requestPermission.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
            onBack = onBack,
        )
    }
}

@Composable
private fun LiveCamera(onBack: () -> Unit, onCaptured: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: CameraViewModel = viewModel {
        CameraViewModel(container.imageRepository, container.settingsRepository, container.optionsFactory)
    }
    val art by viewModel.art.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lensFacing by viewModel.lensFacing.collectAsStateWithLifecycle()
    val capturing by viewModel.isCapturing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    var cameraUnavailable by remember { mutableStateOf(false) }

    LaunchedEffect(lensFacing) {
        val provider = context.cameraProvider()
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val available = try {
            provider.hasCamera(selector)
        } catch (_: CameraInfoUnavailableException) {
            false
        }
        cameraUnavailable = !available
        if (!available) return@LaunchedEffect
        val rotation = view.display?.rotation ?: Surface.ROTATION_0
        viewModel.imageAnalysis.targetRotation = rotation
        viewModel.imageCapture.targetRotation = rotation
        try {
            provider.unbindAll()
            // Bound to this screen's lifecycle, so the camera closes as soon as the user leaves.
            provider.bindToLifecycle(lifecycleOwner, selector, viewModel.imageAnalysis, viewModel.imageCapture)
        } catch (_: IllegalArgumentException) {
            cameraUnavailable = true
        } catch (_: IllegalStateException) {
            cameraUnavailable = true
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CameraEvent.PhotoReady -> onCaptured()
                CameraEvent.CaptureFailed -> launch {
                    snackbarHostState.showSnackbar(resources.getString(R.string.camera_capture_failed))
                }
            }
        }
    }

    // The stored settings arrive within moments; until then the empty background shows.
    val current = settings ?: return
    CameraContent(
        art = art,
        settings = current,
        frontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT,
        cameraUnavailable = cameraUnavailable,
        capturing = capturing,
        onBack = onBack,
        onCapture = viewModel::capture,
        onSwitchCamera = viewModel::switchCamera,
        onChange = viewModel::updateSettings,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun PermissionRationale(
    canAskAgain: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalStudioColors.current
    Box(Modifier.fillMaxSize()) {
        TerminalFrame(
            title = stringResource(R.string.camera_permission_title),
            modifier = Modifier
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_photo_camera),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    stringResource(R.string.camera_permission_text),
                    color = colors.tint(0.85f),
                    textAlign = TextAlign.Center,
                )
                if (canAskAgain) {
                    Button(onClick = onRequest) { Text(stringResource(R.string.camera_permission_grant)) }
                } else {
                    Button(onClick = onOpenSettings) { Text(stringResource(R.string.camera_permission_settings)) }
                }
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private suspend fun Context.cameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener(
        {
            try {
                continuation.resume(future.get())
            } catch (error: ExecutionException) {
                continuation.resumeWithException(error.cause ?: error)
            }
        },
        ContextCompat.getMainExecutor(this),
    )
}

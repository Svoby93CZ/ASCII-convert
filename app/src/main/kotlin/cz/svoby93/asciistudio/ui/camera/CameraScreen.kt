package cz.svoby93.asciistudio.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import cz.svoby93.asciistudio.data.CharsetPreset
import cz.svoby93.asciistudio.data.ColorMode
import cz.svoby93.asciistudio.render.artStyle
import cz.svoby93.asciistudio.ui.components.AsciiArtView
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
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
                    snackbarHostState.showSnackbar(context.getString(R.string.camera_capture_failed))
                }
            }
        }
    }

    val style = settings?.artStyle()
    Box(
        Modifier
            .fillMaxSize()
            .background(style?.let { Color(it.background) } ?: Color.Black),
    ) {
        if (style != null) {
            AsciiArtView(art = art, style = style, modifier = Modifier.fillMaxSize(), interactive = false)
        }
        if (cameraUnavailable) {
            Text(
                stringResource(R.string.camera_unavailable),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            )
        } else if (art == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            FilledTonalIconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
            Spacer(Modifier.weight(1f))
            FilledTonalIconToggleButton(
                checked = settings?.colorMode == ColorMode.PHOTO,
                onCheckedChange = { photoColors ->
                    viewModel.updateSettings { it.copy(colorMode = if (photoColors) ColorMode.PHOTO else ColorMode.PALETTE) }
                },
            ) {
                Icon(painterResource(R.drawable.ic_palette), contentDescription = stringResource(R.string.camera_photo_colors))
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                CharsetPreset.entries.filter { it != CharsetPreset.CUSTOM }.forEach { preset ->
                    FilterChip(
                        selected = settings?.charset == preset,
                        onClick = { viewModel.updateSettings { it.copy(charset = preset) } },
                        label = { Text(stringResource(preset.label)) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        ),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Spacer(Modifier.size(56.dp))
                ShutterButton(enabled = !capturing && !cameraUnavailable, onClick = viewModel::capture)
                FilledTonalIconButton(onClick = viewModel::switchCamera, modifier = Modifier.size(56.dp)) {
                    Icon(painterResource(R.drawable.ic_cameraswitch), contentDescription = stringResource(R.string.camera_switch))
                }
            }
        }

        SnackbarHost(snackbarHostState, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.camera_capture)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color.White.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun PermissionRationale(
    canAskAgain: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_photo_camera),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(48.dp),
        )
        Text(stringResource(R.string.camera_permission_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text(
            stringResource(R.string.camera_permission_text),
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        if (canAskAgain) {
            Button(onClick = onRequest) { Text(stringResource(R.string.camera_permission_grant)) }
        } else {
            Button(onClick = onOpenSettings) { Text(stringResource(R.string.camera_permission_settings)) }
        }
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back), color = Color.White) }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

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

package cz.svoby93.asciistudio.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.svoby93.asciistudio.BuildConfig
import cz.svoby93.asciistudio.LocalAppContainer
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.Signature
import cz.svoby93.asciistudio.ui.studio.TerminalDialog
import cz.svoby93.asciistudio.ui.studio.TerminalLine
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onImagePicked: (Uri) -> Unit,
    onContinueEditing: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel { HomeViewModel(container.imageRepository, container.galleryRepository) }
    val recentImage by viewModel.recentImage.collectAsStateWithLifecycle()
    val galleryCount by viewModel.galleryCount.collectAsStateWithLifecycle()
    val developerMode by container.hiddenFeatures.developerMode.collectAsStateWithLifecycle()
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showPrivacyPolicy by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    // The system photo picker needs no storage permission at all.
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImagePicked(uri)
    }

    HomeContent(
        recentImage = recentImage,
        galleryCount = galleryCount,
        onPickImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onOpenCamera = onOpenCamera,
        onOpenGallery = onOpenGallery,
        onContinueEditing = onContinueEditing,
        onAbout = { showAbout = true },
        onSignature = {
            // The first time, the donut also unlocks the look drawn with the letters of the name.
            val features = container.hiddenFeatures
            if (!features.signatureLook.value) {
                features.unlockSignatureLook()
                val message = resources.getString(R.string.message_signature_unlocked)
                scope.launch { snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long) }
            }
        },
        snackbarHostState = snackbarHostState,
    )

    if (showAbout) {
        AboutDialog(
            developerMode = developerMode,
            onDeveloperModeChange = container.hiddenFeatures::setDeveloperMode,
            onDismiss = { showAbout = false },
            onShowPrivacyPolicy = {
                showAbout = false
                showPrivacyPolicy = true
            },
        )
    }
    if (showPrivacyPolicy) PrivacyPolicyDialog(onDismiss = { showPrivacyPolicy = false })
}

/**
 * The version, the licences and the author's signature. Like the build number in the settings of
 * Android, seven taps on the version switch the developer mode on or off.
 */
@Composable
private fun AboutDialog(
    developerMode: Boolean,
    onDeveloperModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onShowPrivacyPolicy: () -> Unit,
) {
    var taps by remember { mutableIntStateOf(0) }
    // The mode the taps switched to while the dialog is open.
    var switchedTo by remember { mutableStateOf<Boolean?>(null) }
    val currentMode by rememberUpdatedState(developerMode)
    val currentOnChange by rememberUpdatedState(onDeveloperModeChange)
    TerminalDialog(
        title = stringResource(R.string.about_title, BuildConfig.VERSION_NAME),
        onDismiss = onDismiss,
        buttons = {
            TextButton(onClick = onShowPrivacyPolicy) { Text(stringResource(R.string.action_privacy_policy)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        titleModifier = Modifier.pointerInput(Unit) {
            detectTapGestures {
                taps++
                if (taps == DEVELOPER_TAPS) {
                    taps = 0
                    switchedTo = !currentMode
                    currentOnChange(!currentMode)
                }
            }
        },
    ) {
        Text(stringResource(R.string.about_text))
        // The author's nickname in Braille; screen readers say the name instead of the dots.
        Text(
            Signature.BRAILLE,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .align(Alignment.End)
                .clearAndSetSemantics { contentDescription = Signature.NAME },
        )
        val tapsLeft = DEVELOPER_TAPS - taps
        val line = when {
            taps > 0 && tapsLeft <= COUNTDOWN_TAPS ->
                pluralStringResource(R.plurals.developer_taps_left, tapsLeft, tapsLeft)
            switchedTo == true -> stringResource(R.string.developer_mode_on)
            switchedTo == false -> stringResource(R.string.developer_mode_off)
            else -> null
        }
        if (line != null) TerminalLine(line)
    }
}

/** Google Play wants the privacy policy in the app too. Keep it in line with store/privacy-policy.md. */
@Composable
private fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    TerminalDialog(
        title = stringResource(R.string.privacy_policy_title),
        onDismiss = onDismiss,
        buttons = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    ) {
        Text(stringResource(R.string.privacy_policy_text))
    }
}

/** Taps on the version that switch the developer mode, and how many of them count down on screen. */
private const val DEVELOPER_TAPS = 7
private const val COUNTDOWN_TAPS = 4

package cz.svoby93.asciistudio.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.svoby93.asciistudio.BuildConfig
import cz.svoby93.asciistudio.LocalAppContainer
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.ui.studio.TerminalDialog

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
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showPrivacyPolicy by rememberSaveable { mutableStateOf(false) }

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
    )

    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onShowPrivacyPolicy = {
                showAbout = false
                showPrivacyPolicy = true
            },
        )
    }
    if (showPrivacyPolicy) PrivacyPolicyDialog(onDismiss = { showPrivacyPolicy = false })
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit, onShowPrivacyPolicy: () -> Unit) {
    TerminalDialog(
        title = stringResource(R.string.about_title, BuildConfig.VERSION_NAME),
        onDismiss = onDismiss,
        buttons = {
            TextButton(onClick = onShowPrivacyPolicy) { Text(stringResource(R.string.action_privacy_policy)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    ) {
        Text(stringResource(R.string.about_text))
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

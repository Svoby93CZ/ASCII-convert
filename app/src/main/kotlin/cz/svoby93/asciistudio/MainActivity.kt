package cz.svoby93.asciistudio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import cz.svoby93.asciistudio.ui.AsciiStudioNavHost
import cz.svoby93.asciistudio.ui.EditorRoute
import cz.svoby93.asciistudio.ui.HomeRoute
import cz.svoby93.asciistudio.ui.studio.StudioRoot
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {

    /** Images shared to the app from elsewhere, delivered to the navigation graph in order. */
    private val sharedImages = Channel<Uri>(Channel.UNLIMITED)

    override fun onCreate(savedInstanceState: Bundle?) {
        val container = (application as AsciiStudioApp).container
        // The screens take their colours from the stored palette; the splash screen covers the
        // few milliseconds it takes to read it.
        installSplashScreen().setKeepOnScreenCondition { container.settingsRepository.settings.value == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // On recreation the navigation state already contains the shared image.
        if (savedInstanceState == null) sharedImageUri(intent)?.let(sharedImages::trySend)
        addOnNewIntentListener { newIntent -> sharedImageUri(newIntent)?.let(sharedImages::trySend) }

        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
                StudioRoot(settings) {
                    val navController = rememberNavController()
                    LaunchedEffect(navController) {
                        sharedImages.receiveAsFlow().collect { uri ->
                            navController.navigate(EditorRoute(uri.toString())) { popUpTo<HomeRoute>() }
                        }
                    }
                    AsciiStudioNavHost(navController)
                }
            }
        }
    }

    private fun sharedImageUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return null
        return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    }
}

package cz.svoby93.asciistudio.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import cz.svoby93.asciistudio.ui.camera.CameraScreen
import cz.svoby93.asciistudio.ui.editor.EditorScreen
import cz.svoby93.asciistudio.ui.home.HomeScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

/** Opens the editor; with [imageUri] the image is imported first, otherwise the current one is edited. */
@Serializable
data class EditorRoute(val imageUri: String? = null)

@Serializable
data object CameraRoute

@Composable
fun AsciiStudioNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(TRANSITION_MS)) +
                fadeIn(tween(TRANSITION_MS))
        },
        exitTransition = { fadeOut(tween(TRANSITION_MS)) },
        popEnterTransition = { fadeIn(tween(TRANSITION_MS)) },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(TRANSITION_MS)) +
                fadeOut(tween(TRANSITION_MS))
        },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onImagePicked = { uri -> navController.navigate(EditorRoute(uri.toString())) },
                onContinueEditing = { navController.navigate(EditorRoute()) },
                onOpenCamera = { navController.navigate(CameraRoute) },
            )
        }
        composable<EditorRoute> {
            EditorScreen(onBack = { navController.popBackStack() })
        }
        composable<CameraRoute> {
            CameraScreen(
                onBack = { navController.popBackStack() },
                onCaptured = {
                    // The captured photo replaces the camera in the back stack.
                    navController.navigate(EditorRoute()) { popUpTo<HomeRoute>() }
                },
            )
        }
    }
}

private const val TRANSITION_MS = 300

package cz.svoby93.asciistudio.ui.studio

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/**
 * Seconds for the moving decorations of the screens: backgrounds, blinking lights and the donut.
 *
 * The display runs at 60 to 120 frames a second, but decorations look just as good at
 * [DECORATION_FPS], and every frame they draw costs battery. All clocks tick at the same moments,
 * so decorations that move together redraw in one frame. Read the value while drawing (in
 * `drawBehind`, `graphicsLayer` or a `Canvas`), so that a tick redraws without composing again.
 *
 * With [animate] off the clock stands still, at [start] or where it stopped.
 */
@Composable
fun rememberDecorationClock(animate: Boolean = LocalAnimationsEnabled.current, start: Float = 0f): FloatState {
    val clock = remember { mutableFloatStateOf(start) }
    if (animate) {
        LaunchedEffect(clock) {
            // Continues where the clock stood, so that decorations do not jump after a pause.
            val origin = withFrameNanos { it } - (clock.floatValue * NANOS_PER_SECOND).toLong()
            while (true) {
                val frame = withFrameNanos { it }
                clock.floatValue = (frame - origin) / NANOS_PER_SECOND
                // Frame times count in System.nanoTime(), so every clock sleeps until the same tick.
                val next = (frame / TICK_NANOS + 1) * TICK_NANOS
                delay(((next - System.nanoTime()) / NANOS_PER_MILLI).coerceAtLeast(1))
            }
        }
    }
    return clock
}

/**
 * Whether decorations may move: not when the system setting "remove animations" is on, and not in
 * battery saver. Both are followed while the app runs.
 */
@Composable
fun rememberDecorationsMove(): Boolean = rememberAnimatorsEnabled() && !rememberBatterySaver()

@Composable
private fun rememberAnimatorsEnabled(): Boolean {
    var enabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        DisposableEffect(Unit) {
            val listener = ValueAnimator.DurationScaleChangeListener { scale -> enabled = scale != 0f }
            ValueAnimator.registerDurationScaleChangeListener(listener)
            enabled = ValueAnimator.areAnimatorsEnabled()
            onDispose { ValueAnimator.unregisterDurationScaleChangeListener(listener) }
        }
    }
    return enabled
}

@Composable
private fun rememberBatterySaver(): Boolean {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var saving by remember { mutableStateOf(power?.isPowerSaveMode == true) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                saving = power?.isPowerSaveMode == true
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // The mode may have changed while nobody listened.
        saving = power?.isPowerSaveMode == true
        onDispose { context.unregisterReceiver(receiver) }
    }
    return saving
}

/** How often moving decorations change; enough for smooth motion of soft, slow shapes. */
private const val DECORATION_FPS = 30
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val NANOS_PER_MILLI = 1_000_000L
private const val TICK_NANOS = 1_000_000_000L / DECORATION_FPS

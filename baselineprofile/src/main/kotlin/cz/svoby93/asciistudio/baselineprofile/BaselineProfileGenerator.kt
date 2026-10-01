package cz.svoby93.asciistudio.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline Profile. The code that runs while the app starts and in its main journey
 * is then compiled ahead of time when the app is installed, instead of being interpreted and
 * compiled bit by bit while the user waits.
 *
 * Run it with `./gradlew :app:generateBaselineProfile` on a device or emulator with Android 13 or
 * newer, or a rooted one with Android 9 or newer. The profile lands in
 * `app/src/release/generated/baselineProfiles/` and has to be committed.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    /** The start up to the home screen also orders the code of the app for a faster start. */
    @Test
    fun startup() = rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        // A few turns of the donut.
        Thread.sleep(SHORT_PAUSE_MILLIS)
    }

    /** The live camera, a photo taken with it and the editor with its presets and main settings. */
    @Test
    fun journey() = rule.collect(packageName = PACKAGE_NAME) {
        pressHome()
        grantCamera()
        startActivityAndWait()

        tap("Live ASCII camera", "Živá ASCII kamera")
        waitFor("Take photo", "Vyfotit")
        // Frames are read, converted and drawn for a while.
        Thread.sleep(LONG_PAUSE_MILLIS)

        tap("Take photo", "Vyfotit")
        waitFor("Editor")
        Thread.sleep(LONG_PAUSE_MILLIS)

        // Every step ends where it started, because the settings stay for the next round.
        // The editor opens on the presets, whose tiles show the photo in every look.
        tap("Newspaper", "Noviny")
        Thread.sleep(SHORT_PAUSE_MILLIS)
        tap("Classic", "Klasika")
        tap("Colors", "Barvy")
        tap("Photo colors", "Barvy fotky")
        Thread.sleep(SHORT_PAUSE_MILLIS)
        tap("Palette", "Podle palety")
        tap("Style", "Styl")
        tap("Braille")
        Thread.sleep(SHORT_PAUSE_MILLIS)
        tap("Standard", "Standardní")
        Thread.sleep(SHORT_PAUSE_MILLIS)
    }
}

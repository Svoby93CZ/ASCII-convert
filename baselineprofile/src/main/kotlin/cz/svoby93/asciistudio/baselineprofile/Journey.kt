package cz.svoby93.asciistudio.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import java.util.regex.Pattern

/** The application ID, fixed by the app's Google Play entry. */
const val PACKAGE_NAME = "com.asciistudio"

internal const val SHORT_PAUSE_MILLIS = 1_500L
internal const val LONG_PAUSE_MILLIS = 3_000L
private const val TIMEOUT_MILLIS = 20_000L
private const val POLL_MILLIS = 200L

internal fun MacrobenchmarkScope.grantCamera() {
    device.executeShellCommand("pm grant $PACKAGE_NAME android.permission.CAMERA")
}

/** Taps the element with one of [labels] as its text or content description, in any case. */
internal fun MacrobenchmarkScope.tap(vararg labels: String) {
    waitFor(*labels).click()
    Thread.sleep(POLL_MILLIS)
}

/** The element with one of [labels] as its text or content description, once it shows. */
internal fun MacrobenchmarkScope.waitFor(vararg labels: String): UiObject2 {
    val pattern = Pattern.compile(labels.joinToString("|") { Pattern.quote(it) }, Pattern.CASE_INSENSITIVE)
    val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
    while (System.currentTimeMillis() < deadline) {
        device.findObject(By.text(pattern))?.let { return it }
        device.findObject(By.desc(pattern))?.let { return it }
        Thread.sleep(POLL_MILLIS)
    }
    error("Nothing labelled ${labels.joinToString(" or ")} showed up")
}

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

ASCII Studio is an offline Android app (Kotlin, Jetpack Compose, Material 3) that turns photos and
the live camera image into ASCII art. The maintainer writes in Czech; answer in Czech.

## Commands

```bash
./gradlew :engine:test                                   # conversion engine, plain JVM
./gradlew :engine:test --tests '*AsciiConverterTest'     # one class
./gradlew :engine:test --tests '*AsciiConverterTest.invert swaps paper and ink'  # one test (backtick names)
./gradlew :app:testDebugUnitTest                         # SettingsRepositoryTest on a real DataStore file
./gradlew :app:lintDebug                                 # lint errors fail CI
./gradlew :app:assembleDebug                             # also :app:assembleRelease, :app:installDebug
```

- **Android CI** (`.github/workflows/android.yml`) runs on every push: `:engine:test`, then
  `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:bundleRelease`.
  It fails if the release APK requests `android.permission.INTERNET`, because the privacy policy
  promises the app has no internet access.
- **Emulator smoke test** (`.github/workflows/emulator.yml`, `.github/scripts/smoke-test.sh`) runs
  only for pushes whose commit message contains `[emulator]`, or manually. It drives the app by
  tapping visible texts and content descriptions (`ui.py find`: exact match first, then
  case-insensitive substring), so renaming UI labels can break it. Update the script together
  with UI changes, and tag such commits `[emulator]`. Every step prints a base64 JPEG between
  `===== BEGIN IMAGE <name> =====` and `===== END IMAGE <name> =====` in the job log.

## Cloud sessions

- The Android Gradle plugin, AndroidX and the SDK come from `dl.google.com` (`maven.google.com`
  redirects there). When the environment's network policy blocks it, no Gradle task works at all,
  not even `:engine:test`, because the root build script resolves the AGP plugin. Ask the
  maintainer to allow `dl.google.com` in the environment's network settings, or verify through CI.
- The environment's setup script is a copy of `.claude/cloud-setup.sh`:
  - It installs the Android SDK into `/opt/android-sdk`.
  - It points Gradle to the SDK with `systemProp.android.home` in `~/.gradle/gradle.properties`.
    AGP ignores `systemProp.ANDROID_HOME`, so no environment variable is set.
  - When `/opt/android-sdk` is missing, run the script by hand: `bash .claude/cloud-setup.sh`.
  - When AGP asks for other SDK packages, update `PACKAGES` in the script and ask the maintainer
    to paste the new version into the environment.
- Maven Central sometimes answers the shared cloud IP with `429 Too Many Requests`. That is not a
  build problem. Wait a few minutes and run the build again; Gradle keeps what it already
  downloaded.
- CI artifacts are served from `*.blob.core.windows.net`, which may be blocked as well. The smoke
  test screenshots can then be decoded from the job log (see above).
- Fallback for UI work without Google Maven: Compose Desktop 1.7.3 from Maven Central can compile
  and render the app's composables to PNG (`ImageComposeScene`). Its runtime needs
  `androidx.collection`; `org.jetbrains.compose.collection-internal:collection-desktop:1.6.0-beta02`
  works as a substitute. Android-only APIs (`R`, `stringResource`, `painterResource`, the font,
  `AsciiArtView`) need small shims. That is why every screen keeps its UI in a stateless
  `*Content` composable (`HomeContent`, `EditorContent`, `GalleryContent`, `CameraContent`),
  apart from its ViewModel, launchers and other Android APIs.

## Architecture

Two Gradle modules. `engine/` is pure Kotlin/JVM with no Android dependencies. `app/` is the
Android app: code namespace `cz.svoby93.asciistudio`, application ID `com.asciistudio`. The
application ID is tied to the Google Play entry and must never change.

### Conversion engine (`engine/`)

- `AsciiConverter.convert(PixelImage, AsciiOptions): AsciiArt` is a pure function. It has two
  modes:
  - Glyph ramps: `Sampler` averages the image into a cell grid, `ToneMapper` applies auto levels,
    sharpening, brightness and contrast to get ink 0..1, and `Quantizer` picks the nearest level or
    dithers (Floyd–Steinberg, Atkinson, Bayer).
  - Braille: 2×4 dots per cell with binary quantisation.
  - Outlines sample at 2× and use Sobel plus a structure tensor (`EdgeDetector`) to draw `| / - \`.
- `CharRamp` stores each glyph with its measured ink coverage, so tone steps are uneven on
  purpose. Built-in ramps are in `CharRamps`. Custom characters are measured at runtime by the
  app's `GlyphMeasurer` in the bundled JetBrains Mono.
- Tones: bright pixels become dense glyphs (light ink on dark paper). Light palettes flip this in
  `AsciiOptionsFactory` (`invert = settings.invert != palette.isLight`).
- The converter's `cellAspect` must match the renderer's font cell. `AppContainer` passes
  `AsciiRenderer.textCellAspect` into `AsciiOptionsFactory`. Braille always uses 0.5.
- `AsciiArt` holds the glyphs plus the average source colour of every cell, which is used for
  photo-colour rendering and for the HTML/ANSI export in `AsciiExport`.

### App (`app/`)

- **Dependencies:** passed by hand through `AppContainer` and `LocalAppContainer`; there is no
  DI framework.
- **Settings:** `SettingsRepository` is the single source of truth for `StudioSettings`. Updates
  apply in memory at once and reach DataStore after a 400 ms debounce. The editor and the live
  camera share the same settings. A new setting needs:
  - a field with a default in `StudioSettings`;
  - a key in `SettingsRepository`'s read and write methods (enums are stored by name and fall
    back to the default);
  - an extra case in `SettingsRepositoryTest`.

  `StudioSettings` is also `@Serializable`: gallery items keep a JSON copy, so a renamed property
  falls back to its default there. `backdrop` (the screen background) is stored under its old
  key `camera_background`. It decorates the app and is not part of the art, so opening a gallery
  item or resetting the settings keeps it (`sameArtAs` ignores it too).
- **Images:** `ImageRepository` keeps the working image, downscaled to 1600 px, in
  `noBackupFilesDir`. `EditorViewModel` restores it after process death through a
  `SavedStateHandle` flag.
- **Gallery:** `GalleryRepository` keeps saved art in `noBackupFilesDir/gallery`, one folder per
  item with the photo, a JPEG preview from `ArtExporter.writePreview` and `item.json` (settings
  and art size). Items are written to a `.tmp` folder and renamed when complete. The editor's
  "Save to gallery" adds an item unless the gallery already holds this photo with the same art
  settings; `EditorRoute(galleryId)` opens an item again, bringing back its photo and settings.
  The privacy policy promises that the gallery stays on the device and is not backed up.
- **Editor:** `EditorViewModel` combines the image and the settings into `AsciiOptions`, then
  converts with `mapLatest` on `Dispatchers.Default`. Palette-only changes do not reconvert.
  `EditorControls` holds the Style, Tone and Colors controls; `SettingsWindow` shows them in the
  editor and in the camera.
- **Rendering:** `AsciiRenderer` draws on an Android `Canvas`, one `drawText` per row. Photo
  colours come from a `BitmapShader` with one pixel per cell and nearest-neighbour filtering.
  Braille is drawn as real dots. `AsciiArtView` adds fit, zoom and pan. `ArtExporter` renders
  PNGs and writes TXT, HTML and ANSI.
- **Look (`ui/studio`):** every screen takes its colours from the art palette.
  - `StudioRoot` (around the navigation graph in `MainActivity`) applies `StudioTheme`, sets the
    system bar icons and draws `StudioBackdrop` once behind all screens, so screens are
    transparent and the background stays put during transitions.
  - `StudioTheme` derives a Material `ColorScheme` and `StudioColors` (ink, paper, desk) from
    `ArtPalette`; `LocalAnimationsEnabled` is false when `ValueAnimator.areAnimatorsEnabled()`
    is, and then backgrounds and blinking stand still.
  - Windows are `TerminalFrame`s (labels set into the border), `SettingsWindow` is the tabbed
    settings window shared by the editor and the camera, and `StudioChrome` has the top bar,
    `RoundButton` and `TerminalDialog`.
- **Live camera (`ui/camera`):**
  - `CameraViewModel` owns the CameraX use cases. `ImageAnalysis` delivers RGBA frames, keeps
    only the latest, and converts each frame at no more than `StudioSettings.MAX_LIVE_COLUMNS`
    columns. `ImageCapture` takes the full photo, which goes through `ImageRepository` into the
    editor.
  - `CameraScreen` handles the permission and camera binding, then renders the stateless
    `CameraContent`.
- **Navigation:** type-safe routes in `AsciiStudioNavHost`: `HomeRoute`,
  `EditorRoute(imageUri, galleryId)`, `GalleryRoute` and `CameraRoute`. Images shared from other
  apps reach the graph through a channel in `MainActivity`.

## Conventions

- **Language:**
  - Code, comments and commit messages are in English. Commit subjects use the imperative mood,
    and the body explains why.
  - `README.md` and `store/` are in Czech. Keep them in sync when features change.
    `store/listing.md` has the Google Play texts and their length limits.
- **Strings:** add every user-facing string to both `values/strings.xml` and
  `values-cs/strings.xml`. A missing translation is a lint error.
- **Privacy policy:** the text exists in `store/privacy-policy.md`, in the `privacy_policy_text`
  string in both languages, and in a public gist. Change all of them together.
- **Formatting:** lines are at most 120 characters and trailing commas are used
  (`.editorconfig`).
- **Build settings:** dependency versions live in `gradle/libs.versions.toml`. The memory limits in
  `gradle.properties` are sized for an 8 GB laptop.
- **Signing:**
  - Release builds are signed through `keystore.properties` locally, or `RELEASE_*` environment
    variables on CI.
  - `app/debug.keystore` is committed on purpose.
  - `versionCode` is the CI run number.

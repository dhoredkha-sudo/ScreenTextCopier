# Screen Text Copier

Native Android app built with Kotlin, Jetpack Compose, Material 3 and Google ML Kit Text Recognition.

## Implemented
- Dark premium hacker-themed control center and floating avatar with expandable Play/Stop pill.
- Foreground notification for the floating overlay service.
- SYSTEM_ALERT_WINDOW permission flow.
- User-approved MediaProjection screen OCR service. Android displays its own capture-consent dialog; OCR runs at the configured interval and stops when the user disables it or stops the service.
- ML Kit on-device text recognition (no server upload).
- Comma-separated Watch Words filter and SHA-256 duplicate suppression.
- One app-private `captured_texts.txt` file with timestamp/source entry format.
- User-initiated clipboard import (Android restricts background clipboard monitoring; the app does not secretly read clipboard contents).
- View and clear archive, OCR interval setting.
- GitHub Actions workflow builds `assembleDebug` and uploads the APK artifact.

## Build from an Android phone using GitHub
1. Create a GitHub repository and upload the contents of this folder (the `app`, `.github`, root Gradle files and README).
2. Open the repository's **Actions** tab.
3. Enable Actions if GitHub asks, then run **Build Screen Text Copier APK** using **Run workflow**, or push a commit to `main`.
4. Open the completed workflow run, scroll to **Artifacts**, and download `Screen-Text-Copier-debug`.
5. Extract the ZIP and install `app-debug.apk` on Android.

## First launch
1. Tap **START** in the app and allow display-over-other-apps permission when Android opens Settings.
2. Allow notifications when requested so the foreground service can show its persistent status.
3. Tap **Screen OCR watch** and approve Android's screen-capture prompt. Android may show a capture indicator while OCR is active.
4. Tap **STOP** / disable OCR to stop capture. The floating service has a Stop action in its notification too.
5. Use **SAVE CLIPBOARD** to save the current clipboard text. Android does not permit unrestricted background clipboard polling, so import is user initiated.

## Data and privacy
All recognized text is processed on-device by ML Kit and appended to the app-private file. No database, account, cloud, or server is used. The app does not request broad external-storage access. Uninstalling the app removes its private archive unless exported by the user.

## Technical
- minSdk 26, targetSdk 34, Java 17, Gradle 8.9, Android Gradle Plugin 8.7.3.
- OCR uses MediaProjection and a foreground service with the `mediaProjection` type. The user must approve each capture session as required by Android.

## Automatic clipboard watcher

The floating foreground service registers `ClipboardManager.OnPrimaryClipChangedListener` and, only while the user has pressed **Play**, reads the primary clip and appends plain text through `CaptureStore` with source `clipboard`. Pressing **Stop** disables saving immediately. The listener is removed when the service is destroyed.

**Android platform limitation:** Android 10 and later restrict clipboard reads by background apps. A foreground service does not guarantee unrestricted clipboard access on every Android version/device. The listener may fire while the app is backgrounded but the OS can deny reading the clip; this app catches that denial and does not bypass the restriction. For dependable copying, use the app while foregrounded or use the authorized screen-capture OCR mode.

## Updated fixes in this revision
- Clipboard saving switch now starts/stops the floating service's clipboard listener instead of being inert.
- Watch words are persisted in app-private SharedPreferences and restored on next launch.
- Duplicate hashes are rebuilt from the existing `captured_texts.txt` on first load, so app restarts do not forget prior entries.
- Clearing the text file also clears the in-memory duplicate set.
- Main Stop and floating Stop both disable clipboard capture and request OCR service shutdown.

Android note: Android 10+ restricts reading clipboard contents while an app is in the background. The listener can receive change notifications, but the OS may return no text until the app is foregrounded or the user explicitly pastes. This is an OS privacy restriction and cannot be bypassed by an ordinary app. MediaProjection OCR also requires a fresh system consent flow initiated by the user.

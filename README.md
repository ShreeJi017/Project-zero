# ZeroGravity

ZeroGravity is a native, offline-first Android assistant built with Kotlin and Jetpack Compose. Deterministic local commands, Android device actions, on-device reminders, and user-defined safe shortcuts work without a network connection or language model. Chat history and long-term assistant memory remain deferred.

## Build and test

- Application ID: `com.zerogravity.assistant`; minimum SDK 26; compile/target SDK 36.
- Install JDK 17 or newer and Android SDK Platform 36, then run:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

The last command requires a running Android emulator or connected test device.

## UI and app shell

- `MainActivity` installs the AndroidX native splash screen, enables edge-to-edge layout, and applies the saved appearance mode. The splash exits with normal app startup; no artificial delay is added.
- Adaptive launcher icon resources use a black field and a vector-drawn white Zero mark. The manifest connects the icon, splash theme, and ZeroGravity application label to the installed app.
- Compose navigation provides Home, Settings, Personalisation, and History. Bottom navigation is always visible; Home's orbital canvas and page headings support horizontal navigation without attaching gestures to text entry or system edges. Navigation state is saved by the Navigation Compose controller.
- The Home orb is drawn and briefly animated using Canvas. Text and manually activated voice input use the same local command processor. A visible state panel reports listening, processing, recognized text, speech, no-speech, and error states.
- Appearance, voice, battery-warning, assistant-name, and response-style preferences are typed and stored atomically by Preferences DataStore. `SYSTEM`, `LIGHT`, and `DARK` appearance modes are supported. Wake-word/background listening and unattended automation are not active options.
- History consumes the `HistoryRepository` interface. No history repository is registered in this batch, so the app states that conversation transcripts are not being saved; no long-term memory or archive is implemented.
- Settings sections distinguish installed OS voices from missing offline voices. Wake-word detection and arbitrary automation are not advertised as active features.

## Architecture and integration

- `domain/model` and `domain/port` define Android-independent settings, capabilities, history entries, command/device-action contracts, reminder scheduling, safe shortcuts, calendar access, weather providers, and local-model integration points.
- `data/preferences` implements the settings repository using Preferences DataStore. Updates are applied as atomic transformations to avoid stale UI snapshots overwriting concurrent preference changes.
- `domain/command` contains the deterministic English/Hindi/Hinglish parser and router. Supported operations include help, time/date, battery, camera/settings/calculator/clock launch, torch control, reminders, user-confirmed dialer/SMS-composer handoffs, and validated one-command shortcuts. Unknown requests do not trigger cloud calls. Shortcuts cannot contain scripts, nested shortcuts, or arbitrary code.
- `platform` owns Android device integration and permission checks. App launches are restricted to an explicit allowlist. Torch requires camera permission; reminders require notification permission on Android 13 and newer. Calls use `ACTION_DIAL` and SMS uses `ACTION_SENDTO`, so the user reviews and confirms in Android's dialer or messaging app; ZeroGravity does not place calls or send SMS directly. Reminders are stored in app-private preferences and scheduled with `AlarmManager`; timing may be deferred by Android power management. A manifest-declared boot receiver restores scheduled reminders without starting a background service.
- `platform/voice` owns the single Android on-device speech recognizer, the system TextToSpeech engine, and the process-lifetime battery warning monitor. Both speech adapters are registered once in `AppContainer`.
- `domain.port.WeatherProvider` and `CalendarProvider` are optional integration points. No weather provider is configured and no calendar provider is registered. Live weather is not available; it requires internet access and a configured provider. Calendar access is not requested unless a future provider is deliberately integrated.
- Spoken notifications and WhatsApp message access are unavailable; no notification-listener service, notification-access permission, WhatsApp integration, or message-reading path is present. Settings identifies these limits. Calls and SMS only hand off to the system confirmation UI.
- `core/di` constructs application dependencies directly. Register the future history repository here and connect speech, local-model, weather, and calendar providers through the existing domain contracts.
- `ui` owns lifecycle-aware settings state, navigation, loading/error feedback, and the presentation layer.

Cloud AI, credentials, a PC bridge, code/APK generation, smart-home, and watch integrations remain out of scope.

## Local language models

No model is currently catalogued, downloadable, or runnable in this build. Model installation remains disabled rather than shipping a guessed model URL, checksum, license statement, or runtime integration. A model/runtime must be verified for Android compatibility, supported ABIs, redistribution terms, on-disk size, and realistic peak memory before it is offered. Flexible conversation explains this limitation and never falls back to a cloud service. Local commands continue to work without a model.

## Voice subsystem

- **Input:** On Android 12/API 31 and newer, the app uses `SpeechRecognizer.createOnDeviceSpeechRecognizer` and never falls back to the general/network recognizer. Older Android versions, devices without an on-device recognition service, and devices without the requested downloaded language model report unavailable/unsupported rather than sending audio online. The app explicitly requests English (`en-IN`) or Hindi (`hi-IN`) and has no Internet permission. Hindi/English accuracy and availability depend on the installed Android speech service and language packs. Hinglish/code-switch accuracy is not guaranteed; any recognized mixed-language text is passed through unchanged to the same local command parser. Android requires `SpeechRecognizer` control methods on the main thread; audio capture and recognition are performed by the platform recognizer service, not by app-side main-thread audio processing.
- **Output:** The app uses the device's Android TextToSpeech service, with no additional speech model or native library bundled in the APK. It filters voices using `Voice.isNetworkConnectionRequired` and refuses to speak until an installed offline Hindi/English voice is selected. Android's `ACTION_INSTALL_TTS_DATA` opens the system-managed voice-data installer. That installer owns voice licensing, transfer, verification, and cancellation; ZeroGravity cannot show its progress or verify its files itself. After returning, use **Check installed voices**. A device may have no offline voice or may have a vendor-specific voice with different quality. No third-party TTS model is downloaded by this app.
- **Conversation:** The microphone is started only after a user taps the mic control and grants microphone permission. Continuous turn-taking (when selected) performs one recognition, command, and optional spoken response at a time; it stops speech/input on user Stop and is canceled when the activity leaves the foreground. TTS requests audio focus and avoids speech during detected calls or accessible Do Not Disturb modes; Android audio policy may still suppress output. The microphone does not stay active in the background.
- **Wake words/background service:** “Hey Zero”, “OK Zero”, and “Shree” are not supported. There is no always-running recognizer, wake-word detector, microphone foreground service, or lock-screen activation. The Settings page states this rather than treating full speech recognition as wake-word detection.
- **Low battery:** Battery events are observed while the app process is alive. By default one spoken warning is attempted at 15% and is re-armed at 20% (or higher for a higher user-selected threshold). The attempt is persisted to prevent battery broadcast repetition. It uses the selected offline TTS voice and honors audio focus/call/DND checks. If the OS kills the process or speech is unavailable/suppressed, ZeroGravity cannot guarantee a warning.

Android API references: [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer), [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech), [Voice](https://developer.android.com/reference/android/speech/tts/Voice), and [foreground-service restrictions](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone).

## Validation environment

The development container used for this build has OpenJDK 25, Gradle 9.7.1, Android SDK Platform 36, Build Tools 36.0.0, Platform-Tools 37.0.1, and Android command-line tools 23.0.0 installed under `$HOME/Android/Sdk`. Set `ANDROID_HOME` and `ANDROID_SDK_ROOT` to that directory when running Gradle in a new shell. The unit-test suite and debug lint task have been run; lint reports non-fatal compatibility and maintenance warnings. The debug and instrumentation-test APKs build, but no emulator or connected Android device is available in this environment, so instrumented tests and device behavior have not been verified. Physical-device validation is still needed for offline language availability, recognizer accuracy, system TTS voice data installation, audio focus/DND, battery warnings, reminders, and device actions.

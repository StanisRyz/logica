# Логика дня

Native Android application shell with bottom navigation, Material 3 theme modes, and persistent user settings. The stack is Kotlin, Jetpack Compose, Navigation 3, DataStore, and Gradle Kotlin DSL.

Requires JDK 17 and Android SDK Platform 36.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat ktlintCheck
.\gradlew.bat ktlintFormat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Modules:

- `app/` — Android/Compose application shell, navigation, theme, and settings.
- `puzzle-core/` — deterministic Balance generation/solving plus pure-Kotlin gameplay, diagnostics, undo/reset, and hints.

Dependency versions are managed in `gradle/libs.versions.toml`.

# Логика дня

Minimal native Android application baseline. The stack is Kotlin, Jetpack Compose, Material 3, Android Gradle Plugin, and Gradle Kotlin DSL.

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

- `app/` — Android/Compose application.
- `puzzle-core/` — platform-independent puzzle domain and algorithms.

Dependency versions are managed in `gradle/libs.versions.toml`.

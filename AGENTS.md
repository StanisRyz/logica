# Agent instructions

- Stack: Kotlin, Jetpack Compose, Material 3, Android Gradle Plugin, Gradle Kotlin DSL.
- Work CLI-first: use `./gradlew.bat` for normal Windows builds and tests; Android Studio is optional.
- The only module is `app`; source is under `app/src/main`, local unit tests under `app/src/test`.
- Keep the baseline small and add dependencies only when a concrete requirement needs them.
- Future puzzle business logic must remain independent of Android and Compose APIs.

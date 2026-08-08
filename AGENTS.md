# Agent instructions

- Stack: Kotlin, Jetpack Compose, Material 3, AGP, and Gradle Kotlin DSL.
- Work VS Code/CLI-first; the Gradle Wrapper is the build source of truth.
- Dependency versions are managed in `gradle/libs.versions.toml`; keep dependencies minimal.
- `:app` contains Android/Compose code and may depend on `:puzzle-core`.
- `:puzzle-core` contains platform-independent puzzle domain and algorithms; it must never depend on Android, Compose, or `:app`.
- Puzzle engines must be deterministic and expose a `generatorVersion`.
- Avoid unrelated refactors, speculative abstractions, excessive tests, and large documentation changes.

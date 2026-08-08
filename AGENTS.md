# Agent instructions

- Stack: Kotlin, Jetpack Compose, Material 3, AGP, and Gradle Kotlin DSL.
- Work VS Code/CLI-first; the Gradle Wrapper is the build source of truth.
- Dependency versions are managed in `gradle/libs.versions.toml`; keep dependencies minimal.
- The only module is `app`; avoid unrelated refactors, speculative abstractions, excessive tests, and large documentation changes.
- Future puzzle business logic must be independent of Android and Compose. Puzzle engines must be deterministic and expose a `generatorVersion`.

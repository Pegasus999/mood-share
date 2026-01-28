# Repository Guidelines

## Project Structure & Module Organization
`mood_share-android` follows a standard Android multi-module layout. `app/` holds the main `src/main/java` and `res` sources plus `AndroidManifest.xml`. Build metadata lives in `build.gradle`/`app/build.gradle`, shared settings in `settings.gradle`, and Gradle wrapper files (`gradlew`, `gradlew.bat`, `gradle/`). Temporary build output lands under `build/`; avoid committing files from there.

## Build, Test, and Development Commands
- `./gradlew assembleDebug` – compiles the debug APK with current sources and dependencies.
- `./gradlew test` – executes unit tests configured in `app/src/test`.
- `./gradlew lint` – runs Android lint rules and flags style or API issues.
- `./gradlew connectedDebugAndroidTest` – runs instrumentation tests on a connected device/emulator.
Run commands from the repo root; use `./gradlew --stacktrace` when debugging failures.

## Coding Style & Naming Conventions
- Java/Kotlin files under `app/src/main/java` should follow Android/Kotlin style guides (4-space indent, camelCase for methods/variables, PascalCase for classes).
- Resource names in `res/` should be lowercase_with_underscores; layout files live in `res/layout`.
- Keep XML attributes alphabetized where practical and group related resources in the same file.
- Formatting is enforced via Android Studio defaults; run `./gradlew lint` before submitting to catch formatter issues.

## Testing Guidelines
- Unit tests belong in `app/src/test/java`; instrumentation tests in `app/src/androidTest/java`.
- Test class names should mirror their target (e.g., `MainActivityTest`), and methods should describe expected behavior (`fun shouldShowWelcomeMessage()`).
- Coverage is validated by `./gradlew test`. Add new tests alongside feature changes, covering happy and edge cases.

## Commit & Pull Request Guidelines
- Follow `git log` history: use imperative, lowercase prefixes (`fix:`, `feat:`, `docs:`) and brief summaries (e.g., `feat: add onboarding flow`).
- Pull requests require a descriptive title/body, linked issue/bug reference when available, and screenshots/logs if UI changes or regressions are introduced.
- Mention testing performed (commands or manual steps) in the PR description and ping reviewers once the build passes.

## Security & Configuration Tips
- Keep credentials out of the repo; use `local.properties` for SDK paths.
- Enable `android.useAndroidX=true` as configured to avoid dependency mismatches; check `gradle.properties`.

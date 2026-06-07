# Contributing to Linlator

## Development Environment

- **Android Studio** — latest stable
- **Android SDK** — API 34 or newer
- **Android NDK** — 27.x or newer
- **JDK** — 17 (Temurin recommended)

## Setup

1. Clone the repository.
2. Open the project in Android Studio.
3. Let Gradle sync and download dependencies.
4. Ensure `local.properties` points to your SDK and NDK:

```
sdk.dir=/path/to/Android/Sdk
ndk.dir=/path/to/Android/Sdk/ndk/27.x.x
```

## Build

```bash
./gradlew assembleDebug
```

Debug APK is placed at `app/build/outputs/apk/debug/`.

## Code Style

- Follow standard Kotlin conventions.
- Do **not** include comments in code.
- Use meaningful names; let the code speak.
- Format with `ktfmt` or the Kotlin formatter bundled with Android Studio.

## Pull Request Process

1. Create a feature branch from `main`.
2. Make your changes.
3. Ensure the project builds and tests pass.
4. Open a pull request against `main`.
5. Maintainers will review and may request changes.
6. Squash-merge when approved.

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Legado (开源阅读) is an open-source Android e-book reader supporting custom book sources, RSS, local TXT/EPUB, read-aloud (TTS), and a built-in web server for remote management. Language is primarily **Kotlin** with a small amount of Java (e.g. `QueryTTF.java`).

## Build Commands

```bash
# Debug build
./gradlew assembleAppDebug

# Release build (what CI uses)
./gradlew assembleAppRelease --build-cache --parallel --daemon --warning-mode all

# Clean
./gradlew clean

# Download Cronet native library
./gradlew app:downloadCronet

# Build the web frontend (Vue.js, separate from Gradle)
cd modules/web && pnpm i && pnpm build
```

The web module output is copied into `app/src/main/assets/web/vue/`.

## Modules

| Module | Purpose |
|---|---|
| `:app` | Main Android application (`io.legado.app`) |
| `:modules:book` | EPUB/UMD parsing (`me.ag2s`) |
| `:modules:rhino` | Rhino JS engine wrapper (`com.script`) |
| `modules/web` | Vue 3 + Element Plus web frontend (not a Gradle module) |

## Architecture

**MVVM** with AndroidViewModel + ViewBinding (no Jetpack Compose). XML layouts throughout.

### Key base classes (`io.legado.app.base`)
- `BaseViewModel` — extends `AndroidViewModel`, provides `execute()`/`executeLazy()` coroutine helpers
- `VMBaseActivity<VB, VM>` — Activity with ViewBinding + ViewModel
- `VMBaseFragment<VB, VM>` — Fragment with ViewBinding + ViewModel
- `BaseService` — service base class

### Package layout (`io.legado.app`)
- `data/` — Room database (v76, 22 entities), DAOs, entities, migrations
- `model/` — Business logic: `analyzeRule/` (Jsoup/XPath/JsonPath/Regex parsers), `localBook/`, `webBook/`, `rss/`
- `ui/` — Activities/Fragments/ViewModels organized by feature
- `help/` — Business helpers: `config/`, `coroutine/`, `http/`, `rhino/`, `source/`, `storage/` (backup/restore)
- `lib/` — Third-party wrappers: `theme/`, `webdav/`, `permission/`, `prefs/`
- `service/` — Background services (AudioPlay, CacheBook, Download, WebService, TTS)
- `web/` — Built-in NanoHTTPD + WebSocket server
- `utils/` — 80+ Kotlin extension function files

### Important patterns
- **LiveEventBus** for cross-component event communication
- **Coroutines** everywhere via a custom `Coroutine` wrapper in `help/coroutine/`
- **ViewBinding delegates** for binding inflation
- **Rule engine** in `model/analyzeRule/` — parses book source rules using Jsoup, XPath, JsonPath, Regex
- **Rhino JS** — book sources can include JavaScript rules evaluated via embedded Rhino (`help/rhino/`)
- Database access is via the global `appDb` instance in `data/AppDatabase.kt`

### Entry points
- `App.kt` — Application class (Cronet, Rhino, notifications, theme init)
- `ui/welcome/WelcomeActivity.kt` — LAUNCHER activity
- `ui/main/MainActivity.kt` — Main screen with bottom nav (bookshelf, explore, RSS, my)
- `api/ReaderProvider.kt` — Content Provider API for external access
- `web/HttpServer.kt` — Built-in web server

## Build Configuration

- **Build files**: Groovy DSL (not Kotlin DSL)
- **Version catalog**: `gradle/libs.versions.toml`
- **Gradle**: 9.3.1, **AGP**: 9.1.0, **Kotlin**: 2.3.0, **KSP**: 2.3.4
- **compileSdk/targetSdk**: 36, **minSdk**: 28
- **JVM toolchain**: Java 21
- **Product flavors**: dimension `mode`, flavor `app` (CI also builds `google` for Play Store)
- **Build types**: `release` (minified + shrunk, suffix `.release`) and `debug` (suffix `.debug`)
- **Versioning**: `3.{yy}.{MMdd}{HH}`, versionCode = `10000 + git commit count`
- **Core library desugaring** enabled (for java.nio on older APIs)

## Pinned Dependencies (do not update without careful review)

- **Jsoup** 1.16.2 — pinned due to breaking change in newer versions (see libs.versions.toml comment)
- **Hutool** 5.8.22 — pinned
- **protobuf-javalite** 4.26.1 — pinned

## Testing

Tests are minimal. Frameworks: JUnit 4, Espresso, AndroidX Test, Room Testing.

```bash
# Unit tests
./gradlew testAppDebugUnitTest

# Instrumented tests (requires device/emulator)
./gradlew connectedAppDebugAndroidTest
```

- Unit tests: `app/src/test/java/io/legado/app/`
- Instrumented tests: `app/src/androidTest/java/io/legado/app/`
- Room schemas exported to `app/schemas/` for migration testing

## Code Style

- No ktlint/detekt configured; Kotlin `official` code style (`gradle.properties`)
- `checkDependencies = true` for lint in each module
- No pre-commit hooks

## CI

GitHub Actions workflows in `.github/workflows/`:
- `test.yml` — builds on push to master and PRs
- `release.yml` — manual signed release build + Google Play upload
- `web.yml` — builds web module on changes to `modules/web/`
- `cronet.yml` — weekly Cronet update check

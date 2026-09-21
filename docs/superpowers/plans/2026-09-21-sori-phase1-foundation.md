# Sori Phase 1 (Foundation) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Sori a consistent Spotify-like identity. The app is always dark with neutral greys, uses a single coral accent and a bold type hierarchy, and defaults to the classic now-playing layout with a gradient background.

**Architecture:** New Sori-only files hold the palette (`ui/theme/SoriColors.kt`), the typography (`ui/theme/SoriTypography.kt`) and the first-run preference seeding (`sori/SoriDefaults.kt`). Upstream files only get small `// Sori:` hooks: `Theme.kt` picks the Sori scheme and typography, and `App.kt` runs the seeding once.

**Tech Stack:** Jetpack Compose Material 3, materialkolor, AndroidX DataStore Preferences, JUnit4 (+ Robolectric where needed).

**Spec:** `docs/superpowers/specs/2026-09-21-sori-spotify-quality-design.md`

## Global Constraints

- Only write a seeded preference when its key is absent. Never overwrite a user choice.
- Seed exactly once per `SORI_DEFAULTS_VERSION` (marker key `sori_defaults_version`).
- The Sori palette applies only when `themeColor == DefaultThemeColor`. A custom color keeps upstream behaviour.
- `DefaultThemeColor = Color(0xFFFF6B6B)`.
- Keep Material default font sizes and line heights. Change only weight and letter spacing.
- Every upstream hook carries a `// Sori:` comment.

---

### Task 1: First-run defaults seeding

**Files:**
- Create: `app/src/main/kotlin/com/metrolist/music/sori/SoriDefaults.kt`
- Create: `app/src/test/kotlin/com/metrolist/music/sori/SoriDefaultsTest.kt`
- Modify: `app/src/main/kotlin/com/metrolist/music/App.kt` (one `applicationScope.launch(Dispatchers.IO)` block)

**Interfaces:**
- Produces: `object SoriDefaults { const val VERSION: Int; fun applyTo(prefs: MutablePreferences): Boolean }`. `applyTo` returns true when it changed anything.

Behaviour:
- If `prefs[sori_defaults_version] >= VERSION`, do nothing and return false.
- Otherwise, for each default:
  - `DarkModeKey = DarkMode.ON.name`
  - `DynamicThemeKey = false`
  - `UseNewPlayerDesignKey = false`
  - `PlayerBackgroundStyleKey = PlayerBackgroundStyle.GRADIENT.name`

  write it only if the key is absent. Then write the marker and return true.

Tests:
- A fresh store gets all four defaults and the marker.
- A user-set value is kept. Example: `DarkModeKey = "OFF"` stays `OFF`.
- A second run is a no-op (returns false).

### Task 2: Sori palette + Theme hook

**Files:**
- Create: `app/src/main/kotlin/com/metrolist/music/ui/theme/SoriColors.kt` → `fun soriDarkColorScheme(): ColorScheme`
- Modify: `ui/theme/Theme.kt`
  - `DefaultThemeColor` value
  - `baseColorScheme` selection, which becomes: default + dark → `soriDarkColorScheme()`; default + light → materialkolor seeded with `DefaultThemeColor`; otherwise unchanged

Checks:
- Compiles.
- The MuMu capture shows `#121212` backgrounds, coral accents and neutral nav pills.

### Task 3: Typography

**Files:**
- Create: `ui/theme/SoriTypography.kt` → `val SoriTypography: Typography`. It is built from `Typography()` with a weight/letterSpacing copy per role.
- Modify: `Theme.kt` → `MaterialTheme(colorScheme = …, typography = SoriTypography, content = …)`

### Task 4: Verify and commit
- `./gradlew :app:testFossDebugUnitTest --tests '*SoriDefaultsTest*' --tests '*LoginPagePolicyTest*' :app:assembleFossDebug`
- Install the debug build on MuMu. Capture Home, Library, Player and Settings → Theme, and compare them with the pre-change captures (`current_ui.png`).
- Commit per task, on branch `feat/spotify-quality`. Do not merge to main.

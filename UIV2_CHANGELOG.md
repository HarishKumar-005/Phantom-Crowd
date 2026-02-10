# UIv2 Changelog — Phantom Crowd Light-First Redesign

**Date:** 2026-02-10
**Commit:** Single atomic commit (see git log)

---

## Summary of Changes

### Design System (`DesignSystem.kt`)
- 24 color tokens (primary `#1767D1`, secondary `#029F87`, severity, heatmap, neutral)
- 6 typography styles (displayLarge 34sp → labelLarge 12sp) using Roboto/system font
- Shape tokens: card 12dp, chip 10dp, pill 24dp
- Spacing scale: 4/8/12/16/24/32/48 dp
- Elevation: smallCard 2dp, card 4dp, dialog 8dp
- Motion: 180ms standard (FastOutSlowIn), 750ms urgentPulse

### Theme Changes
- **Light-only branded palette** — disabled Android dynamic color
- Status bar: light background with dark icons
- Removed dark theme / purple default

### New Components (8 files)
| Component | Purpose |
|-----------|---------|
| `PCard.kt` | Surface card with outline border, 12dp corners |
| `PChip.kt` | Filter chip with 48dp touch target, selected/unselected |
| `SeverityBadge.kt` | Severity pill (Urgent/High/Medium/Low) |
| `PBottomNav.kt` | Bottom nav with center FAB for Post |
| `PTopBar.kt` | Center-aligned top app bar |
| `PDialog.kt` | Accessible AlertDialog |
| `PToast.kt` | Snackbar with live region semantics |
| `HeatmapLegend.kt` | Map legend (Red 5+, Yellow 2–4, Green 1) |

### Screen Restyles (6 screens)

| Screen | Key Changes |
|--------|-------------|
| `NearbyIssuesScreen` | Confirm button (was Upvote), Navigate CTA (secondary), SeverityBadge, tokenized cards+shimmer, new empty state copy |
| `PostCreationARScreen` | Step indicator ("Step 1/5 — Category"), surface top bar |
| `ARViewScreen` | Frosted surface labels (was opaque colored), tokenized status chip |
| `MapDiscoveryTab` | Surface info overlay (was dark), HeatmapLegend component |
| `NavigationTab` | Light background (was black), tokenized distance colors, subtle arrival |
| `ImpactDashboardScreen` | "Estimated Reach" label, tokenized stat colors |

### Microcopy Updates
- Empty feed: "No recent reports nearby. Tap + to report anonymously and help your community."
- Upvote → **Confirm** ("✓ Confirmed" after tap)
- Navigation arrival: "✅ You've arrived!" (was "🎉 YOU ARRIVED! 🎉")
- Step indicator: "Step 1/5 — Category" (was "Report Issue (1/5)")

### Feature Flag
- `BuildConfig.UIV2_ENABLED` = `true` (default enabled)
- To disable: set `buildConfigField "boolean", "UIV2_ENABLED", "false"` in `app/build.gradle` defaultConfig

### Tests Added
| Test File | Coverage |
|-----------|----------|
| `AnchorRepositoryTest.kt` | Cloud-first fallback, local fallback, offline mode (7 tests) |
| `AnchorDataTest.kt` | Data class defaults, copy, unique IDs (4 tests) |

---

## Dependency Versions (Kept Conservative)

| Dependency | Version | Justification |
|------------|---------|---------------|
| **Kotlin** | 1.9.20 | Current project version, stable. Sources: [kotlinlang.org](https://kotlinlang.org/docs/releases.html) |
| **Compose BOM** | 2023.08.00 | Compatible with Kotlin 1.9.20 + compiler extension 1.5.4. Sources: [developer.android.com/jetpack/compose/bom](https://developer.android.com/jetpack/compose/bom/bom-mapping) |
| **kotlinCompilerExtensionVersion** | 1.5.4 | Matches Kotlin 1.9.20. Sources: [developer.android.com](https://developer.android.com/jetpack/androidx/releases/compose-compiler) |
| **ARCore** | 1.41.0 | Working in project, latest stable is 1.52.0 but kept for stability. Sources: [developers.google.com/ar](https://developers.google.com/ar/develop/java/enable-arcore), [GitHub releases](https://github.com/google-ar/arcore-android-sdk/releases) |
| **SceneView** | 2.2.1 | Compatible with ARCore 1.41.0 and SDK 35. Latest is 2.3.3 but would require migration. Sources: [GitHub SceneView](https://github.com/SceneView/sceneview-android/releases), [mvnrepository.com](https://mvnrepository.com/artifact/io.github.sceneview/arsceneview) |
| **CameraX** | 1.3.1 | Stable branch, no breaking changes. Sources: [developer.android.com/jetpack/androidx/releases/camera](https://developer.android.com/jetpack/androidx/releases/camera) |
| **Firebase BOM** | 32.7.0 | Current project version, stable. Sources: [firebase.google.com/docs/android/setup](https://firebase.google.com/docs/android/setup) |
| **MediaPipe Text** | latest.release | Dynamic version for on-device AI. Sources: [developers.google.com/mediapipe](https://developers.google.com/mediapipe/solutions/text/text_classifier/android) |
| **Mockito** | 5.8.0 | Added for unit tests. Sources: [mvnrepository.com](https://mvnrepository.com/artifact/org.mockito/mockito-core) |
| **Turbine** | 1.0.0 | Flow testing. Sources: [github.com/cashapp/turbine](https://github.com/cashapp/turbine) |

---

## Rollback Plan

### 1. Get commit SHA
```bash
git log -1 --pretty=format:"%H %s"
```

### 2. Revert the commit
```bash
git revert <COMMIT_SHA> --no-edit
git push
```

### 3. If deployed to Play Console
- Unpublish the release or roll back to previous version
- If Remote Config was used: set `UIV2_ENABLED=false`

### 4. Quick feature flag disable (no revert needed)
In `app/build.gradle`, change:
```groovy
buildConfigField "boolean", "UIV2_ENABLED", "false"
```
Then rebuild: `./gradlew assembleDebug`

---

## Build & Test Commands

```bash
# Format code
./gradlew ktlintFormat

# Static analysis
./gradlew detekt

# Unit tests
./gradlew test

# Instrumented tests (requires emulator)
./gradlew connectedAndroidTest

# Build debug APK
./gradlew assembleDebug

# Full verification
./gradlew clean assembleDebug test
```

---

## Files Changed

### New Files (12)
- `app/src/main/java/com/phantomcrowd/ui/theme/DesignSystem.kt`
- `app/src/main/java/com/phantomcrowd/ui/components/PCard.kt`
- `app/src/main/java/com/phantomcrowd/ui/components/PChip.kt`
- `app/src/main/java/com/phantomcrowd/ui/components/SeverityBadge.kt`
- `app/src/main/java/com/phantomcrowd/ui/components/PBottomNav.kt`
- `app/src/main/java/com/phantomcrowd/ui/components/PTopBar.kt`
- `app/src/main/java/com/phantomcrowd/ui/components/PDialog.kt`
- `app/src/main/java/com/phantomcrowd/ui/components/PToast.kt`
- `app/src/main/java/com/phantomcrowd/ui/components/HeatmapLegend.kt`
- `app/src/test/java/com/phantomcrowd/AnchorRepositoryTest.kt`
- `app/src/test/java/com/phantomcrowd/AnchorDataTest.kt`
- `UIV2_CHANGELOG.md`

### Modified Files (10)
- `app/build.gradle` — feature flag, test deps, buildConfig true
- `app/src/main/java/com/phantomcrowd/ui/theme/Color.kt` — light-first palette
- `app/src/main/java/com/phantomcrowd/ui/theme/Type.kt` — full typography
- `app/src/main/java/com/phantomcrowd/ui/theme/Theme.kt` — branded light scheme
- `app/src/main/java/com/phantomcrowd/ui/NearbyIssuesScreen.kt` — tokens, confirm, navigate CTA
- `app/src/main/java/com/phantomcrowd/ui/PostCreationARScreen.kt` — step indicator, top bar
- `app/src/main/java/com/phantomcrowd/ui/ARViewScreen.kt` — frosted labels, tokens
- `app/src/main/java/com/phantomcrowd/ui/ImpactDashboardScreen.kt` — estimated reach, tokens
- `app/src/main/java/com/phantomcrowd/ui/tabs/MapDiscoveryTab.kt` — legend, tokens
- `app/src/main/java/com/phantomcrowd/ui/tabs/NavigationTab.kt` — light bg, tokens

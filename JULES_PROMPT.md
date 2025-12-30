# JULES PROMPT - Phantom Crowd Project Context

<instruction>
You are an expert Android/Kotlin software engineer. You are working on the Phantom Crowd hackathon project.

**CRITICAL FIRST STEPS:**
1. Run `git status` and `git diff` to understand the current state
2. Run `./gradlew assembleDebug` to verify build status
3. **SEARCH THE WEB** for the latest SceneView 2.0 documentation, examples, and API usage before attempting any AR fixes
4. Read the PENDING WORK section carefully - this is your primary mission

**WEB SEARCH REQUIREMENT:**
Before fixing any SceneView/AR issues, you MUST search for:
- "SceneView 2.0.3 ArSceneView example Android Kotlin"
- "io.github.sceneview arsceneview composable usage 2024"
- "SceneView AR GitHub examples jetpack compose"
- "ARCore SceneView migration from Sceneform"

The AR implementation is BLOCKED due to API incompatibilities. Only proceed after researching the actual API.
</instruction>

---

# 📋 PENDING WORK - PHASE F & G AR CAMERA

## ❌ BLOCKED: SceneView AR Camera Implementation

### What We Tried
We attempted to implement AR camera functionality using `io.github.sceneview:arsceneview:2.0.3` but encountered persistent compilation errors.

### Failed Approaches

**Approach 1: ARScene Composable**
```kotlin
// FAILED - Parameters don't match
ARScene(
    modifier = Modifier.fillMaxSize(),
    childNodes = childNodes,
    engine = engine,
    modelLoader = modelLoader,
    planeRenderer = true,
    onSessionConfiguration = { session, config -> ... },
    onSessionUpdated = { session, frame -> ... },
    onTap = { hitResult -> ... }
)
```
**Error:** `Cannot find a parameter with this name: onSessionConfiguration`

**Approach 2: ArSceneView in AndroidView**
```kotlin
// FAILED - Class not resolving
AndroidView(
    factory = { ctx ->
        io.github.sceneview.ar.ArSceneView(ctx).apply {
            this.planeRenderer.isEnabled = true
            this.onTapAr = { hitResult, _ -> ... }
        }
    }
)
```
**Errors:**
- `Unresolved reference: ArSceneView`
- `Unresolved reference: planeRenderer`
- `Unresolved reference: onTapAr`

**Approach 3: ArNode Usage**
```kotlin
// FAILED - Constructor requirements unknown
val arNode = ArNode(engine)
arNode.anchor = anchor
```
**Error:** `No value passed for parameter 'engine'`

### Current Workaround
We replaced AR camera with CameraX for the navigation feature:
```kotlin
// WORKING - But not AR
androidx.camera.view.PreviewView  // Simple camera preview
// Arrow is a Compose overlay, not 3D AR
```

### What Needs to Be Fixed

#### Files Requiring AR Camera:
1. `ARNavigationScreen.kt` - Currently uses CameraX, should use SceneView AR
2. `ARViewScreen.kt` - Completely disabled, needs full rewrite
3. `PostCreationARScreen.kt` - Form-based, should show AR camera for wall posting

#### Desired Functionality:
1. AR camera feed with plane detection visualization
2. Tap-to-place anchor on detected surfaces
3. 3D arrow rendered in AR space (not just Compose overlay)
4. Cloud anchor hosting for persistent AR markers

---

## 🔍 RESEARCH REQUIRED

### SceneView 2.0 API Questions
Please search the web and find answers to:

1. **What is the correct import for ArSceneView in SceneView 2.0.3?**
   - Is it `io.github.sceneview.ar.ArSceneView`?
   - Or a different package?

2. **What are the constructor parameters for ArSceneView?**
   - Does it need Context only?
   - Or additional parameters like Engine?

3. **How do you access the AR session in SceneView 2.0?**
   - `arSceneView.session` or `arSceneView.arSession`?
   - How to configure plane detection mode?

4. **How do you create and place AR nodes?**
   - What is the correct ArNode constructor?
   - How to attach to an Anchor?

5. **Does SceneView 2.0 have a Compose wrapper?**
   - Is there an `ARScene` composable?
   - What are its parameters?

6. **What dependencies are required?**
   - Just `arsceneview:2.0.3`?
   - Or additional core dependencies?

---

## 📚 WHAT I KNOW ABOUT SCENEVIEW (From My Training Data)

### SceneView Overview
- **Repository:** https://github.com/SceneView/sceneview-android
- **Purpose:** Modern 3D/AR rendering for Android, successor to deprecated Sceneform
- **Key difference:** Uses Filament renderer instead of OpenGL

### SceneView 1.x vs 2.0 Changes (Approximate)
| Feature | 1.x | 2.0 |
|---------|-----|-----|
| Node class | `ArNode` | `ArNode` (changed params) |
| Scene access | `.scene` | Removed or changed |
| Compose support | Limited | `ARScene` composable |
| Model loading | `ModelNode.loadModel()` | `modelLoader.loadModel()` |

### Known SceneView 2.0 Patterns (May Be Outdated)
```kotlin
// Compose setup (UNVERIFIED)
val engine = rememberEngine()
val modelLoader = rememberModelLoader(engine)
val childNodes = rememberNodes()

ARScene(
    modifier = Modifier.fillMaxSize(),
    engine = engine,
    modelLoader = modelLoader,
    childNodes = childNodes,
    // Session callbacks may have different names
)
```

### ARCore Concepts (Still Valid)
- **Session:** Core ARCore session managing tracking
- **Frame:** Per-frame data (camera, planes, anchors)
- **Plane:** Detected surface (horizontal/vertical)
- **Anchor:** Fixed point in real world
- **HitResult:** Intersection of ray with planes
- **TrackingState:** TRACKING, PAUSED, STOPPED

### Plane Detection Config
```kotlin
val config = session.config.apply {
    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
    depthMode = Config.DepthMode.AUTOMATIC
    lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
}
session.configure(config)
```

---

## 🏗️ FULL PROJECT CONTEXT

### App Name: Phantom Crowd
### Purpose: AR-based civic issue reporting and navigation app
### Hackathon: TechSprint 2.0
### Timeline: 4 days remaining until submission
### Build Status: ✅ PASSING (as of Dec 31, 2025)

---

## TECHNOLOGY STACK

### Core
- **Language:** Kotlin 1.9.0
- **UI Framework:** Jetpack Compose (BOM 2023.08.00)
- **Min SDK:** 24
- **Target SDK:** 34
- **Compile SDK:** 34
- **Build System:** Gradle 8.11.1 + AGP 8.7.3

### Dependencies (build.gradle)
```gradle
// ARCore & SceneView
implementation 'com.google.ar:core:1.41.0'
implementation 'io.github.sceneview:arsceneview:2.0.3'  // BROKEN IMPORTS

// CameraX (working fallback)
def camerax_version = "1.3.1"
implementation "androidx.camera:camera-core:${camerax_version}"
implementation "androidx.camera:camera-camera2:${camerax_version}"
implementation "androidx.camera:camera-lifecycle:${camerax_version}"
implementation "androidx.camera:camera-view:${camerax_version}"

// Firebase
implementation platform('com.google.firebase:firebase-bom:32.7.0')
implementation 'com.google.firebase:firebase-firestore-ktx'
implementation 'com.google.firebase:firebase-storage-ktx'
implementation 'com.google.firebase:firebase-crashlytics'

// Maps
implementation 'org.osmdroid:osmdroid-android:6.1.20'

// Location
implementation 'com.google.android.gms:play-services-location:21.3.0'

// Compose
implementation platform('androidx.compose:compose-bom:2023.08.00')
implementation 'androidx.compose.ui:ui'
implementation 'androidx.compose.material3:material3'

// Coroutines
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3'
```

---

## PROJECT STRUCTURE

```
app/src/main/java/com/phantomcrowd/
├── ar/                          # AR-related components
│   ├── ARCoreManager.kt         # ARCore session management
│   ├── ARModelRenderer.kt       # DISABLED - Legacy Sceneform code
│   ├── ARWallRenderer.kt        # DISABLED - Legacy Sceneform code
│   ├── CloudAnchorSyncManager.kt # Cloud anchor sync with Firebase
│   ├── GeospatialAnchorManager.kt # Geospatial anchors
│   └── VoiceGuidanceManager.kt  # TTS for navigation (WORKING)
│
├── data/
│   ├── AnchorData.kt            # Issue data class
│   ├── GeofenceManager.kt       # Geofence registration
│   └── LocalAnchorStorage.kt    # Offline cache
│
├── receiver/
│   └── GeofenceReceiver.kt      # Geofence notifications
│
├── ui/
│   ├── MainActivity.kt          # Main activity + tab navigation
│   ├── MainViewModel.kt         # Shared ViewModel
│   ├── ARViewScreen.kt          # DISABLED - Needs SceneView rewrite
│   ├── ARNavigationScreen.kt    # CameraX camera + arrow overlay (WORKING)
│   ├── NearbyIssuesScreen.kt    # List of nearby issues
│   ├── PostCreationARScreen.kt  # Form-based posting (AR deferred)
│   └── tabs/
│       ├── MapDiscoveryTab.kt   # OSM map with markers
│       └── NavigationTab.kt     # 2D compass + AR button
│
└── utils/
    ├── BearingCalculator.kt     # GPS bearing math
    ├── Constants.kt             # App constants
    ├── Logger.kt                # Categorized logging
    ├── NetworkHelper.kt         # Online/offline detection
    └── OfflineMapCache.kt       # OSM tile caching
```

---

## IMPLEMENTED PHASES

### Phase A: Firebase Cloud Persistence ✅
- Firestore `issuesGlobal` collection
- Real-time sync
- Geohash-based location queries

### Phase B: Heatmap Enhancement ✅
- OSM map integration
- Color-coded markers (Red: 5+, Yellow: 2-4, Green: 1)
- Distance labels

### Phase C: Geofencing Service ✅
- GeofenceManager for 100m radius alerts
- Push notifications when near issues

### Phase D: Cloud AR Anchoring ✅
- CloudAnchorSyncManager (code exists, needs AR view)
- GeospatialAnchorManager for GPS-based AR

### Phase E: Offline Mode ✅
- LocalAnchorStorage for offline cache
- NetworkHelper for connectivity detection
- OfflineMapCache for map tiles

### Phase F: Wall Overlay Posting ⚠️ PARTIAL
- ✅ PostCreationARScreen (form-based UI)
- ✅ Firebase Storage dependency
- ❌ AR camera view (blocked by SceneView issues)
- ❌ Wall detection and tap-to-place
- ❌ Photo capture

### Phase G: AR Navigation ✅ (with workaround)
- ✅ VoiceGuidanceManager (TTS)
- ✅ ARNavigationScreen (CameraX + Compose arrow)
- ✅ Compass sensor integration
- ✅ Smooth arrow rotation
- ⚠️ Not true 3D AR (just camera overlay)

---

## DATABASE STRUCTURE

### Firestore: `issuesGlobal`
```javascript
{
  id: "uuid-string",
  latitude: 12.9716,
  longitude: 77.5946,
  altitude: 0.0,
  geohash: "tdr1w0",
  messageText: "Pothole on main road",
  category: "infrastructure",  // safety, infrastructure, environment, facility
  timestamp: 1704067200000,
  cloudAnchorId: "",           // For persistent AR (Phase D)
  wallAnchorId: "wall-uuid",   // For wall posting (Phase F)
  photoUrl: "",                // For photo attachments
  upvotes: 0
}
```

---

## MANIFEST PERMISSIONS
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />

<uses-feature android:name="android.hardware.camera.ar" android:required="false" />
<uses-feature android:name="android.hardware.camera" android:required="false" />

<meta-data android:name="com.google.ar.core" android:value="optional" />
```

---

## WHAT JULES WILL DO WITH THIS PROMPT

### Expected Behavior
1. **Read context:** Jules will understand the full project structure
2. **Identify blocker:** The SceneView 2.0 API incompatibility
3. **Web search:** Look up current SceneView documentation
4. **Fix imports:** Determine correct class names and packages
5. **Implement AR:** Rewrite ARViewScreen.kt and update ARNavigationScreen.kt
6. **Test:** Run build to verify fixes

### Recommended Workflow for Jules
1. `git status` → see current branch
2. `./gradlew assembleDebug` → confirm build passes
3. Search web for SceneView 2.0.3 API
4. View `ARNavigationScreen.kt` to understand current workaround
5. Create new AR implementation based on research
6. Test incrementally with `./gradlew compileDebugKotlin`

### Success Criteria
- ✅ Build passes
- ✅ `ArSceneView` imports resolve correctly
- ✅ AR camera shows plane detection
- ✅ Tap-to-place creates anchors
- ✅ 3D arrow renders in AR space

---

## TESTING CHECKLIST

### Build Tests
```bash
./gradlew clean
./gradlew assembleDebug
```

### Device Tests
1. [ ] App launches
2. [ ] Location permission → map shows position
3. [ ] Issues appear on map
4. [ ] Tap issue → Navigate tab
5. [ ] "Open AR Navigation" → Camera with arrow
6. [ ] Arrow rotates with phone
7. [ ] Voice guidance works
8. [ ] Arrival detection (≤20m)

### AR-Specific Tests (After Fix)
1. [ ] AR camera opens without crash
2. [ ] Plane detection visualized
3. [ ] Tap places anchor on plane
4. [ ] 3D content renders correctly
5. [ ] Performance is smooth (>30 FPS)

---

## DEMO SCRIPT

1. "Phantom Crowd helps citizens report and navigate to civic issues"
2. Open Map → "Issues in this area with heatmap"
3. Tap marker → "Navigate to this safety issue"
4. Open AR Navigation → "Camera with floating arrow"
5. Rotate phone → "Real-time direction guidance"
6. [Voice speaks] → "Voice navigation included"
7. "This is AR civic engagement"

**Judge reaction goal: "Whoa, this is impressive!"** 🎉

---

<mission_brief>
## YOUR MISSION

**PRIMARY OBJECTIVE:** Fix the SceneView 2.0 AR implementation

**STEPS:**
1. Search the web for "SceneView 2.0.3 ArSceneView Android Kotlin example"
2. Find the correct API usage for:
   - ArSceneView initialization
   - Plane detection configuration
   - ArNode creation and placement
   - Tap-to-place functionality
3. Update `ARNavigationScreen.kt` to use real AR (not just CameraX)
4. Rewrite `ARViewScreen.kt` with working SceneView code
5. Optionally enhance `PostCreationARScreen.kt` with AR camera

**DELIVERABLES:**
- Working AR camera with plane detection
- 3D arrow in AR space (not Compose overlay)
- Build passes without SceneView import errors

**CONSTRAINTS:**
- Must use SceneView 2.0.3 (already in dependencies)
- Must work with Jetpack Compose
- Must handle permission checks
- Must be stable (no crashes)

**IF SCENEVIEW 2.0 CANNOT BE FIXED:**
- Document why it's not working
- Propose alternative: SceneView 1.x or pure ARCore
- Current CameraX workaround is acceptable for demo
</mission_brief>
# Architecture Overview

Phantom Crowd follows a **mobile-first decentralized architecture.**

---

## Components

| Component | Technology | Purpose |
|-----------|------------|---------|
| Android Client | Kotlin + Jetpack Compose | UI and state management |
| AR Spatial Anchoring | ARCore | Surface detection and anchor placement |
| AR Rendering | SceneView + Filament | 3D rendering of AR labels |
| Camera | CameraX | Camera feed and capture |
| Backend | Firebase Firestore | Cloud issue storage and sync |
| Spatial Indexing | Geohash | Efficient location-based queries |
| AI Moderation | MediaPipe (on-device) | Text classification before upload |
| Local Cache | SharedPreferences / Room | Offline-first fallback |

---

## Data Flow

```
User creates report
       ↓
MediaPipe on-device moderation (safety check)
       ↓
AR anchor created via ARCore
       ↓
Issue stored in Firestore (with geohash index)
       ↓
Nearby users retrieve reports via 9-cell geohash query
       ↓
Reports rendered as AR floating labels via SceneView
       ↓
Community validates reports (upvotes / confirmations)
       ↓
Spatial heatmap generated from clusters
```

---

## Spatial Query Strategy

Phantom Crowd uses a **geohash grid** to efficiently query only nearby issues:

- User location encoded to geohash prefix
- 9 surrounding cells queried (center + 8 neighbors)
- Default radius: **1 km**
- Firestore compound queries on `geohash` field

---

## Privacy Architecture

- No user accounts or identifiers stored
- Reports contain: latitude, longitude, category, severity, message text, timestamp
- No PII (email, phone, device ID) collected at any point
- All content moderation performed **on-device** before upload

---

## MVVM Structure

```
MainActivity
    └── MainScreen (Compose)
          └── MainViewModel
                ├── AnchorRepository
                │     ├── FirebaseAnchorManager (cloud)
                │     └── LocalStorageManager (offline)
                └── GPSUtils (location)
```

# Phantom Crowd

Phantom Crowd is an AR-based civic issue reporting and navigation application built for Android using Kotlin and Jetpack Compose. It leverages Augmented Reality (AR) to visualize and report civic issues (like potholes, broken streetlights, etc.) in the real world using geospatial anchors.

## Features

- **AR Issue Reporting**: Place virtual 3D markers in the real world to report issues.
- **Geospatial Anchors**: Issues are anchored to specific GPS coordinates using ARCore's Geospatial API.
- **Interactive Map**: View reported issues on an interactive map powered by OpenStreetMap.
- **Nearby Issues**: Discover issues reported by others in your vicinity.
- **Real-time Backend**: Powered by Firebase Firestore for real-time data synchronization.
- **Media Uploads**: Attach photos to your reports (stored in Firebase Storage).
- **Navigation**: AR-assisted navigation to issue locations.

## Screenshots

| Home Screen | AR View | Map View | Post Issue |
|:---:|:---:|:---:|:---:|
| ![Home Screen](docs/screenshots/home.png) | ![AR View](docs/screenshots/ar_view.png) | ![Map View](docs/screenshots/map_view.png) | ![Post Issue](docs/screenshots/post_issue.png) |

*(Note: Screenshots to be added)*

## Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetbrains/compose) (Material3)
- **AR Engine**: [SceneView](https://github.com/SceneView/sceneview-android) (ARCore Geospatial API)
- **Maps**: [osmdroid](https://github.com/osmdroid/osmdroid) (OpenStreetMap)
- **Backend**: [Firebase](https://firebase.google.com/) (Firestore, Storage, Crashlytics)
- **Camera**: [CameraX](https://developer.android.com/training/camerax)
- **Architecture**: MVVM (Model-View-ViewModel)

## Setup Instructions

### Prerequisites
- Android Studio Iguana or newer.
- An Android device with ARCore support (Google Play Services for AR).
- A Google Cloud Project with **ARCore API** enabled.
- A Firebase Project.

### 1. Clone the Repository
```bash
git clone https://github.com/your-username/phantom-crowd.git
cd phantom-crowd
```

### 2. Configure Firebase
1.  Create a project in the [Firebase Console](https://console.firebase.google.com/).
2.  Add an Android app to your Firebase project.
3.  Download the `google-services.json` file.
4.  Place `google-services.json` in the `app/` directory of the project.

### 3. Configure API Keys
1.  Get an API Key from Google Cloud Console with **ARCore API** enabled.
2.  Create or open `local.properties` in the project root.
3.  Add your API key:
    ```properties
    AR_CORE_API_KEY=your_api_key_here
    ```

### 4. Build and Run
1.  Open the project in Android Studio.
2.  Sync Gradle.
3.  Connect your physical Android device.
4.  Run the app (`app` configuration).

**Note:** The emulator must be configured with specific coordinates and camera support to test AR features, but a physical device is highly recommended.

## Team

**Phantom Crowd Team**

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

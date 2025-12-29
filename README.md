# Phantom Crowd - AR Location-Based Messaging (Phase 1 MVP)

This is the source code for Phase 1 of Phantom Crowd. It implements the Core AR Geospatial functionality using ARCore and Sceneform, with local JSON persistence.

## Project Overview
- **Language**: Kotlin
- **UI**: Jetpack Compose
- **AR**: ARCore Geospatial API + Sceneform (Maintained)
- **Data**: Local JSON Storage (`anchors.json`)
- **Location**: Google Play Services Location

## Setup Instructions

### 1. Open in Android Studio
1.  Open Android Studio.
2.  Select "Open" and navigate to `Phantom Crowd` folder.
3.  Let Gradle sync.

### 2. Configure API Key (SECURE WAY)
Phantom Crowd uses ARCore's Geospatial API. To keep your key safe and not expose it on GitHub:

1.  Go to [Google Cloud Console](https://console.cloud.google.com/).
2.  Enable **ARCore API**.
3.  Create an API Key.
4.  Open the file `local.properties` in the root of your project (this file is ignored by Git).
5.  Add the following line:
    ```properties
    AR_CORE_API_KEY=your_actual_api_key_here
    ```
    *(Replace `your_actual_api_key_here` with the key starting with AIza...)*

The app will now automatically inject this key into the Manifest during the build process. Do **NOT** verify `local.properties` into version control.

### 3. Emulator Configuration (For MVP Testing)
To test AR Geospatial on the emulator:
1.  Use an AVD with **API Level 33 or 34** (Google Play supported).
2.  Launch the Emulator.
3.  Click the "..." (Extended Controls).
4.  Go to **Location**.
5.  Set coordinates to **Latitude: 12.9716, Longitude: 79.1578** (or your target location).
6.  Go to **Camera** (in Extended Controls) and ensure a virtual scene is selectable (standard feature for newer emulators).

### 4. Running the App
1.  Run the app on the Emulator.
2.  Allow **Camera** and **Location** permissions.
3.  **Post Issue**: Go to "Post Issue" tab, type a message, and post.
4.  **AR View**: Go to "AR View" tab. If the Emulator camera is pointing correctly and GPS is set, you should see the label floating.
5.  **Nearby**: Check the list tab to see your message.

## Troubleshooting
- **AR Session Fail**: If AR session fails, ensure the Emulator supports AR (Google Play Services for AR must be installed on the image). `x86_64` images sometimes lack full ARCore support; consider using a physical device for best AR results.
- **GPS Not Updating**: Use the "Send" button in Extended Controls > Location to force a GPS update.

## File Structure
- `ui/`: Compose screens (MainActivity, PostIssue, Nearby, ARView).
- `ar/`: ARCore and Sceneform managers.
- `data/`: Data models and JSON storage.
- `utils/`: Helpers.

## Next Steps (Phase 2)
- Firebase Integration (Real-time DB).
- ML Filtering.
- User Authentication.

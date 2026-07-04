# 👻 Phantom Crowd — Anonymous AR-Based Community Issue Reporting

> **Phantom Crowd is an anonymous, location-anchored reporting platform that uses AR to make hidden community problems visible.**

[![GitHub](https://img.shields.io/badge/GitHub-PhantomCrowd-blue?style=flat-square&logo=github)](https://github.com/your-username/phantom-crowd)
[![Android](https://img.shields.io/badge/Android-Kotlin-green?style=flat-square&logo=android)](https://developer.android.com/kotlin)
[![ARCore](https://img.shields.io/badge/ARCore-1.41.0-red?style=flat-square&logo=google)](https://developers.google.com/ar)
[![Hackathon](https://img.shields.io/badge/Hackathon-2026-yellow?style=flat-square)](#)

---

## ❓ The Problem

In an era of increasing surveillance and shrinking civic space, individuals often fear expressing dissent or reporting safety issues due to repercussions. Traditional social media detach messages from their physical context, diluting their impact on the real world. Communities need a way to reclaim physical spaces for expression without compromising personal safety.

## 💡 The Solution: Phantom Crowd

**Phantom Crowd** bridges the gap between digital anonymity and physical presence. It is an augmented reality application that allows users to leave persistent, anonymous "ghost" messages anchored to specific real-world locations.

*   **Reclaim Public Space:** Turn any wall, street, or landmark into a digital bulletin board.
*   **Safety First:** Report hazards or harassment zones without revealing identity.
*   **Collective Memory:** Ensure that important messages persist in the location where they matter most.

---

## 🚀 Quick Start

### Prerequisites
- **Android Studio** 2024.1.2 or higher
- **Android SDK 35+**
- **Kotlin 1.9.x**
- **Google Play Services (ARCore)**

### Clone & Build
```bash
# Clone the repository
git clone https://github.com/HarishKumar-005/Phantom-Crowd.git
cd phantom-crowd

# Build and run
./gradlew assembleDebug
# Or open in Android Studio and press Run (Shift + F10)
```

### First Time Setup
1. Grant camera and location permissions.
2. Allow GPS to establish your position.
3. Explore nearby issues (50m radius).
4. Tap the AR button to view spatial anchors.
5. Create a new post on any detected surface.

---

## 🎮 Features

### ✅ **Core Features**
- **📍 Location-Based Messaging** - Posts anchored to GPS coordinates.
- **👁️ AR Surface Anchors** - Persistent "ghost" messages on real walls/floors.
- **🗺️ Nearby Issues Map** - View issues within a 50m radius in real-time.
- **🔍 AR Navigation** - Navigate to other users' messages with an AR compass.
- **⬆️ Upvote System** - Community engagement without user identification.
- **🏘️ 6-Tab Navigation** - Nearby, Post Creation, Map, Navigation, AR View, Profile.

### 🔐 **Privacy & Security**
- **100% Anonymous** - No user ID stored in posts.
- **Geohashing Ready** - Location privacy options available.
- **No Personal Data** - Only location & message content.
- **Local Filtering** - On-device content filtering available.

### 🎨 **User Experience**
- **Material Design 3** - Modern, intuitive UI.
- **Real-time Updates** - Firebase Realtime Database.
- **Smooth Animations** - 60 FPS target achieved.
- **Offline Support** - Local caching with cloud sync.

---

## 🏗️ Architecture

### **MVC + MVVM Hybrid**
```
┌─────────────────────────────────────────────────┐
│              UI Layer (Jetpack Compose)          │
│  PostCreationARScreen, NearbyIssuesScreen, etc  │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│          ViewModel Layer (MainViewModel)         │
│  State Management, Business Logic, AR Logic      │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│       Data Layer (Firebase + Local Storage)     │
│   Firestore, Realtime DB, SharedPreferences     │
└─────────────────────────────────────────────────┘
```

### **Key Components**

| Component | File | Purpose |
|-----------|------|---------|
| **MainActivity** | `MainActivity.kt` | Entry point, navigation hub. |
| **MainViewModel** | `MainViewModel.kt` | Central state management. |
| **ARViewScreen** | `ARViewScreen.kt` | ARCore visualization & plane detection. |
| **SurfaceAnchorScreen** | `SurfaceAnchorScreen.kt` | Anchor placement workflow. |
| **PostCreationARScreen** | `PostCreationARScreen.kt` | Post creation in AR. |
| **NearbyIssuesScreen** | `NearbyIssuesScreen.kt` | Nearby posts list (50m radius). |
| **ARNavigationScreen** | `ARNavigationScreen.kt` | Navigate to other posts. |

---

## 🛠️ Tech Stack

### **Frontend**
- **Jetpack Compose** 2023.08.00 - Declarative UI framework.
- **Material Design 3** - Modern Material UI components.
- **Compose BOM** - Unified Compose library versioning.

### **AR & Camera**
- **ARCore** 1.41.0 - Augmented Reality framework.
- **CameraX** 1.3.1 - Modern camera handling.
- **SceneView** 2.2.1 - AR rendering library.

### **Backend & Data**
- **Firebase Realtime Database** - Real-time updates.
- **Firestore** - Cloud document storage.
- **Firebase Authentication** - User management (optional).

### **Architecture & State**
- **Kotlin Coroutines** - Async programming.
- **StateFlow** - Reactive state management.
- **ViewModel** - Lifecycle-aware state holder.

---

## 🎯 Use Cases

### **1. Campus Safety Network**
- Students post about dark areas, unsafe routes.
- Location-anchored warnings at specific buildings.
- Community-driven campus safety improvement.

### **2. Women's Safety Movement**
- Anonymous reporting of harassment zones.
- Spatial anchors on streets where incidents occurred.
- Collective awareness without individual exposure.

### **3. Civil Protest & Activism**
- Anonymous messaging for political movements.
- Location-based awareness campaigns.
- Decentralized, persistent messaging.

### **4. Community Feedback**
- Report local infrastructure issues.
- Vote on community concerns.
- Visual feedback on area-specific problems.

### **5. Event Management**
- Post experiences at locations.
- Create location-based event feedback.
- Crowdsourced event ratings.

---

## 🚀 Demo Walkthrough (for Judges)

### **Step 1: Launch App**
```
1. Open Phantom Crowd app.
2. Grant Camera & Location permissions.
3. App initializes ARCore session.
```

### **Step 2: Explore Nearby Issues**
```
1. Tap "Nearby" tab (first tab).
2. View all issues within 50 meters.
3. See issue category, distance, upvote count.
4. Tap card to see details.
```

### **Step 3: Create a Post in AR**
```
1. Tap "Post Issue" tab.
2. Select issue category.
3. Tap "Create in AR".
4. Allow camera access.
5. Point camera at surface (wall/floor).
6. See yellow grid (plane detection).
7. Tap surface to place anchor.
8. Write message.
9. Submit to Firebase.
```

### **Step 4: View in AR**
```
1. Tap "AR View" tab.
2. Camera activates.
3. Point at surfaces with anchors.
4. See "ghost" messages overlaid on real world.
5. Try walking around to see spatial persistence.
```

### **Step 5: Navigate to Posts**
```
1. Tap "Navigate" tab.
2. Select a nearby issue.
3. AR compass points to location.
4. Follow arrows to walk to post location.
```


## 🔧 Troubleshooting

### **"Camera in use" Error**
- Close other camera apps.
- Restart the Phantom Crowd app.
- Check camera permissions in Settings.

### **No Plane Detection**
- Ensure good lighting.
- Point at textured surface (avoid white walls).
- Keep device steady for 2 seconds.
- Try different angle.

### **GPS Not Working**
- Enable Location Services.
- Grant location permissions.
- Wait 30 seconds for GPS lock.
- Move to open area (away from buildings).

### **Firebase Not Syncing**
- Check internet connection.
- Verify Firebase project is active.
- Check Firestore security rules.
- Clear app cache if needed.

---

## 📁 Project Structure

```
phantom-crowd/
├── app/src/main/
│   ├── kotlin/com/example/phantomcrowd/
│   │   ├── MainActivity.kt                 # Entry point
│   │   ├── MainViewModel.kt                # State management
│   │   ├── ui/
│   │   │   ├── ARViewScreen.kt            # AR visualization
│   │   │   ├── SurfaceAnchorScreen.kt     # Anchor placement
│   │   │   ├── PostCreationARScreen.kt    # Post creation
│   │   │   ├── NearbyIssuesScreen.kt      # List nearby
│   │   │   ├── ARNavigationScreen.kt      # Navigation
│   │   │   └── PostIssueScreen.kt         # Post form
│   │   ├── data/
│   │   │   ├── FirebaseManager.kt         # Firebase ops
│   │   │   ├── LocationManager.kt         # GPS handling
│   │   └── utils/
│   │       └── GeoHashUtils.kt            # Location hashing
│   ├── res/
│   │   ├── drawable/                      # Icons & images
│   │   ├── values/                        # Strings & colors
│   │   └── mipmap/                        # App icons
│   └── AndroidManifest.xml
├── build.gradle                           # Dependencies
├── gradle.properties
└── README.md                              # This file
```

---

## 🏆 Hackathon Details :

**Team name:** Tech Pros

**Team leader name:** Akshaya S

**Team Members :**
1. Harish Kumar S P
2. Kaviya K
3.Layasree S

---

## Quick Links

- 🔗 [GitHub Repository](https://github.com/HarishKumar-005/Phantom-Crowd)
- 📱 [Download APK](https://github.com/HarishKumar-005/Phantom-Crowd/releases/download/v1.0/Phantom-Crowd.apk)
- 🎥 [Demo Video](https://drive.google.com/file/d/12xKXQiIaCgmRVPmgjaMb8uyvOgjxfudr/view?usp=sharing)

---

Made with ❤️ for social justice. 👻

# Setup Guide

## Requirements

| Requirement | Version |
|------------|---------|
| Android Studio | Hedgehog 2023.1.1+ |
| Android SDK | API 26+ (Android 8.0) |
| Target SDK | API 35 (Android 15) |
| ARCore supported device | Required for AR features |
| Firebase account | Required for cloud sync |

---

## Step 1 — Clone the Repository

```bash
git clone https://github.com/HarishKumar-005/phantom-crowd
cd phantom-crowd
```

---

## Step 2 — Open in Android Studio

```
File → Open → select the phantom-crowd folder
```

Wait for Gradle sync to complete.

---

## Step 3 — Configure Firebase

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. Add an Android app with package name `com.phantomcrowd`
3. Download `google-services.json`
4. Place it in the `app/` directory
5. Enable **Cloud Firestore** in your Firebase Console
6. Set Firestore rules to allow reads/writes (for development):

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if true;
    }
  }
}
```

> ⚠️ Tighten rules before production deployment.

---

## Step 4 — Configure ARCore API Key

Add this to your `local.properties` file:

```
AR_CORE_API_KEY=your_arcore_api_key_here
```

Get your key from [Google Cloud Console](https://console.cloud.google.com) → ARCore API.

---

## Step 5 — Build and Run

```bash
./gradlew assembleDebug
```

Or run directly from Android Studio on a **physical ARCore-supported device**.

> ℹ️ AR features require a physical device. They do not work on emulators.

---

## Verified Devices

| Device | AR Support |
|--------|-----------|
| Samsung Galaxy A14 | ✅ |
| Samsung Galaxy S Series | ✅ |
| Google Pixel Series | ✅ |
| Most mid-range Android (2020+) | ✅ |

---

## Troubleshooting

**Build fails with 16KB alignment warning**
→ Already handled via `jniLibs { useLegacyPackaging = true }` in `build.gradle`.

**Firebase not connecting**
→ Verify `google-services.json` is in `app/` directory and package name matches.

**AR session doesn't start**
→ Ensure device supports ARCore. Install ARCore from the Play Store if prompted.

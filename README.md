
# Phantom Crowd

### Open infrastructure for AR-based civic reporting systems

![License](https://img.shields.io/badge/license-MIT-green)
![Platform](https://img.shields.io/badge/platform-Android-blue)
![ARCore](https://img.shields.io/badge/ARCore-supported-orange)
![Architecture](https://img.shields.io/badge/architecture-MVVM-purple)
![Status](https://img.shields.io/badge/status-active-success)

Phantom Crowd is an **open-source spatial civic infrastructure platform** that enables communities to **report real-world issues anonymously using Augmented Reality (AR).**

Instead of submitting reports through centralized dashboards, Phantom Crowd allows users to **anchor civic reports directly to physical locations** using ARCore.

These reports form a **shared spatial awareness layer** where communities can visualize safety issues, infrastructure problems, and environmental hazards **directly in the real world**.

The project is designed around three principles:

• **Privacy-first civic reporting**
• **Spatial computing for public transparency**
• **Community validation of real-world issues**

Phantom Crowd aims to become a **global open infrastructure layer for spatial civic reporting systems.**

---

# Problem

Cities, campuses, and communities often struggle with **transparent reporting of local issues**, including:

• unsafe streets
• harassment zones
• damaged infrastructure
• environmental hazards
• poorly lit areas

Traditional reporting platforms have several problems:

• require identity verification
• reports exist only in centralized dashboards
• lack real-world spatial context
• low community engagement

As a result, many real-world issues remain **unreported or invisible**.

---

# Solution

Phantom Crowd introduces a **spatial civic reporting layer**.

Using **Augmented Reality**, issues are anchored directly to their **physical location**.

When users view the environment through their phone camera, they can see **crowd-sourced civic reports floating in the real world.**

This creates a **collective awareness layer for public spaces.**

---

# Core Features

### Anonymous Reporting

No accounts or identity required.

Users can report issues anonymously to encourage participation.

---

### AR Location Anchoring

Reports are pinned to real-world surfaces using **ARCore anchors**.

This ensures reports appear exactly where the issue exists.

---

### Community Validation

Users can validate reports through **upvotes and confirmations**.

This helps filter misinformation.

---

### Spatial Heatmaps

Clusters of reports generate **risk heatmaps** that highlight problem areas.

---

### Offline-First Design

Reports are cached locally and synchronized when connectivity is available.

---

### On-Device Moderation

Content moderation is performed locally using **MediaPipe text analysis**.

This prevents harmful or abusive reports.

---

### Privacy-First Architecture

Phantom Crowd intentionally avoids collecting:

• personal identity
• phone numbers
• email addresses
• device identifiers

---

# System Architecture

Phantom Crowd follows a **mobile-first decentralized architecture.**

```
Android Device
     │
CameraX + ARCore
     │
SceneView Rendering Engine
     │
Local Processing Layer
     │
AI Moderation Engine
     │
Firebase Firestore
     │
Geospatial Query Engine
     │
Community Validation System
```

---

# Technology Stack

| Layer            | Technology         |
| ---------------- | ------------------ |
| Language         | Kotlin             |
| UI Framework     | Jetpack Compose    |
| Architecture     | MVVM               |
| AR Framework     | ARCore             |
| AR Rendering     | SceneView          |
| Camera           | CameraX            |
| Backend          | Firebase Firestore |
| AI Moderation    | MediaPipe          |
| Spatial Indexing | Geohash            |

---

# How It Works

1. User opens Phantom Crowd app
2. Camera scans environment
3. ARCore detects surfaces
4. User places issue marker
5. Issue is stored in Firestore
6. Nearby users see the issue in AR
7. Community validates reports
8. Spatial heatmaps highlight risk zones

---

# Data Model

### AnchorData

```
id: String
latitude: Double
longitude: Double
category: String
severity: Int
timestamp: Long
upvotes: Int
description: String
```

---

# Issue Categories

Phantom Crowd supports multiple civic issue types.

• Safety Hazard
• Harassment Area
• Poor Lighting
• Infrastructure Damage
• Environmental Hazard

Each issue includes **severity levels** to help prioritize action.

---

# Spatial Query System

Phantom Crowd uses **geohash-based indexing** to perform efficient spatial queries.

Benefits:

• fast nearby search
• scalable data retrieval
• reduced backend load

Users only receive reports **relevant to their location**.

---

# Use Cases

### Campus Safety

Students can mark unsafe areas or harassment zones.

---

### Smart Cities

Citizens can report infrastructure problems directly on location.

---

### Disaster Response

Communities can mark blocked roads, damaged buildings, or hazards.

---

### Urban Transparency

Authorities and communities gain real-time visibility into civic issues.

---

# Project Structure

```
phantom-crowd/

app/

data/
   models/
   repository/

ui/
   screens/
   components/

ar/
   ARAnchorManager
   SceneRenderer

moderation/
   TextModerationEngine

navigation/

utils/

viewmodel/
```

---

# Installation

Clone the repository

```
git clone https://github.com/yourusername/phantom-crowd
```

Open the project in **Android Studio**

```
File → Open → phantom-crowd
```

Configure Firebase

```
Add google-services.json
Enable Firestore
```

Run the application on an **ARCore supported Android device**.

---

# Contributing

We welcome contributions from developers interested in:

• Augmented Reality systems
• spatial computing
• civic technology
• smart city platforms

Possible areas of contribution:

• AR performance optimization
• UI/UX improvements
• moderation models
• spatial query optimization
• visualization dashboards

---

# Roadmap

Upcoming improvements:

• web dashboard for city administrators
• WebXR spatial viewer
• decentralized storage layer
• AI issue classification
• predictive civic heatmaps
• AR navigation to reported issues

---

# Privacy Model

Phantom Crowd is designed as a **privacy-first civic platform**.

The system intentionally avoids collecting user identities.

Reports exist purely as **location-based anonymous signals**.

---

# License

MIT License

---

# Vision

Phantom Crowd aims to become a **global open infrastructure layer for spatial civic reporting.**

By combining:

• Augmented Reality
• community validation
• privacy-first design

the platform introduces a new model for **collective civic awareness.**

---

# Author

Harish Kumar S P

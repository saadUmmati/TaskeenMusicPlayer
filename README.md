# 🎵 Taskeen – Android Music Player

A complete, production-ready Android music player built with **Kotlin** and **XML** layouts.

---

## ✨ Features

| Feature | Description |
|---|---|
| 📚 **Song Library** | Scans device storage and lists all audio files |
| ▶️ **Full Playback Controls** | Play, Pause, Next, Previous, Seek |
| 🔀 **Shuffle Mode** | Randomized playback order |
| 🔁 **Repeat Modes** | Off → Repeat All → Repeat One |
| 🎨 **Dynamic Theming** | Album art extracted color palette changes the player background (Palette API) |
| 🔔 **Notification Controls** | Persistent media notification with playback controls |
| 🎵 **Mini Player** | Always-visible mini player bar on the library screen |
| 🔍 **Search** | Real-time song search by title, artist, or album |
| 📊 **Sorting** | Sort by Title, Artist, Album, Duration, or Recently Added |
| 🖼️ **Album Art** | Loaded from MediaStore with Glide, fallback to default art |
| 🎧 **Background Playback** | Foreground Service keeps music playing when app is minimized |
| 🔊 **Audio Focus** | Properly requests and manages audio focus |

---

## 📁 Project Structure

```
MusicPlayer/
├── app/src/main/
│   ├── java/com/melodix/player/
│   │   ├── model/
│   │   │   └── Song.kt                  # Data class for a song
│   │   ├── adapter/
│   │   │   └── SongAdapter.kt           # RecyclerView adapter
│   │   ├── service/
│   │   │   └── MusicService.kt          # Foreground service for playback
│   │   ├── ui/
│   │   │   ├── MainActivity.kt          # Song library screen
│   │   │   └── PlayerActivity.kt        # Now Playing screen
│   │   └── utils/
│   │       └── MusicUtils.kt            # MediaStore queries, search, sort
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_main.xml        # Library + mini player
│   │   │   ├── activity_player.xml      # Full player screen
│   │   │   └── item_song.xml            # Song list row
│   │   ├── drawable/                    # All vector icons
│   │   ├── menu/menu_main.xml           # Search + Sort toolbar menu
│   │   └── values/
│   │       ├── colors.xml
│   │       ├── strings.xml
│   │       └── themes.xml
│   └── AndroidManifest.xml
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## 🚀 Setup Instructions

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- Kotlin 1.9+
- Minimum SDK: **26** (Android 8.0 Oreo)
- Target SDK: **34** (Android 14)

### Steps

1. **Open in Android Studio**
   ```
   File → Open → Select the MusicPlayer/ folder
   ```

2. **Sync Gradle**
   - Click "Sync Now" in the banner, or:
   ```
   File → Sync Project with Gradle Files
   ```

3. **Run on device/emulator**
   - Connect a physical Android device (recommended — emulators have no music files)
   - Click ▶ Run, or press `Shift+F10`

4. **Grant Permissions**
   - On first launch, grant storage permission when prompted
   - On Android 13+: `READ_MEDIA_AUDIO` + `POST_NOTIFICATIONS`

---

## 🎨 Design System

| Token | Value |
|---|---|
| Background | `#0D0D0D` (near black) |
| Surface | `#1A1A1A` |
| Accent | `#1DB954` (Spotify green) |
| Text Primary | `#FFFFFF` |
| Text Secondary | `#99FFFFFF` |
| Corner Radius (cards) | `12dp` / `16dp` |

---

## 📦 Screenshots

<p align="center">
  
  <img src="https://github.com/user-attachments/assets/fd515a09-c44c-46bb-b376-248dca9fb7aa" width="23%" alt="Main Library" />
  <img src="https://github.com/user-attachments/assets/3fe66a55-c484-4335-b97a-14af449bed7b" width="23%" alt="Player UI" />
  <img src="https://github.com/user-attachments/assets/b5e9f78b-6323-46c5-bc1e-479c05e60de0" width="23%" alt="Dynamic Colors" />
  <img src="https://github.com/user-attachments/assets/3f7741cc-8b6e-4766-bd2d-2fad79bba69d" width="23%" alt="Mini Player" />
</p>

---


## 📦 Dependencies

```gradle
// UI
com.google.android.material:material:1.11.0
androidx.constraintlayout:constraintlayout:2.1.4
androidx.cardview:cardview:1.0.0
androidx.recyclerview:recyclerview:1.3.2

// Media
androidx.media:media:1.7.0

// Image loading
com.github.bumptech.glide:glide:4.16.0

// Color extraction
androidx.palette:palette-ktx:1.0.0

// Lifecycle
androidx.lifecycle:lifecycle-service:2.7.0
```

---

## 🔒 Permissions

```xml
<!-- Read music files -->
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />           <!-- Android 13+ -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />      <!-- Android 12 and below -->

<!-- Background playback -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

<!-- Notification controls -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Keep CPU awake while playing -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

---

## 🏗️ Architecture Overview

```
MainActivity
    │
    ├── SongAdapter (RecyclerView) ──→ item_song.xml
    │       └── loads songs via MusicUtils.getAllSongs()
    │
    └── MiniPlayer bar ──→ taps open PlayerActivity
            │
            └── binds to MusicService (Foreground Service)
                    │
                    ├── MediaPlayer (audio engine)
                    ├── MediaSession (system media controls)
                    └── Notification (playback controls in shade)

PlayerActivity
    └── binds to MusicService
            ├── SeekBar ←→ Handler polling every 500ms
            ├── Play/Pause/Next/Previous buttons
            ├── Shuffle & Repeat mode toggles
            └── Palette API for dynamic background color
```



## 🚀 Getting Started

1. **Clone the Repo:** `git clone https://github.com/yourusername/melodix-player.git`
2. **Build:** Open in Android Studio (Electric Eel or newer recommended).
3. **Run:** Ensure you grant Media Permissions when prompted to see your music library.

---

## 🛠️ Customization Tips

- **Change accent color**: Edit `accent` in `colors.xml`
- **Filter short files**: Adjust the `30000` ms threshold in `MusicUtils.kt`
- **Add Equalizer**: Launch system equalizer via `Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL")`
- **Add Favorites**: Store favorite song IDs in `SharedPreferences` or `Room` database
- **Add Playlists**: Implement a `Room` database with `Playlist` and `PlaylistSong` entities

---

## 📄 License

MIT License — free to use, modify, and distribute.

---

<p align="center">
*Developed by Muhammad Saad Ahmed — shipping products, not just code.*</p>

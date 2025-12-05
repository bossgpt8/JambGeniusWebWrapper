# JambGenius Android Web Wrapper

A feature-rich Android wrapper that displays JambGenius website as a native app experience.

## Version 1.1.0

### Features

**Core Features:**
- Opens JambGenius website in WebView
- Works like a native app
- Back button navigates within app
- All website features work: exams, chat, payments

**v1.1.0 New Features:**
- Splash screen with JambGenius branding
- Loading progress bar at top of screen
- Pull-to-refresh (swipe down to reload)
- Network auto-reconnect when back online
- File download handler (PDFs, documents)
- Offline banner with retry button
- Enhanced offline page with personalized greeting
- Screenshot protection (FLAG_SECURE)

### Security Features
- Screenshot protection enabled
- Scoped storage compliant (API 29+)
- Modern lifecycle management
- Secure WebView configuration

## Build Instructions

### Option 1: GitHub Actions (Recommended)

1. Push this folder to GitHub
2. Go to **Actions** tab
3. Click **Run workflow** or push to trigger
4. Download APK from **Artifacts** section

#### Setting up Signed APK

1. Generate a keystore:
```bash
keytool -genkey -v -keystore jambgenius.jks -keyalg RSA -keysize 2048 -validity 10000 -alias jambgenius
```

2. Add these secrets in GitHub (Settings > Secrets > Actions):
   - `SIGNING_KEY`: Base64 of your jambgenius.jks
   - `KEY_ALIAS`: `jambgenius`
   - `KEY_STORE_PASSWORD`: Your keystore password
   - `KEY_PASSWORD`: Your key password

Convert keystore to Base64:
```bash
# Linux/Mac
base64 -i jambgenius.jks

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("jambgenius.jks"))
```

### Option 2: Local Build

Requirements:
- JDK 17
- Android SDK

```bash
# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

APK locations:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk`

## Project Structure

```
JambGeniusWebWrapper/
├── app/
│   ├── src/main/
│   │   ├── java/com/jambgenius/web/app/
│   │   │   ├── MainActivity.java         # Main WebView activity
│   │   │   ├── SplashActivity.java       # Splash screen
│   │   │   └── JambGeniusMessagingService.java # Push notifications
│   │   ├── res/
│   │   │   ├── drawable/                 # Icons and backgrounds
│   │   │   ├── layout/                   # Activity layouts
│   │   │   ├── mipmap-*/                 # App icons
│   │   │   └── values/                   # Colors, strings
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── .github/workflows/
│   └── build-apk.yml                     # GitHub Actions workflow
├── gradle/wrapper/
├── build.gradle
├── settings.gradle
├── gradlew                               # Linux/Mac build script
└── gradlew.bat                           # Windows build script
```

## Requirements

- Android 5.0 (API 21) or higher
- Internet connection for full functionality
- ~5MB storage

## Installation

1. Download APK from GitHub Actions artifacts
2. Transfer to Android phone
3. Enable "Install from unknown sources" in Settings
4. Tap APK to install
5. Open JambGenius!

## Support

For issues, open a GitHub issue or contact support.

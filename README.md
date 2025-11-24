# JambGenius Web Wrapper

A lightweight Android wrapper that displays JambGenius website in app form.

## Build Instructions

### Install Android SDK (if not already installed)

```bash
# Download Android SDK Command Line Tools
wget https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip

# Extract
unzip commandlinetools-linux-*_latest.zip
mkdir -p ~/Android/Sdk/cmdline-tools
mv cmdline-tools ~/Android/Sdk/cmdline-tools/latest

# Set environment
export ANDROID_SDK_ROOT=~/Android/Sdk
export PATH=$PATH:~/Android/Sdk/cmdline-tools/latest/bin

# Install SDK components
yes | sdkmanager --licenses
sdkmanager "platforms;android-30" "build-tools;30.0.3"
```

### Build APK

```bash
cd JambGeniusWebWrapper
./gradlew assembleRelease
```

APK location: `app/build/outputs/apk/release/app-release-unsigned.apk`

### Sign APK

```bash
# Generate keystore (first time)
keytool -genkey -v -keystore jambgenius.jks -keyalg RSA -keysize 2048 -validity 10000 -alias jambgenius

# Sign APK
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore jambgenius.jks app/build/outputs/apk/release/app-release-unsigned.apk jambgenius
```

## Features

- Opens JambGenius website in WebView
- Works like a native app
- Back button navigates within app
- All website features work: exams, chat, payments, everything

## Distribution

Share APK file or distribute through:
- GitHub Releases
- F-Droid
- APKMirror
- Your website

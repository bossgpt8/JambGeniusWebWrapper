# GitHub Actions APK Build Setup

This guide will help you automatically build your JambGenius Android APK using GitHub Actions.

## Step 1: Push to GitHub

```bash
cd JambGeniusWebWrapper
git init
git add .
git commit -m "Initial commit - JambGenius Web Wrapper"
git branch -M main
git remote add origin https://github.com/YOUR-USERNAME/JambGeniusWebWrapper.git
git push -u origin main
```

## Step 2: Create Signing Key

On any computer (or online), generate a keystore file:

```bash
keytool -genkey -v -keystore jambgenius.jks -keyalg RSA -keysize 2048 -validity 10000 -alias jambgenius
```

You'll need:
- Keystore password
- Key alias: `jambgenius`
- Key password

## Step 3: Add GitHub Secrets

1. Go to: **GitHub Repository > Settings > Secrets and variables > Actions**
2. Add these secrets:

| Secret Name | Value |
|-------------|-------|
| `SIGNING_KEY` | Base64 of your jambgenius.jks file (see below) |
| `KEY_ALIAS` | `jambgenius` |
| `KEY_STORE_PASSWORD` | Your keystore password |
| `KEY_PASSWORD` | Your key password |

### Convert keystore to Base64:

**On Linux/Mac:**
```bash
base64 -i jambgenius.jks -o signing_key.txt
# Copy contents of signing_key.txt
```

**On Windows:**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("jambgenius.jks")) | Out-File -FilePath "signing_key.txt"
# Copy contents of signing_key.txt
```

## Step 4: Trigger Build

### Option A: Automatic (Every Push)
Just push code to main branch and it builds automatically.

### Option B: Manual Trigger
1. Go to GitHub repository
2. Click "Actions" tab
3. Select "Build APK" workflow
4. Click "Run workflow"

## Step 5: Download APK

1. Go to GitHub repository
2. Click "Actions" tab
3. Find the latest build
4. Click it
5. Scroll down to "Artifacts"
6. Download `jambgenius-app.zip`
7. Extract and get `app-release-aligned.apk`

## Step 6: Install on Phone

1. Transfer APK to your Android phone
2. Open file manager
3. Navigate to the APK file
4. Tap to install
5. Allow installation from unknown sources if prompted
6. Launch JambGenius!

## Troubleshooting

**Build fails with "gradle not found":**
- GitHub Actions will download it automatically - just retry

**Signing fails:**
- Check all 4 secrets are set correctly
- Base64 string shouldn't have line breaks

**APK won't install:**
- Check your phone allows "Install from unknown sources"
- Try on different phone or emulator

## Subsequent Builds

To rebuild the APK:
1. Make changes to code
2. Commit and push: `git push`
3. Go to Actions tab
4. Download APK from latest build

That's it! Your APK builds automatically on GitHub's 64-bit servers! 🚀

# Beacon Android Build Guide

Use this guide to produce a full native Android build of the Beacon React Native app and run it on the Android Emulator while it talks to the docker-compose stack shipped in this repository (`localhost:8080`).

## Prerequisites
- Node.js 20+ and npm (already required by the repo).
- Android Studio with the latest SDK and build tools (target API level 34).
- Java Development Kit 17 or newer (Gradle wrappers use JDK 17+).
- Android Emulator configured in Android Studio (Pixel 7 / Android 14 recommended).
- Expo CLI installed locally (`npm install -g expo`), or invoke it via `npx expo`.

## One-time native setup
1. Install JS dependencies:
   ```bash
   cd ui/react-native
   npm install
   ```
2. Ensure the native project is synced with Expo’s config:
   ```bash
   npx expo prebuild
   ```
   This regenerates `android/` with the latest Expo assets.

## Bring up the local backend cluster
- From the repository root (one level up from `ui/react-native`), start the services defined in `docker-compose.yml`:
  ```bash
  docker compose up --build
  ```
  nginx inside the compose stack proxies the UI and APIs on `http://localhost:8080`.

## Configure API access for the emulator
- Export the Expo variables so the app targets the compose gateway:
  ```bash
  export EXPO_PUBLIC_API_BASE_URL="http://localhost:8080"
  export EXPO_PUBLIC_DEV_MODE="true"   # enables demo credentials if desired
  ```
- The gateway terminates on `localhost`, and the repo’s Android debug manifest already allows cleartext traffic to the host.

## Build and run the debug app on an emulator
1. In terminal tab 1 start Metro in localhost mode (bound to port `8083`, so it does not collide with the compose services):
   ```bash
   cd ui/react-native
   npm run start:local
   ```
2. In terminal tab 2, build and deploy the native app:
   ```bash
   cd ui/react-native
   npm run android
   ```
   - Expo will invoke Gradle (`android/app`) to create a debug build and install it on the running emulator.
   - On first run, accept any Android SDK license prompts that appear in the terminal.
3. Once the app launches, log in using Google (if configured) or the `demo/demo` credentials when dev mode is enabled.

## Running without Expo CLI
If you prefer direct Gradle control:
```bash
cd ui/react-native/android
./gradlew installDebug    # Builds and installs the debug APK on the default emulator/device
```
Metro must still be running (see “start:local” step above) so the JS bundle is served.

## Network troubleshooting tips
- If you see login failures from the emulator, confirm the API base URL is `http://localhost:8080` by printing `CONFIG.apiBaseUrl` in the app or using React DevTools.
- Cleartext HTTP is allowed in `android/app/src/debug/AndroidManifest.xml` for local development; no extra config needed as long as you use the debug build.
- Restart Metro with `npm run start -- --clear` if you suspect an old JS bundle is cached.

## Creating a release APK/AAB (optional)
1. Generate a keystore (once) and register it in `android/app/build.gradle` if you need signed builds.
2. Assemble a release artifact:
   ```bash
   cd ui/react-native/android
   ./gradlew assembleRelease    # Produces app-release.apk in android/app/build/outputs/apk/release
   ./gradlew bundleRelease      # Produces app-release.aab for Play Store uploads
   ```
3. Because release builds disable the development server, you must embed a production JS bundle beforehand or host it remotely. For local testing stick with the debug build.

You now have a native Android install that bypasses Expo Go and talks directly to your local services.

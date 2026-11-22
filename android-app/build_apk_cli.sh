#!/bin/bash
# Local APK Build Automation Script (No Android Studio required!)
set -e

echo "=========================================================="
echo "  CS4473 Network Meter: Command-Line APK Builder"
echo "=========================================================="

# 1. Check Java
if ! command -v java &> /dev/null; then
    echo "Error: Java (JDK) not found. Please install OpenJDK 17 or 21."
    exit 1
fi
echo "✓ Java detected: $(java -version 2>&1 | head -n 1)"

# 2. Check Android SDK
if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [ -d "/opt/homebrew/share/android-commandlinetools" ]; then
        export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
    elif [ -d "/usr/local/share/android-sdk" ]; then
        export ANDROID_HOME="/usr/local/share/android-sdk"
    fi
fi

if [ -z "$ANDROID_HOME" ] || [ ! -d "$ANDROID_HOME" ]; then
    echo ""
    echo "Notice: Android SDK not detected on your local PATH."
    echo "To build locally without Android Studio, run:"
    echo "    brew install --cask android-commandlinetools"
    echo "    brew install gradle"
    echo "    sdkmanager \"platforms;android-34\" \"build-tools;34.0.0\""
    echo ""
    echo "Alternatively, use GitHub Actions (100% cloud, 0 local setup):"
    echo "Push this repo to GitHub and download the APK from the Actions tab!"
    exit 1
fi

echo "✓ ANDROID_HOME: $ANDROID_HOME"

# 3. Build APK
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Building Debug APK with Gradle..."
if command -v gradle &> /dev/null; then
    gradle assembleDebug
else
    chmod +x ./gradlew
    ./gradlew assembleDebug
fi

APK_PATH="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo ""
    echo "=========================================================="
    echo "✓ BUILD SUCCESSFUL!"
    echo "APK generated at: $APK_PATH"
    echo "=========================================================="
    echo "To install on connected phone via adb:"
    echo "    adb install -r \"$APK_PATH\""
else
    echo "Error: APK file was not created at expected path: $APK_PATH"
fi

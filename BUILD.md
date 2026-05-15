# Neo - Build Instructions

## Prerequisites

**IMPORTANT**: Neo requires **Java 17** to build. The build will fail with Java 21 or Java 25.

### Installing Java 17

**On Arch Linux:**
```bash
sudo pacman -S jdk17-openjdk
```

**On Ubuntu/Debian:**
```bash
sudo apt install openjdk-17-jdk
```

### Setting Java 17 for the Build

**Option 1: Use archlinux-java (Arch Linux)**
```bash
# List available Java versions
archlinux-java status

# Set Java 17 as default
sudo archlinux-java set java-17-openjdk
```

**Option 2: Set JAVA_HOME for this project only**
```bash
# Find Java 17 installation
ls /usr/lib/jvm/

# Build with Java 17
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew assembleDebug
```

**Option 3: Configure in gradle.properties (recommended)**

Create or edit `gradle.properties` in the project root:
```properties
org.gradle.java.home=/usr/lib/jvm/java-17-openjdk
```

## Building the APK

Once Java 17 is configured:

```bash
cd /home/hassan/Projects/neo-fyp
./gradlew assembleDebug
```

The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

## Installing on Device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Opening in Android Studio

1. Open Android Studio
2. File → Open → Select `/home/hassan/Projects/neo-fyp`
3. Android Studio will sync Gradle automatically
4. Make sure Android Studio is using Java 17:
   - File → Settings → Build, Execution, Deployment → Build Tools → Gradle
   - Set "Gradle JDK" to "17"

## Troubleshooting

### "25.0.1" Error
This means Gradle is using Java 25. Set JAVA_HOME to Java 17 as shown above.

### jlink Error
This occurs with Java 21+. Use Java 17 specifically.

### Missing Android SDK
Set the SDK location in `local.properties`:
```properties
sdk.dir=/home/hassan/Android/Sdk
```

## Current Build Status

Verified locally with Java 17:

```bash
./gradlew compileDebugKotlin
./gradlew testDebugUnitTest
./gradlew assembleDebugAndroidTest
./gradlew assembleDebug
```

All four commands pass. Physical multi-device BLE mesh testing still needs to be run on Android devices before final demonstration.

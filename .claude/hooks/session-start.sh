#!/bin/bash
# Prepares a Claude Code on the web container so Gradle builds, ktlint, and tests of every module
# (including :app) work: JDK 17 toolchain, a Maven Central mirror, the Android SDK, and warm caches.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
JDK17_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ANDROID_SDK=/opt/android-sdk
CMDLINE_TOOLS_ZIP=commandlinetools-linux-13114758_latest.zip
# gradlew defaults GRADLE_USER_HOME to this git-ignored project directory.
GRADLE_HOME_DIR="$PROJECT_DIR/.gradle-user-home"

# 1. The Gradle toolchain requires JDK 17.
if [ ! -x "$JDK17_HOME/bin/javac" ]; then
  apt-get update -q >/dev/null
  DEBIAN_FRONTEND=noninteractive apt-get install -y -q openjdk-17-jdk-headless >/dev/null
fi

# 2. Maven Central rate-limits shared cloud IPs (HTTP 429); Google's Maven Central mirror does not.
mkdir -p "$GRADLE_HOME_DIR/init.d"
cat > "$GRADLE_HOME_DIR/init.d/maven-central-mirror.gradle.kts" <<'KTS'
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"

fun RepositoryHandler.useMirror() {
    all {
        if (this is MavenArtifactRepository && url.toString().startsWith("https://repo.maven.apache.org")) {
            setUrl(mirror)
        }
    }
}

beforeSettings {
    pluginManagement.repositories.useMirror()
    dependencyResolutionManagement.repositories.useMirror()
}

allprojects {
    buildscript.repositories.useMirror()
    repositories.useMirror()
}
KTS
# RuStore's Maven repository answers 404 to cloud machines, so :app builds here without its SDK.
cat > "$GRADLE_HOME_DIR/gradle.properties" <<PROPS
org.gradle.java.installations.paths=$JDK17_HOME
logica.withoutRustore=true
PROPS

# 3. Android SDK for :app and the Android targets of the multiplatform modules.
if [ ! -d "$ANDROID_SDK/platforms/android-36" ]; then
  mkdir -p "$ANDROID_SDK/cmdline-tools"
  if [ ! -x "$ANDROID_SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    tmp=$(mktemp -d)
    curl -fsSL -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
    unzip -q "$tmp/tools.zip" -d "$tmp"
    rm -rf "$ANDROID_SDK/cmdline-tools/latest"
    mv "$tmp/cmdline-tools" "$ANDROID_SDK/cmdline-tools/latest"
    rm -rf "$tmp"
  fi
  yes | JAVA_HOME=$JDK17_HOME "$ANDROID_SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_SDK" --licenses >/dev/null || true
  JAVA_HOME=$JDK17_HOME "$ANDROID_SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_SDK" \
    "platforms;android-36" "build-tools;36.0.0" "platform-tools" >/dev/null
fi
echo "sdk.dir=$ANDROID_SDK" > "$PROJECT_DIR/local.properties"

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export ANDROID_HOME=$ANDROID_SDK"
    echo "export ANDROID_SDK_ROOT=$ANDROID_SDK"
  } >> "$CLAUDE_ENV_FILE"
fi

# 4. Warm the Gradle daemon and dependency caches so the first build in the session is fast.
cd "$PROJECT_DIR"
ANDROID_HOME=$ANDROID_SDK bash ./gradlew --quiet :puzzle-core:jvmTestClasses :web-app:compileKotlinJs >/dev/null

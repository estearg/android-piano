#!/bin/bash
# Builds the signed release bundle (.aab) for Google Play and a signed universal .apk for testing.
# The upload key is read from /home/saw/piano-jks (harika-piyano.jks + sifre.txt), outside the repository.
# Usage: ./build-release.sh
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=${JAVA_HOME:-/home/saw/android-jdk/jdk-17.0.20.1+1}; export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
KEYSTORE_DIR=${KEYSTORE_DIR:-/home/saw/piano-jks}
BUNDLETOOL=${BUNDLETOOL:-/home/saw/android-tools/bundletool-all-1.18.3.jar}
./gradlew -PkeystoreDir="$KEYSTORE_DIR" bundleRelease
VERSION=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts); CODE=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
mkdir -p release; AAB=release/Harika-Piyano-$VERSION-$CODE.aab; APK=release/Harika-Piyano-$VERSION-$CODE-test.apk
cp app/build/outputs/bundle/release/app-release.aab "$AAB"
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
java -jar "$BUNDLETOOL" build-apks --bundle="$AAB" --output="$T/t.apks" --mode=universal --ks="$KEYSTORE_DIR/harika-piyano.jks" \
  --ks-pass=file:"$KEYSTORE_DIR/sifre.txt" --ks-key-alias=harika-piyano --key-pass=file:"$KEYSTORE_DIR/sifre.txt" >/dev/null
unzip -qo "$T/t.apks" universal.apk -d "$T" && mv "$T/universal.apk" "$APK"
ls -la "$AAB" "$APK"

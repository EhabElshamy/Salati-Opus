#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if command -v gradle >/dev/null; then
  gradle wrapper --gradle-version 8.11.1
else
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  curl -fL --retry 3 https://services.gradle.org/distributions/gradle-8.11.1-bin.zip -o "$tmp/gradle.zip"
  curl -fL --retry 3 https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256 -o "$tmp/gradle.sha256"
  printf '%s  %s\n' "$(cat "$tmp/gradle.sha256")" "$tmp/gradle.zip" | sha256sum -c -
  unzip -q "$tmp/gradle.zip" -d "$tmp"
  "$tmp/gradle-8.11.1/bin/gradle" wrapper --gradle-version 8.11.1
fi
printf '\nWrapper created. Open this folder in Android Studio.\n'

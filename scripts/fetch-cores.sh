#!/usr/bin/env bash
# Downloads Xray-core and sing-box Android arm64 binaries and places them into jniLibs
# as libxray.so / libsingbox.so. Android extracts jniLibs into nativeLibraryDir with exec permission,
# which lets the app run them as subprocesses.
set -euo pipefail
XRAY_VERSION="${XRAY_VERSION:-v26.3.27}"
SINGBOX_VERSION="${SINGBOX_VERSION:-1.14.0}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/jniLibs/arm64-v8a"
TMP="$(mktemp -d)"
mkdir -p "$DEST"
echo "Fetching Xray-core $XRAY_VERSION"
curl -fsSL -o "$TMP/xray.zip" "https://github.com/XTLS/Xray-core/releases/download/$XRAY_VERSION/Xray-android-arm64-v8a.zip"
unzip -q -o "$TMP/xray.zip" xray -d "$TMP/xray"
cp "$TMP/xray/xray" "$DEST/libxray.so"
echo "Fetching sing-box $SINGBOX_VERSION"
curl -fsSL -o "$TMP/sb.tar.gz" "https://github.com/SagerNet/sing-box/releases/download/v$SINGBOX_VERSION/sing-box-$SINGBOX_VERSION-android-arm64.tar.gz"
tar -xzf "$TMP/sb.tar.gz" -C "$TMP"
cp "$TMP/sing-box-$SINGBOX_VERSION-android-arm64/sing-box" "$DEST/libsingbox.so"
chmod +x "$DEST"/*.so
rm -rf "$TMP"
ls -la "$DEST"

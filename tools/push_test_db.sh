#!/usr/bin/env bash
# Debug builds only: copies a sample offline database onto a device/emulator as
# databases/master-test.db inside the app's private storage. The debug app then reads that file
# instead of downloading and syncing (see docs/testing-with-sample-db.md).
#
# Usage: tools/push_test_db.sh [-s <adb serial>] [--with-properties] [path/to/sample.db]
#   default path: "$HOME/Downloads/master-db 1.db"
#   --with-properties  also adds a `properties` table with one sample property to the pushed copy
#                      (needs sqlite3 on the host); the property list is otherwise online-only.
# Safe to run again: it replaces the previous copy and its -wal/-shm/-journal files.
# `run-as` only works for debuggable apps, so this can never touch a release install.
set -euo pipefail

# Git Bash on Windows rewrites /data/... arguments into C:/Program Files/Git/data/...; keep device paths as written.
export MSYS_NO_PATHCONV=1

PKG="com.sclera.sclerago"
DB_NAME="master-test.db"
SERIAL=""
WITH_PROPERTIES=0
SRC="$HOME/Downloads/master-db 1.db"

while [ $# -gt 0 ]; do
  case "$1" in
    -s) SERIAL="$2"; shift 2 ;;
    --with-properties) WITH_PROPERTIES=1; shift ;;
    -h|--help) sed -n 2,11p "$0"; exit 0 ;;
    *) SRC="$1"; shift ;;
  esac
done

[ -f "$SRC" ] || { echo "Sample database not found: $SRC" >&2; exit 1; }

# Find adb: PATH, then ANDROID_HOME / ANDROID_SDK_ROOT, then the default Windows SDK location.
ADB="$(command -v adb || true)"
for root in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "${LOCALAPPDATA:-}/Android/Sdk"; do
  [ -z "$ADB" ] && [ -n "$root" ] && [ -x "$root/platform-tools/adb" -o -x "$root/platform-tools/adb.exe" ] && ADB="$root/platform-tools/adb"
done
[ -n "$ADB" ] || { echo "adb not found. Set ANDROID_HOME or add platform-tools to PATH." >&2; exit 1; }
adb_cmd() { if [ -n "$SERIAL" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cp "$SRC" "$WORK/$DB_NAME"
# Native Windows tools (adb, sqlite3) need a Windows path for local files; plain Linux/macOS keep the path as is.
LOCAL_DB="$WORK/$DB_NAME"
if command -v cygpath >/dev/null; then LOCAL_DB="$(cygpath -m "$LOCAL_DB")"; fi

if [ "$WITH_PROPERTIES" = 1 ]; then
  command -v sqlite3 >/dev/null || { echo "--with-properties needs sqlite3 on the host PATH." >&2; exit 1; }
  # Columns read by data/mapper/PropertyMapper (toProperty) and PropertyOfflineSource.
  sqlite3 "$LOCAL_DB" <<'SQL'
DROP TABLE IF EXISTS properties;
CREATE TABLE properties (
  id TEXT PRIMARY KEY, name TEXT, type TEXT, settings TEXT,
  address_line1 TEXT, address_line2 TEXT, country TEXT, state TEXT, region TEXT, city TEXT, postcode TEXT,
  contact_name TEXT, contact_email TEXT, contact_phone TEXT, notes TEXT,
  available_from TEXT, available_until TEXT, access_code TEXT, is_removed INTEGER DEFAULT 0);
INSERT INTO properties VALUES (
  '7b1f0c5e-3a52-4d0e-9a41-5c1d2e8f6a10', 'Harbour Point Tower', 'Office', '{"gps":true}',
  '1 Harbour Street', '', 'Australia', 'NSW', 'Sydney', 'Sydney', '2000',
  'Sample Contact', 'contact@example.com', '+61 2 5550 0100', 'Sample property for testing',
  '00:01', '23:59', '1234', 0);
SQL
fi

REMOTE="/data/local/tmp/$DB_NAME"
echo "Pushing $(basename "$SRC") to the device..."
adb_cmd push "$LOCAL_DB" "$REMOTE" >/dev/null

# Stop the app first so nothing holds the old file open, then replace it inside the app's private storage.
adb_cmd shell am force-stop "$PKG"
adb_cmd shell "run-as $PKG sh -c 'mkdir -p databases && rm -f databases/$DB_NAME databases/$DB_NAME-wal databases/$DB_NAME-shm databases/$DB_NAME-journal && cp $REMOTE databases/$DB_NAME && ls -l databases/$DB_NAME'" \
  || { echo "run-as failed: install a DEBUG build of $PKG first." >&2; exit 1; }
adb_cmd shell rm -f "$REMOTE"

cat <<MSG

Done. databases/$DB_NAME is on the device.
Next: launch Sclera Go (debug), sign in, and the screens read from this file. Sync is off in this mode.
Reset: adb shell run-as $PKG rm databases/$DB_NAME   (then the real download and sync run again).
MSG

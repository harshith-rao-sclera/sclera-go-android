# Testing with the sample database

Debug builds can read a local copy of the sample database instead of downloading and syncing one, so you can check that screens really come from the database. Release builds contain none of this.

## Steps

1. Build and install the debug app: `./gradlew :app:installDebug` (or run it from Android Studio once).
2. Push the sample database (default `~/Downloads/master-db 1.db`):
   `bash tools/push_test_db.sh [-s <serial>] [--with-properties] ["path/to/master-db 1.db"]`
   It copies the file to `databases/master-test.db` in the app's private storage and stops the app.
3. Launch the app and sign in. Sync reports Ready at once; no download and no sync request is made.

## What you should see

- Assets: 63 in total. 54 AI suggestion, 8 submitted, 1 exception.
- Building "Harbour Point Tower" with 10 floors and 29 locations.
- 13 standard fields and 25 master formats.
- 1 validation job with 63 results and 30 issues.

## Prove it is the database

Change a name on the device copy, relaunch, and see it change on screen:

    adb shell run-as com.sclera.sclerago sqlite3 databases/master-test.db "update assets set name='DB TEST' where id='<an asset id>'"

(If the device has no `sqlite3`, pull the file with `adb exec-out run-as com.sclera.sclerago cat databases/master-test.db > edit.db`, edit it on the computer and push it again with the script.) Edits made in the app are written to the same file.

## Reset

`adb shell run-as com.sclera.sclerago rm databases/master-test.db`, then restart the app: the real download and sync run again. Re-run the script to restore the original sample.

## Notes and limits

- Sync is OFF in this mode; nothing is uploaded or downloaded, and the test database has no sync triggers.
- Sign-out clears everything else but keeps `master-test.db`, so it need not be pushed again.
- Not from the database: people and groups, settings, and properties (online-only, need the API / QA token). Use `--with-properties` to add a `properties` table with one sample property ("Harbour Point Tower") so the property list can come from the file too.

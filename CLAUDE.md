# Sclera Go: development rules

Android app (Kotlin, Jetpack Compose, `com.sclera.sclerago`). These rules apply to every change, whether
it is a new feature, a fix or a UI tweak. Check them before you start and again before you finish.
Also follow **SOLID** (section 7) and **OWASP MASVS/MASTG security** (section 8; sensitive data includes
tokens, sessions, emails, location, access codes, contacts and asset photos).
Run `/audit` to check the **whole project** against them.

## 1. Architecture: Clean Architecture + MVVM

Layers and where code goes (dependencies point inward: `presentation → domain ← data`):

| Layer | Package | Contains | Must not use |
|---|---|---|---|
| Domain | `domain/model`, `domain/repository`, `domain/usecase/<feature>`, `domain/validation` | Plain Kotlin: models, repository **interfaces**, use cases (one rule each, `operator fun invoke`) | Android, Compose, Retrofit, `data/` or `presentation/` |
| Data | `data/remote`, `data/local`, `data/repository`, `data/mapper` | Retrofit services, stores, repository **implementations**, mappers | `presentation/` |
| Presentation | `presentation/<feature>`, `presentation/components`, `presentation/theme`, `presentation/icons`, `presentation/model`, `presentation/common` | Screens (Composables), `@HiltViewModel`s, UI state, UI-only mappers (colours, icons) | `data/` directly (go through use cases) |
| DI | `di/` | Hilt modules (`@Binds` repositories, `@Provides` network) | Business logic |
| Services | `services/` | Platform engines: scanners, media, AI helpers | Screens |

- **MVVM:** a screen is a stateless Composable fed by a ViewModel. The ViewModel holds UI state and calls
  **use cases**; it never contains business rules, network calls or storage access. Composables never
  call repositories, Retrofit or `data/` classes.
- **Group by feature:** everything for one feature lives together (`presentation/<feature>`,
  `domain/usecase/<feature>`). A new feature gets its own package in each layer it needs.
- One use case = one rule. Bundle a feature's use cases in a `<Feature>UseCases` class for injection.
- Models in `domain/model` carry no colours, icons or Compose types; map them in `presentation/model`.
- Repositories expose `StateFlow`/`suspend`; implementations are bound in a Hilt module. In-memory
  implementations are fine until an API exists, but keep them behind the interface.

## 2. Standard Android stack. Don't hand-roll what these provide

- **Hilt** for all dependency injection (`@HiltViewModel`, `@Inject`, `@Module`). No singletons/service locators.
- **Retrofit + OkHttp** for every HTTP call (service interface in `data/remote`, wrapped by an `*Api` class).
  No `HttpURLConnection` in new code.
- **Kotlin Coroutines + Flow** for async work; ViewModels use `viewModelScope`; use cases are `suspend`
  or return Flow. No `Thread`, no `AsyncTask`.
- **Jetpack Compose + Material 3** for UI; `collectAsStateWithLifecycle` for flows.
- Persistence, when needed: **Room** (data) or **DataStore** (settings). Not raw SharedPreferences for new features.
- Versions go in `gradle/libs.versions.toml`. No hard-coded dependency versions in `build.gradle.kts`.
- Prefer a maintained AndroidX/Google library over custom code.

## 3. Localization: every user-visible string

- No hard-coded user text in Kotlin: toasts, labels, errors, placeholders, content descriptions,
  dialog text, notification text. Use `stringResource(R.string.…)` / `pluralStringResource`, or `UiText`
  from a ViewModel.
- Add each string to `res/values/strings.xml` and translate it in **every** existing locale folder
  (`values-ar, de, es, fr, hi, in, it, iw, ja, ko, nl, pl, pt-rBR, ru, th, tr, uk, vi, zh-rCN, zh-rTW`).
  `i18n/LocalizationTest` fails if a locale is missing a key or has mismatched format arguments.
- Use positional format args (`%1$s`) and `<plurals>`; never build sentences by concatenation.
- The product name "Sclera Go" is not translated (`translatable="false"`).
- Layouts must work in RTL (Arabic, Hebrew): use `start`/`end`, never `left`/`right`.
- **Known gap:** ~138 strings in older screens are still hard-coded. Move them to resources whenever you
  touch a screen; `/audit` lists what is left.

## 4. UI: phone, tablet portrait, tablet landscape

Every screen and sheet must be built and checked for all three:

- **Phone:** narrower than 600dp (shortest side). **Tablet:** shortest side ≥ 600dp, in portrait **and** landscape.
- Use the shared helpers in `presentation/components/Adaptive.kt`: `isTablet()`, `pageGutter()`,
  `sheetFrame()` / `SheetMaxWidth`, `ReadableWidth`. Don't re-implement breakpoints per screen.
- Design decisions already made: lists stay **one card per row** on every size; content pages centre at
  ≤760dp; sheets open **centred** (≤640dp, all corners rounded, no drag handle) on tablets and docked at
  the bottom on phones.
- Follow the design canvas / Figma for layout, spacing, colours and icons. Don't invent styles.
- Colours, type and spacing come from `presentation/theme` (`C`, `Txt`); icons from `presentation/icons`.
- Every screen has `@Preview`s for **Phone**, **Tablet portrait** and **Tablet landscape**
  (see `EmailStepPreview` in `LoginScreen.kt`).
- Check ≥48dp touch targets, content descriptions on icon-only buttons, and large-font behaviour.

## 5. Reusable code

- Before writing a widget, search `presentation/components` (and the feature package) for an existing one.
  Extend it with a parameter rather than copying it.
- A widget used by two or more features lives in `presentation/components`; a widget used by one feature
  stays in that feature's package (`private`/`internal`).
- Same for logic: shared rules go in a use case or `domain/` helper, not copied between ViewModels.
- No dead code, commented-out code, or duplicated constants. Keep files focused (split anything past
  ~600 lines by responsibility).

## 6. Tests and coverage. Required with every change

Write the tests in the same change as the code. Coverage must stay **above 80%** (line coverage), overall
and for every new or changed class.

- **Unit tests** (`app/src/test`, JUnit): use cases, mappers, validators, ViewModels (fake repositories,
  `kotlinx-coroutines-test`), repositories. Cover success, failure and edge cases.
- **UI tests** (Compose `createComposeRule`; Robolectric in `src/test`, instrumented in `src/androidTest`):
  each screen's key states and interactions, run for phone **and** tablet portrait/landscape
  configurations (`@Config(qualifiers = "w411dp-h891dp")`, `"w800dp-h1280dp"`, `"w1280dp-h800dp"`).
  Select nodes with `testTag`/semantics, not by translated text where it can be avoided.
- **Integration tests:** ViewModel → use cases → real in-memory repositories; Retrofit services against a
  local HTTP server (see `AuthApiTest`) to pin URLs, headers and error mapping; Hilt wiring with
  `@HiltAndroidTest` where it adds value.
- Tests are deterministic: no real network, no sleeps, injected dispatchers/clocks.
- Measure coverage with JaCoCo (`./gradlew jacocoTestReport`).
  JaCoCo is configured (`./gradlew :app:jacocoTestReport`, `jacocoCoverageVerification` enforces the 80% rule).
  **Status: about 20% of lines are covered; the gap is mostly Compose screens and the scan engines.** Raise it with every change.
- Run the test suite **only when asked** (or via `/audit`); but always write the tests.

## 7. SOLID

Apply these when designing every class, Composable and module; flag a violation rather than working around it.

- **S, Single responsibility:** one reason to change per class. A use case = one rule, a repository = one
  data source/area, a ViewModel = one screen's state, a Composable = one piece of UI. Split a class that
  mixes UI state, business rules and I/O.
- **O, Open/closed:** extend through new implementations, parameters or `when` branches on sealed types
  instead of editing stable code. Add a repository implementation or a use case rather than patching callers.
- **L, Liskov substitution:** every implementation honours its interface's contract (nullability, thrown
  errors, threading). A fake used in tests must behave like the real one, so a test double can replace it.
- **I, Interface segregation:** small, focused interfaces (per feature, as in `domain/repository`). Don't
  add methods "in case"; don't force a class to depend on methods it never calls.
- **D, Dependency inversion:** depend on abstractions. ViewModels and use cases take **interfaces**
  (`AssetRepository`), never `*Impl`; concrete classes are wired only in Hilt modules. Constructor
  injection everywhere, nothing created with `Foo()` inside business code.
- Prefer immutable models (`data class`, `val`), sealed types for results/errors, and composition over
  inheritance. Inject dispatchers and clocks so code is testable.

## 8. Security: OWASP MASVS / MASTG

Follow the **OWASP Mobile Application Security Verification Standard (MASVS v2)**: **L1 for the whole
app, and the L2 controls for authentication, session and token handling**. Verify with the **MASTG** test
cases for the matching MASVS category. Treat security as part of "done", not a later pass.

### Sensitive data in this app (handle all of it as sensitive)
- **Credentials and sessions:** access token, session id and cookie, the login email, user details
  (role, organisation, phone, photo URL).
- **Location:** GPS position, geofence and building/location trees.
- **Site access:** property and building **access codes**, contacts (name, email, phone), people and
  access data (who can open which property).
- **Asset evidence:** photos, videos and documents of assets, plus scan results (IPs, MACs, BLE ids,
  Wi-Fi SSIDs/BSSIDs, device names).

### MASVS-STORAGE (data at rest)
- Never store sensitive data in plain text. Tokens, session ids and access codes go in encrypted storage
  backed by the **Android Keystore** (AES-GCM, e.g. Tink or DataStore + AEAD). `EncryptedSharedPreferences`
  is deprecated, so don't add new uses.
- Photos, videos and documents stay in app-private storage (`filesDir`), never external/shared storage;
  delete them when their asset or draft is discarded and on sign-out.
- **Sign-out wipes** the session store, cookies, WebView data, caches and drafts.
- Backups: exclude every file holding sensitive data (`backup_rules.xml`, `data_extraction_rules.xml`);
  set `allowBackup="false"` unless there is a reviewed reason.
- No sensitive data in logs, crash reports, analytics, clipboard, screenshots or notifications. Never log
  tokens, cookies, emails, codes, locations or full response bodies. Logs carry events and codes only, and
  debug logs are stripped from release.
- Use `FLAG_SECURE` (or hide content in recents) on screens showing access codes or session details.

### MASVS-CRYPTO
- Only standard, vetted algorithms and libraries (Keystore, Tink, `SecureRandom`, AES-GCM, SHA-256+).
  No custom crypto, no MD5/SHA-1/DES/ECB, no hard-coded keys, IVs or secrets, no `java.util.Random` for
  anything security-related. Keys are generated on the device and non-exportable.

### MASVS-AUTH (login and session)
- Authenticate through the SSO/Keycloak flow only. Never ask for or store a password in the app.
- Tokens: short-lived, refreshed before expiry, sent only in the `Authorization` header (never in URLs or
  query strings), and **cleared and the user signed out** on any session-ending error.
- Verify authorization on the server; the app must never rely on hidden UI for access control.
  Re-check the session when the app returns to the foreground.
- Gate sensitive actions (sign-out, role/permission changes) behind confirmation; consider biometric
  re-auth (`BiometricPrompt`) for high-risk actions.

### MASVS-NETWORK
- **HTTPS only.** Declare `android:usesCleartextTraffic="false"` and a `networkSecurityConfig`; no
  cleartext hosts, no user-added CA trust in release.
- Never weaken TLS: no trust-all `TrustManager`, no `HostnameVerifier` that returns true, no ignoring
  `onReceivedSslError` (cancel the load). Pin certificates (OkHttp `CertificatePinner`) for the Sclera API
  hosts once keys are agreed, with a backup pin and a rotation plan.
- All network code goes through the shared Retrofit/OkHttp client. No logging interceptor above `BASIC`
  in release, and never one that prints headers or bodies with tokens.
- Validate and encode everything built into a URL (`Uri.encode`, Retrofit `@Path`/`@Query`).

### MASVS-PLATFORM (IPC, WebView, permissions)
- Export only what must be reachable (`MainActivity` launcher). Everything else `exported="false"`; any
  intent filter, deep link, content provider or broadcast receiver needs a security review and input
  validation. Use `PendingIntent.FLAG_IMMUTABLE`.
- **WebView:** enable JavaScript only for the SSO page and the map; load only HTTPS URLs from the
  allow-listed hosts; no `addJavascriptInterface`, no `file://`/content access, safe browsing on, and
  third-party cookies off unless the SSO flow needs them. Clear WebView data on sign-out.
- Request the minimum permissions, at the moment of use, with a rationale (camera, location, Bluetooth,
  nearby Wi-Fi). Handle denial gracefully; never collect location or scans silently.
- Treat all external input (URIs from pickers, intents, QR/BLE/Wi-Fi/network scan results, spreadsheet
  files, server JSON) as untrusted: validate type, size and content before use (e.g. the 100MB import cap).

### MASVS-CODE (code quality and build)
- Release builds: **R8 on** (minify + shrink), `debuggable=false`, no test/debug code or endpoints, no
  secrets or API keys in source, resources or `BuildConfig`. Environment URLs and keys come from build
  configuration or the server, never hard-coded in the UI layer.
- Keep dependencies current and from the version catalog; check them for known CVEs (Dependabot or
  `./gradlew dependencyCheckAnalyze`); no dependency from an unknown source.
- Parse untrusted data with safe APIs; avoid `Runtime.exec`, reflection on user input and dynamic code
  loading; handle every exception without leaking details to the user or logs.

#### Code obfuscation (R8)
Rules live in `app/src/main/keepRules/rules.keep` (wired in the `release` build type with
`proguard-android-optimize.txt`). Keep them in this file, not scattered across modules.
- **Always on in release:** `isMinifyEnabled = true` and `isShrinkResources = true`. Never disable them
  to work around a crash; fix the keep rule instead.
- **Rename everything by default.** Keep the global obfuscation settings: `-repackageclasses ''` (package
  names don't leak the architecture) and `-allowaccessmodification`. Don't add `-dontobfuscate`,
  `-dontoptimize` or a broad `-keep class com.sclera.sclerago.** { *; }`.
- **Keep rules are narrow and justified.** Keep only what is reached by reflection, serialization,
  JNI or a `@JavascriptInterface` (name the exact class/members, with a comment saying why). Prefer
  `-keepclassmembers` / `-keepnames` with an `allowshrinking` modifier over `-keep`. Don't add
  library-wide keeps when the library ships its own consumer rules.
- **Strip logs:** `-assumenosideeffects` removes `Log.v/d/i/w` in release. Don't rely on obfuscation
  to hide sensitive data: tokens, keys and secrets never go in code, strings or `BuildConfig`
  (obfuscation does not encrypt string literals).
- **Stack traces:** keep `SourceFile,LineNumberTable` with `-renamesourcefileattribute SourceFile`.
  Archive `build/outputs/mapping/release/mapping.txt` (and upload it to the crash tool) for every
  released `versionCode`; without it crashes can't be read.
- **Verify every release build:** run the release variant (or `./gradlew :app:assembleRelease`), sign in
  and exercise login, scans, imports and sharing. Any new reflection-based library, JavaScript
  interface, `@Serializable` model or Retrofit model needs its keep rule added and tested in the same change.
- MASTG check: decompile the release APK; class and method names are unreadable, no debug symbols,
  no `Log.d/i/w` calls left. Hardening beyond R8 (string encryption, anti-tamper) is decided under
  MASVS-RESILIENCE, not added ad hoc.

### MASVS-PRIVACY
- Collect only what a feature needs; say why in the permission rationale and the privacy policy.
  Photos and location are used only to onboard assets, with no hidden collection and no third-party SDK
  that sends data off the device without approval.
- Don't send personal data to external services (analytics, crash tools, AI helpers) without approval;
  strip identifiers first.
- **Telemetry (Firebase Crashlytics, Analytics, Performance; approved):** collection is on in **release only**
  (`telemetryEnabled` manifest placeholder), advertising id and ad personalisation are off. Code reaches it
  only through `domain/telemetry` (`AnalyticsTracker`, `CrashReporter`); never call Firebase from screens,
  ViewModels or use cases. Events are the fixed `AnalyticsEvent` list with parameters that pass
  `TelemetryParams.sanitise`; crash reports carry a short snake_case code, never a message or cause. Never
  send an email, name, user id, token, location, access code, SSID/MAC or file name, and never call
  `setUserId`. A new event or a new Firebase product is a reviewed change; update the privacy policy with it.
  `app/google-services.json` holds Firebase client identifiers (not secrets): keep the API key restricted to
  the app's package and signing certificates in the Google Cloud console.

### MASVS-RESILIENCE (decide per risk)
- For a hardened release consider root/emulator/debugger detection, certificate pinning and tamper checks
  (Play Integrity). Decide this with the team; record it, don't add ad-hoc checks.

### Security in every change
- Before coding a feature that touches sensitive data, name the data and where it is stored, sent and shown.
- Write security tests where they apply: a test that sign-out clears storage, that tokens never reach logs,
  that bad TLS is rejected, that malformed input is refused.
- Security findings are fixed before the feature is "done"; if one can't be, record it as a known gap here.

### Known security gaps (fix when touching these areas)
Fixed on `feature/security-quality-hardening`: `AuthStore` is Keystore-encrypted (AES-GCM) and old plain values
migrate on first read; `allowBackup="false"`, `networkSecurityConfig` with pinning, cleartext off; `FLAG_SECURE`
on access-code screens; the map WebView loads bundled Leaflet through `WebViewAssetLoader`; the SSO WebView only
loads allow-listed hosts; sign-out clears every repository, files and web data; imports are size-limited.
Still open:
- The login WebView changes (host allow-list, third-party cookies off) and sign-out → sign-in wipe have not been
  verified end to end on a device; do this before release.
- Property/building **access codes** and contacts live in the in-memory repositories (cleared on sign-out) and
  are shown in plain form; they must be encrypted when they are persisted or come from the API.
- Release logs: confirm `Log.d/i/w` are stripped or free of identifiers (R8 keep rules).
- `./gradlew dependencyCheckAnalyze` is configured but needs an `NVD_API_KEY`; run it and record the result.

## 9. Working agreements

- Never work on `main`. Use a short feature branch; pull `main` before starting and before pushing; resolve
  conflicts by understanding both sides.
- Small, focused commits with a message that says what changed and why.
- Don't change visible behaviour or UI when refactoring unless asked.
- Don't add libraries, permissions or services without saying so and why.
- Keep secrets, tokens and personal data out of code, logs and commits.

## Definition of done

1. Layers and packages follow section 1; no cross-layer imports.
2. Standard stack used (Hilt, Retrofit, coroutines/Flow, Compose).
3. All text localized into every locale.
4. UI verified on phone, tablet portrait and tablet landscape; previews added.
5. Reusable pieces shared, no duplication.
6. Unit + UI + integration tests written; coverage > 80%.
7. SOLID respected: abstractions injected, small single-purpose classes.
8. Security (MASVS) checked for any change touching sensitive data: encrypted at rest, HTTPS only,
   nothing sensitive in logs, minimal permissions, untrusted input validated.
9. Builds cleanly; `/audit` reports no new violations.

---
description: Audit the whole project against the rules in CLAUDE.md and report violations
---

Audit the **entire** Sclera Go project (all of `app/src/main`, `app/src/test`, `app/src/androidTest`, the
Gradle files and `res/`) against `CLAUDE.md`. Read `CLAUDE.md` first, then check every rule below across
**all** files, not just recently changed ones. Running `/audit` counts as the user asking you to run the
test suite. Report findings; do **not** change code unless the user then asks you to fix them.

Use Grep/Glob for the scans (use an Explore subagent if the project is large) and show `file:line` for each
finding. For each check give **PASS** or a list of violations with a count.

## 1. Architecture
- `domain/` imports nothing from `android.*`, `androidx.compose.*`, `retrofit2`, `okhttp3`, `…data.*` or
  `…presentation.*`. (`domain/model` must have no Compose/Android types.)
- `presentation/` does not import `…data.remote`, `…data.local`, `…data.repository`; screens and
  Composables do not call repositories or Retrofit (they go through ViewModels).
- ViewModels contain no business rules, no direct network/storage code; rules live in `domain/usecase`.
- Every repository interface in `domain/repository` has a Hilt-bound implementation in `data/repository`.
- Feature code is grouped per feature package; flag files in the wrong layer/package and files > 600 lines.

## 2. Standard stack
- No `HttpURLConnection`/`java.net.URL(...).openConnection` for API calls (media download helpers: list them).
- No manual DI (`object` service locators, hand-written factories); ViewModels are `@HiltViewModel`.
- No `Thread`, `AsyncTask`, `GlobalScope`. Dependencies are declared in `gradle/libs.versions.toml`,
  not hard-coded in `app/build.gradle.kts`.

## 3. Localization
- Find hard-coded user-visible strings in Kotlin (string literals passed to `Txt(`, `Text(`, `flash(`,
  `contentDescription`, `placeholder`, dialog text, `Toast`), excluding logs, keys, ids, test tags.
- Every key in `res/values/strings.xml` exists in all 20 locale folders with matching format arguments
  (run `LocalizationTest`, below).
- Flag `left`/`right` paddings or alignments that break RTL, and string concatenation that builds sentences.

## 4. UI: phone, tablet portrait, tablet landscape
- Every screen Composable has `@Preview`s for phone, tablet portrait and tablet landscape.
- No ad-hoc breakpoints: tablet logic uses `isTablet()`, `pageGutter()`, `sheetFrame()` from `Adaptive.kt`.
- Sheets use the shared frame; lists are one card per row; touch targets ≥ 48dp;
  icon-only buttons have content descriptions.
- Colours/spacing/type come from `presentation/theme`, not hard-coded per screen (list new hard-coded
  `Color(0x…)` outside `theme/` and `model/`).

## 5. Reuse
- Find duplicated Composables/helpers across features (same shape, different name) and constants defined
  twice; propose which should move to `presentation/components` or `domain/`.
- Dead code: unused private functions, unused imports, commented-out blocks.

## 6. Tests and coverage
- Run the unit and Robolectric UI tests: `./gradlew :app:testDebugUnitTest` and report pass/fail counts
  and any failures with their messages.
- If `src/androidTest` UI tests exist and a device/emulator is attached, say so (do not start one unasked).
- Coverage: if JaCoCo is configured, run `./gradlew jacocoTestReport` and report overall line coverage and
  every class below 80%. If it is **not** configured, say so and list which classes/packages have **no**
  test at all (use cases, ViewModels, repositories, mappers, screens), since coverage can't be above 80%
  without them.
- Check for the three kinds of tests: unit, UI (including tablet configurations) and integration.

## 7. SOLID
- **S:** classes mixing UI state, business rules and I/O; ViewModels/use cases/repositories that do more
  than one job; Composables over ~300 lines doing several things.
- **D:** ViewModels, use cases or repositories that depend on a concrete `*Impl`/`Retrofit`/`Context`-bound
  class instead of an interface; objects created with `Foo()` in business code instead of injected;
  hard-coded dispatchers/clocks that block testing.
- **I:** interfaces with methods no implementer/consumer uses; **L/O:** implementations that break their
  interface's contract (e.g. throw where the interface returns a result) and `when`/`if` chains on type
  that should be polymorphic.

## 8. Security (OWASP MASVS / MASTG)
Scan **all** code, manifest, resources and Gradle files. List each finding with `file:line`, the MASVS
category (STORAGE, CRYPTO, AUTH, NETWORK, PLATFORM, CODE, PRIVACY, RESILIENCE) and severity.
- **STORAGE:** tokens/session ids/emails/access codes/contacts written to SharedPreferences, files,
  databases or external storage without Keystore-backed encryption; sensitive files not excluded from
  backup; `allowBackup`; media stored outside app-private storage; data not cleared on sign-out.
- **Logging/leakage:** `Log.*`, `println`, `Toast`, analytics or crash calls that include tokens, cookies,
  emails, codes, locations or response bodies; `HttpLoggingInterceptor` above BASIC; sensitive screens
  without `FLAG_SECURE`; sensitive text copied to the clipboard.
- **CRYPTO:** custom crypto, MD5/SHA-1/DES/ECB, hard-coded keys/IVs/secrets, `java.util.Random` for
  security, exportable keys.
- **AUTH:** passwords handled in-app, tokens in URLs/query strings, tokens not cleared on session end,
  authorization decided only in the UI.
- **NETWORK:** `http://` URLs, `usesCleartextTraffic`, missing `networkSecurityConfig`, trust-all
  `TrustManager`, permissive `HostnameVerifier`, ignored `onReceivedSslError`, no certificate pinning.
- **PLATFORM:** exported components beyond the launcher, intent filters/deep links, `PendingIntent`
  without `FLAG_IMMUTABLE`, WebView `setJavaScriptEnabled`/`addJavascriptInterface`/file access/
  third-party cookies and which URLs they load, permissions beyond what features need, untrusted input
  (URIs, files, scan results, server JSON) used without validation.
- **CODE:** hard-coded secrets/API keys/URLs with credentials, `debuggable`, R8 off in release, debug code
  in release, dependencies with known CVEs (run a dependency check if available), `Runtime.exec`.
- **PRIVACY:** personal data sent to third parties, permissions without rationale, hidden collection.
- State the MASVS level you could verify (L1 vs L2 for auth/session) and what a manual MASTG test or
  device check would still be needed for.

## Report format
1. A one-line verdict per section (1 to 8): PASS / FAIL (n issues).
2. Findings grouped by section, each with `file:line` and the rule it breaks.
3. A prioritized fix list (highest impact first) with rough effort, and what you'd do first.
4. State plainly anything you could not check (e.g. no emulator, JaCoCo missing).

# Sclera Go: development rules

Android app (Kotlin, Jetpack Compose, `com.sclera.sclerago`). These rules apply to every change, whether
it is a new feature, a fix or a UI tweak. Check them before you start and again before you finish.
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
- **Known gap:** ~80 strings in older screens are still hard-coded. Move them to resources whenever you
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
  **Not configured yet:** add it (and a ≥80% verification rule) as part of the next test work.
- Run the test suite **only when asked** (or via `/audit`); but always write the tests.

## 7. Working agreements

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
7. Builds cleanly; `/audit` reports no new violations.

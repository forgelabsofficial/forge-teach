# Forge Teach — Project Rating & Improvement Backlog

> Last updated: July 2026  
> Overall rating: **6.8 / 10**

---

## Rating Breakdown

| Dimension | Score | Notes |
|---|---|---|
| Concept & Ambition | 9/10 | Global curriculum support, AI-grounded testing, multi-provider AI, timetable scheduling — strong for v0.1.0 |
| AI Integration | 8/10 | Provider-agnostic adapter pattern, retry with backoff/jitter, fallback chain, DuckDuckGo grounding |
| Onboarding Flow | 7.5/10 | 5-step wizard, one-question-at-a-time test, searchable subject picker, country/grade with local terminology |
| Design System | 7/10 | `ForgeColorSet` + `CompositionLocal`, dynamic light/dark, shared tokens (`GlassCard`, `forgeFieldColors`) |
| Data Layer | 6.5/10 | Room with migrations, cascading FKs, `ProgressEntity` schema — but not fully wired up |
| Security | 6/10 | API keys in `EncryptedSharedPreferences` + Android Keystore. Student data in plain DataStore is a gap |
| Feature Completeness | 4/10 | No "mark complete" UI, progress is write-only, two stub settings buttons, unreachable screen |
| Architecture | 5/10 | No DI, no ViewModel on Dashboard, plan re-generated on every visit, no onboarding completion check |
| CI / CD | 5/10 | Tests never run, no lint, Gradle wrapper regenerated on every build, no release signing |
| Bugs | 5/10 | All notifications share ID 1001, `AnthropicAdapter` ignores selected model, plans unlinked from student |

---

## 🔴 High Priority — Fix Before Users

### 1. Add "Mark Complete" to sessions
The entire progress system exists in Room (`SessionEntity.completed`, `ProgressEntity`, `ProgressDao`, `PlanRepository.addProgress`) but there is no UI for it. The dashboard currently infers completion purely from time passing, not from the student confirming a session is done.

**What to do:** Add a checkbox or swipe-to-complete gesture on each session card in `PlanScreen`. On tap, call `repo.addProgress(sessionId, status = "completed")` and update `SessionEntity.completed = true`. Update dashboard `completedCount` to query the `completed` field rather than comparing `isoDateTime` to now.

---

### 2. Fix notification IDs — all overwrite each other
`NotificationHelper.showNotification()` always uses `id = 1001`. Every scheduled session notification silently replaces the previous one. Students only ever see the last notification.

**What to do:** Pass a unique ID to `showNotification`. In `ScheduleManager`, generate the ID as `(planId * 1000 + sessionIndex)` and pass it through the `WorkManager` input data.

---

### 3. Stop re-generating the plan on every PlanScreen visit
`PlanScreen` calls `AiClient.generatePlan()` inside `LaunchedEffect(Unit)` on every composition. Every time the user taps the Plan tab it burns API credits and shows a loading spinner.

**What to do:** Check Room first — if a plan exists for the current student, load and display it. Only call `AiClient.generatePlan()` if no saved plan exists, or when the user explicitly taps "Regenerate".

---

### 4. Check onboarding completion on launch
The start destination is always `welcome`. Returning users who have already onboarded see the welcome screen every time and must tap "Get Started" again.

**What to do:** In `MainScreen`, on startup check if a `StudentProfile` exists in Room (or a saved `Assessment` in DataStore). If yes, set the start destination to `dashboard` instead of `welcome`.

---

### 5. Fix `AppContent.kt` stale colour reference
`AppContent.kt` likely still references `ForgeColors.BgBase` which no longer exists after the theme refactor. This will cause a build failure.

**What to do:** Replace with `forgeColors.bgBase` inside the composable or simply remove the explicit background colour since `ForgeBackground` handles it.

---

### 6. Persist full `StudentProfile` to Room
Country, grade, curriculum body, system ID, and key exams are built during onboarding but are only saved inside the `Assessment` JSON blob in DataStore. They are lost if that blob changes structure.

**What to do:** Add columns to `StudentProfileEntity`: `countryCode`, `countryName`, `gradeLevelId`, `gradeLevelLabel`, `curriculumBody`, `keyExams` (JSON array string). Populate them in `OnboardingViewModel.buildAssessment()` / `PlanPreviewStep` when saving the profile. Bump Room DB version + add migration.

---

## 🟡 Medium Priority — Next Sprint

### 7. Add Hilt for dependency injection
`PlanRepository` is instantiated with `remember { PlanRepository(ctx) }` directly inside composables. `SecureStorage` is called with raw `LocalContext`. These make unit testing impossible and lifecycle management manual.

**What to do:**
- Add `hilt-android` + `hilt-compiler` to `build.gradle.kts`
- Annotate `MainActivity` with `@AndroidEntryPoint`
- Create a `@Module` that provides `AppDatabase`, `PlanRepository`, `SecureStorage` as singletons
- Convert `OnboardingViewModel` and a new `DashboardViewModel` to `@HiltViewModel`

---

### 8. Add a ViewModel for DashboardScreen
Dashboard directly launches a coroutine and creates `PlanRepository` inside the composable. This state is lost on rotation and the repository is re-instantiated on every recomposition.

**What to do:** Create `DashboardViewModel` with `viewModelScope`. Move the `LaunchedEffect` data loading into `init {}` or a `loadData()` call. Expose `plan`, `studentName`, `completedCount`, `totalCount` as `StateFlow`.

---

### 9. Wire up the Settings screen
"AI Provider" and "Notifications" rows in `SettingsScreen` have `onClick = { /* future */ }` and do nothing.

**What to do:**
- "AI Provider" → navigate to a standalone `ProviderSettingsScreen` (reuse the `ProviderSetupStep` composable with a "Save" button instead of "Continue")
- "Notifications" → open the system notification settings for the app using `Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)`

---

### 10. Run tests and lint in CI
Unit tests exist (`StudentProgressTest`, MockWebServer adapter tests) but the CI workflow only runs `assembleDebug`. Every PR could silently break tests.

**What to do:** Add these steps to `android-ci.yml` before the build step:
```yaml
- name: Run unit tests
  run: cd android && ./gradlew test --no-daemon

- name: Run lint
  run: cd android && ./gradlew lint --no-daemon
```

---

### 11. Commit the Gradle wrapper jar
The CI workflow installs `gradle` via `apt-get` and runs `gradle wrapper` on every build to generate the wrapper jar. This adds 1–2 minutes to every run and is fragile (apt version may differ).

**What to do:** Run `gradle wrapper --gradle-version 8.5` locally once, commit `gradle/wrapper/gradle-wrapper.jar` to the repo, and remove the "Generate Gradle wrapper" step from the CI workflow entirely.

---

### 12. Adaptive question count per grade level
`CapabilityTestClient` scales question count by number of subjects (3 per subject, min 15, max 30) but ignores grade level. A Primary 2 student and a Form 6 student are given the same question count and difficulty range.

**What to do:** Add a `baseQuestionsForLevel(levelId: String): Int` function that maps grade categories to a question count (e.g. primary = 8, junior secondary = 12, senior secondary = 18, A-level equivalent = 24). Multiply by a subject factor and pass to `buildPrompt`.

---

### 13. Fix `AnthropicAdapter` ignoring selected model
`AnthropicAdapter.generatePlan()` hardcodes `model = "claude-3-5-sonnet-20241022"` regardless of what the user selected in the provider setup step. `CapabilityTestClient` correctly reads `SecureStorage.getApiModel()` for Anthropic — the plan adapter should do the same.

**What to do:** In `AnthropicAdapter`, read the stored model with `SecureStorage.getApiModel(context)` and use it instead of the hardcoded string, falling back to `claude-3-5-sonnet-20241022` if null.

---

## 🟢 Longer Term — Product Roadmap

### 14. Offline / on-device AI fallback
Kids in low-connectivity regions are likely the biggest target market but currently the app is fully dependent on a cloud AI provider. If there's no internet or no API key, the test falls back to 18 generic hardcoded questions.

**What to do:** Integrate Google's Gemma 2B (or Gemma 3 1B) via `com.google.ai.edge.litert` or `androidx.ai.client.generativeai` as a local model option. Show it in the provider dropdown as "On-Device (Gemma)" — no API key required. The model weights (~1.5 GB) can be downloaded once on first use via `WorkManager`.

---

### 15. Session review / flashcard mode
After a student marks a session complete, there's no reinforcement. The learning loop is open.

**What to do:** When a session is marked complete, trigger a "Quick Review" — 5 questions on that session's topic generated by `CapabilityTestClient` with a narrowed prompt (`subject = session.topic, questionCount = 5`). Store the review score in `ProgressEntity.score`. Over time this builds a per-topic mastery curve.

---

### 16. Parent / teacher dashboard
The provider setup step already has a "parent or teacher should complete this" message, implying a dual-user model. There is no actual separation between roles.

**What to do:** Add a PIN-protected "Parent Mode" accessible from Settings. In parent mode: view the child's progress across all subjects, adjust the plan, reconfigure the AI provider, and see capability test history. The child's flow remains read-only from their perspective.

---

### 17. Adaptive plan based on test results
`TimetableEngine.generateSchedule()` distributes topics in a simple round-robin pattern regardless of the capability test results. A student who scored 20% in Maths and 90% in English should get more Maths sessions, not an equal split.

**What to do:** Pass `Assessment.subjectLevels` into `TimetableEngine`. Weight session slots inversely to capability score — a level-1 subject gets 3× the sessions of a level-5 subject. This makes the plan actually respond to the diagnostic.

---

### 18. Multi-student support
`StudentProfileEntity` and the `studentId` FK on plans are already set up for this. The UI just doesn't expose it.

**What to do:** Add a student switcher to the profile screen (a horizontal chip row or a bottom sheet). When the user switches students, reload the plan and dashboard from that student's Room data. Support "Add Student" which re-runs onboarding steps 1–3 (skip provider setup, it's already configured). Useful for families sharing one device.

---

### 19. Encrypt student data in DataStore
The `Assessment` object stored in DataStore contains the student's name, country, grade, availability schedule, and capability score — all in plain JSON.

**What to do:** Replace plain `DataStore<Preferences>` with an encrypted equivalent. The simplest approach is to serialize the `Assessment` to a string, encrypt it using the same AES256-GCM key already used for `SecureStorage`, and store the ciphertext in DataStore.

---

### 20. Migrate from Gson to kotlinx.serialization
Gson has silent failure modes: it ignores unknown fields, materialises `null` for non-nullable types, and fails unpredictably on generic types like `List<T>`. The `Assessment → DataStore` round-trip is the riskiest spot.

**What to do:**
- Add `kotlin("plugin.serialization")` to `build.gradle.kts` and `kotlinx-serialization-json`
- Annotate all domain models (`Assessment`, `Plan`, `SessionItem`, `StudentProfile`, etc.) with `@Serializable`
- Replace `Gson().toJson` / `Gson().fromJson` calls in `DataStoreUtils`, `ResponseParser`, and the schema loaders
- Remove the `gson` dependency

---

### 21. Version catalogue for dependencies (`libs.versions.toml`)
`build.gradle.kts` has 25+ pinned dependency versions inline. `navigation-compose` is on 2.5.3 while Compose UI is on 1.6.0 and Material3 is on 1.1.0 (1.3.x is now stable). These mismatches accumulate silently.

**What to do:** Create `gradle/libs.versions.toml`, move all versions there, and reference them as `libs.compose.ui`, `libs.material3`, etc. in `build.gradle.kts`. Bump all Compose-family dependencies to matching versions (Compose BOM `2024.06.00` or later covers UI + Material3 + Navigation in a consistent set).

---

### 22. Promote `security-crypto` to stable
`androidx.security:security-crypto:1.1.0-alpha03` is an alpha release with known issues on Android 12+ devices related to key migration.

**What to do:** Replace with `1.0.0` (stable). If the `MasterKey` API differs slightly, the adjustment is minimal and well-documented.

---

## Summary Table

| # | Title | Priority | Effort |
|---|---|---|---|
| 1 | Mark session complete UI | 🔴 High | Small |
| 2 | Fix notification IDs | 🔴 High | Small |
| 3 | Stop re-generating plan on every visit | 🔴 High | Small |
| 4 | Check onboarding completion on launch | 🔴 High | Small |
| 5 | Fix AppContent stale colour reference | 🔴 High | Tiny |
| 6 | Persist full StudentProfile to Room | 🔴 High | Medium |
| 7 | Add Hilt DI | 🟡 Medium | Large |
| 8 | DashboardViewModel | 🟡 Medium | Small |
| 9 | Wire up Settings screen | 🟡 Medium | Small |
| 10 | Tests + lint in CI | 🟡 Medium | Small |
| 11 | Commit Gradle wrapper jar | 🟡 Medium | Tiny |
| 12 | Adaptive question count per grade | 🟡 Medium | Small |
| 13 | AnthropicAdapter reads selected model | 🟡 Medium | Tiny |
| 14 | On-device AI (Gemma) | 🟢 Roadmap | Large |
| 15 | Session review / flashcard mode | 🟢 Roadmap | Medium |
| 16 | Parent / teacher dashboard | 🟢 Roadmap | Large |
| 17 | Adaptive plan from test results | 🟢 Roadmap | Medium |
| 18 | Multi-student support | 🟢 Roadmap | Medium |
| 19 | Encrypt DataStore student data | 🟢 Roadmap | Medium |
| 20 | Migrate Gson → kotlinx.serialization | 🟢 Roadmap | Medium |
| 21 | Dependency version catalogue | 🟢 Roadmap | Small |
| 22 | Promote security-crypto to stable | 🟢 Roadmap | Tiny |

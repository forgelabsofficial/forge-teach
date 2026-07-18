# Forge Teach — Feature Roadmap

> Last updated: July 2026  
> Rating at time of writing: **6.8 / 10**

This document covers the recommended next features and improvements, ordered by impact and effort. For bug fixes and implementation gaps see `IMPLEMENTATION_STATUS.md`.

---

## Phase 1 — Foundation (Fix Before Users)

These are bugs or gaps that will make the app feel broken. All are small changes.

### 1.1 Fix notification IDs
**File:** `NotificationHelper.kt`, `ScheduleManager.kt`  
**Problem:** Every notification uses `id = 1001`. Each session reminder silently replaces the previous one. Students only ever see one notification regardless of how many sessions are scheduled.  
**Fix:** Generate a unique ID per session — e.g. `(planId * 1000 + sessionIndex)`. Pass through WorkManager input data.

### 1.2 Stop plan regenerating on every PlanScreen visit
**File:** `PlanScreen.kt`  
**Problem:** `PlanScreen` calls `AiClient.generatePlan()` in `LaunchedEffect(Unit)` on every composition. Every tab switch burns API credits and shows a loading spinner.  
**Fix:** Load saved plan from Room first. Only call `AiClient.generatePlan()` if no saved plan exists, or when user explicitly taps "Regenerate".

### 1.3 Check onboarding completion on launch
**File:** `MainScreen.kt`  
**Problem:** Start destination is always `welcome`. Returning users see the welcome screen every app open.  
**Fix:** On startup, check if a `StudentProfile` exists in Room. If yes, set start destination to `dashboard`.

### 1.4 Fix `AppContent.kt` stale colour reference
**File:** `AppContent.kt`  
**Problem:** References `ForgeColors.BgBase` which no longer exists after the theme refactor.  
**Fix:** Replace with `forgeColors.bgBase` inside the composable, or remove the explicit background since `ForgeBackground` handles it.

### 1.5 Fix `AnthropicAdapter` ignoring selected model
**File:** `AnthropicAdapter.kt`  
**Problem:** Hardcodes `claude-3-5-sonnet-20241022` regardless of what the user selected.  
**Fix:** Read `SecureStorage.getApiModel(context)` and use it, fallback to the hardcoded default if null.

### 1.6 Add "Mark Session Complete" UI
**Files:** `PlanScreen.kt`, `PlanRepository.kt`  
**Problem:** The entire progress system (Room entities, DAO, `SessionEntity.completed`) exists but there is no UI to check off a session. Dashboard completion % is inferred from time passing, not user action.  
**Fix:** Add a tap-to-complete button or swipe gesture on each session card. On confirm, call `repo.addProgress(sessionId, "completed")` and set `SessionEntity.completed = true`. Update dashboard to query the `completed` field.

---

## Phase 2 — Core Value (Next Sprint)

### 2.1 Adaptive Learning Plan from Test Results  ⭐ Highest priority feature
**Files:** `TimetableEngine.kt`, `OnboardingViewModel.kt`  
**Problem:** `TimetableEngine` round-robins all selected subjects equally. The capability test scores `subjectLevels` (1–5 per subject) but they are never fed back into the plan. A student who scored 15% in Maths and 80% in English gets the same Maths and English session split.  
**Implementation:**
- Pass `Assessment.subjectLevels` into `TimetableEngine.generateSchedule()`
- Weight session allocation inversely to capability score — level 1 subject gets 3× slots of level 5 subject
- Session duration also adapts — weak subjects get full-length sessions, strong subjects get shorter spaced reviews
- Add an optional AI-generated "Why this plan?" explanation shown in the plan preview

```kotlin
// Proposed signature change
fun generateSchedule(
    assessment: Assessment,
    weeks: Int = 4,
    startDate: LocalDate = LocalDate.now(),
    zone: ZoneId? = null,
    subjectWeights: Map<String, Float> = emptyMap()  // subjectId -> weight multiplier
): Plan
```

### 2.2 Session Review / Flashcard Mode
**Files:** New `ReviewScreen.kt`, reuse `CapabilityTestClient`  
**Problem:** After a session is marked complete there is no reinforcement. The learning loop is open.  
**Implementation:**
- When a session is marked complete, trigger "Quick Review" — 5 MCQ questions on that topic
- Reuse `CapabilityTestClient` with a narrowed prompt: `subject = session.topic, questionCount = 5, difficulty = current level`
- Store review score in `ProgressEntity.score`
- Show a streak counter on the dashboard ("3 sessions reviewed this week")

### 2.3 Check Onboarding Completion on Launch + Skip Flow
**File:** `MainScreen.kt`  
**Problem:** No resume — every app open starts from `welcome`.  
**Implementation:**
- Check `PlanRepository.getLatestStudentProfile()` on startup
- If profile exists → go to `dashboard`
- If profile exists but no plan → go to `plan` (skips assessment, generates from saved profile)
- New users → `welcome` → `onboarding`

### 2.4 Persist Full StudentProfile to Room
**Files:** `StudentProfileEntity.kt`, `AppDatabase.kt`, `OnboardingViewModel.kt`  
**Problem:** Country, grade, curriculum body, key exams are only in the `Assessment` JSON blob in DataStore. Schema changes silently corrupt it.  
**Implementation:**
- Add columns: `countryCode`, `countryName`, `gradeLevelId`, `gradeLevelLabel`, `curriculumBody`, `keyExams` (JSON string)
- Room migration v3 → v4 with `ALTER TABLE` statements
- Populate from `StudentProfile` when saving in `PlanPreviewStep`
- Display in `ProfileScreen` (country flag + grade label)

---

## Phase 3 — Architecture & Quality

### 3.1 Add Hilt Dependency Injection
**Problem:** `PlanRepository` is instantiated with `remember { PlanRepository(ctx) }` directly in composables. Makes testing hard, creates multiple instances, no singleton guarantee.  
**Implementation:**
- Add `hilt-android` + `hilt-compiler` to `build.gradle.kts`
- `@AndroidEntryPoint` on `MainActivity`
- `@Module` providing `AppDatabase`, `PlanRepository`, `SecureStorage` as `@Singleton`
- Convert `OnboardingViewModel` → `@HiltViewModel`
- New `DashboardViewModel`, `PlanViewModel` as `@HiltViewModel`

### 3.2 DashboardViewModel
**Problem:** `DashboardScreen` directly launches coroutines and creates `PlanRepository` inside the composable. State lost on rotation.  
**Implementation:** Move all data loading into `DashboardViewModel` with `viewModelScope`. Expose `uiState: StateFlow<DashboardUiState>`.

### 3.3 PlanViewModel
**Problem:** `PlanScreen` calls `AiClient.generatePlan()` and manages `editableSessions` state directly in the composable.  
**Implementation:** Move to `PlanViewModel`. Expose `planState`, `isLoading`, `canRegenerate`. This also naturally fixes the re-generation on every visit bug.

### 3.4 Run Tests + Lint in CI
**File:** `.github/workflows/android-ci.yml`  
**Problem:** Unit tests exist (MockWebServer adapter tests, repository tests) but `android-ci.yml` only runs `assembleDebug`. Tests never gate a PR.  
**Implementation:**
```yaml
- name: Run unit tests
  run: cd android && ./gradlew test --no-daemon

- name: Run lint
  run: cd android && ./gradlew lint --no-daemon
```

### 3.5 Commit Gradle Wrapper Jar
**Problem:** CI installs `gradle` via `apt-get` and regenerates the wrapper jar on every build. Adds 1–2 minutes per run and is fragile.  
**Fix:** Run `gradle wrapper --gradle-version 8.5` locally, commit `gradle/wrapper/gradle-wrapper.jar`, remove the "Generate Gradle wrapper" CI step.

### 3.6 Adaptive Question Count by Grade Level
**File:** `CapabilityTestClient.kt`  
**Problem:** Question count scales by subject count only. A Primary 2 student and a Form 6 student get the same count and difficulty range.  
**Fix:** Add `baseQuestionsForLevel(levelId: String): Int`:
- Primary / Elementary → 8 questions
- Junior Secondary / Middle → 12 questions  
- Senior Secondary / High → 20 questions
- A-Level / IB DP / Senior → 25 questions

---

## Phase 4 — Product Expansion (Roadmap)

### 4.1 On-Device AI (Gemma 2B / 3 1B)  ⭐ High strategic value
**Problem:** App requires a cloud AI provider + API key. Students in low-connectivity or low-income areas (a large portion of the target market) are effectively excluded.  
**Implementation:**
- Add `com.google.ai.edge.litert` or `androidx.ai.client.generativeai` dependency
- Add "On-Device (Gemma)" to provider dropdown — no API key required
- Model weights (~1.5 GB) downloaded once on first use via `WorkManager` (WiFi-only, background)
- Capability test, plan generation, and session reviews all run locally
- Show a "Running on device" badge in the UI

### 4.2 Parent / Teacher Dashboard
**Problem:** Provider setup acknowledges a parent/teacher role but there is no actual role separation. Parents can't monitor progress without seeing the child's full onboarding flow.  
**Implementation:**
- PIN-protected "Parent Mode" route accessible from Settings
- Parent view: per-subject progress chart, capability score history, session completion streak, next scheduled session
- Parent can reconfigure AI provider, adjust plan weeks, approve/reject plan suggestions
- Child's view remains unchanged — read-only for their own progress

### 4.3 Multi-Student Support
**Problem:** Room DB has `studentId` FK on plans. The UI just doesn't expose it.  
**Implementation:**
- Student switcher in ProfileScreen (horizontal chip row or bottom sheet)
- "Add Student" button re-runs onboarding steps 1–3 (skip provider setup)
- Dashboard and Plan screens load data scoped to the active student
- Useful for families sharing one device with multiple children

### 4.4 Encrypt Student Data in DataStore
**Problem:** `Assessment` (student name, country, grade, availability schedule, capability score) stored as plain JSON in Jetpack DataStore.  
**Implementation:**
- Serialize `Assessment` to JSON string
- Encrypt using the same AES256-GCM key from `SecureStorage`'s `MasterKey`
- Store ciphertext in DataStore instead of plaintext JSON
- Decrypt on read

### 4.5 Migrate Gson → kotlinx.serialization
**Problem:** Gson has silent failure modes — ignores unknown fields, returns `null` for non-nullable types, fails unpredictably on generics. The `Assessment → DataStore` round-trip is the most fragile.  
**Implementation:**
- Add `kotlin("plugin.serialization")` + `kotlinx-serialization-json`
- `@Serializable` on all domain models (`Assessment`, `Plan`, `SessionItem`, `StudentProfile`, etc.)
- Replace all `Gson().toJson` / `Gson().fromJson` in `DataStoreUtils`, `ResponseParser`, schema loaders
- Remove `gson` dependency

### 4.6 Version Catalogue (`libs.versions.toml`)
**Problem:** 25+ pinned versions inline in `build.gradle.kts`. `navigation-compose` is on 2.5.3 while Compose UI is 1.6.0 and Material3 is 1.1.0 (1.3.x is stable). Mismatches accumulate silently.  
**Implementation:**
- Create `gradle/libs.versions.toml`
- Move all versions there
- Use Compose BOM `2024.09.00` or later to keep Compose UI + Material3 + Navigation in sync
- Reference as `libs.compose.ui`, `libs.material3`, etc.

### 4.7 Promote `security-crypto` to Stable
**Problem:** `androidx.security:security-crypto:1.1.0-alpha03` is an alpha with known issues on Android 12+ devices.  
**Fix:** Replace with `1.0.0` (stable). Minor API adjustments if needed.

---

## Summary Table

| # | Title | Phase | Priority | Effort |
|---|---|---|---|---|
| 1.1 | Fix notification IDs | 1 | 🔴 High | Tiny |
| 1.2 | Stop plan re-generating on every visit | 1 | 🔴 High | Small |
| 1.3 | Check onboarding completion on launch | 1 | 🔴 High | Small |
| 1.4 | Fix AppContent stale colour reference | 1 | 🔴 High | Tiny |
| 1.5 | AnthropicAdapter reads selected model | 1 | 🔴 High | Tiny |
| 1.6 | Mark session complete UI | 1 | 🔴 High | Small |
| 2.1 | Adaptive plan from test results ⭐ | 2 | 🔴 High | Medium |
| 2.2 | Session review / flashcard mode | 2 | 🟡 Medium | Medium |
| 2.3 | Onboarding completion check + resume | 2 | 🔴 High | Small |
| 2.4 | Persist full StudentProfile to Room | 2 | 🟡 Medium | Medium |
| 3.1 | Hilt dependency injection | 3 | 🟡 Medium | Large |
| 3.2 | DashboardViewModel | 3 | 🟡 Medium | Small |
| 3.3 | PlanViewModel | 3 | 🟡 Medium | Small |
| 3.4 | Tests + lint in CI | 3 | 🟡 Medium | Small |
| 3.5 | Commit Gradle wrapper jar | 3 | 🟡 Medium | Tiny |
| 3.6 | Adaptive question count by grade | 3 | 🟡 Medium | Small |
| 4.1 | On-device AI (Gemma) ⭐ | 4 | 🟢 Roadmap | Large |
| 4.2 | Parent / teacher dashboard | 4 | 🟢 Roadmap | Large |
| 4.3 | Multi-student support | 4 | 🟢 Roadmap | Medium |
| 4.4 | Encrypt DataStore student data | 4 | 🟢 Roadmap | Medium |
| 4.5 | Gson → kotlinx.serialization | 4 | 🟢 Roadmap | Medium |
| 4.6 | libs.versions.toml | 4 | 🟢 Roadmap | Small |
| 4.7 | security-crypto stable | 4 | 🟢 Roadmap | Tiny |

# Placeholder & Stub Audit — Forge Teach Android

> Last updated: July 2026

Items that are stubs, mocks, partially implemented, or have known issues.

---

## Active Stubs / Incomplete

### `OnboardingSettings.kt`
- **Status:** UNREACHABLE STUB  
- **Problem:** Never registered in `NavHost`. Uses `SecureStorage.saveApiModel()` to persist a timezone — wrong storage key.  
- **Action:** Either register it as a proper settings route, or delete it and fold its fields into `ProfileScreen`.

### `SettingsScreen` (inline in `MainScreen.kt`)
- **Status:** PARTIAL — two stub buttons  
- **Problem:** "AI Provider" and "Notifications" rows have `onClick = { /* future */ }`.  
- **Action:** Wire "AI Provider" to a provider settings route. Wire "Notifications" to `Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)`.

### `NotificationHelper.showNotification()`
- **Status:** BUG — hardcoded notification ID `1001`  
- **Problem:** Every session notification overwrites the last one.  
- **Action:** Accept `notificationId: Int` parameter. Generate unique IDs in `ScheduleManager` (e.g. `planId * 1000 + sessionIndex`).

### `PlanScreen.kt` — plan regeneration on every visit
- **Status:** BUG  
- **Problem:** Calls `AiClient.generatePlan()` in `LaunchedEffect(Unit)` every composition.  
- **Action:** Load from Room first; only call AI when no plan exists or user taps "Regenerate". Move to `PlanViewModel`.

### `AppContent.kt` — stale colour reference
- **Status:** LIKELY COMPILE ERROR  
- **Problem:** May reference `ForgeColors.BgBase` which no longer exists after theme refactor.  
- **Action:** Replace with `forgeColors.bgBase` or remove.

### `AnthropicAdapter.kt` — hardcoded model
- **Status:** BUG  
- **Problem:** `model = "claude-3-5-sonnet-20241022"` ignores user-selected model from `SecureStorage`.  
- **Action:** Read `SecureStorage.getApiModel(context)`, fallback to default if null.

### `DataStoreUtils` — plain JSON storage
- **Status:** FUNCTIONAL BUT INSECURE  
- **Problem:** `Assessment` object (student name, country, grade, availability) stored as unencrypted JSON.  
- **Action:** Encrypt before write, decrypt on read. See `ROADMAP.md` item 4.4.

---

## Mocks (Intentional Fallbacks)

### `AiClientMock.kt`
- **Status:** INTENTIONAL MOCK  
- **Used when:** No API key configured, or any AI call fails.  
- **Behaviour:** Deterministic — one session per topic starting 2026-07-15, 600ms simulated delay.  
- **Keep as-is** — good offline fallback. Will be supplemented by on-device Gemma (roadmap item 4.1).

### `GenericModelLoader` fallback list
- **Status:** INTENTIONAL FALLBACK  
- **Problem:** Returns `[default, small, large]` if all model-discovery requests fail.  
- **Keep as-is** for now — provides minimal graceful degradation. Log the failure visibly in UI.

---

## Scaffold / Partial Implementations

### `ProgressDao` + `ProgressEntity`
- **Status:** IMPLEMENTED IN DB, NEVER CALLED  
- **Problem:** `PlanRepository.addProgress()` exists but no screen calls it.  
- **Action:** Wire to "Mark Complete" UI (roadmap Phase 1, item 1.6).

### `SessionEntity.completed` field
- **Status:** IMPLEMENTED IN DB, NEVER WRITTEN  
- **Problem:** Dashboard completion count uses time-based inference, not this field.  
- **Action:** Write `completed = true` when session marked done. Read it in dashboard query.

### `StudentProfileEntity` — missing curriculum fields
- **Status:** PARTIAL  
- **Problem:** Only stores `name` and `timezone`. Country, grade, curriculum body, key exams are lost after onboarding unless DataStore blob survives.  
- **Action:** Add columns, bump Room version to 4 with migration. See `ROADMAP.md` item 2.4.

### Plans not linked to students
- **Status:** BUG  
- **Problem:** `PlanScreen` calls `repo.savePlan(plan)` without a `studentId`. The `studentId` FK on `PlanEntity` is always null.  
- **Action:** Pass student ID when saving plan. Query by student ID when loading.

---

## CI Gaps

### Tests never run in CI
- **Status:** GAP  
- **Problem:** `android-ci.yml` only runs `assembleDebug`. Unit tests exist but never gate a PR.  
- **Action:** Add `./gradlew test` step before build. See `ROADMAP.md` item 3.4.

### Gradle wrapper jar missing
- **Status:** WORKAROUND IN CI  
- **Problem:** `gradle-wrapper.jar` not committed. CI regenerates it via `apt-get install gradle` on every run.  
- **Action:** Commit the jar. Remove the generation step. See `ROADMAP.md` item 3.5.

### No lint in CI
- **Status:** GAP  
- **Action:** Add `./gradlew lint` step.

---

## Dependency Concerns

| Dependency | Issue | Action |
|---|---|---|
| `security-crypto:1.1.0-alpha03` | Alpha, known Android 12+ issues | Promote to `1.0.0` stable |
| `navigation-compose:2.5.3` | Behind Compose UI 1.6.0 | Align via Compose BOM |
| `material3:1.1.0` | 1.3.x is stable | Update |
| Gson | Silent failure on generics/nulls | Migrate to `kotlinx.serialization` |
| No version catalogue | Versions drift silently | Add `libs.versions.toml` |

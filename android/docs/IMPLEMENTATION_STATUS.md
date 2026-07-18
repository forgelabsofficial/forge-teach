# Implementation Status — Forge Teach Android

> Last updated: July 2026  
> Version: 0.1.0 (commit `13972a1`)

---

## Overall Status

| Layer | Status |
|---|---|
| Project scaffold & CI | ✅ Done |
| AI client & adapters (7 providers) | ✅ Done |
| Model discovery (live API) | ✅ Done |
| Secure storage (API keys) | ✅ Done |
| Room database (plans, sessions, students, progress) | ✅ Done |
| Onboarding — 5-step wizard | ✅ Done |
| Curriculum catalogue (50+ countries) | ✅ Done |
| Subject catalogue (60+ subjects, multilingual) | ✅ Done |
| AI capability test (dynamic MCQ, curriculum-grounded) | ✅ Done |
| DuckDuckGo web search (no API key) | ✅ Done |
| Timetable engine (timezone-aware scheduling) | ✅ Done |
| Notification scheduling (WorkManager) | ✅ Done |
| Dashboard screen | ✅ Done |
| Plan screen | ✅ Done |
| Profile screen | ✅ Done |
| Design system (Forge Orange, light/dark theme) | ✅ Done |
| Mark session complete | ❌ Not started |
| Adaptive plan from test results | ❌ Not started |
| On-device AI (Gemma) | ❌ Not started |
| Parent / teacher mode | ❌ Not started |
| Multi-student support | ❌ Not started |
| Dependency injection (Hilt) | ❌ Not started |
| Tests running in CI | ❌ Blocked (wrapper jar missing) |

---

## What's Implemented — Full Detail

### 1. Project Scaffold & CI
- Gradle 8.5, Kotlin + Compose plugins, JVM target 17
- GitHub Actions workflow: JDK 17 → Android SDK → generate wrapper → `assembleDebug` → upload APK artifact (30-day retention)
- Build triggers on every push to any branch and on PRs to `main`
- `minSdk 24` (Android 7.0), `compileSdk / targetSdk 34`

### 2. AI Client & Adapters
**`AiClient`** (facade) — reads `provider` + `apiKey` from `SecureStorage`, routes to the right adapter. Falls back to `AiClientMock` if no key or on any exception.

| Adapter | Provider | Endpoint |
|---|---|---|
| `OpenAiAdapter` | OpenAI + compatible (DeepSeek, Mistral, Groq, Cohere, Azure, GitHub) | `/v1/chat/completions` |
| `AnthropicAdapter` | Anthropic Claude | Messages API |
| `GoogleAdapter` | Google Gemini | `generateContent` |

- `RetryInterceptor` — exponential backoff with 30% jitter, up to 3 retries, respects `Retry-After` header
- `AiClientMock` — deterministic fallback, 600ms simulated delay, one session per topic

### 3. Model Discovery (Live API)
**`ModelRegistry`** — 14 registered providers with base URLs and auth header schemes.  
Specialized loaders: `OpenAiModelLoader`, `AnthropicModelLoader`, `GoogleModelLoader`, `MistralModelLoader`, `GroqModelLoader`, `DeepseekModelLoader`.  
`GenericModelLoader` — tries `/v1/models`, `/models`, `/v1/engines`, etc. as fallback.  
`ModelRegistry.providerIds` and `providerDisplayNames` exposed for UI dropdown.

### 4. Secure Storage
`SecureStorage` — `EncryptedSharedPreferences` backed by Android Keystore AES256-GCM.  
Stores: `api_provider`, `api_key`, `api_model`.  
**Gap:** `Assessment` object (student name, country, grade, availability) stored as plain JSON in DataStore — not encrypted.

### 5. Room Database
Version 3 with inline migrations 1→2 and 2→3.

| Entity | Table | Key fields |
|---|---|---|
| `PlanEntity` | `plans` | `id`, `weeks`, `studentId?` |
| `SessionEntity` | `sessions` | `planId` FK, `date`, `isoDateTime?`, `topic`, `duration`, `completed`, `status` |
| `StudentProfileEntity` | `students` | `name`, `timezone?`, `createdAt`, `updatedAt` |
| `ProgressEntity` | `progress` | `sessionId` FK, `status`, `notes?`, `score?` |

`PlanRepository` — wraps all DAOs; accepts `dbOverride` for test injection.

### 6. Onboarding — 5-Step Wizard
Managed by `OnboardingViewModel` (StateFlow). Navigation via local `var step` inside `OnboardingScreen`.

| Step | Screen | Purpose |
|---|---|---|
| 0 | `ProviderSetupStep` | Select provider (dropdown), enter API key, load models from live API |
| 1 | `CountryGradeStep` | Pick country → curriculum system → grade level; shows key exams |
| 2 | `StudentProfileStep` | Name, subject picker (searchable, 60+ subjects), availability, session length |
| 3 | `CapabilityTestStep` | AI-generated MCQ test, one question at a time with slide animations |
| 4 | `PlanPreviewStep` | Generated timetable preview; Accept saves to Room + schedules notifications |

### 7. Curriculum & Subject Catalogues
**`curriculum_catalogue.json`** — 50+ countries with local grade terminology:
- Africa: Nigeria (JSS/SS), Ghana (JHS/SHS), South Africa (Grade), Kenya (Grade/CBC), Ethiopia, Egypt, Tanzania (Standard/Form), Uganda (Primary/Senior), Rwanda, Senegal (CM/Collège), Morocco
- Americas: USA (K-12), Canada, Brazil (Ano), Mexico (Grado), Argentina, Colombia
- Europe: UK (Year), France (CP/6ème), Germany (Klasse), Spain (ESO), Italy, Poland, Netherlands, Portugal, Russia
- Asia: India (CBSE + ICSE), China, Japan, South Korea, Pakistan, Bangladesh, Philippines, Indonesia, Malaysia, Singapore
- Middle East: Saudi Arabia
- Oceania: Australia, New Zealand
- International: IB (PYP/MYP/DP), Cambridge (IGCSE/A-Level)

**`subjects_catalogue.json`** — 60+ subjects in 9 categories with multilingual aliases (Chinese, Japanese, Korean, Russian, Swahili, Arabic, Hindi etc.).

### 8. AI Capability Test
**`CapabilityTestClient`**:
1. Parallel DuckDuckGo web search via `WebSearchTool` for curriculum context
2. Grounded prompt: 3 questions/subject, min 15, max 30 (targeting ~70% curriculum coverage)
3. Dispatches to configured AI provider
4. Parses JSON array, retries once with simpler prompt if < 5 questions returned
5. Falls back to 18 hardcoded questions across 6 subjects

**`WebSearchTool`** — keyless DuckDuckGo: Instant Answer API first, HTML scrape fallback. Returns ≤ 2000 chars. Never throws.

### 9. Timetable Engine
`TimetableEngine` — pure Kotlin, timezone-aware:
1. Expands availability map over the date range into candidate `ZonedDateTime` slots
2. Pass 1: one session per day, round-robin across selected topics
3. Pass 2: fills extra sessions into remaining slots if needed
4. Fallback: daily sessions at 09:00 if no availability provided

### 10. Notification Scheduling
`ScheduleManager` — `WorkManager` `OneTimeWorkRequest` per session. Delay computed from `isoDateTime` (or `date + 09:00`). Unique work name = `date + sessionIndex`.  
**Known bug:** All notifications share `id = 1001` — each overwrites the previous.

### 11. Design System
- **`ForgeBrand`** — orange constants (#F97316 primary, light/dark variants, success/error/warning)
- **`ForgeColorSet`** — semantic colours (bgBase, glassFill, textPrimary/Secondary/Muted etc.) via `CompositionLocal`
- Full **dark and light** Material3 colour schemes, both orange-accented, follows system preference
- **Shared components:** `GlassCard`, `ForgeBackground`, `GradientButton`, `GhostButton`, `SectionLabel`, `forgeFieldColors()`, `forgeOutlinedBorder()`
- Custom `AppShapes` (8dp → 28dp) and `AppTypography` (12 text styles, no hardcoded colours)

---

## Known Bugs

| # | Description | File | Severity |
|---|---|---|---|
| B1 | All notifications share `id = 1001` — each overwrites the last | `NotificationHelper.kt` | High |
| B2 | `PlanScreen` calls `AiClient.generatePlan()` on every tab visit | `PlanScreen.kt` | High |
| B3 | `AnthropicAdapter` ignores user-selected model, hardcodes `claude-3-5-sonnet` | `AnthropicAdapter.kt` | Medium |
| B4 | Plans saved without `studentId` — not linked to student in DB | `PlanScreen.kt` | Medium |
| B5 | `AppContent.kt` may reference stale `ForgeColors.BgBase` (post-refactor) | `AppContent.kt` | High |
| B6 | `OnboardingSettings.kt` unreachable (not in NavHost) and uses wrong storage key | `OnboardingSettings.kt` | Low |

---

## Remaining Work

### High Priority (before user testing)
- [ ] Fix B1–B5 above
- [ ] Add "Mark Complete" UI to session cards
- [ ] Check onboarding completion on launch — skip welcome for returning users
- [ ] Persist full `StudentProfile` (country, grade, curriculum) to Room
- [ ] Run tests in CI (`./gradlew test`) — currently only `assembleDebug` runs

### Medium Priority
- [ ] `DashboardViewModel` — move data loading out of composable
- [ ] Wire Settings screen (AI Provider → re-run provider setup; Notifications → system settings)
- [ ] Add Hilt DI — remove `remember { PlanRepository(ctx) }` from composables
- [ ] Adaptive question count by grade level in `CapabilityTestClient`
- [ ] Commit Gradle wrapper jar to repo (stop regenerating in CI)

### Roadmap
See `android/docs/ROADMAP.md` for full feature roadmap.

---

## File Index

```
android/app/src/main/
├── assets/
│   ├── assessment_questions.json     — profile form schema (v0.3)
│   ├── curriculum_catalogue.json     — 50+ country grade systems (v2.0)
│   ├── subjects_catalogue.json       — 60+ subjects with aliases (v1.0)
│   └── dashboard_welcome.png
├── java/com/aiteacher/
│   ├── MainActivity.kt
│   ├── ai/
│   │   ├── AiClient.kt               — provider facade
│   │   ├── OpenAiAdapter.kt / Api.kt / Models.kt
│   │   ├── AnthropicAdapter.kt / Api.kt
│   │   ├── GoogleAdapter.kt / Api.kt
│   │   ├── CapabilityTestClient.kt   — AI MCQ test generator
│   │   ├── WebSearchTool.kt          — DuckDuckGo, no API key
│   │   ├── ModelRegistry.kt          — 14 providers
│   │   ├── *ModelLoader.kt           — per-provider + generic
│   │   ├── ResponseParser.kt
│   │   ├── RetryInterceptor.kt / Backoff.kt
│   │   └── AiClientMock.kt           — deterministic fallback (used by onboarding)
│   ├── data/
│   │   ├── AppDatabase.kt            — Room v3
│   │   ├── PlanEntity.kt / PlanDao.kt
│   │   ├── SessionEntity.kt
│   │   ├── StudentProfileEntity.kt / StudentDao.kt
│   │   ├── ProgressEntity.kt / ProgressDao.kt
│   │   └── PlanRepository.kt
│   ├── onboarding/
│   │   ├── OnboardingScreen.kt       — 5-step wizard
│   │   ├── OnboardingViewModel.kt
│   │   ├── AssessmentModels.kt       — domain models
│   │   ├── AssessmentSchema.kt       — JSON loaders
│   │   ├── DynamicForm.kt            — schema-driven form renderer
│   │   ├── TimetableEngine.kt        — timezone-aware scheduler
│   │   └── AiClientMock.kt
│   ├── security/
│   │   └── SecureStorage.kt
│   ├── ui/
│   │   ├── AppContent.kt
│   │   ├── MainScreen.kt             — NavHost + bottom nav
│   │   ├── DashboardScreen.kt
│   │   ├── PlanScreen.kt
│   │   ├── ProfileScreen.kt
│   │   ├── Theme.kt                  — ForgeBrand + ForgeColorSet + M3 schemes
│   │   ├── DesignSystem.kt           — shared components
│   │   ├── DataStoreUtils.kt
│   │   └── OnboardingSettings.kt     — ⚠ unreachable, needs wiring
│   └── work/
│       ├── ScheduleManager.kt
│       ├── NotificationWorker.kt
│       └── NotificationHelper.kt
```

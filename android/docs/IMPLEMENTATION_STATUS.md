Implementation Status — AI Teacher Android
Recorded: 2026-07-13T17:27:51+01:00

SUMMARY
This document records what has been implemented so far (code changes, tests, scaffolds) and what remains from the MVP plan. Use this as a single-source note inside the repository.

WHAT WE'VE IMPLEMENTED (DETAILED)

1) Project & Scaffold
- Gradle wrapper scripts + gradle-wrapper.properties added (gradle-wrapper.jar left to CI).
- Android Compose skeleton & Material3 Theme.
- Key files: android/build.gradle.kts, android/app/build.gradle.kts, android/gradle/wrapper/gradle-wrapper.properties

2) Onboarding UI & ViewModel
- Compose OnboardingScreen with provider + API key input, model loader and profile fields.
- OnboardingViewModel persists provider/model via SecureStorage and can call ModelRegistry to load models.
- Files: android/app/src/main/java/com/aiteacher/onboarding/OnboardingScreen.kt, OnboardingViewModel.kt

3) AI Client & Adapters
- AiClient selects provider by SecureStorage.getApiProvider(context).
- OpenAI adapter finalized to use Chat Completions-like flow: sends messages to /v1/chat/completions, extracts assistant content JSON and maps to Plan + SessionItem.isoDateTime.
- Anthropic & Google adapters scaffolded and map to Plan when provider returns expected JSON.
- Adapters accept baseUrl override for testing.
- Files: android/app/src/main/java/com/aiteacher/ai/AiClient.kt, OpenAiAdapter.kt, AnthropicAdapter.kt, GoogleAdapter.kt, OpenAiApi.kt, OpenAiModels.kt

4) Model discovery & provider registry
- GenericModelLoader (best-effort) plus provider-specific loaders for OpenAI, Anthropic, Google, Mistral, Groq, Deepseek.
- ModelRegistry maps provider id -> baseUrl + auth header and exposes provider-specific loaders and getExtendedLoader.
- Files: android/app/src/main/java/com/aiteacher/ai/ModelLoader.kt, ModelRegistry.kt, OpenAiModelLoader.kt, AnthropicModelLoader.kt, GoogleModelLoader.kt, MistralModelLoader.kt, GroqModelLoader.kt, DeepseekModelLoader.kt

5) Secure storage
- EncryptedSharedPreferences wrapper for provider, apiKey, selected model (SecureStorage).
- File: android/app/src/main/java/com/aiteacher/security/SecureStorage.kt

6) Plan persistence & DB
- Room entities: PlanEntity, SessionEntity (now includes isoDateTime), PlanDao, AppDatabase (version bumped to 2) and PlanRepository with test-friendly injection.
- DB fallbackToDestructiveMigration enabled for prototype.
- Files: android/app/src/main/java/com/aiteacher/data/*

7) Scheduling & Notifications
- ScheduleManager computes Instants from ISO datetimes or date-only and schedules WorkManager OneTimeWorkRequests with calculated initialDelay.
- NotificationWorker + NotificationHelper implemented.
- Files: android/app/src/main/java/com/aiteacher/work/*

8) Plan UI
- PlanScreen displays generated plan, allows editing isoDateTime per session before accepting, persists plan via PlanRepository and schedules notifications.
- Files: android/app/src/main/java/com/aiteacher/ui/PlanScreen.kt

9) Tests & CI
- Unit tests added: PlanRepositoryTest, ScheduleManagerTest, OpenAiAdapterTest, AnthropicAdapterTest, GoogleAdapterTest, ModelLoaderTest.
- MockWebServer added to test dependencies.
- GitHub Actions CI workflow (.github/workflows/android-ci.yml) to bootstrap Gradle wrapper and run unit tests.

10) Networking resiliency
- RetryInterceptor for OkHttp with exponential backoff wired into provider Retrofit clients.

11) Documentation
- android/docs/PLACEHOLDERS.md updated to reflect scaffolds/partials.

REMAINING WORK (DETAILED)

High priority (MVP-critical)
- assessment-questions: Draft full question set, JSON schema, scoring rules, and integration into Onboarding (Question-driven flow). (todo: assessment-questions)
- data-model: Expand Room schema for student profile, progress, session status, timezone preferences; add explicit Room migrations (v1->v2 targeted migration). (todo: data-model)
- onboarding-flow: Wire dynamic question sets, save-draft, accessibility, and UX polish; connect to assessment questions + scoring. (todo: onboarding-flow)
- timetable-engine: Implement conversion logic that respects availability, session duration, spacing rules, and adjusts to user timezone & preferences. (todo: timetable-engine)
- persistence wiring: finalize PlanRepository edge cases, background syncing (if any), and tests for repository. (todo: persistence)

Medium priority
- Finalize provider adapters:
  - Replace best-effort mapping with strict request/response models for each provider (OpenAI, Anthropic, Google, Mistral, Groq, Deepseek, Cohere, HuggingFace, etc.).
  - Add per-provider telemetry, rate-limit handling, and robust retries/backoff policies.
- Expand ModelLoader implementations to handle provider quirks (pagination, POST endpoints, project-scoped endpoints like Google/Azure).
- Add MockWebServer tests covering error conditions (timeouts, 429/5xx, malformed JSON) for every adapter.

Low priority / future
- Sync backend: optional remote sync, account support
- Teacher dashboard & multi-user features
- Analytics, export, report generation

FILES ADDED OR MODIFIED (KEY)
- android/app/src/main/java/com/aiteacher/ai/: AiClient, OpenAiApi.kt, OpenAiAdapter.kt, OpenAiModels.kt, AnthropicApi/Adapter, GoogleApi/Adapter, ModelLoader.*
- android/app/src/main/java/com/aiteacher/onboarding/: OnboardingScreen.kt, OnboardingViewModel.kt, AssessmentModels.kt, AiClientMock.kt
- android/app/src/main/java/com/aiteacher/data/: PlanEntity.kt, SessionEntity.kt (isoDateTime), PlanDao.kt, AppDatabase.kt (version=2), PlanRepository.kt, SessionEntity.kt
- android/app/src/main/java/com/aiteacher/work/: ScheduleManager.kt (ISO parsing), NotificationWorker.kt, NotificationHelper.kt
- android/app/src/main/java/com/aiteacher/ui/: PlanScreen.kt, OnboardingSettings.kt
- android/app/src/main/java/com/aiteacher/security/: SecureStorage.kt
- android/app/src/test/java/com/aiteacher/: PlanRepositoryTest.kt, ScheduleManagerTest.kt, OpenAiAdapterTest.kt, AnthropicAdapterTest.kt, GoogleAdapterTest.kt, ModelLoaderTest.kt
- android/app/build.gradle.kts: added MockWebServer test dependency
- .github/workflows/android-ci.yml: CI to run tests and bootstrap Gradle
- android/docs/PLACEHOLDERS.md, android/docs/IMPLEMENTATION_STATUS.md (this file)

TODO STATUS (summary)
- Done: ai-client, notifications, tests, provider-adapters (marked in session DB)
- In progress: design-ui
- Pending: ai-integration, assessment-questions, data-model, onboarding-flow, timetable-engine, persistence, docs, sync-backend

NEXT RECOMMENDED STEPS
1) Draft the assessment question JSON schema and scoring rules so Onboarding can be driven from JSON (start: assessment-questions).  
2) Implement timetable-engine that converts Plan + availability into scheduled sessions using timezone-aware logic and spacing rules.  
3) Replace destructive Room migration with explicit Migration implementations for v1->v2.  
4) Harden adapters with strict models and MockWebServer error-case tests.

If you'd like, continue now with: 1) assessment-questions JSON + integration into Onboarding, or 2) timetable-engine implementation. Indicate choice and I will proceed and persist more artifacts to the repo.

Placeholder audit — AI Teacher Android

Purpose
Record of placeholder or scaffold implementations that require replacement with production-grade dynamic implementations.

Files and status

- com.aiteacher.onboarding.AiClientMock.kt
  - Status: MOCK
  - Notes: Local deterministic mock used when no API key present. Replace with provider adapters (OpenAI/Anthropic/Google) and robust parsing.

- com.aiteacher.ai.OpenAiAdapter.kt, OpenAiApi.kt
  - Status: SCAFFOLD -> PARTIAL IMPLEMENTATION
  - Notes: Retrofit interface and adapter implemented with Authorization interceptor. Adapter now attempts to map a provider-style "plan" object to the internal Plan model and falls back to AiClientMock on errors. Still needs robust schema mapping, retries, telemetry, and provider selection.

- com.aiteacher.ai.AiClient.kt
  - Status: ADAPTER (partial: provider selection implemented)
  - Notes: Now selects provider adapter by SecureStorage provider setting (openai / anthropic / google). Falls back to mock on error. Needs more robust selection logic, per-provider config, and telemetry aggregation.

- com.aiteacher.onboarding.OnboardingScreen.kt
  - Status: PARTIAL UI
  - Notes: Compose UI implemented with Material3 components for prototype. Needs polish, accessibility checks, and support for dynamic question sets (JSON-driven). Save-draft functionality is stubbed.

- com.aiteacher.ui.PlanScreen.kt
  - Status: WORKING PROTOTYPE
  - Notes: Calls AiClient for plan, persists plan to Room and schedules demo notifications. Scheduling uses short delays for demo — replace with real date/time parsing and WorkManager scheduling.

- com.aiteacher.security.SecureStorage.kt
  - Status: IMPLEMENTED (EncryptedSharedPreferences)
  - Notes: Prototype secure storage for API keys. Ensure Keystore and device compatibility testing.

- com.aiteacher.data.* (Room)
  - Status: IMPLEMENTED
  - Notes: Entities, DAO, DB, and repository implemented. Add migration strategy and more fields as needed (timezone, session status, external IDs).

- com.aiteacher.work.ScheduleManager.kt & NotificationWorker.kt
  - Status: UPDATED (uses real date parsing)
  - Notes: computeScheduledInstant(sessionDate) added and WorkManager scheduling now calculates initialDelay in milliseconds based on system timezone. Still needs: user-preferred session time, timezone edge-case tests, exact-alarm policy decisions and Doze behavior validation.

- android/gradle wrapper
  - Status: PARTIAL
  - Notes: Wrapper scripts and properties added. gradle-wrapper.jar missing — CI must bootstrap or generate the wrapper.

Testing & safety gaps

- No unit/integration tests present for PlanRepository, ScheduleManager, or AiClient adapters.
- AI provider adapters lack request signing and secure transport handling in production.
- Persisted API keys stored via EncryptedSharedPreferences — audit required for compliance.

Recommended next work (priority)
1. Implement full OpenAI/Anthropic adapters with Retrofit and map responses to Plan model. Add feature-flagged mocking for dev.
2. Replace ScheduleManager demo delays with real schedule computation and ensure WorkManager constraints and alarms.
3. Add unit tests for PlanRepository, ScheduleManager, and adaptiveModel logic. Add CI job to run tests.
4. Expand OnboardingScreen to load question sets from JSON assets and implement Save Draft.
5. Audit SecureStorage and data handling for privacy and compliance.

Providers now scaffolded/partially implemented:
- openai
- anthropic
- google
- mistral
- deepseek
- groq
- github
- cohere
- huggingface
- alephalpha
- replit
- azure
- baidu
- stability

Model auto-loading
- Onboarding loads models from provider using a GenericModelLoader that tries common endpoints and parses common JSON shapes. SecureStorage now persists provider, api key and selected model.

Caveats
- Some providers require special endpoints or pagination; provider-specific loaders implemented for openai, anthropic, and google. GenericModelLoader remains for others but provider-specific loaders are recommended for reliability.

Testing
- Added MockWebServer-based unit test for OpenAiAdapter, AnthropicAdapter, GoogleAdapter (android/app/src/test/.../*.kt). Add more tests for additional adapters.

CI
- Added .github/workflows/android-ci.yml to bootstrap Gradle wrapper and run unit tests on push/PR.

Recorded at: 2026-07-13T17:08:00Z

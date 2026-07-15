# Forge Teach

> AI-powered study companion for school children — calibrated to your curriculum, your grade, your pace.

---

## What it does

Forge Teach is an Android app that:

1. **Onboards the student** through a 5-step wizard — selects country, curriculum, grade level, and subjects
2. **Tests their knowledge** using an AI-generated capability test grounded with live curriculum data from the web
3. **Builds a personalised timetable** based on their availability and (soon) their test results
4. **Schedules reminders** for each study session via WorkManager notifications
5. **Tracks progress** on the dashboard — completed sessions, upcoming sessions, study hours

---

## Supported Curricula

50+ countries with local grade terminology — Nigeria (JSS/SS), UK (Year), France (6ème), Germany (Klasse), India (CBSE + ICSE), China (年级), Japan (年), Kenya (CBC), USA (K-12), Brazil (Ano), and more. Also supports IB (PYP/MYP/DP) and Cambridge International.

---

## AI Providers

Supports 14 providers via a provider dropdown — all with live model loading from the API:

OpenAI · Anthropic · Google Gemini · Mistral · Groq · DeepSeek · Cohere · Hugging Face · GitHub Models · Azure OpenAI · Aleph Alpha · Replit · Baidu ERNIE · Stability AI

No API key? The app falls back to built-in questions and a local mock plan generator.

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Android SDK (API 34)

### Build locally
```bash
cd android
# Generate Gradle wrapper (first time only — wrapper jar not yet committed)
gradle wrapper --gradle-version 8.5
./gradlew assembleDebug
```

The debug APK is output to `android/app/build/outputs/apk/debug/app-debug.apk`.

### CI Build
Every push triggers a GitHub Actions build. Download the APK from the **Actions** tab → latest workflow run → **Artifacts** section → `app-debug-<sha>`.

---

## Project Structure

```
forge-teach/
├── android/
│   ├── app/src/main/
│   │   ├── assets/            — curriculum_catalogue.json, subjects_catalogue.json, assessment_questions.json
│   │   └── java/com/aiteacher/
│   │       ├── ai/            — adapters, model loaders, capability test, web search
│   │       ├── data/          — Room DB (plans, sessions, students, progress)
│   │       ├── onboarding/    — 5-step wizard, view model, timetable engine
│   │       ├── security/      — EncryptedSharedPreferences API key storage
│   │       ├── ui/            — screens, theme (Forge Orange), design system
│   │       └── work/          — WorkManager notification scheduling
│   └── docs/
│       ├── IMPLEMENTATION_STATUS.md   — what's built, known bugs, file index
│       ├── ROADMAP.md                 — next features, phases 1–4
│       └── PLACEHOLDERS.md           — stubs, mocks, CI gaps, dependency concerns
├── IMPROVEMENTS.md            — project rating (6.8/10) and improvement backlog
└── README.md                  — this file
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| Navigation | Navigation Compose |
| State | ViewModel + StateFlow |
| Database | Room 2.6.1 |
| Networking | Retrofit 2.9.0 + OkHttp 4.11.0 |
| Background | WorkManager 2.8.1 |
| Storage | DataStore Preferences + EncryptedSharedPreferences |
| Build | Gradle 8.5, Kotlin 1.9, Compose Compiler 1.5.3 |
| CI | GitHub Actions → debug APK artifact |

---

## Documentation

| File | Contents |
|---|---|
| `android/docs/IMPLEMENTATION_STATUS.md` | Full feature status, known bugs, file index |
| `android/docs/ROADMAP.md` | Next features — Phase 1 (bugs), Phase 2 (core value), Phase 3 (architecture), Phase 4 (product expansion) |
| `android/docs/PLACEHOLDERS.md` | Stubs, mocks, incomplete implementations, CI gaps |
| `IMPROVEMENTS.md` | Project rating breakdown and improvement suggestions |

---

## Current Version

**v0.1.0** — Debug build only. Not production-ready.  
See `android/docs/ROADMAP.md` Phase 1 for the fixes needed before user testing.

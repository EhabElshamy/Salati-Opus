# Salati — صلاتي

Offline Android app for tracking the five obligatory prayers and managing qadaa. Arabic/English (RTL), Sharkia/Egypt defaults, Egyptian General Authority of Survey method, Shafi‘i Asr. No account, ads, analytics, server or internet permission.

## Build status — read first

This is a **source project**. It was generated without Java, Kotlin, Gradle or the Android SDK available, so it has **not been compiled, no APK was produced, and the unit tests have not been run**. Expect possibly a few small compile fixes on first sync.

Stack: AGP 8.7.3 · Gradle 8.11.1 · JDK 17 · Kotlin 2.1.0 (Compose compiler plugin) · KSP 2.1.0-1.0.29 · Compose BOM 2024.12.01 / Material 3 · Room 2.6.1 · kotlinx-serialization 1.7.3 · coroutines 1.9.0. minSdk 26, compile/target SDK 35. Prayer times are computed by the app's own Kotlin implementation (no third-party library).

## Option A — build in the cloud (GitHub Actions)

1. Create a **private** repository on GitHub and upload the contents of this ZIP (including the hidden `.github` folder).
2. The workflow `.github/workflows/android.yml` runs automatically: unit tests, then `assembleDebug`.
3. Open *Actions → latest run → Artifacts* and download `salati-debug-apk` (and `test-reports`).

## Option B — Android Studio

1. Install Android Studio (Ladybug 2024.2 or newer) with JDK 17 and Android SDK 35.
2. The Gradle wrapper JAR is not included. Either run `./bootstrap.sh` (macOS/Linux) or `bootstrap.ps1` (Windows) once, or open the folder in Android Studio and accept its offer to use/create a Gradle wrapper (8.11.1).
3. *File → Open* → select the `Salati` folder → wait for Gradle sync.
4. Run tests: `./gradlew testDebugUnitTest`. Build APK: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

## Install the APK on your phone

1. Copy `app-debug.apk` to the phone (USB, Drive, etc.).
2. Open it; allow *Install unknown apps* for the file manager/browser when asked.
3. Or with USB debugging: `adb install -r app-debug.apk`.

## Project layout

- `domain/` — pure Kotlin: prayer-time calculation, qadaa ledger rules (`QadaaMath`, `QadaaPlanner`, `QadaaService`), auto-missed policy, periods, completion estimates, statistics, backup validation.
- `data/` — Room database and repository (implements `LedgerStore`; all balance changes in one transaction).
- `ui/` — Compose screens: Setup, Today, Qadaa, Calendar (Prayers / Qadaa / Old fashion), Statistics, Settings.
- `notify/` — AlarmManager reminders, rescheduled on boot, update, time and time-zone changes.
- `docs/paper-tracker-reference.png` — the paper tracker used as the reference for the Old fashion view.

## Key behaviour

- **Initial estimate:** days = round-half-up(years × 365) per prayer (365-day approximation, stated in the UI); each prayer can be adjusted. It is a planning estimate, not a religious ruling.
- **Ledger:** every change is an immutable entry (estimate, adjustment, confirmed missed, make-up, correction, undo). Undo adds a reversing entry; nothing is deleted. Balances never go below zero.
- **Missed prayers:** counted exactly once. Changing a missed prayer to prayed removes its debt; if make-ups (counted oldest-first) already covered it, you choose explicitly whether to reduce the balance or keep it.
- **Auto-missed:** when the next prayer begins with no record, the prayer is labelled missed but waits for your confirmation before entering qadaa (configurable). Unrecorded and auto-labelled prayers are shown separately in statistics.
- **Completion estimates:** by daily goal, and by recorded pace (last 30 days, needs at least 5 active days); otherwise “not enough data”.
- **Backup:** JSON export/import through the system file picker; the file is fully validated and a summary shown before it replaces data. Android cloud backup is disabled.
- **Location:** Zagazig (30.5877, 31.5020) pre-fills as a Sharkia reference point only; setup requires you to confirm coordinates (GPS or manual).

## Tests included, not executed here

Unit tests live in `app/src/test/java/com/salati/app/` and run on the JVM (no device needed):

| File | Covers |
|---|---|
| `QadaaMathTest` | 365-day estimate, half-up rounding of fractional years, Arabic/Western digit parsing, invalid input, five prayers only (no Witr) |
| `QadaaServiceTest` | duplicate prevention, missed→prayed reversal, past-date edits, oldest-debt-first make-ups, explicit decision when a missed prayer was already made up, no negative balances, undo / batch undo, auto-missed confirmation, congregation rules, estimate changes with clamping and no deletion |
| `DomainTest` | prayer times (Cairo, Egyptian method, ±3 min), DST from zone rules, Hanafi vs Shafi‘i Asr, minute offsets, auto-missed policy, Saturday weeks, quarter/half-year periods, 147-day paper sheet, completion estimates, backup validation (bad JSON, wrong app/version, duplicates, double counting, broken references, negative balances), statistics |

Run them with:

```bash
./gradlew testDebugUnitTest
```

**Honest status:** this project was generated in an environment without Java, Kotlin, Gradle or the Android SDK. The code was reviewed by hand against the domain APIs, and the prayer-time algorithm was cross-checked with an independent Python port (Cairo, 1 Jan 2024: Fajr 05:18, Dhuhr 11:58, Asr 14:47, Maghrib 17:06, Isha 18:29). However, **no Gradle build, APK or unit test has been run.** Expect to fix small compile issues the first time you sync in Android Studio.

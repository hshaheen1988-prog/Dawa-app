# Dawa (دوائي) — guide for agents working on this repo

Medication-reminder Android app for families: exact alarms, repeating dose patterns, INR tracking for warfarin,
interaction warnings (warfarin / acetazolamide), Ramadan mode, widget, PDF report, optional family sharing.
Arabic (default, RTL) + English. Offline-first. Free, no ads, no analytics.

Read `CHANGELOG.md` first — it is the project log and lists what is on this branch vs `main`.

## Repository layout (flat — on purpose)
The owner has no local dev tools and edits by uploading files in the GitHub web UI, so every source file sits in
the repo root. `.github/workflows/build.yml` arranges them into an Android project at build time:

| Root file(s) | Goes to |
|---|---|
| `build.gradle` (contains `apply false`) | `android/build.gradle` |
| `app.build.gradle` | `android/app/build.gradle` |
| `settings.gradle`, `gradle.properties` | `android/` |
| `AndroidManifest.xml`, `*.java` | `android/app/src/main/…/com/hamza/dawa/` |
| `ic_launcher.xml` | `res/mipmap-anydpi-v26/` |
| `ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `ic_stat.xml`, `widget_bg.xml`, `widget_btn.xml` | `res/drawable/` |
| `strings.xml`, `styles.xml` | `res/values/` |
| `widget_dose.xml` → `res/layout/`, `widget_info.xml` → `res/xml/` | |
| `index.html`, `sw.js`, `manifest.json`, `icon.svg` | `assets/www/` |

A new resource file needs a line in the workflow's copy step **and** in `tools/local-build/build_apk.sh`.
Other folders: `supabase/` (SQL), `tests/` (Playwright + Postgres), `tools/local-build/` (no-Gradle build).
Do not restructure into a normal Gradle tree without the owner's OK — it breaks his upload workflow.

## Architecture
**Web app — `index.html` (single file, ~245 KB, vanilla JS)**
- State in `localStorage['dawa.v1']` (`S`), loaded through `migrate()` — keep old data readable when adding fields.
- i18n: `T.ar` / `T.en`; every new string needs both. Some entries are functions (`t('x')(arg)`).
- Schedule: `slotsFor(pid, date)` (modes daily / interval / weekdays / pattern / prn, stages, Ramadan via `medTimes`).
- Native sync: `syncNative()` → `DawaNative.schedule(buildAlarms())`, `setFamily`, `setWidget(widgetData())`.
- Family: `rpc()`, `famSync()` (every 45 s, foreground only), `famPush()` (hash-guarded), `famPullFollow()`, `famAlarms()`.
- Hooks the native side calls: `nativeResume`, `nativePause`, `nativeMark`, `nativeBack`, `nativeVoiceResult`.

**Native (Java, no androidx; minSdk 26, target/compile 34; `com.hamza.dawa`)**
- `MainActivity` — WebView on `file:///android_asset/www/index.html`, file chooser, lifecycle hooks.
- `Bridge` (`window.DawaNative`, version 4) — isNative, version, schedule, setFamily, setWidget, testVoice, printHtml,
  takePendingMarks, notifyNow, testAlarm, status, notificationsAllowed, exactAlarmsAllowed, open*Settings, saveFile, share.
- `Alarms` (AlarmManager.setAlarmClock, channels `dawa_alarm_v2` + silent `dawa_alarm_voice_v1`, pending marks),
  `AlarmReceiver`, `AlarmService` (foreground mediaPlayback: tone + TTS loop, 5-min cap), `AlarmActivity`
  (full-screen over lock screen), `BootReceiver`, `DoseWidget` (RemoteViews, 1–3 rows by height),
  `Family` (background server calls + outbox).

**Server — Supabase** (URL + anon key are in `index.html`; the anon key is public by design)
- Tables are private (RLS on, privileges revoked). Access only through `SECURITY DEFINER` functions `fam_*`,
  authenticated by device id + secret; sharing uses a 6-character code.
- `supabase/family.sql` = base, `supabase/family-update-7.sql` = latest. The owner runs SQL by hand in the
  Supabase SQL editor. New server changes → a new `supabase/family-update-N.sql` that keeps existing function
  signatures (older app versions must keep working).

## Signing & releases
- Release key only in GitHub Secrets: `DAWA_KEYSTORE_B64` (base64 of the .jks), `DAWA_KEY_PASS` (store + key
  password, alias `dawa`). Fingerprint SHA-256 `27:A5:6E:…:9F:88:2F` (full value in CHANGELOG).
  **Never commit a keystore or password.** The old `dawa.jks` in git history is compromised — never use it.
- Push to `main` → build → GitHub Release `v1.0.<run_number>`; public link
  `https://github.com/hshaheen1988-prog/Dawa-app/releases/latest/download/Dawa.apk`.
  Other branches: Actions → *Build APK* → *Run workflow* → pick the branch → APK as artifact, no release.
- versionCode = run_number. 1.0.10 was built locally with versionCode 10; any locally built APK must use a
  versionCode ≥ what users have and ≤ the next CI run_number, or updates get blocked.
- If Gradle/Google servers are unreachable: `tools/local-build/build_apk.sh` (see header; needs the key via env).

## Tests (run before every commit)
```
pip install playwright && playwright install chromium   # or use a preinstalled Chromium
python3 tests/test_v2.py      # onboarding, patterns, alarms payload, report share, native bridge stub
python3 tests/test_f.py       # INR (+ day-before reminder), widget payload, voice text, Ramadan
python3 tests/test_ix.py      # interaction search
python3 tests/test_r.py       # PDF report HTML
python3 tests/test_backup.py  # save/share/paste/file restore
sudo bash tests/setup_pg.sh && python3 tests/test_fam.py   # two-phone family flow on local Postgres + mock PostgREST
```
Each prints `ERRORS: []` when clean. Screenshots land in `tests/shots/` (git-ignored).

## Rules
- Keep it offline-first; the network is only for family sharing.
- No new dependencies (no androidx, no JS frameworks) without a strong reason.
- Arabic + English for every string; check RTL and dark mode.
- Medical content: the interaction list is not exhaustive — keep the disclaimers.
- Don't merge to `main` or run SQL on the live project without the owner's OK.
- The owner communicates in Arabic (Jordanian) and prefers short, numbered, step-by-step instructions.

## Open items
1. Owner: add the two Secrets, then merge `develop` → `main` (until then the public link serves 1.0.9, old key).
2. Owner: run `supabase/family-update-7.sql`.
3. Not yet confirmed on a real phone: 1.0.10 widget layout, save-to-Downloads backup, pause/resume sync.
4. Family sharing not yet tested on two real phones.
5. Later: Google Play listing (Android developer verification reaches more countries from 2027), privacy
   policy URL = `PRIVACY.md` on `main`.

# Dawa (دوائي) — project log

Newest first. Version = `1.0.<GitHub run_number>`; versionCode = run_number.

## 1.0.10 — branch `develop` (2026-09-25) — updates 6 + 7
Built locally (tools/local-build) and handed to the owner as an APK; **not yet on `main`**, so the public
download link still serves 1.0.9.

**Security**
- New release signing key. Stored only in GitHub Secrets (`DAWA_KEYSTORE_B64`, `DAWA_KEY_PASS`).
  Certificate SHA-256 `27:A5:6E:19:82:85:5E:B0:14:2A:99:3E:73:B8:4A:1A:DB:96:A7:FB:DA:43:9E:DC:DE:BA:B8:A4:E6:9F:88:2F`.
- The old `dawa.jks` (password was in `build (1).gradle`) was public → treated as compromised and removed.
  Because the key changed, existing installs must: export backup → uninstall → install → restore (one time).
- `app.build.gradle` reads the key from env; the workflow decodes it from Secrets and fails clearly if missing.
- Workflow publishes a Release only from `main`; other branches: Actions → Run workflow → artifact only.

**INR (update 6)**
- Reminder the day before the test (default 20:00, can be turned off) + test-day reminder (default 09:00), both editable in the INR sheet (`inrRem` / `setInrRem`, alarm ids `inrp|…` and `inr|…`).

**Widget (update 6)** — `widget_dose.xml`, `DoseWidget.java`, `widget_info.xml` (4×3), `widget_bg.xml`
- Header with done/total + progress bar; note line (INR today/tomorrow, followed people); up to 3 rows,
  each with its own ✓ (marks without opening the app); late doses in yellow; "+N more" or tomorrow's first dose;
  rows adapt to widget height (`rowsFor`, `onAppWidgetOptionsChanged`).

**Backup (update 7)**
- Export: save JSON to Downloads/Dawa (`Bridge.saveFile`, Android 10+) or share. Import: pick a file or paste text;
  "have a backup?" link on onboarding; confirm before replacing data (`openImport`, `importText`).

**Family sharing load (update 7)** — needs `supabase/family-update-7.sql`
- No syncing while the app is in the background (`nativePause` from `MainActivity.onPause`).
- Patient pushes only when the snapshot/marks hash changes (or every 6 h).
- `fam_pull` returns the schedule only when it changed (2-min overlap) → follower sync 3.7 KB → 0.18 KB.
- Limits: 30 registrations/min, 10 followers per patient, 300 KB per upload; devices unused for 180 days are deleted.
- `invalid device` → client re-registers; clear messages for "too many followers" / "busy".

**Other**
- `PRIVACY.md` (AR/EN) + link in Settings; web app VERSION 2.1; Bridge version 4.

## 1.0.9 — update 5
- Doctor report as PDF (`PrintManager`), option "include lab tests" (last 5 INR readings).
- Family sharing (Supabase RPC `fam_*`, 6-char share code): patient picks which medicines to share; follower
  sees them, per-medicine reminder / missed-dose alerts, can mark a dose as given; background "check" alarms
  and "Taken" from notification/alarm/widget go straight to the server (`Family.java`, outbox retry).

## Update 4
- INR tracking (targets, chart, history entry, status colours), voice alarm (TTS loop in `AlarmService`),
  Ramadan mode (suhoor/iftar times), home-screen widget, tap any date → popup with that day's doses.
- Workflow that arranges the flat upload into an Android project.

## Update 3
- Drug-interaction search for warfarin and acetazolamide (26 + 12 groups), warnings in the medicine form and list.

## Update 2
- Full-screen ringing alarm over the lock screen with medicine name + dose, alarm check-up screen, test alarm;
  big UI refresh (dark mode, RTL fixes).

## First APK
- WebView app (single `index.html`) with exact alarms: daily / every N days / weekdays / repeating dose
  patterns (e.g. 2, 2, 2.5) / as needed, multiple family profiles, Arabic + English, offline.

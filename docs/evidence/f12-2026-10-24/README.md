# F12 integration evidence — 24 October 2026

Branch: local `feat/storage`, fast-forwarded to network integration commit `55ece8d`.
Device: Medium Phone API 37.0, Android 17, emulator-5554.
Production Application uses NetworkRepository with Cong Duong's CacheManager.
The debug preview label was updated to describe the real API/cache source.

## Results

- Debug APK and instrumentation APK built successfully; lint: 0 errors, 21 warnings.
- M3RepositoryTest: 18 passed (`repository.txt`). Controlled responses verify cache TTL,
  force refresh, fallback, invalid cache, main-thread callbacks and worker I/O.
- M3FileCacheTest: 2 passed (`file-cache.txt`).
- M3PersistentCacheTest, online seed: 1 passed (`online-seed.txt`). Actual HTTP requests
  fetched global statistics and 231 countries, with `fromCache=false`.
- App force-stopped; emulator Wi-Fi and mobile data disabled.
- M3PersistentCacheTest, offline read: 1 passed (`offline-read.txt`). New process read
  global statistics and 231 countries from file, with `fromCache=true` in 432 ms.
- Watchlist preview launched offline: Vietnam and its figures remain visible with the
  offline label (`offline-watchlist.png`, `offline-ui.xml`). This launch also logs CACHE countries.
- Wi-Fi and mobile data restored to their original enabled state. Refresh fetched
  countries from the network; offline label disappeared (`online-watchlist.png`, `online-ui.xml`).

## Process and source proof

See `cache-proof.txt`:

- Seed PID 4014: REQUEST/PARSE/NETWORK for global and countries;
  `phase=seed pid=4014 elapsedMs=2187 countries=231`.
- Offline PID 4103: CACHE global and CACHE countries;
  `phase=read pid=4103 elapsedMs=432 countries=231`.
- UI PID 4131: CACHE countries while offline; REQUEST/PARSE/NETWORK after restoring connectivity.

22 test executions passed in this run (18 + 2 + 1 + 1).
The 432 ms measurement covers Repository retrieval, not full app startup/render time.
No app data was cleared between the seed and offline phases. Neither APK was uninstalled.
No new request is logged for either offline process in the captured application logs.

## Scope remaining

The main three-tab navigation and country detail star button are not included in this
integration. F11's complete navigation flow still needs those teammates' changes.
This evidence confirms live API-backed file caching and offline Watchlist display in
the debug host; it does not certify every other screen of the finished team app.

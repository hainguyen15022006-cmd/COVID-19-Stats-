# Minh Son - Resources, localization and overdraw

## Scope

- Resource ownership: `res/values/*`, `res/values-vi/*`, `res/drawable/*`.
- Evidence and presentation ownership: `slide/`.
- Acceptance tests: T11 (Vietnamese) and T13 (GPU overdraw).
- Presentation slot: 09:00-10:30, Optimization - overdraw and RecyclerView.

## Completed on `feat/resources`

- Added a shared light/dark Material color system.
- Added semantic chart and offline-state colors.
- Added a reusable dimension and typography scale.
- Replaced the default Android launcher artwork with the project globe/pulse icon.
- Moved country sort labels to named bilingual string resources.
- Kept the theme window background as the single base layer; screen layouts should not add an identical full-screen background.

## T11 - Vietnamese evidence checklist

Capture both English and Vietnamese for every integrated screen:

1. Global tab: six metrics, data date, source-stopped notice, chart labels.
2. Country tab: search hint, sort options, continent filter, empty/error states.
3. Country detail: eight metrics, unavailable-value explanation, periods, chart legend.
4. Watchlist tab: title, empty state, offline banner, retry and swipe hint.
5. Verify number formatting: English `11,624,000`; Vietnamese `11.624.000`.
6. Verify date formatting follows the active locale.
7. Run Lint and require zero `HardcodedText` and zero `MissingTranslation` findings.

Current evidence possible before the remaining branches merge:

- Country list in English: `slide/evidence/t11-country-list-en.png`.
- Country list in Vietnamese: `slide/evidence/t11-country-list-vi.png`.
- Sort and continent options in both languages.
- Number grouping on country rows.

Verified on the API 30 emulator:

- System locale order: Vietnamese (Vietnam), then English (United States).
- Search, sort and continent-filter labels switch to Vietnamese.
- Country rows use Vietnamese grouping, for example `111.820.082 ca` and
  `1.219.487 tử vong`.
- The current English and Vietnamese resource sets contain the same string keys.
- Android Lint reports zero `HardcodedText`, zero `MissingTranslation` and zero
  `UnusedResources` findings for the current integrated code.

## T13 - overdraw evidence checklist

Android Studio emulator steps:

1. Open Developer options.
2. Choose **Debug GPU overdraw -> Show overdraw areas**.
3. Capture the same state before and after optimization.
4. Capture three required screens: Global, Country list and Country detail.
5. Keep screen position, data and theme identical between each before/after pair.
6. Store final images under `slide/evidence/` only after the corresponding screen is merged.

Color interpretation:

- Original color: drawn once.
- Blue: one extra draw.
- Green: two extra draws.
- Pink/red: heavy overdraw and the first optimization target.

Do not claim "no overdraw" without the three real before/after pairs.

## What is waiting for other branches

- Global tab and three-tab main screen: no remote branch is available yet.
- Country detail/chart: committed on `origin/congthanh-country-detail-chart`, not merged to `main`.
- Real network/cache/watchlist: committed on `origin/feat/networknew`, not merged to `main`.
- Final localization audit must run after those branches are merged because they add new strings and layouts.
- Final T13 images require Global and Country detail to exist on `main`.

## 90-second speaking notes

"My part standardizes resources and verifies localization and overdraw. All user-facing text is stored in Android resources with matching English and Vietnamese versions, so changing the system language changes the app without Java conditionals. Numbers and dates must be formatted with the active locale rather than concatenated as raw values. For rendering performance, I use Android's Debug GPU overdraw view to compare the same three screens before and after optimization. We remove duplicate full-screen backgrounds and unnecessary nested drawing layers, while RecyclerView reuses row views through ViewHolder. The evidence is the real before/after captures, not an unsupported FPS claim."

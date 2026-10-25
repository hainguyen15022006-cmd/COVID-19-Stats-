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

T11 completed on the API 37 emulator with English and Vietnamese pairs:

- Global: `t11-api37-global-en.png`, `t11-api37-global-vi.png`.
- Country list: `t11-api37-country-list-en.png`, `t11-api37-country-list-vi.png`.
- Country detail: `t11-api37-country-detail-en.png`,
  `t11-api37-country-detail-vi.png`.
- Watchlist: `t11-api37-watchlist-en.png`, `t11-api37-watchlist-vi.png`.

Verified results:

- All four integrated screens switch their user-facing UI to Vietnamese.
- English grouping `111,820,082` changes to Vietnamese `111.820.082`.
- Global date `Mar 9, 2023` changes to `9 thg 3, 2023`.
- Detail date `March 9, 2023` changes to `9 tháng 3, 2023`.
- English and Vietnamese each contain 89 matching main string keys.
- Android Lint reports zero `HardcodedText`, zero `MissingTranslation`, zero
  `UnusedResources` and zero `Overdraw` findings.

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

T13 completed on the API 37 emulator with GPU overdraw enabled:

- Global: `t13-before-global.png`, `t13-after-global.png`.
- Country list: `t13-before-country-list.png`, `t13-after-country-list.png`.
- Country detail: `t13-before-country-detail.png`,
  `t13-after-country-detail.png`.

Optimizations applied:

- Removed duplicate toolbar and tab backgrounds; the themed window remains the
  single full-screen base layer.
- Moved the country-row ripple from `background` to `foreground`, preserving
  touch feedback without painting an extra opaque row background.
- Removed the image button base background and made chart backgrounds explicit
  `@null` in portrait and landscape layouts.
- Removed two obsolete strings left behind after screen integration.

The final captures contain no pink/red full-screen regions. Blue remains inside
charts and interactive controls because those components legitimately draw their
own content. Lint independently confirms zero `Overdraw` findings.

## Integration status

- Global, Country list, Country detail/chart and Watchlist are all integrated.
- T11 and T13 no longer wait for another screen branch.
- `origin/feat/core-utils` and `origin/fix/demo-offline-chart` remain separate
  remote branches and were not mixed into this resources commit.

## 90-second speaking notes

"My part standardizes resources and verifies localization and overdraw. All user-facing text is stored in Android resources with matching English and Vietnamese versions, so changing the system language changes the app without Java conditionals. Numbers and dates must be formatted with the active locale rather than concatenated as raw values. For rendering performance, I use Android's Debug GPU overdraw view to compare the same three screens before and after optimization. We remove duplicate full-screen backgrounds and unnecessary nested drawing layers, while RecyclerView reuses row views through ViewHolder. The evidence is the real before/after captures, not an unsupported FPS claim."

# Menu shared matcher — 2026-10-30

The menu now uses gameplay's GameImeEditText and WordMatchEngine. English spellings are supplied by EnglishTypingLanguage. The independent whole-buffer startsWith matching and clearing on mismatch have been removed. MenuInputController only adapts static targets/composition to menu navigation.

- `armorarmory` completes armory with no lost restarting a.
- `aaaaa` keeps armory's a highlighted after every update.
- `armorp` immediately starts play at p; `xplay` completes play.
- `up` keeps both upgrades/up and play/p progress.
- Committed strokes, cumulative composing snapshots, whole batches, uppercase input and composition-to-commit transitions are covered.
- All four tap and typed destinations, animation position/opacity, duplicate selection protection and return-to-menu reset remain covered.

Debug/test builds passed. JVM tests: 77 passed (69 existing and 8 menu tests). Pixel 7a / Android 17 instrumented tests: 20 passed, including both reported cases under committed and composing input and existing Japanese input/streak/overlap regressions. Output: `menu-shared-matcher-physical.txt`.

The `pl` app-window render was refreshed and visually inspected after sharing the input pipeline. Device tests use notification replay, not new human Simeji typing. The underlying IME decoder itself was not changed.

Backup before this fix: `C:\Users\nozyj\home\dev\backups\testgame-menu-retry-20261030\src`.

# Title menu revision — 2026-11-02

Rows: start / coming soon... / keyboard test / quit. Placeholder is excluded from matching and taps. Quit closes the game task after the existing completion animation. Keyboard test includes a literal space, accepted through both IME and hardware-space callbacks.

Verification uses the connected physical Pixel 7a. Use an emulator only when no physical device is connected. TitleMenuInstrumentedTest covers current labels, prefix highlighting and retry behavior, tap simulation, disabled placeholder, menu text fitting, navigation, keyboard-space transport, completion animation, and task removal by both typed and tapped quit. InputConnection tests are synthetic transport integration, not claims about new human keyboard evidence.

Results: Debug and AndroidTest APK builds passed; 167 unit tests passed. Pixel 7a: TitleMenuInstrumentedTest 14 tests and LevelUpInstrumentedTest 4 tests passed. Actual captures: menu-default.png, menu-st.png and levelup-phone.png. The title rows fit on one line, the placeholder arrow stays muted, and the existing palette and geometry are preserved.

Weapon conditions now share WeaponTriggerGlyphs for descriptions, activation and evolution: Blade j/k/m, Orbiter n/o/p, additional y/z then w. The real Japanese dictionary is used to verify every advertised trigger has matching 2–7-character words. Chopper's description explicitly states main-weapon fire, available flight capacity, one base slot and reuse after screen exit; it has no letter condition. Source comparison and the distinction between source data and local adaptation are documented in ../../WeaponActivationAudit.md.

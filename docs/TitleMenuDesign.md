# Title menu

2026-10-30. Figma reference: file `KUASBGX7fk25F7B14yKKwH`, normal frame `1:3`, partially typed `pl` frame `9:42`, revised layout `14:97`, and its background `14:98`.

## Visual contract

- Title: Glyflick. Four commands: play, armory, upgrades, options. When the keyboard is hidden, a `Launch IME` action appears at the bottom and opens the same hidden input field.
- White background, fine light-grey grid and geometric outlines, charcoal title/arrows/typed letters, pale untyped letters.
- Keep the composition minimal. `Launch IME` is the requested action from Figma, not an instructional caption. Do not add helper text or retry explanations.
- The grey IME rectangle and its label in the earlier Figma frame represent the system keyboard only. They are never drawn by the app. The revised background node `14:98` continues the grid underneath the keyboard region.
- The grid is a transparent PNG at `app/src/main/res/drawable-nodpi/menu_grid.png`, exported from the grid lines in the latest Figma background. `docs/verification/render-menu-grid.py` regenerates it from `docs/verification/figma-backdrop-frame-14-98.svg`; it preserves Figma's 1080px-wide grid and extends it vertically so it continues behind the IME on tall screens.
- MenuBackdropView draws only the double circle and double rotated square natively with Canvas. Their geometry remains the same as Figma node `14:98` and is confirmed in `docs/verification/figma-backdrop-frame-14-98.svg`. Keep the shapes separate from the grid image so each can expand and fade away during navigation.
- Figma uses Polymath Display, which is not supplied to the project. Android's built-in sans-serif and sans-serif-light approximate it. The title stays at 148px and menu/action text at 90px in the 1080-unit layout. Title bounds are x=88, y=191, 904×231; menu starts at x=121, its 4-row group is centered near y=747 with a 768px-wide hit area; `Launch IME` is x=291, y=1608, 498×315. Scale these coordinates uniformly with display width. Rows remain at least 48dp tall and small viewports scroll.
- Untyped text is #B8B8B8 (Figma #CDCDCD), slightly darkened for visibility while retaining the tone. Ink stays #555555; white and the exported light-grey background are retained.

## Interaction

- Tap a full menu row, or type its English command. Inputs are case insensitive. The editor requests ASCII input and no suggestions through Android's standard EditorInfo flags.
- Keyboard transport is the same GameImeEditText used by gameplay; recognition uses the same WordMatchEngine. EnglishTypingLanguage registers literal English spellings. MenuInputController only adapts static menu targets and the shared composition contract to navigation.
- All active candidate prefixes are darkened, including `pl` for play. Each character also starts fresh word candidates: `armorarmory` completes armory, `aaaaa` keeps armory's first letter highlighted on every update, and `armorp` immediately highlights play's p. No mismatching character is swallowed. With `up`, upgrades and play both have progress, just like overlapping game words.
- Deletion follows the shared game transport and is blocked. Composing input is previewed without duplicate advancement and a complete command is applied immediately without Enter. No explanatory captions are added.
- On a tap, complete the command one character every 50ms (twice the former 100ms interval). If the same command already has a prefix, continue from that prefix; tapping another command starts at its beginning.
- A completed typed command and a completed simulated command share a 420ms AccelerateDecelerate animation: the selected word keeps its own centre fixed, scales from 1× to 2×, and fades from 100% to 0% opacity. Other title content fades out, then the input-test screen opens.
- During that same completion phase the circle pair and square pair expand around their own centres and fade out. Their final scale is calculated from the viewport so both pairs move fully offscreen, including on tall displays. The grid stays still and opaque. Cancelling/returning resets the shape progress.
- All four commands currently open the existing input-test activity, displaying `<command> → 入力テスト`.
- `Launch IME` is visible only while the system keyboard is hidden. Tapping it focuses the shared input field and requests the keyboard. Selection locks until navigation. Repeated taps and incoming input during the animation do not trigger additional navigation. Pausing cancels delayed work and the animation; returning from the input-test screen resets the title menu.
- System-disabled animations bypass the tap animation, respecting the Android animation setting.

## Boundaries and verification

The Japanese language rules and recognition engine are unchanged; English targets now use that same recognition engine. The launcher is TitleActivity, while MainActivity remains directly launchable for diagnostic workflows.

Pre-change source backup: `C:\Users\nozyj\home\dev\backups\testgame-menu-20261030\src`.

Earlier build and device results are recorded in `docs/verification/title-menu-20261030.md`, `docs/verification/menu-shared-matcher-20261030.md` and `docs/verification/menu-native-background-20261030.md`. The revised Figma layout and PNG grid update are tracked in the latest session note.

## Version control

This Windows Android checkout uses colocated Git/Jujutsu. Existing Git identity is reused. The verified title and shared-input baseline is commit `4461d22`; native background animation is a separate following change. No remote is configured.

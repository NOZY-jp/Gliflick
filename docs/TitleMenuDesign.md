# Title menu

2026-10-30. Figma reference: file `KUASBGX7fk25F7B14yKKwH`, normal frame `1:3`, partially typed `pl` frame `9:42`, revised layout `14:97`, and its background `14:98`.

## Visual contract

- Title: Glyflick. Four commands: play, armory, upgrades, options. When the keyboard is hidden, a `Launch IME` action appears at the bottom and opens the same hidden input field.
- Warm off-white background `#EFEFE5`, fine grid `#E4E4D8`, pale geometric outlines `#C7C7BC`, ink `#464646`, and pale untyped letters. The untyped text stays slightly darker than Figma's `#CDCDCD` at `#B8B8B8` so it remains legible on the warmer background.
- Keep the composition minimal. `Launch IME` is the requested action from Figma, not an instructional caption. Do not add helper text or retry explanations.
- The grey IME rectangle and its label in the earlier Figma frame represent the system keyboard only. They are never drawn by the app. The revised background node `14:98` continues the grid underneath the keyboard region.
- The grid is a transparent PNG at `app/src/main/res/drawable-nodpi/menu_grid.png`, rasterized from Figma Frame 3's Group 4 (`docs/verification/frame3-assets/grid.svg`). `docs/verification/render-menu-grid.py` regenerates it, preserves the 1080px-wide grid and extends it vertically behind the IME on tall screens.
- MenuBackdropView draws the double circle and double rotated square natively with Canvas. Their geometry stays separate from the grid so they can expand and fade away during navigation.
- Frame 3 adds a concentric target mark at `(136, 1077)`, a navigation arrow with two outlined diamonds at `(97, 1258)`, and the semantic Warder mark at `(504, 1229)`. The source vectors are `app/src/main/res/raw/title_target.svg`, `title_navigation_marks.svg`, and `warder_barrier.svg`; the renderer produces high-resolution Android PNGs from them. Their design color is `#C7C7BC`; the arrow's inner cutout uses the background color.
- Figma uses Polymath Display, which is not supplied to the project. Android's built-in sans-serif and sans-serif-light approximate it. The title stays at 148px and menu/action text at 90px in the 1080-unit layout. Title bounds are x=88, y=191, 904×231; menu starts at x=121, its 4-row group is centered near y=747 with a 768px-wide hit area; `Launch IME` is x=291, y=1608, 498×315. Scale these coordinates uniformly with display width. Rows remain at least 48dp tall and small viewports scroll.
- Untyped text is #B8B8B8 (Figma #CDCDCD), slightly darkened for visibility while retaining the tone. Frame 3's ink and background are #464646 and #EFEFE5.

## Interaction

- Tap a full menu row, or type its English command. Inputs are case insensitive. The editor requests ASCII input and no suggestions through Android's standard EditorInfo flags.
- Keyboard transport is the same GameImeEditText used by gameplay; recognition uses the same WordMatchEngine. EnglishTypingLanguage registers literal English spellings. MenuInputController only adapts static menu targets and the shared composition contract to navigation.
- All active candidate prefixes are darkened, including `pl` for play. Each character also starts fresh word candidates: `armorarmory` completes armory, `aaaaa` keeps armory's first letter highlighted on every update, and `armorp` immediately highlights play's p. No mismatching character is swallowed. With `up`, upgrades and play both have progress, just like overlapping game words.
- Deletion follows the shared game transport and is blocked. Composing input is previewed without duplicate advancement and a complete command is applied immediately without Enter. No explanatory captions are added.
- On a tap, complete the command one character every 50ms (twice the former 100ms interval). If the same command already has a prefix, continue from that prefix; tapping another command starts at its beginning.
- A completed typed command and a completed simulated command share a 420ms AccelerateDecelerate animation: the selected word keeps its own centre fixed, scales from 1× to 2×, and fades from 100% to 0% opacity. Other title content fades out, then the input-test screen opens.
- During that same completion phase the circle pair and square pair expand around their own centres and fade out. Their final scale is calculated from the viewport so both pairs move fully offscreen, including on tall displays. The target, navigation, and Warder marks fade with the outlines; the grid stays still and opaque. `GridBackgroundRenderer` continues drawing the same grid after `play` opens combat. Cancelling/returning resets the shape progress.
- `play` opens Repeater endless combat immediately, without mode selection. The other three commands currently open the existing input-test activity, displaying `<command> → 入力テスト`.
- `Launch IME` is visible only while the system keyboard is hidden. Tapping it focuses the shared input field and requests the keyboard. Selection locks until navigation. Repeated taps and incoming input during the animation do not trigger additional navigation. Pausing cancels delayed work and the animation; returning from the input-test screen resets the title menu.
- System-disabled animations bypass the tap animation, respecting the Android animation setting.

## Boundaries and verification

The Japanese language rules and recognition engine are unchanged; English targets now use that same recognition engine. The launcher is TitleActivity, while MainActivity remains directly launchable for diagnostic workflows.

Pre-change source backup: `C:\Users\nozyj\home\dev\backups\testgame-menu-20261030\src`.

Earlier build and device results are recorded in `docs/verification/title-menu-20261030.md`, `docs/verification/menu-shared-matcher-20261030.md` and `docs/verification/menu-native-background-20261030.md`. Frame 3's palette, SVG marks, generated PNGs, and Pixel 7a screenshot are recorded in `docs/verification/title-frame3-20261030.md`.

## Version control

This Windows Android checkout uses colocated Git/Jujutsu. Existing Git identity is reused. The verified title and shared-input baseline is commit `4461d22`; native background animation is a separate following change. No remote is configured.

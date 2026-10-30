# Title menu

2026-10-30. Figma reference: file `KUASBGX7fk25F7B14yKKwH`, normal frame `1:3`, partially typed `pl` frame `9:42`.

## Visual contract

- Title: Glyflick. Four commands: play, armory, upgrades, options.
- White background, fine light-grey grid and geometric outlines, charcoal title/arrows/typed letters, pale untyped letters.
- The screen has only its title and command rows over the background. Do not add instructional captions or retry explanations; the user explicitly wants this minimal composition.
- The grey IME rectangle and its label in Figma represent the system keyboard only. They are never drawn by the app. Figma's background node `4:6` extends underneath the keyboard region.
- MenuBackdropView draws the grid, double circle and double rotated square natively with Canvas. Dimensions, radii, rotation and stroke widths come from Figma node `4:6`; the reference SVG is saved at `docs/verification/figma-backdrop-source.svg`. The 1080-unit coordinate system scales uniformly with display width, and the grid continues over the entire display. There is no runtime background PNG or remote asset URL.
- Figma uses Polymath Display, which is not supplied to the project. Android's built-in sans-serif and sans-serif-light approximate it. Menu text and hit areas adapt to width; rows are at least 48dp tall. Small viewports scroll.
- Untyped text is #B8B8B8 (Figma #CDCDCD), slightly darkened for visibility while retaining the tone. Ink stays #555555; white and the exported light-grey background are retained.

## Interaction

- Tap a full menu row, or type its English command. Inputs are case insensitive. The editor requests ASCII input and no suggestions through Android's standard EditorInfo flags.
- Keyboard transport is the same GameImeEditText used by gameplay; recognition uses the same WordMatchEngine. EnglishTypingLanguage registers literal English spellings. MenuInputController only adapts static menu targets and the shared composition contract to navigation.
- All active candidate prefixes are darkened, including `pl` for play. Each character also starts fresh word candidates: `armorarmory` completes armory, `aaaaa` keeps armory's first letter highlighted on every update, and `armorp` immediately highlights play's p. No mismatching character is swallowed. With `up`, upgrades and play both have progress, just like overlapping game words.
- Deletion follows the shared game transport and is blocked. Composing input is previewed without duplicate advancement and a complete command is applied immediately without Enter. No explanatory captions are added.
- On a tap, complete the command one character every 100ms. If the same command already has a prefix, continue from that prefix; tapping another command starts at its beginning.
- A completed typed command and a completed simulated command share a 420ms AccelerateDecelerate animation: the selected word keeps its own centre fixed, scales from 1× to 2×, and fades from 100% to 0% opacity. Other title content fades out, then the input-test screen opens.
- During that same completion phase the circle pair and square pair expand around their own centres and fade out. Their final scale is calculated from the viewport so both pairs move fully offscreen, including on tall displays. The grid stays still and opaque. Cancelling/returning resets the shape progress.
- All four commands currently open the existing input-test activity, displaying `<command> → 入力テスト`.
- Selection locks until navigation. Repeated taps and incoming input during the animation do not trigger additional navigation. Pausing cancels delayed work and the animation; returning from the input-test screen resets the title menu.
- System-disabled animations bypass the tap animation, respecting the Android animation setting.

## Boundaries and verification

The Japanese language rules and recognition engine are unchanged; English targets now use that same recognition engine. The launcher is TitleActivity, while MainActivity remains directly launchable for diagnostic workflows.

Pre-change source backup: `C:\Users\nozyj\home\dev\backups\testgame-menu-20261030\src`.

Build and test results are recorded in `docs/verification/title-menu-20261030.md`, `docs/verification/menu-shared-matcher-20261030.md` and `docs/verification/menu-native-background-20261030.md`.

## Version control

This Windows Android checkout uses colocated Git/Jujutsu. Existing Git identity is reused. The verified title and shared-input baseline is commit `4461d22`; native background animation is a separate following change. No remote is configured.

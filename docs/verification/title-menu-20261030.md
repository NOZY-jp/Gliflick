# Title menu verification — 2026-10-30

## Result

- Debug app and instrumented-test APK builds passed.
- JVM suite: 72 passed (69 existing and 3 menu matching tests).
- Pixel 7a, serial `38251JEHN13144`, Android 17: full instrumented suite 18 passed after the 2×, fixed-centre, 100%→0% animation revision. Output: `title-menu-physical.txt`.
- After removing all visible menu captions, rebuilt and reran the seven menu tests: 7 passed. Output: `title-menu-final-physical.txt`. Japanese gameplay code was not changed by the caption removal.
- Final APK installed on the physical device. Physical device was preferred over the emulator.

## Verified behavior

- Normal menu and `pl` composing input match the two supplied Figma states in structure and tone. No IME placeholder rectangle/label or instructional caption is drawn.
- All four commands work through touch and through English InputConnection text, and each destination identifies its command.
- Tap animation exposes the first letter after the 100ms tick; subsequent selection/input cannot redirect an in-progress selection.
- Tapping play after typing `pl` completes the same command. Returning resets prefix and visual state.
- Invalid input clears, and an uppercase retry succeeds.
- Completion animates scale and decreasing opacity before navigation. Instrumentation checks the selected word's centre remains fixed during the animation. Final implementation uses 1×→2× and opacity 1→0 over 420ms with AccelerateDecelerateInterpolator.
- Existing Japanese notification, streak, overlap and six-versus-eight regressions passed in the full suite.

## Visual evidence

- `menu-default-pixel7a.png`: final minimal title screen.
- `menu-pl-pixel7a.png`: only `pl` is darkened.
- `menu-transition-pixel7a.png`: intermediate enlargement/fade with its centre preserved.

These three PNGs are native app-window renders from the physical device, taken with `decorView.draw(Canvas)`. They deliberately exclude the system keyboard, lock screen and other apps. Background is visible throughout the app window, including the area behind the IME. The bundled Figma background file is nonempty, 1080×2040, and is drawn at its original aspect ratio.

This is automated UI/input-notification verification. It is not new human Simeji typing evidence. Polymath Display was not supplied; Android system sans-serif fonts approximate it.

The first test run exposed a consumed ActivityMonitor in the multi-navigation fixture; the fixture now registers a fresh monitor after each return. The title also requests editor focus when its window gains focus. Final suites passed with these corrections.

## Memory synchronization

The local vault `E:\home\wiki` and configured share `\\100.104.189.113\e\home\wiki` could not be accessed. Project decisions and requested visual constraints are saved in `docs/TitleMenuDesign.md` and the active `AGENTS.md`; wiki synchronization and wiki validation remain pending connectivity.

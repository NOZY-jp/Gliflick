# Native background verification — 2026-10-30

MenuBackdropView now draws the grid, two circles and two rotated squares directly with Canvas. The former runtime PNG has been removed. Figma node 4:6's exported SVG is retained as geometric reference in `figma-backdrop-source.svg`.

- Circle centre: (1208, -169); radii 1041.5 and 983; stroke widths 35 and 12.
- Squares: sides 578.212 and 504.256; rotation 38.4896 degrees; stroke widths 31 and 10. Original Figma origins are retained.
- All positions use the original 1080-unit width and uniform scaling. The full-height grid uses 198-unit spacing and keeps its position/opacity during transitions.
- Both outline groups enlarge around their own centres and fade to zero, driven by the same 420ms eased completion progress as the word. Final scale puts the farthest viewport corner inside each group's innermost boundary, so the outlines leave the display. Returning/cancelling resets progress to zero.
- The selected word still enlarges to 2× around its fixed centre and fades from 100% to 0% opacity.

Debug/test builds passed. Pixel 7a / Android 17: all 10 menu instrumented tests passed, including typo recovery, repeated a, four tap/typed destinations, independent background clearing, grid visibility and reset after return. Output: `menu-native-background-physical.txt`. Matching was previously verified by the 77-test JVM suite and 20-test complete device suite before this drawing-only change.

Visually inspected native app-window renders: `menu-default-pixel7a.png`, `menu-pl-pixel7a.png`, `menu-transition-pixel7a.png`, and `menu-grid-only-pixel7a.png`. The last image isolates the completed background state with the menu still shown for inspection. No system/private screens are captured.

Version management uses colocated Git/Jujutsu with the existing Git author identity. The tested pre-background baseline is `4461d22`; this native-shape change is recorded separately. No remote/publishing is configured.

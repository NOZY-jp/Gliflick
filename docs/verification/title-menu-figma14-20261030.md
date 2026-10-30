# Figma title menu update — frame 14:97

## Changes

- Tap autofill now enters one character every 50ms, half the previous 100ms interval.
- The title and menu now use the Figma screen-edge coordinates; removing the status-bar top padding keeps the title from drifting downward. Menu rows use the Figma left edge and a 108px line step, so `upgrades` forms the widest point of the left-aligned word group.
- The `Launch IME` action uses the Figma position and size. It is hidden while the keyboard inset is present; tapping it focuses the shared game input field and shows the keyboard.
- The updated Figma grid lines are rasterized to `app/src/main/res/drawable-nodpi/menu_grid.png`. The source export and filtered SVG are kept beside the generator in this directory. The double arc and rotated squares stay native and independently animated.

## Verification

- `uv run --with cairosvg python docs/verification/render-menu-grid.py` regenerated the grid PNG at 1080×4096.
- `.\gradlew.bat :app:assembleDebug` succeeded.
- Installed the APK on the Pixel 7a running Android 17. The title/menu placement matched the revised Figma frame; the button appeared after hiding the keyboard and reopened Simeji when tapped.
- Automated test suites were not run for this visual update. No new human typing trace was collected.

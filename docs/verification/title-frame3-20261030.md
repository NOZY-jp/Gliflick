# Figma Frame 3 title-screen update

Reference: Figma file `KUASBGX7fk25F7B14yKKwH`, Frame 3 node `14:97`, background node `14:98`.

## Applied appearance

| Element | Figma | App |
| --- | --- | --- |
| Background | `#EFEFE5` | `#EFEFE5` |
| Grid | `#E4E4D8` | `#E4E4D8` |
| Shapes | `#C7C7BC` | `#C7C7BC` |
| Title / typed letters | `#464646` | `#464646` |
| Untyped letters | `#CDCDCD` | `#B8B8B8` for stronger visibility on the warm background |

The background grid comes from Group 4 and is extended below the Figma frame so it continues under the system IME on tall screens. The existing double circle and double rotated square remain independent Canvas shapes and keep their transition animation.

## Frame 3 marks

- Group 1 target mark: `(136, 1077)`, `57 × 57` design units.
- Group 9 navigation arrow and double diamond: `(97, 1258)`, `136 × 124` design units.
- Group 7 Warder mark: `(504, 1229)`, `101.398 × 103.305` design units, rotated `-2.6°`. Its SVG uses the specified semantic geometry: radius `18`, side length `27`, apex angle `22.5°`, eight rays at `45°` intervals, and a flat-ended `120°` barrier arc.

The three marks fade with the existing background transition. Their editable vector sources are in `app/src/main/res/raw/`; high-resolution transparent PNGs are generated for Canvas by `docs/verification/render-menu-grid.py`. Figma source vectors are preserved under `docs/verification/frame3-assets/`.

## Verification

- `:app:assembleDebug`: successful.
- Installed and opened on the connected physical Pixel 7a / Android 17. The menu palette, grid, three added marks, and their placement were visually checked.
- Screenshot: `title-frame3-device.png`.
- Automated tests and new human keyboard input were not run for this visual update.

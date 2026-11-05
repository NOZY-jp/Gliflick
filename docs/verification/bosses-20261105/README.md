# Boss encounters, rerolls and typed chips — 2026-11-05

Final verification used the connected Pixel 7a. An emulator was used only during the earlier period with no physical device. After reconnection, verification switched to the phone with the user's permission.

## Checks and provenance

All 190 JVM tests passed. BossAndRerollTest covers the two-minute drop hazard, real kill drops, same-frame kills, typed collection and expiry, exact encounter boundaries, paused introductions and go, the global sixteen-body cap, Gemini healing and 20-key defense/retraction, Cypher aura entry/exit and tsuse/tuse/tuce alternatives, two-completion seals with both weapon kinds, the full public-domain Soseki passage, moving Poet orbs, and viewport changes.

All eleven final Android tests passed: BossInstrumentedTest (4), CombatInstrumentedTest (3), and LevelUpInstrumentedTest (4). The loot test goes through the actual GameImeEditText InputConnection; this remains synthetic input evidence, not a claim of human Simeji typing. Encounters and elapsed time are controlled test fixtures, not a naturally played twelve-minute run.

The installed APK was pulled and matched the intended output byte-for-byte. SHA-256: `6c22aa6b98d219b1bcb67a5cc3533ab2c3c102385158149e908a37f3b9df34ea`.

Early test attempts exposed fixture timing and device keyguard interruptions. Captures now stop/advance controlled simulation instead of aging actors during screenshot waits, and wait for actual orb contact. Test windows use FLAG_KEEP_SCREEN_ON. The temporary USB stay-awake setting used during build preparation was restored from 2 to its original 0 during the final test batch; the final 29.851-second batch passed under the original system setting. No lock-screen bypass or permanent screen-timeout change was made. The app was returned to its title afterward.

## Actual phone frames

Only the arena is captured; personal keyboard/status chrome are excluded. Visual review covered the following states:

| State | Frame |
| --- | --- |
| Paused introduction with grey typed-go chip | [gemini-introduction.png](gemini-introduction.png) |
| Independent rounded bodies and inward-facing pupils | [gemini-pair.png](gemini-pair.png) |
| Tapered tendrils attached at inner body edges; no mash counter | [gemini-tendril.png](gemini-tendril.png) |
| Flickering glyph body, broad aura and generated kana | [cypher-aura.png](cypher-aura.png) |
| Poet eye, current passage, progress and squared enemy word | [poet-passage-and-seal.png](poet-passage-and-seal.png) |
| Grey reroll word chip and rising sparkle shapes, without smoke discs | [reroll-sparkling-dice.png](reroll-sparkling-dice.png) |
| Collected die removed and rerolls increased to four | [reroll-collected.png](reroll-collected.png) |

## Reference comparison

The [Gemini announcement GIF](https://clan.fastly.steamstatic.com/images/44862715/01e59071ce4220b91476486d230916e9d455dbc1.gif), [Cypher announcement GIF](https://clan.fastly.steamstatic.com/images/44862715/9608e91c933be56a5ff72c176d80eefa943700bb.gif) and [official Poet screenshot](https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/2400160/ss_cdd249dc74d96291c52d4baadad2f829f6a42c67.1920x1080.jpg) were displayed and compared with the Android frames. Gemini preserves paired rounded squares, circular cutouts and directional pupils; tendrils begin at their inner edges rather than crossing the eyes. Cypher uses a small core with changing symbols and a wide circle. Poet uses a procedural/vector eye and a separate passage panel, not a conventional damageable enemy body. Shapes were independently reconstructed for the mobile scene; source images are not product textures. See [the design](../../BossAndRerollDesign.md) for local parameter choices and deliberate differences.

Raw APKs, source-reference media and intermediate logs remain in ignored build/boss-audit-20261105. User-edited boss introduction text is preserved; unrelated Android Studio metadata changes are excluded from this feature commit.

# Oscillator wave and Repeater impact feedback — 2026-11-05

Final verification used the connected Pixel 7a, not the emulator. The user authorized interrupting the current run for deployment. These are real CombatActivity/Canvas frames using a controlled loadout and simulation steps, not naturally obtained weapons or human typing evidence. Screenshots contain only the app's arena, excluding the personal keyboard and system chrome.

- All 180 JVM tests passed. OscillatorMotionTest covers dense radial spacing, growing displacement, gradual retargeting and preserved old tip directions, repeated actual contacts, Twin-Headed/Plasma compatibility, and direct-Repeater-only rings.
- With an ordinary stationary Drone 150dp away, five successive sweeps each generated four direct contacts. The test clears random Ignite to count only wave contacts. Counts can vary with distance, size, upgrades and movement; they are not a fixed damage quota.
- Four physical-device tests passed: OscillatorInstrumentedTest plus the three existing CombatInstrumentedTest cases (typing/burst/XP, pause/resume, retry/reset).
- Both final APK builds succeeded. The installed APK was pulled and matched the intended output byte-for-byte: SHA-256 `1d69a0850f683d84e632bba89ebbea8168c5f930bb9dac2957d4dbc0cc05c0df`.
- Visual review confirmed a dense pale-grey dotted wave, the base turning ahead of the outer tip, multiple contact numbers without rings on the right-hand Oscillator target, and a grey enlarged ring on the separate left-hand Repeater target. The app was returned to its title afterward.

The screenshot helper waits for the committed frame and then the screenshot surface, so consecutive states are not stale copies. Intermediate failed build/capture attempts and raw APKs remain under ignored build/oscillator-audit-20261105.

| State | Actual screen |
| --- | --- |
| Stable upward wave | [wave-up.png](wave-up.png) |
| 0.167s after aiming right | [wave-turn-early.png](wave-turn-early.png) |
| 0.4s after aiming right | [wave-turn-late.png](wave-turn-late.png) |
| Wave contact feedback | [wave-contacts.png](wave-contacts.png) |
| Repeater ring beside Oscillator contacts | [repeater-grey-ring.png](repeater-grey-ring.png) |

[Design and public references](../../OscillatorMotionDesign.md) distinguish the public demo mechanism from local mobile dimensions.

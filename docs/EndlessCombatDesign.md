# Repeater endless combat

2026-10-30. Implements the user's play-screen reference and normal drones only.

## Goal

Play opens combat immediately. A fixed central player, elapsed timer, level, linear and circular XP, and HP form the HUD. The lower weapon UI is deferred. Drones approach at constant speed; typing a caption queues a three-shot Repeater burst and replaces that caption.

## Structure

- `EnemyDefinition` separates kind, variant, shape and fixed balance. Each `Enemy` creates its own `EnemyBehavior` and `Durability`; pursuit and ordinary HP are the current implementations. Shields or immunity can replace durability without changing projectile collision.
- `MainWeapon` is the targeting/update contract; `RepeaterDefinition` and `ProjectileDefinition` hold its balance. Weapons emit `WeaponAction` commands; the world resolves those commands. A future beam or area effect adds a command/handler without changing input transport or the activity.
- `EnemySpawnPolicy` selects definitions from elapsed time and player level. The current policy selects only a normal drone; new enemy selection does not belong in the renderer or keyboard controller.
- `WordDefinition.readingLength` is derived from the reading, and `WordLengthPolicy` selects a length before `CombatSession` chooses a word from that group. The length schedule does not depend on enemy type or weapon.
- `CombatWorld` advances movement, scheduled shots, swept circle collisions, spawning, damage, XP and death effects. It has no Android or IME dependencies.
- `CombatSession` connects world events to dynamic word targets and caption animations. Enemy lifetime and caption lifetime are independent.
- `WordMatchEngine` remains the shared correctness/streak engine. Dynamic addition/removal and retiring a target preserve other candidates. Retirement disables fresh starts after the already observed input, including an unfinished IME composition; current paths may still finish.
- `CombatView` runs a fixed 120Hz simulation on the UI thread through Choreographer. `CombatRenderer` draws snapshots. Activity lifecycle pauses the clock and simulation; the available arena follows system/IME insets.

## Reading-length progression

The combat word pool uses Japanese readings from two through seven Unicode code points. `readingLength` comes from `splitTextCharacters(reading)`, so small kana and the long-vowel mark each count as one, matching the existing reading-character streak rule. The diagnostic word-list screen uses the same curated set; every entry is within the 2–7 character reading range.

The pool is grouped by `readingLength` and currently contains:

| Reading length | Words |
| ---: | ---: |
| 2 | 108 |
| 3 | 172 |
| 4 | 164 |
| 5 | 103 |
| 6 | 100 |
| 7 | 100 |

The curated list contains 747 entries; every reading-length group has at least 100 candidates. It uses standalone words and established compounds. The only particle-bearing expressions are the two requested idioms, 「黄泉の国」 and 「虎の巻」.

`ProgressiveGaussianWordLengthPolicy` controls which groups are available and the Gaussian centre. The maximum starts at three and increases once every four minutes, reaching seven at 16 minutes. The centre starts at two and increases by 0.5 once every two minutes, reaching six at 16 minutes. Standard deviation starts at one and is configurable. For each available length `n`, the policy uses `exp(-0.5 * ((n - centre) / standardDeviation)^2)` and normalizes across the unlocked groups that contain unused words. It chooses a length first, then selects uniformly within that group so group size does not multiply the length probability. At the boundaries, truncating a discrete distribution means the observed sample average may differ slightly from its centre.

| Elapsed | Unlocked lengths | Gaussian centre |
| ---: | --- | ---: |
| 0 min | 2–3 | 2.0 |
| 2 min | 2–3 | 2.5 |
| 4 min | 2–4 | 3.0 |
| 8 min | 2–5 | 4.0 |
| 12 min | 2–6 | 5.0 |
| 16 min onward | 2–7 | 6.0 |

## Lifetimes and targeting

Completion queues three shots in FIFO burst order, with 120ms between shots. Each shot samples the living target's position once and then travels without homing. If the target has died, remaining shots use its last known aim point. Collision selects the earliest intersected live body, independently of the selected target; relative swept-circle tests prevent tunnelling through moving drones.

On word completion, the old prompt scales 1x to 2x about its own centre and fades over 420ms with ease-in/out. The replacement fades in over 240ms. Shared and nested word matching keeps the existing contract.

On enemy death, its body and inactive caption fade over 140ms. Ten independently sized square particles fly radially at random angles; their size follows 0 -> 1 -> 0. A partially typed caption is pinned to the death point, retired from new targeting, and retained until its existing input completes or fails. Its continuation prevents a false mistake while clearing the label, but awards no additional reading streak, overlap bonus, shot, or kill XP. Newly spawned words never consume already observed composition text.

## Initial balance choices

| Parameter | Adopted value | Basis |
| --- | --- | --- |
| Drone HP / contact damage | 80 / 40 | First variant in the [official enemy table](https://alias.black/glyphica/wiki_english.html), checked 2026-10-30 |
| Repeater kinetic damage / burst count | 35 / 3 | [Japanese community Wiki](https://wikiwiki.jp/glyphica/リピーター), checked 2026-10-30; secondary-source values, not independently measured |
| Player HP | 300 | User's reference image; also appears in the official Wiki's health example |
| Drone speed / radius | 8.5 logical dp/s / 4.5dp | Prototype screen tuning; public absolute values not verified |
| Bullet speed / radius / shot interval | 520dp/s / 1.25dp / 120ms | Prototype screen tuning; public absolute values not verified |
| Enemy count / spawn | Six initial, eight maximum, 2.8s | Prototype population limit |
| XP | 10 per kill; first threshold50, subsequent thresholds +20 | Prototype progression |

The default HP80 / damage35 requires three ordinary hits. A test injects damage40 to verify the user's two-hit-kill example still emits its third projectile. Crits, bleed and upgrades are deferred. The [lead developer's explanation](https://steamcommunity.com/app/2400160/discussions/0/591784375625107988/) confirms that the base Repeater has no range stat; bullets therefore leave only on contact or exiting the arena. Level changes update the HUD without weapon upgrades. Contact consumes a drone; HP0 stops combat and offers a tap to restart.

## Verification

Pure Kotlin tests cover straight flight, three shots surviving target death, interception, swept collision, XP, particles and retiring captions including composing input. Existing recognition tests protect the shared IME/streak behaviour. Build and physical-device UI smoke checks cover navigation, HUD and typing-to-shoot. Visual checks do not substitute for new human IME evidence.

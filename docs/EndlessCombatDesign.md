# Repeater endless combat

2026-10-30. Implements the user's play-screen reference and normal drones only.

## Goal

Play opens combat immediately. A fixed central player, elapsed timer, level, linear and circular XP, and HP form the HUD. The lower weapon UI is deferred. Drones approach at constant speed; typing a caption queues a three-shot Repeater burst and replaces that caption.

## Structure

- `EnemyDefinition` separates kind, variant, shape and fixed balance. Each `Enemy` creates its own `EnemyBehavior` and `Durability`; pursuit and ordinary HP are the current implementations. Shields or immunity can replace durability without changing projectile collision.
- `MainWeapon` is the targeting/update contract; `RepeaterDefinition` and `ProjectileDefinition` hold its balance. Weapons emit `WeaponAction` commands; the world resolves those commands. A future beam or area effect adds a command/handler without changing input transport or the activity.
- `EnemySpawnPolicy` selects enemy definitions, while `EnemySpawnSchedule` independently selects spawn counts and timing. The current definition policy selects only a normal drone; neither decision belongs in the renderer or keyboard controller.
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
| 3 | 516 |
| 4 | 492 |
| 5 | 309 |
| 6 | 100 |
| 7 | 100 |

The curated list contains 1,625 entries; every reading-length group has at least 100 candidates. The three-, four-, and five-character groups were each expanded to three times their previous counts. It uses standalone words and established compounds, excludes する-verbs, and limits particle-bearing expressions to the two requested idioms, 「黄泉の国」 and 「虎の巻」.

`ProgressiveGaussianWordLengthPolicy` controls which groups are available and the Gaussian centre. The maximum starts at three and increases once every four minutes, reaching seven at 16 minutes. The centre starts at two and increases by 0.5 once every two minutes, reaching six at 16 minutes. Standard deviation starts at one and is configurable. For each available length `n`, the policy uses `exp(-0.5 * ((n - centre) / standardDeviation)^2)` and normalizes across the unlocked groups that contain unused words. It chooses a length first, then selects uniformly within that group so group size does not multiply the length probability. At the boundaries, truncating a discrete distribution means the observed sample average may differ slightly from its centre.

| Elapsed | Unlocked lengths | Gaussian centre |
| ---: | --- | ---: |
| 0 min | 2–3 | 2.0 |
| 2 min | 2–3 | 2.5 |
| 4 min | 2–4 | 3.0 |
| 8 min | 2–5 | 4.0 |
| 12 min | 2–6 | 5.0 |
| 16 min onward | 2–7 | 6.0 |

## Enemy spawn waves and entry captions

`EnemySpawnPolicy` chooses which definition appears; `EnemySpawnSchedule` decides when and how many enemies to request. The arena starts with no enemies. Low-rate random arrivals become possible after 1.5 seconds, and the first group is scheduled at 5 seconds so the game does not open with a crowd.

`CompositeWaveSpawnSchedule` combines two independent rhythms. A smooth 120-second envelope moves from 0.75× at the start, to 1.25× at 60 seconds, and back to 0.75× at 120 seconds. Every five-second pulse has a 45% chance to request a group; its Poisson mean is 1.5 enemies, multiplied by the slow wave and a player-level factor. Pulse intervals receive up to ±0.3 seconds of jitter. Between pulses, a separate Poisson arrival process uses a base rate of 0.18 enemies per second, also scaled by those factors. Only living enemies count toward the cap of eight; requests beyond available slots are discarded.

For this prototype, the player-level factor is `min(1 + 0.04 × (level - 1), 1.4)`. This is an explicit, editable game-tuning choice, not a recovered Glyphica formula. The current `Threat` value is a placeholder at zero and has no effect on scheduling.

Public sources only describe the original threat system at a high level. The [Glyphica wiki](https://alias.black/glyphica/wiki_english.html) says threat rises over a run, Endless compares it with a player-level adaptive value, and special/elite enemies have class caps. A [developer interview](https://redharegames.wordpress.com/2025/02/03/game-talk-glyphica/) confirms that a central threat formula changes difficulty and density and that enemy types can be controlled by time brackets, but it does not publish the formula or its conversion to spawn counts. This implementation therefore does not invent a Threat progression or claim to reproduce that formula.

Scheduled enemies begin outside one of the four viewport edges, at least their radius plus 12dp beyond it. Their caption follows their position from the moment they spawn; it is not projected to a separate handoff point or clamped to the viewport, so part or all of it can remain clipped until the enemy enters. Layout is selected once from the spawn origin: enemies originating in the upper half place their word 34dp below the body and their health bar above it, while lower-half spawns keep the word 22dp above and the health bar below. The chosen side remains fixed as the enemy moves across the arena.

## Lifetimes and targeting

Completion queues three shots in FIFO burst order, with 120ms between shots. Each shot samples the living target's position once and then travels without homing. If the target has died, remaining shots use its last known aim point. Collision selects the earliest intersected live body, independently of the selected target; relative swept-circle tests prevent tunnelling through moving drones.

On word completion, the old prompt scales 1x to 2x about its own centre and fades over 420ms with ease-in/out. The replacement fades in over 240ms. Shared and nested word matching keeps the existing contract.

On enemy death, its body and inactive caption fade over 140ms. Ten independently sized square particles fly radially at random angles; their size follows 0 -> 1 -> 0. A partially typed caption is pinned to the death point, retired from new targeting, and retained until its existing input completes or fails. Its continuation prevents a false mistake while clearing the label, but awards no additional reading streak, overlap bonus, shot, or kill XP. Newly spawned words never consume already observed composition text.

## Hit feedback

When a projectile reduces an enemy's HP, `CombatWorld` records the enemy's interpolated centre at hit time, actual HP reduction, enemy radius, and incoming shot angle in one `HitFeedback`. The ring and fragments start from this enemy-centre position rather than the projectile's contact point. `CombatRenderer` draws a small health bar under that enemy only while HP is below maximum; its fill uses current HP, and it follows the enemy's death fade.

The damage number starts slightly above the enemy's top edge and drifts upward 18dp over 640ms. Its initial opacity is 75% and fades to 0%; its 16.5dp type size is 1.5 times the previous size and still scales from 100% to 50%. The impact ring lasts 240ms, starting at the enemy radius and expanding to 120%, with stroke width 30% -> 0% of that radius. Ten muted-ink rectangular fragments (the UI's #97978F grey) start at the enemy centre per hit; each travels within Θ ± 60° along the incoming projectile angle (for Θ=30°, the spread is -30°–90°), oriented along its travel direction. Initial length is 1.2–2.6 enemy radii, thickness is 28% of the radius (minimum 1dp), and speed is 25–70dp/s. Its length falls linearly to zero over 375–615ms, 1.5 times the previous lifetime, so fragments travel farther before disappearing. The health bar is twice its previous width, sits 8dp below the enemy, and uses neutral grey for both track and fill. These are independent hit effects, so a lethal hit can show both hit feedback and the existing death burst.

## Initial balance choices

| Parameter | Adopted value | Basis |
| --- | --- | --- |
| Drone HP / contact damage | 80 / 40 | First variant in the [official enemy table](https://alias.black/glyphica/wiki_english.html), checked 2026-10-30 |
| Repeater kinetic damage / burst count | 35 / 3 | [Japanese community Wiki](https://wikiwiki.jp/glyphica/リピーター), checked 2026-10-30; secondary-source values, not independently measured |
| Player HP | 300 | User's reference image; also appears in the official Wiki's health example |
| Drone speed / radius | 8.5 logical dp/s / 4.5dp | Prototype screen tuning; public absolute values not verified |
| Bullet speed / radius / shot interval | 520dp/s / 1.25dp / 120ms | Prototype screen tuning; public absolute values not verified |
| Enemy count / spawn | No immediate enemies; eight alive maximum; 120s envelope, 5s groups, random trickle | Prototype wave tuning; exact source-game formula not public |
| XP | 10 per kill; first threshold50, subsequent thresholds +20 | Prototype progression |

The default HP80 / damage35 requires three ordinary hits. A test injects damage40 to verify the user's two-hit-kill example still emits its third projectile. Crits, bleed and upgrades are deferred. The [lead developer's explanation](https://steamcommunity.com/app/2400160/discussions/0/591784375625107988/) confirms that the base Repeater has no range stat; bullets therefore leave only on contact or exiting the arena. Level changes update the HUD without weapon upgrades. Contact consumes a drone; HP0 stops combat and offers a tap to restart.

## Verification

Pure Kotlin tests cover straight flight, three shots surviving target death, interception, swept collision, XP, particles and retiring captions including composing input. Existing recognition tests protect the shared IME/streak behaviour. Build and physical-device UI smoke checks cover navigation, HUD and typing-to-shoot. Visual checks do not substitute for new human IME evidence.

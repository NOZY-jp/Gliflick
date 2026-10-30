# Repeater endless combat

2026-10-30. Implements the user's play-screen reference and normal drones only.

## Goal

Play opens combat immediately. A fixed central player, elapsed timer, level, linear and circular XP, and HP form the HUD. The lower weapon UI is deferred. Drones approach at constant speed; typing a caption queues a three-shot Repeater burst and replaces that caption.

## Structure

- `EnemyDefinition` separates kind, variant, shape and fixed balance. Each `Enemy` creates its own `EnemyBehavior` and `Durability`; pursuit and ordinary HP are the current implementations. Shields or immunity can replace durability without changing projectile collision.
- `MainWeapon` is the targeting/update contract; `RepeaterDefinition` and `ProjectileDefinition` hold its balance. Weapons emit `WeaponAction` commands; the world resolves those commands. A future beam or area effect adds a command/handler without changing input transport or the activity.
- `EnemySpawnDensity` makes a randomized one-enemy arrival decision from a smoothly varying rate. `EnemySpawnPolicy` independently selects the definition; `DroneEnemySpawnPolicy` chooses a tier and optional mutation. Neither decision belongs in the renderer or keyboard controller.
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

## Random enemy arrivals and entry captions

`EnemySpawnPolicy` chooses which definition appears; `EnemySpawnDensity` decides whether one enemy appears in the current simulation step. The arena starts empty and random arrivals are possible immediately. Each step can create at most one enemy. There are no groups, pulse intervals, or delayed first wave.

`RandomEnemySpawnDensity` samples an independent Poisson arrival chance each update: `1 - exp(-rate × dt)`. The rate starts at 0.9 enemies/second and moves smoothly between 0.8× and 1.2× over a 120-second density cycle. The player-level multiplier is `min(1 + 0.04 × (level - 1), 1.4)`. The living-enemy cap is 16; arrivals pause only while that cap is full, then resume on the next random decision. There is no separate interval timer or multi-enemy request.

The spawn rate and level multiplier are explicit, editable prototype tuning, not a recovered Glyphica formula. The current `Threat` value is a placeholder at zero and has no effect on spawn density.

Public sources only describe the original threat system at a high level. The [Glyphica wiki](https://alias.black/glyphica/wiki_english.html) says threat rises over a run, Endless compares it with a player-level adaptive value, and special/elite enemies have class caps. A [developer interview](https://redharegames.wordpress.com/2025/02/03/game-talk-glyphica/) confirms that a central threat formula changes difficulty and density and that enemy types can be controlled by time brackets, but it does not publish the formula or its conversion to spawn counts. This implementation therefore does not invent a Threat progression or claim to reproduce that formula.

Enemies begin outside one of the four viewport edges, at least their effective radius plus 12dp beyond it. Each enemy has a numeric `size` multiplier; effective radius is the base radius × tier body scale × mutation scale. Collision, spawn margins, body geometry and hit feedback use that effective radius. Every Drone tier is circular, and `EnemyMarkerPattern` reproduces the seven visual motifs in the [official Wiki tier images](https://alias.black/glyphica/wiki_english.html). The center disk stays the same size: Tier 2 adds an outer ring, Tier 3 adds another, and Tier 4 fills the band between those two rings. Total radius grows from Tier 1 through Tier 3, then stays at the Tier 3 radius for Tiers 4–7. Tier 5 cuts a center hole about the size of the shared core disk; Tiers 6–7 add segmented and ringed segmentation details. These layout proportions follow the user's visual description rather than the Wiki image crop dimensions. Caption and health-bar positions follow the effective enemy radius, while font sizes and health-bar dimensions stay fixed. Caption placement is selected once from the spawn origin and follows the enemy without viewport clamping; upper-half spawns put the text below and health bar above, with the inverse for lower-half spawns.

| Tier | Official image | Marker | Body size |
| ---: | --- | --- | ---: |
| 1 | [Drone.png](https://alias.black/glyphica/img/Drone.png) | Solid center disk | 1.00 |
| 2 | [Drone2.png](https://alias.black/glyphica/img/Drone2.png) | Center disk and outer ring | 2.00 |
| 3 | [Drone3.png](https://alias.black/glyphica/img/Drone3.png) | Center disk and two outer rings | 2.83 |
| 4 | [Drone4.png](https://alias.black/glyphica/img/Drone4.png) | Center disk with filled outer band | 2.83 |
| 5 | [Drone5.png](https://alias.black/glyphica/img/Drone5.png) | Ring with center hole | 2.83 |
| 6 | [Drone6.png](https://alias.black/glyphica/img/Drone6.png) | Segmented ring with center hole | 2.83 |
| 7 | [Drone7.png](https://alias.black/glyphica/img/Drone7.png) | Ringed segmented ring | 2.83 |

## Lifetimes and targeting

Completion queues three shots in FIFO burst order, with 120ms between shots. Each shot samples the living target's position once and then travels without homing. If the target has died, remaining shots use its last known aim point. Collision selects the earliest intersected live body, independently of the selected target; relative swept-circle tests prevent tunnelling through moving drones.

On word completion, the old prompt scales 1x to 2x about its own centre and fades over 420ms with ease-in/out. The replacement fades in over 240ms. Shared and nested word matching keeps the existing contract.

On enemy death, its body and inactive caption fade over 140ms. Ten independently sized square particles fly radially at random angles; their size follows 0 -> 1 -> 0. A partially typed caption is pinned to the death point, retired from new targeting, and retained until its existing input completes or fails. Its continuation prevents a false mistake while clearing the label, but awards no additional reading streak, overlap bonus, shot, or kill XP. Newly spawned words never consume already observed composition text.

## Hit feedback

When a projectile reduces an enemy's HP, `CombatWorld` records the enemy's interpolated centre at hit time, actual HP reduction, effective enemy radius, and incoming shot angle in one `HitFeedback`. The ring starts at that enemy's effective radius and expands to 120%, so a genuinely larger enemy also gets a proportionally larger ring. The ring and fragments start from the enemy-centre position rather than the projectile's contact point. `CombatRenderer` draws a small health bar under that enemy only while HP is below maximum; its fill uses current HP, and it follows the enemy's death fade. Health-bar placement follows enemy size, but its width and thickness stay fixed.

The damage number starts slightly above the enemy's top edge and drifts upward 18dp over 640ms. Its initial opacity is 75% and fades to 0%; its 16.5dp type size is 1.5 times the previous size and still scales from 100% to 50%. The impact ring lasts 240ms, starting at the enemy radius and expanding to 120%, with stroke width 30% -> 0% of that radius. Ten muted-ink rectangular fragments (the UI's #97978F grey) start at the enemy centre per hit; each travels within Θ ± 60° along the incoming projectile angle (for Θ=30°, the spread is -30°–90°), oriented along its travel direction. Initial length is 1.2–2.6 enemy radii, thickness is 28% of the radius (minimum 1dp), and speed is 25–70dp/s. Its length falls linearly to zero over 375–615ms, 1.5 times the previous lifetime, so fragments travel farther before disappearing. The health bar is twice its previous width, sits 8dp below the enemy, and uses neutral grey for both track and fill. These are independent hit effects, so a lethal hit can show both hit feedback and the existing death burst.

## Drone tiers and mutations

The [official enemy table](https://alias.black/glyphica/wiki_english.html) publishes seven Drone balance rows. In table order, their HP/contact damage are 80/40, 200/60, 500/90, 1,000/120, 1,800/150, 3,200/180, and 5,800/210. Absolute Drone speed is not published; all tiers therefore use the prototype base speed of 5.95dp/s, reduced from the earlier prototype value of 8.5dp/s.

The same Wiki says Drones can appear enlarged or hastened: Enlarged triples HP, while Hastened doubles movement speed. It gives no mutation probability and does not say that the two forms can co-occur. This project keeps them mutually exclusive as requested and uses a configurable 5% combined chance per Drone spawn (2.5% for each form). Tier body scales are 1.0×, 2.0×, then 2.83× for Tiers 3–7; Enlarged multiplies that by 1.5×. This changes effective radius and size-based effect geometry; caption and health-bar positions move to leave room, but their font sizes and bar dimensions remain fixed. The body scales and mutation probability are local tuning, not published values. Mutations do not change tier contact damage.

The Wiki describes Threat as rising during a run and says Endless uses the higher of Threat and a player-level adaptive value to determine spawn difficulty, but publishes neither the Threat formula nor tier thresholds. `DroneTierDistributionPolicy` leaves `Threat` untouched. Its progression is `clamp(max(elapsedSeconds / 120, (playerLevel - 1) / 4), 0, 6)` and its Gaussian center is `1 + progression`; spread grows from 0.4 to 0.7 tiers. Tier 1 is the only possible tier at progression zero. Each higher tier's Gaussian weight is multiplied by a smoothstep unlock ramp over one progression step, so stronger tiers enter the probability cloud gradually rather than appearing immediately or replacing every enemy on level-up. Existing enemies keep their stats. These rates are prototype tuning, not a claim about the source game's exact Threat formula.

The official [Update 12 announcement](https://steamcommunity.com/app/2400160/announcements/) says two stronger late-game Drone levels were added, but provides no balance values. The Wiki table still lists seven rows, so this implementation caps at tier 7 and leaves the two later rows out until their HP and contact-damage values are published or measured.

## Initial balance choices

| Parameter | Adopted value | Basis |
| --- | --- | --- |
| Drone tiers HP / contact damage | 80/40, 200/60, 500/90, 1,000/120, 1,800/150, 3,200/180, 5,800/210 | Seven rows in the [official enemy table](https://alias.black/glyphica/wiki_english.html), checked 2026-10-30 |
| Repeater kinetic damage / burst count | 35 / 3 | [Japanese community Wiki](https://wikiwiki.jp/glyphica/リピーター), checked 2026-10-30; secondary-source values, not independently measured |
| Player HP | 300 | User's reference image; also appears in the official Wiki's health example |
| Drone speed / base radius | 5.95 logical dp/s / 4.5dp | Prototype screen tuning; speed reduced to 70% of the previous 8.5dp/s. Enlarged uses 1.5× numeric size locally. |
| Drone tier appearance | Center disk stays constant; body scale 1 / 2 / 2.83, then remains 2.83 through Tier 7 | Outer circles are added through Tier 3; later tiers alter fill and holes without increasing the Tier 3 outer radius. Proportions follow the user's visual description. |
| Drone tier weights | Tier 1 only at start; smooth Gaussian-like cloud advances by 1 per 120s or 4 player levels | Each new tier's probability ramps in across one progression step. Exact source-game Threat formula is unpublished. |
| Bullet speed / radius / shot interval | 520dp/s / 1.25dp / 120ms | Prototype screen tuning; public absolute values not verified |
| Enemy count / spawn | Random single arrivals; 0.9/s base rate modulated 0.8×–1.2× over 120s; 16 alive maximum | Rate cloud and level multiplier are local tuning; there are no groups or interval timer. |
| XP | 10 per kill; first threshold50, subsequent thresholds +20 | Prototype progression |

The default HP80 / damage35 requires three ordinary hits. A test injects damage40 to verify the user's two-hit-kill example still emits its third projectile. Crits, bleed and upgrades are deferred. The [lead developer's explanation](https://steamcommunity.com/app/2400160/discussions/0/591784375625107988/) confirms that the base Repeater has no range stat; bullets therefore leave only on contact or exiting the arena. Level changes update the HUD without weapon upgrades. Contact consumes a drone; HP0 stops combat and offers a tap to restart.

## Verification

Pure Kotlin tests cover straight flight, three shots surviving target death, interception, swept collision, XP, particles and retiring captions including composing input. Existing recognition tests protect the shared IME/streak behaviour. Build and physical-device UI smoke checks cover navigation, HUD and typing-to-shoot. Visual checks do not substitute for new human IME evidence.

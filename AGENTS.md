# Input matching invariants

- Typing streak counts newly confirmed reading characters, not completed words or Roman key count. Small kana each count one. Shared input counts once: ちゃわんがん is 6, while ちゃわん + わんがん is 8. Keep these examples in regression tests.
- Word completion preserves other words' partial progress. Shared-input effect hooks come from overlapping completion intervals, not merely two consecutive successes. See docs/MistypeDesign.md for the implemented scoring contract.

## Title UI

- Read docs/TitleMenuDesign.md before changing the title menu. The Figma IME rectangle is a keyboard placeholder, not a product panel: the grid continues underneath it. Never render the IME label/grey rectangle as app UI.
- Pale menu letters mean not yet typed; dark letters show actual or simulated input. This is not a disabled-menu state. Keep the white/light-grey/charcoal tone and the intentional title Glyflick.
- Preserve the minimal composition: no visible instructional captions or retry explanations on the title screen unless explicitly requested. The Figma `Launch IME` action is shown only while the system keyboard is hidden. Completion scales 1× to 2× around the word's own fixed centre and fades opacity 100% to 0%.
- The latest Figma frame is `14:97`; its grid comes from `app/src/main/res/drawable-nodpi/menu_grid.png`, regenerated with `docs/verification/render-menu-grid.py`. Background circles and squares remain native shapes, independently animated outward and faded away during completion; do not flatten those shapes into the grid PNG.
- Tapping a menu command simulates one character every 50ms. Keep the four menu rows aligned at the Figma left edge and use their text lengths to form the rising/falling right edge.
- Preserve the completed title transition frame while the destination is covering TitleActivity; resetting MenuBackdropView during the outgoing onPause can redraw shapes that already faded away. Reset the menu and backdrop after returning to the title.
- Menu and gameplay share GameImeEditText and WordMatchEngine; menus only adapt static targets/navigation, and English belongs in TypingLanguage. Do not add a separate correctness algorithm or discard a failing character before considering it as a fresh word start. Keep armorarmory and continuously highlighted aaaaa in regression coverage. Instrumented notification replay is not human keyboard evidence.

## Play scene

- Read docs/EndlessCombatDesign.md before extending combat. Fixed definitions, per-enemy EnemyBehavior/Durability, MainWeapon implementations, enemy-definition selection (`EnemySpawnPolicy`), and randomized individual arrival density (`EnemySpawnDensity`) are separate extension points. Spawn density decides at most one arrival per simulation step; current living-enemy cap is `CombatConfig.maxEnemiesAlive = 16`. Do not add group counts or an interval timer. Drone tiers start at Tier 1 only for 60 seconds, then slowly add Tier 2; Tier 3+ unlock one step per boss-defeat notification through `CombatWorld.notifyBossDefeated()` (the boss is not implemented yet). Keep the current `Threat` value unused until its source formula is available; the player-level spawn multiplier is separate prototype tuning. Enemies begin outside the viewport and captions follow their positions immediately without viewport clamping. Enemies whose spawn origin is in the upper half place captions below and health bars above; keep this layout fixed for that enemy's lifetime. CombatWorld stays free of Android views and keyboard transport; CombatRenderer never decides matching, damage or spawning.
- Combat words use WordDefinition.readingLength, derived from the Japanese reading's Unicode characters, with small kana counted individually. Keep the 2–7 reading-length pool and its time-based WordLengthPolicy separate from enemy and weapon logic; the schedule and distribution are documented in docs/EndlessCombatDesign.md.
- Completing a word queues an attack and replaces that caption; only HP depletion counts as a combat kill. The shared WordMatchEngine remains the correctness boundary.
- A dead enemy's partially typed caption may only continue its already observed input, including pending IME composition. It cannot begin fresh matches, fire, earn new reading streak or overlap rewards. Removing/spawning an enemy must not fabricate a mistype or replay old input into a new target.
- Every projectile freezes its own direction at emission and uses earliest swept collision, including non-target enemies. A queued burst survives target death and fires remaining shots toward the last known point.
- Damage feedback starts at the enemy's interpolated centre at hit time and is created from that position, damage value, and incoming projectile angle in CombatWorld; muted-ink fragments travel at Θ ± 60°. CombatRenderer only draws it. Show enemy health bars only below maximum HP and keep impact numbers, rings, and fragments tied to that hit's lifetime.

- Level-up selection and secondary weapon work must read docs/LevelUpDesign.md. WeaponCatalog owns upgrade data; SecondaryWeapon controllers own activation; CombatWorld owns damage/XP/pickups; renderers only present. Pause all simulation clocks during choices and preserve queued rewards. Glyph triggers use the dictionary's standard Roman spelling equally for kana and Roman input; this is game-rule metadata, never reconstructed physical input or a new matcher stream.

## Version control

- This checkout is a colocated Git/Jujutsu repository. Use jj status/diff to review and jj describe/new to preserve completed changes; keep the local main bookmark current.

## Approval gate

- On 2026-10-26 the user approved implementing all requirements in InputFlowApproval.md v1. Follow its evidence-first sequence: capture real target-IME input before the decoder, then implement based on observed data. Do not substitute guessed callback streams for that evidence or silently change keyboard requirements.
- For IME bugs, replaying assumed InputConnection messages does not prove the real keyboard sends those messages. Record actual target-IME notifications before the decoder while a person types the cases, and distinguish that evidence from synthetic tests.
- The si/shi distinction and immediate defeat were physically verified with the user's Simeji Japanese QWERTY in docs/verification/ime-human-fixed-r2.jsonl. Do not generalize this to unmeasured IMEs or report physical verification based solely on notification replays.

- Never manufacture missing Roman keystrokes or rewrite received input into a preferred spelling. Preserve sourceText verbatim for diagnostics. The game display uses inputText: the uniquely confirmed recognition path, or the actual kana when no Roman path is uniquely established. Never feed that presentation string back as input tokens.
- IME kana observations may confirm a single language-defined terminal: vowel, length mark, or the measured nasal n → ん transition with no following prefix. They cannot supply arbitrary unseen consonants. Preserve the observed prefix: s → し confirms si, sh → し confirms shi. Unknown multi-syllable rewrites must not claim canonical Roman input.
- Japanese Roman word-final ん requires nn. Optional internal nn defaults to n and switches only after the second n. In the measured Simeji trace, n → んp confirms n before p, while n → ん confirms nn; preserve that distinction. Keep this in shared language rules so matching and prompts agree.
- IME kana replacements are observations with the previously observed Latin prefix. Match those constraints against the input graph; do not expand them into a guessed input stream.
- A target's default spelling stays visible while compatible with observed input: s keeps shippai; si switches to sippai. Do not choose a shorter alternative before actual input rules out the default.
- Regression checks for keyboard fixes must cover raw Latin and the relevant IME composition snapshots, including the resulting display and completion event.

<!-- LLMWIKI:START -->
# LLMwiki

- Vault: `\\100.104.189.113\e\home\wiki`
- マスター指示: `\\100.104.189.113\e\home\wiki\WIKIROOT.md`
- 共通ルール: `\\100.104.189.113\e\home\wiki\_shared\AI_SHARED_RULES.md`
- ワークフロー: `\\100.104.189.113\e\home\wiki\_shared\WORKFLOWS.md`
- Wiki 品質: `\\100.104.189.113\e\home\wiki\_shared\WIKI_QUALITY_SYSTEM.md`
- ユーザー設定: `\\100.104.189.113\e\home\wiki\preferences\index.md`
- プロジェクトノート: `\\100.104.189.113\e\home\wiki\projects\glyphica-android.md`
- プロジェクト索引: `\\100.104.189.113\e\home\wiki\projects\index.md`
- 作業資料カテゴリ索引: `\\100.104.189.113\e\home\wiki\materials\index.md`
- 作業資料索引: `\\100.104.189.113\e\home\wiki\materials\glyphica-android\index.md`
- 作業資料ディレクトリ: `\\100.104.189.113\e\home\wiki\materials\glyphica-android`
- 当日の日報: `\\100.104.189.113\e\home\wiki\daily\2026-10-26.md`
- 知見: `\\100.104.189.113\e\home\wiki\knowledge`
- 意思決定: `\\100.104.189.113\e\home\wiki\decisions`
- セッション: `\\100.104.189.113\e\home\wiki\daily\sessions`
- テンプレート: `\\100.104.189.113\e\home\wiki\Template`

## 作業開始

変更前に共通ルール、ワークフロー、Wiki 品質、ユーザー設定、プロジェクトノート、当日の日報（存在する場合）、関連知見を読むこと。作業資料はカテゴリ索引、プロジェクト索引の順に読んでからディレクトリを一覧し、今回必要な原稿を読むこと。人間の原稿は明示的な編集依頼なしに変更しない。

## 作業終了

意味のある作業の後、後日の再開に必要な状態と判断を `projects`、`decisions`、`knowledge`、必要な場合だけ `daily/sessions` へ反映すること。訂正では誤った正本を置換し、訂正用の追記を残さないこと。
<!-- LLMWIKI:END -->

# 武器撃破による進化

2026-10-31 / project/glyphica-android

## 条件と操作

Repeaterと全11サブ武器は、最後のHPを削った武器へ1撃破を帰属する。状態異常による撃破も、その状態の発生元へ帰属する。単語の完成、自機への接触による敵消滅、死体への追加入力は撃破に数えない。

公式の[公開デモ](https://playglyphica.com/glyphica/index.html)の`Weapon`を確認し、各段階で50、100、150撃破、累計50・150・300撃破で進化選択を1回ずつ保留する。通常は一周につき3回。以前のRepeaterの「累計50刻み・枝の全ランクを取得可能」という実装を置き換える。通常レベルアップで各武器の進化カウント+10/+20/+30も取得できる。

画面上の`武器進化 [␣] ×n`から選択を開く。進化は通常レベルアップより優先し、装備順で一つずつ処理する。複数の進化とレベルアップは失われない。選択・リロール中はシミュレーションを止め、リロールしても対象武器を変えない。各装備枠下の細い線は次の進化までの進捗、枠右上の小さな数字は取得済み進化回数を示す。

## 責務と選択肢

- `WeaponRunState`：一周の進化カウント、段階、枝のランク、通常・全体・一時補正。
- `WeaponEvolution`：進化の共通契約。Repeaterは既存enum、サブ武器は`SecondaryEvolution`の43枝。I取得後だけIIが候補に入り、最大ランクを再提示しない。
- `EvolutionCatalog`：候補と前提条件。MeteorのSurveillanceとRelentlessは相互排他。
- `EvolutionEffects`：数値補正と属性変換。全ドローン・タレット・リモートへの補正はUI文字列ではなく`WeaponFamily`で適用し、取得済み・後から取得する武器の両方に効く。
- 各controller / actor：生成数、強化機、追尾、連鎖、折り返し、継続爆撃、実線テザーを実装。描画は効果を決定しない。
- `CombatWorld`：撃破帰属、属性・状態異常、全体効果、追加発動文字の共有割り当て、特殊燃料と一時ダメージ2倍。

未実装の盾破壊が前提のSentry Emergency Protocolと、未実装のSplatter Shotが前提のMinefield Dirty Bombは候補に含めない。通常プレイで作用しない進化を出すより、前提システムの導入時に追加する。

## サブ武器の進化

名称・公開数値は[公式Wiki](https://alias.black/glyphica/wiki_english.html)を基準にし、[日本語Wiki：Spectre](https://wikiwiki.jp/glyphica/スペクター)、[Chopper](https://wikiwiki.jp/glyphica/チョッパー)、[Steamコミュニティガイド](https://steamcommunity.com/sharedfiles/filedetails/?id=3353482070)と照合した。以下は実装した枝の索引で、正確なランク別効果は`SecondaryEvolution`にまとめる。

| 武器 | 進化枝 |
| --- | --- |
| Spectre | Supercharged / Self Destruct I–II / Volatile Fuel I–II / Stalking Algorithms |
| Sentry | Defense Grid I–II / Solid Core I–II / Watcher Supreme |
| Tesla Mine | Abundant Coils I–II / Electrical Burn I–II / Restricted Area I–II / Storm Sovereign |
| Oscillator | Twin-Headed / Ball Lightning I–II / Stabilized Plasma I–II / Prime Resonance |
| Meteor | Surveillance I–II / Ice Rain I–II / Relentless I–II / Sky Spotter |
| Hunter | Hunter Elite / Electrified Bolts I–II / Chain Lightning I–II / Predator Strain |
| Chopper | Dual Wield / Flaming Blades I–II / Boomerang I–II / Bloodbath I–II / Saw Launcher I–II |
| Minefield | Minelayer I–II / Infusion I–II / Hunter Seeker I–II / Recursive Mines I–II |
| Blade Trap | Blade Repository I–II / Poison Coating I–II / Sawtooth Blades I–II / Cutting Edge |
| Orbiter | Continuum I–II / Storm Ring I–II / Doppelganger / Tether I–II |
| Guardian | Toxic Ward I–II / Experimental Fuel I–II / Guardian Team I–II |

追加発動文字はデモの`english.triggers`のy/z、v/wを使用する。この二組は一周で共有し、一組を取得した武器だけの条件へ追加する。他武器が次に取得すると残りの組を得る。両組取得済みなら追加文字の枝を候補から外す。日本語の入力方式にかかわらず辞書の標準ローマ字で条件を判定する既存方針を維持する。

Superchargedは取得後の生成20機ごと、Hunter Eliteは12機ごと。通常機と区別したインスタンスを作り、強化機のダメージ・持続・速度とHunterの射程を補正する。生成カウンターは武器controllerにあり、再描画や入力方式で変化しない。

Experimental Fuelは出現時に種類を決め、公式燃料アイコンと短いラベルで示す。8秒間、ダメージ+50%・速度+100%・範囲+50%のいずれか一つ。別種類の補給は前の効果を置き換える。IIでは効果と持続が1.5倍、12秒になる。全Guardianを補給し、燃料と強化時間は選択中に減らない。

## 資料間の差と試作値

公開デモは[配信スクリプト](https://playglyphica.com/glyphica/dist/scripts/core/main.js)の`Weapon.getEvolution`、各武器の進化条件、`on_render`とモデル定義を確認した。参照時のSHA-256は`BC625353FB2900AADB5DC30B70048228BFF4958BB23C4060DF70F2AC9AFA7E42`。ゲーム本体のコードはリポジトリへ複製しない。

- デモの進化カウントは残りカウントを次段階へ持ち越し、次の必要数を増やす。累計50/150/300はこの処理から得た値。Steamガイドの50/100/150という説明だけから累計値と解釈しない。
- Flaming Blades IIは公式Wiki・デモ説明の熱+10を採用。デモの効果コードは+12で食い違う。
- Saw LauncherはWiki説明の小型2枚、IIで+2枚を採用。デモは3枚・5枚で異なる。Repeater出血率のII加算もWikiの+12ポイントを採用する。
- Recursive Minesはデモの720フレームを60fpsで12秒と換算。基礎地雷数もデモの3へ揃え、半数切り捨ての初回が実際に1個生成されるようにした。
- 前回調整したエリア型のサイズ1.5倍を維持する。射程のdp換算、弾速、爆発の半径、攻撃間隔、Bloodbathのダメージ2倍の10秒持続は本作の試作値。Steam最新版との完全一致は保証しない。
- 属性別の全体補正は共通ダメージ経路に適用し、Poison Coatingは追加ケミカル打撃として扱う。既存Repeaterの複合属性合算という簡略化は残る。装甲・耐性・盾は今後別の防御モデルで拡張する。

## 外観と検証

43枝のカード字形は公式`Evolution-Icons-Glyphica.ttf`から`evolution_icon_*.svg`へ輪郭を変換。攻撃体はカード字形を流用しない。強化Hunterは公開デモの離れた二枚翼、強化Spectreは太い軌跡・中心点・外周円、Saw Launcherは半径1/.75/.2の12歯モデルを専用SVGとCanvasで描く。Storm Ringはオーブと軌跡の電撃、Tetherは実線で効果範囲を示す。

単体では段階境界・上限・撃破帰属・全枝の取得・複数報酬・武器固定リロール・全体補正・追加文字共有・特殊燃料・貫通・連鎖・折り返し・逆回転・自爆・追尾・自動生成・状態異常を確認する。Androidでは全12武器の進化カードと選択適用、11サブ武器の進化後の実Canvas描画を確認する。記録は[検証結果](verification/evolutions-20261031/README.md)。

---
type: design
status: active
updated: 2026-10-31
tags: [project/glyphica-android, weapons, visuals]
---

# ゲーム中の武器アクターの外観

- Updated: 2026-10-31
- Project: glyphica-android
- Scope: 現行の11種。カード・装備枠のアイコンとは別の描画契約。

## 参照した実物

[公式Steamページ](https://store.steampowered.com/app/2400160/Glyphica_Typing_Survival/)のスクリーンショットとAnniversary Trailer、[公式プレスキット](https://www.squeakywheel.ph/glyphica-presskit)のgameplay2、[Guardian紹介GIF](https://clan.fastly.steamstatic.com/images/44862715/e82a750f09dfde8ba44c3c9863e530be33e0cd22.gif)、[Orbiter紹介GIF](https://clan.fastly.steamstatic.com/images/44862715/2408213a03f42da41abd44494be99ad011eed0e0.gif)を目視した。Hunterの三角形と電撃はトレーラー22秒付近で拡大されている。

[公式Webデモ](https://playglyphica.com/)はゲーム中の図形を手続き的に描いている。[配信されている描画スクリプト](https://playglyphica.com/glyphica/dist/scripts/core/main.js)の下表のクラスも照合し、画像だけでは判別しにくい刃の枚数や軌跡の意味を確定した。ゲームのソース全体や画像を製品へコピーせず、幾何形状と動きを独自のKotlin／SVGで再構成する。WebデモとSteam版が同一バージョンであるとは扱わない。

## 全11種の対応

編集対象は`app/src/main/res/raw/weapon_actor_<name>.svg`。すべて薄いインク色で表示し、敵の赤と区別する。

| 武器 / SVG末尾 | 原作で確認した形と動き | 描画の参照クラス |
| --- | --- | --- |
| Spectre / spectre | 通常型は移動履歴10点ほどの細いフェード軌跡。四角いアイコンや固定の本体はない。敵を追って飛び続ける | SpectreDrone.on_render |
| Hunter / hunter | 前後に長い三角形の輪郭。先端から敵へ折れ線状の電撃 | HunterDrone.on_render / on_renderStart |
| Sentry / sentry | 自機外周に取り付けられた小さな塗りつぶし三角形。弾は小さな輪と輪の残像 | SentryTurret / ProjSentry / ProjSentryTrail |
| Tesla Mine / tesla_mine | 三角形の輪郭、薄い範囲円、敵へ伸びる電撃 | TeslaMine |
| Oscillator / oscillator | 揺れる列を作る小さな円形粒子。Repeaterの長いトレイルは付けない | ProjOscillator |
| Meteor / meteor | 短い径方向の4本線と4つの分割円弧の照準。斜め上から入る線と広がる爆発 | MeteorTarget / MeteorLines |
| Chopper / chopper | 互いに反対を向く長い2枚刃、薄い回転残像。Blade Trapと別形状 | chopperModel / ChopperInstance |
| Minefield / minefield | 固い小円を中心に3本の短いスポークが回り、薄い円で範囲を示す | MineDrone |
| Blade Trap / blade_trap | 穴のある小さい中心輪と、4本の長い棒状の刃・先端 | BladeTrap.on_renderStart |
| Orbiter / orbiter | 大きめの半透明の塗り円と小さくなる円の残像。完成した敵の地点から自機の周りを回る。自機から球までは薄い破線 | ProjOrbiter |
| Guardian / guardian | 方向を示す三角形をくり抜いた小円、周囲の破線軌道、対向して回る2個の輪状火球と残像、燃料バー | GuardianDrone.on_renderStart / on_render |

`WeaponIconRenderer.actorResource()`は全武器を列挙し、カードアイコンへの汎用フォールバックを持たない。静的シルエットは単一のSVG pathとviewBoxで編集できる。輪郭や切り抜きはevenoddの複合path。SpectreのSVGは軌跡の1区間として伸縮し、移動履歴に沿って並べる。

燃料Pickupの`weapon_pickup_fuel.svg`は、デモが実際に使う`Pickup-Icons-Glyphica.ttf`のU+003Dから輪郭を変換した。WikiのGuardian武器アイコンをPickupへ流用しない。参照スクリプト取得時のSHA-256は`BC625353FB2900AADB5DC30B70048228BFF4958BB23C4060DF70F2AC9AFA7E42`。

## 動きと責務

`WeaponActor`は60Hz相当の12点の移動履歴を保持する。`CombatWorld`がシミュレーション更新後に記録し、描画側で位置を作らない。SpectreとHunterの移動は`DroneActor`、Guardianの追跡・自身の火球・燃料は`GuardianActor`に分離する。

Spectreは近接型にも移動処理を適用し、ターゲット付近で停止せず旋回する。Hunterは射程に入るまで移動し、三角形の先端から電撃を表示する。GuardianはWikiのレーザー説明との混成をやめ、公開されている実際のゲーム映像・デモで一致する周回火球を採用した。Orbiterは球の半径と公転半径を分け、範囲強化は球の大きさへ作用させる。

本作のdpサイズ・速度・寿命は引き続きモバイル用の設定である。公開デモには基礎値も含まれているため、Wikiで見つからない値を「公開されていない」とは断定しない。公式デモのフレーム単位の値をSteam版の秒数や本作のdpへ無条件で置き換えない。

## 確認

`SecondaryWeaponMotionTest`はSpectreの中央から遠方への移動・接触後の継続飛行・専用ダメージ、Hunterの移動と射撃、Orbiterの起点と公転、Guardianの追跡・火球・燃料停止を確認する。`WeaponVisualInstrumentedTest`は更新した実際のCombatActivityで11種を個別に発動し、画像を保存する。制御されたシーンでのAndroid描画確認であり、人のIME入力の根拠ではない。

画像と結果は[外観確認](verification/weapon-visuals-20261031/README.md)へ記録する。

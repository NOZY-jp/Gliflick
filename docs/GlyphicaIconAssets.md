# Glyphica公式アイコンのSVG

## 出典と変換

[Glyphica公式Wiki](https://alias.black/glyphica/wiki_english.html)の各武器見出しは `Title-Icons-Glyphica.ttf` の字形を、Repeater進化一覧は `Evolution-Icons-Glyphica.ttf` の字形を使っている。Wikiに表示されるコードポイントの輪郭をSVGの`path`へ変換した。アプリへはフォントを埋め込まず、編集できるSVGをそのまま`res/raw`から読み込む。

武器アイコンはWikiのアクセント色で保存。字形の外形自体が色面となり、内部の線や切り欠きは透明になる公式の表示方式に合わせた。レベルアップカード、戦闘中の設置物、下部の装備枠で同じSVGを使い、描画時だけコンテキストに合わせて色を変える。

## 武器アイコン

| WeaponId | SVG | 公式Wikiの字形 |
| --- | --- | --- |
| REPEATER | `app/src/main/res/raw/weapon_icon_repeater.svg` | U+EC14 |
| SPECTRE | `app/src/main/res/raw/weapon_icon_spectre.svg` | U+EC13 |
| SENTRY | `app/src/main/res/raw/weapon_icon_sentry.svg` | U+EC15 |
| TESLA_MINE | `app/src/main/res/raw/weapon_icon_tesla_mine.svg` | U+EC16 |
| OSCILLATOR | `app/src/main/res/raw/weapon_icon_oscillator.svg` | U+EC2F |
| METEOR | `app/src/main/res/raw/weapon_icon_meteor.svg` | U+EC1B |
| HUNTER | `app/src/main/res/raw/weapon_icon_hunter.svg` | U+EC2C |
| CHOPPER | `app/src/main/res/raw/weapon_icon_chopper.svg` | U+EC2D |
| MINEFIELD | `app/src/main/res/raw/weapon_icon_minefield.svg` | U+EC30 |
| BLADE_TRAP | `app/src/main/res/raw/weapon_icon_blade_trap.svg` | U+EC4E |
| ORBITER | `app/src/main/res/raw/weapon_icon_orbiter.svg` | U+EC4D |
| GUARDIAN | `app/src/main/res/raw/weapon_icon_guardian.svg` | U+EC4C |

通常の武器アップグレードは、カードにその武器のアイコンを表示する。公式Wikiの通常強化表は名称・効果を掲載し、強化ごとの専用イラストは示していないため、別の記号を作り足さない。

## Repeaterの進化アイコン

レベルアップカードは従来の武器アイコン、進化カードは各進化の専用アイコンを使う。

| RepeaterEvolution | SVG | 公式Wikiの字形 |
| --- | --- | --- |
| FROST_FANG | `app/src/main/res/raw/evolution_icon_frost_fang.svg` | U+EC13 |
| RICOCHET | `app/src/main/res/raw/evolution_icon_ricochet.svg` | U+EC12 |
| SHOTGUN | `app/src/main/res/raw/evolution_icon_shotgun.svg` | U+EC15 |
| EXPLODING_BULLETS | `app/src/main/res/raw/evolution_icon_exploding_bullets.svg` | U+EC3B |

アプリは`WeaponIconRenderer`でSVGのviewBoxとパスを読み、Canvasへ描画する。パスを更新すると、ビルド後のすべての表示箇所へ反映される。ファイル名、viewBox、path要素を保ち、パス形状は自由に編集できる。

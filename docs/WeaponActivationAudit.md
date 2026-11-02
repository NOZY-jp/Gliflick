# 武器の発動条件の再照合

2026-11-02。情報の優先順は公式Wiki、公式配信Webデモの実際の処理、日本語非公式Wiki。デモとSteam現行版の版差、および原作の画面単位と本作のdp換算は分けて扱う。

- [公式Wiki](https://alias.black/glyphica/wiki_english.html)
- [公開デモ](https://playglyphica.com/glyphica/index.html)・[配信スクリプト](https://playglyphica.com/glyphica/dist/scripts/core/main.js)
- [日本語用文字定義](https://playglyphica.com/glyphica/dist/localization/japanese.triggers)・[英語用文字定義](https://playglyphica.com/glyphica/dist/localization/english.triggers)
- [日本語Wiki：ハンター](https://wikiwiki.jp/glyphica/ハンター)・[チョッパー](https://wikiwiki.jp/glyphica/チョッパー)

取得したmain.jsのSHA-256は`BC625353FB2900AADB5DC30B70048228BFF4958BB23C4060DF70F2AC9AFA7E42`、japanese.triggersは`301DD2584AF6BDB67F56980A7CB943FD613D296060872E6D3C7E2C1C21B8A3C0`。配信ソースそのものはビルド用の無視対象へ保存し、製品や公開リポジトリへ複製しない。

## 文字条件

| 武器 | 確認できた発動条件 |
| --- | --- |
| Spectre | 完成語中のa/e/oの出現数だけ生成 |
| Hunter | 完成語中のi/o/uの出現数だけ生成 |
| Tesla Mine | b/c/d/eで始まる完成語 |
| Minefield | f/g/h/iで始まる完成語 |
| Blade Trap | j/k/l/mで始まる完成語 |
| Orbiter | n/o/p/qで始まる完成語 |
| Chopper | 主武器発射時、使用可能な刃の枠がある場合 |
| Sentry / Guardian | 取得時に生成。Guardianの活動には燃料が必要 |
| Oscillator | 自動発射。完成語は向きを更新 |
| Meteor | 自動出現する照準の語を完成すると発動 |

固定文字の条件自体は誤りではない。公開デモの日本語用・英語用定義も上記の文字群で一致した。本作では入力方式による有利不利を避け、引き続き辞書の標準ローマ字を条件にする。入力に含まれただけで発動せず、敵の語の完成イベントで一度処理する。

生成座標はSpectre / Hunter / Tesla / Blade / 入力起動Minefield / Orbiterについて、公開デモのonWordCompleteが受け取る完成対象のgetPosition()。本作もその時点の敵座標へ修正。Teslaだけに存在した独自の5秒召喚禁止時間も廃止。再帰地雷の自動生成は引き続き別経路。

## Chopper

デモのChopperは初期choppersReady=1。主武器の発射時に1枠消費し、場外へ抜けるかBoomerangが帰還してから返却する。Dual Wieldは使用可能枠を一つ増やす。同じ単語で2枚を同時発射する効果ではない。Saw Launcherは同時発射した鋸の全てが終了してから1枠を返却する。本作はflight単位でactor群を保持し、稼働中の単語完成で無制限に刃を追加しない。鋸数は従来どおり公式Wiki側の2/4を採用し、デモの3/5とは区別する。

## Hunter

公開デモの基礎電気ダメージ10、rateOfFire=120、duration=350を参照。120回/分は0.5秒周期、活動350フレームは60fps換算約5.83秒。従来の0.3秒周期と7秒寿命を修正した。基礎ビームDPSは33.3相当から20相当へ変わるが、これは任意の難易度調整ではなく公開定義との照合結果。強化・スタティックなどの別ダメージは加算され得る。

生成地点から20フレーム相当で周囲へ展開し、その後活動時間を消費する。射程内では移動を止め、照準が合っている間ビームを表示し、ダメージだけ周期で与える。Electrified Boltsは周期が2倍。Hunter Eliteは基礎ダメージ2倍・活動時間1.5倍。射程70dp、速度75dp/秒、展開距離28/45dpは本作の画面換算であり、Steam版の確定寸法とはしない。原作の±1ダメージ乱数、Elite生成前の追加待機、2つの独立ヒットは今回再現せず、Eliteは合算20ダメージで処理する。

## 入力専用の報酬選択

CombatViewのカードヒット選択経路、CombatSession.tapUpgrade、UpgradeSelectionのタップ自動入力を撤去。表示語の完成だけが報酬を決める。Spaceで開く・リロール、リロールボタン、通常タイトルのタップ入力は維持する。

検証は[エミュレーター記録](verification/weapon-rules-20261102/README.md)を参照。

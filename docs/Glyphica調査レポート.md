---
title: Glyphica: Typing Survival 調査レポート
status: research
created: 2026-10-26
tags:
  - project/glyphica-android
  - android
  - game-design
---

# Android Studio のアプリ用・ゲーム用ひな形と Glyphica 調査レポート

- 調査日: 2026-10-26
- 対象: Android Studio 実験用 `TestGame` の描画方式選定と、Glyphica の基本ループ・序盤仕様
- 状態: 公開情報をもとに整理。敵出現の秒単位スケジュールなど、公開資料で確認できない数値は推定していない。

## 1. 普通のアプリ用ひな形とゲーム用ひな形

Android Studio のひな形は「普通のアプリ」と「ゲーム」の二択に固定されているわけではありません。Android Studio には Compose / Views の画面ひな形や、C/C++ 対応プロジェクトなど複数の入口があります。Android公式ドキュメントは、Empty Compose Activity を Compose アプリの一般的な出発点として案内しています。ゲームも、作る内容に応じて通常の Activity、Canvas、OpenGL、GameActivity、Unity / Godot 等のゲームエンジンから選びます。[Android Studio のテンプレート](https://developer.android.com/studio/projects/templates) / [プロジェクト作成](https://developer.android.com/studio/projects/create-project)

| 観点 | 一般的なアプリ用ひな形 | `TestGame` の元のひな形 | 今回用意した Canvas ひな形 |
| --- | --- | --- | --- |
| 中心となる処理 | 画面を表示し、ボタン・入力・画面遷移などのイベントへ応答 | C/C++ のゲーム処理を Android の `GameActivity` から起動 | Kotlin の `SurfaceView` で `Canvas` を繰り返し描画 |
| 画面の作り方 | Compose または View / XML。レイアウトや標準 UI 部品を利用 | EGL と OpenGL ES で描画。頂点・シェーダー・テクスチャ等を扱う | `drawCircle` / `drawText` などで図形・文字を直接描く |
| 入力 | タップ、文字入力、ボタン等を画面部品ごとに受ける | `GameActivity` が C/C++ 側へタッチ・キー・テキスト入力を渡す | `MotionEvent` を受けて、描画中のゲーム状態へ渡す |
| 時間の進め方 | 画面イベントや状態変更のたびに再描画する構成が多い | ゲーム側で更新・描画ループを組む | 更新 (`update`) と描画 (`drawFrame`) を約60回/秒で呼ぶ |
| 向いている用途 | 設定画面、一覧、フォーム、学習記録など | C/C++ や高度な 2D / 3D 描画、既存ゲームエンジンとの統合 | 今回のような小規模 2D ゲーム、実験で描画や当たり判定を学ぶ用途 |

元の `TestGame` は `GameActivity`、Android NDK、CMake、EGL / OpenGL ES 3 のサンプルを含む、C/C++ 中心の構成です。`GameActivity` は C/C++ ゲームのライフサイクルや入力を扱いやすくするための Android ゲーム開発ライブラリで、Android の標準 `Activity` を置き換えるものです。これはゲーム全般に必須ではありません。[GameActivity 公式ガイド](https://developer.android.com/games/agdk/game-activity) / [AGDK](https://developer.android.com/games/agdk/get-agdk)

今回の `CanvasSample01` は `SurfaceView` の有効期間に合わせて描画スレッドを動かし、`lockCanvas()` で Canvas を取得して描く方式でした。これを Kotlin 側へ移し、NDK を必要としない構成にしています。`SurfaceView` は別スレッドから描画できる一方、Surface が作成されてから破棄されるまでの間だけ描画スレッドが触る必要があります。[SurfaceView API](https://developer.android.com/reference/android/view/SurfaceView) / [Canvas API](https://developer.android.com/reference/android/graphics/Canvas)

### Processing に近い感覚で書けるか

はい。画面上の位置や速度を状態として持ち、毎フレーム「状態を更新する → Canvas に描く」と書くため、Processing の `draw()` ループに近い感覚で 2D 表現を作れます。円・線・文字・画像を描き、タップ位置に応じて状態を変えるところから始められます。

違いは、Processing のような実行環境が自動で用意する部分も Android 側では自分で扱うことです。具体的には Surface の生成・破棄、描画スレッドの停止、タッチイベント、画面サイズと密度、アプリが一時停止したときの描画停止です。今回の `MySurfaceView.kt` はそれらを薄く包み、ゲーム側で触る場所を `CircleSprite.update()` と `CircleSprite.draw()` に分けています。`SurfaceView` と `Canvas` の詳しい API は上記 Android 公式資料を参照してください。

## 2. Glyphica: Typing Survival の概要

Glyphica は、文字入力とホードサバイバルを組み合わせた一人用ローグライトです。敵の上に表示された単語を入力して主武器の標的を決め、押し寄せる敵を処理します。単なるタイピング速度テストではなく、敵の優先順位、武器、強化の組み合わせを考えるアリーナ型アクションです。Steam の説明では1回のプレイを約10〜20分とし、速度が高いほど攻撃力を発揮しやすい設計を掲げています。[Steam ストアページ](https://store.steampowered.com/app/2400160/Glyphica_Typing_Survival/)

Steam 版は2024年10月9日に Early Access として公開され、調査日現在もストアページ上で Early Access と表示されています。開発者のゲーム内 Wiki と更新告知は、武器・Omens（難易度修正）・Armory などが更新され続けていることを示します。そのため、ここでは「発売時のデモ」ではなく、調査日に参照できた現行公開情報を基準にしています。[Steam ストアページ](https://store.steampowered.com/app/2400160/Glyphica_Typing_Survival/) / [公式更新告知](https://steamcommunity.com/app/2400160/announcements/)

## 3. 基本のゲームループ

1. **開始前にランの土台を選ぶ。** Trial または Endless を遊び、使える主武器やアンロック済みの永続強化を準備します。2026年8月の Armory 更新では、武器や Augment を一覧し、コインで恒久強化する機能が追加されました。Augment はラン開始時に持ち込む枠数の範囲で装備します。[公式 Wiki](https://alias.black/glyphica/wiki_english.html) / [Update 12: The Armory](https://steamcommunity.com/app/2400160/announcements/)
2. **敵語を入力して狙う。** 画面に現れた敵の語を入力すると主武器がその敵を標的にします。敵を倒すには語入力だけでなく武器の攻撃力も必要です。敵の種類には主武器へ向かって移動する Drone、弾を撃つ敵、攻撃をチャージする Shooter などがいます。[公式 Wiki](https://alias.black/glyphica/wiki_english.html) / [Steam ストアページ](https://store.steampowered.com/app/2400160/Glyphica_Typing_Survival/)
3. **撃破とレベルアップで構成を作る。** 敵を倒すと経験値を得て、レベルアップ時に武器や強化を選びます。主武器とは別に最大4つのレベルアップ武器を追加でき、武器ごとに異なる発動条件があります。特定の文字を打つと発動する武器や、入力に合わせて自動的に攻撃する武器があります。[公式 Wiki](https://alias.black/glyphica/wiki_english.html) / [初心者向け Steam ガイド](https://steamcommunity.com/sharedfiles/filedetails/?id=3353482070)
4. **ドロップを回収して選択を広げる。** 敵や宝箱から出る回収物にはそれぞれ語が表示され、プレイヤー向けガイドによれば制限時間内に入力すると回収できます。Loot は候補から選び、そのラン中に有効なパッシブ効果を得ます。Shield、Freeze、Double Damage、Reroll、Health などもあります。[初心者向け Steam ガイド](https://steamcommunity.com/sharedfiles/filedetails/?id=3353482070) / [公式 Wiki](https://alias.black/glyphica/wiki_english.html)
5. **敵の圧力が上がる。** 公式 Wiki は敵出現が「Threat」に基づき、時間とともに難しくなると説明しています。Trial は Trial レベルに応じて脅威が増し、Endless はプレイヤーのレベルを使った適応スケーリングと比較して出現難度を決めます。Trial の終わりにはボスが待ち受けます。[公式 Wiki](https://alias.black/glyphica/wiki_english.html) / [Steam ストアページ](https://store.steampowered.com/app/2400160/Glyphica_Typing_Survival/)

## 4. 序盤で押さえる仕様

### 最初の数分に起きること

- ランの開始後、主武器を軸に敵と戦い、敵の語を入力して標的を切り替えます。主武器は入力速度や狙い方が異なるため、解放済みのものを選ぶ場合は説明を読んで操作の違いを確認します。公式 Wiki の現在の一覧には Repeater、Heat Ray、Barrage、Splatter Shot、Overmind、Wildcard がありますが、すべてが新規セーブで最初から選べるかはこの公開情報だけでは確定できません。[公式 Wiki](https://alias.black/glyphica/wiki_english.html)
- キー入力に成功すると、入力中の語が進み、語を完了したときに攻撃・標的決定が進みます。プレイヤー向けガイドは、完了後に敵の表示語が切り替わる挙動も説明しています。誤入力はコンボ維持に響きますが、誤った文字が別の敵の語の先頭と一致するとコンボが続くケースもあると同ガイドは記しています。[初心者向け Steam ガイド](https://steamcommunity.com/sharedfiles/filedetails/?id=3353482070)
- 序盤の強化選択では、プレイヤーガイドはクリティカル率と攻撃速度を優先候補に挙げています。主武器だけで押し切るのではなく、範囲を補う副武器を獲得し、武器タグや属性をそろえることで後半に向けた構成を作ります。これはガイド作者のプレイ助言であり、唯一の正解ではありません。[初心者向け Steam ガイド](https://steamcommunity.com/sharedfiles/filedetails/?id=3353482070)
- 試行錯誤をしやすくする Practice モードは、2026年6月の公式更新で Relaxed から改名され、死亡しない練習モードになりました。[公式 Update 11: Roguelite Omens](https://steamcommunity.com/app/2400160/announcements/)

### 公開資料では確認できないこと

敵の出現秒数、序盤の正確な敵数、経験値の閾値、語の文字数分布、チュートリアルの画面遷移は、今回参照した公式ページでは仕様表として公開されていません。個別の実プレイや最新版の画面を観察しない限り、これらを固定仕様として扱うべきではありません。

## 5. フリック入力版へ移す場合の考え方

以下は Glyphica の既存仕様ではなく、今回の Android 作品向けの設計案です。

- 敵ごとに短い日本語のかな語を割り当て、画面下のフリック入力パッドで一文字ずつ入力する。
- 入力中の文字を Canvas 上で強調し、語を完成させたらその敵へ主武器の攻撃を行う。敵の上に「次に打つ語」が見える構造を保つことで、Glyphica の標的選択ループを残せます。
- 最初は2〜4文字程度の短い語だけで試し、敵の語と回収物の語を色・位置で区別します。スマートフォンでは入力パッドが画面下部を使うため、敵や弾の表示領域を上側に確保します。
- Android の日本語 IME に自由入力させる方式ではなく、ゲーム内の簡単なフリックパッドにかなを直接割り当てると、候補変換中の文字列やキーボード表示状態をゲーム側で扱わずに済みます。まずは Canvas の `onTouchEvent` と座標判定で入力パッドの領域を試作できます。

これは PC 版のキーボード入力をそのまま移植するものではなく、入力手段に合わせたアレンジです。Canvas の基礎を保ちつつ、次の段階で「敵オブジェクト」「語の入力進捗」「攻撃」「経験値」「レベルアップ画面」と順に足すのが学習しやすい順序です。

## 6. 参照資料

### Android 公式資料

- [Android Studio のコードテンプレート](https://developer.android.com/studio/projects/templates)
- [Android Studio でプロジェクトを作成する](https://developer.android.com/studio/projects/create-project)
- [Android GameActivity](https://developer.android.com/games/agdk/game-activity)
- [Android Game Development Kit](https://developer.android.com/games/agdk/get-agdk)
- [SurfaceView API](https://developer.android.com/reference/android/view/SurfaceView)
- [Canvas API](https://developer.android.com/reference/android/graphics/Canvas)
- [MotionEvent API](https://developer.android.com/reference/android/view/MotionEvent)

### Glyphica 資料

- [Glyphica: Typing Survival — Steam ストア](https://store.steampowered.com/app/2400160/Glyphica_Typing_Survival/)
- [Glyphica 公式 Wiki](https://alias.black/glyphica/wiki_english.html)
- [Glyphica 公式 Steam 更新告知](https://steamcommunity.com/app/2400160/announcements/)
- [A Beginner's Guide To: Glyphica — Steam コミュニティガイド](https://steamcommunity.com/sharedfiles/filedetails/?id=3353482070)（プレイヤー作成。コンボやドロップなど、公式 Wiki にない挙動の補助資料として使用）
- [INDIE FREAKS: Glyphica のゲーム紹介](https://www.indie-freaks.com/blog/indiegem-glyphica-typing-survival)（日本語版の入力方式の補助確認）

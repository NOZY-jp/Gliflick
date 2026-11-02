# Shooterの元画像とAndroid描画の照合

2026-11-02。最初の実装は、追加の山形を後方の飾りと誤解し、位置・間隔・比率を独自に近似していた。Androidで描画できることを確認してもWiki画像の再現を検証したことにはならない。全5ティアを元画像から再構成する。

## 形状の根拠

[公式Wiki](https://alias.black/glyphica/wiki_english.html)のShooter.png〜Shooter5.pngを`references/`へ出典付きで保存。これらは比較用で、アプリへPNGとして組み込まない。`shooter-reference-geometry.json`にURL・SHA-256・画像内の中心・向き・画素からの寸法換算を記録する。

赤い輪郭を抽出し、最も大きい本体の三角形から先端方向を求める。公開デモの本体先端22.5単位（Tier1〜3）、30単位（Tier4〜5）を寸法基準とする。本体と別部品の位置関係を保持したまま先端を+Xに正規化し、直線ポリゴンへ整理する。追加の山形は全て本体の先端側にある。Tier2で1個、Tier3/4で2個、Tier5で太い1個となる。

全体の見た目の半径は22.501 / 36.202 / 47.951 / 51.151 / 52.274単位。`EnemyCatalog`の見た目の半径も同じ値へ合わせる。物理的な本体の衝突円は公開デモに従い15単位のまま。HP・接触ダメージ・発射周期はこの見た目の修正で変えない。

## 再生成と実画面検証

```powershell
uv run --with opencv-python-headless --with pillow python docs/verification/shooter-reference-20261102/rebuild-shooter-svg.py
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-watch-fs
```

再生成はShooterの5個のSVGと見た目の半径を更新する。他の敵の生成器ではこれらを上書きしない。

`EnemyKindsInstrumentedTest`は実Activity上のCanvasを撮影し、描画した個体の実位置・向き・スケールをJSONへ保存する。比較ツールはその実PNGを同じ向き・縮尺に変換し、Wiki原画像と並べる。SVGのプレビューをAndroid画面の代わりにはしない。

```powershell
uv run --with opencv-python-headless --with pillow python docs/verification/shooter-reference-20261102/compare-android-reference.py
```

照合の基準は輪郭IoU 0.85以上、元画像の独立した各赤色部品の再現率0.75以上。数値だけで完了せず、`comparison.png`のWiki原画像／Android実画面／差分を見比べる。拡大縮小によるアンチエイリアス差を含むため、完全に同じ画素とはしない。

## 結果

最終ビルド・単体160件成功。エミュレーター`emulator-5554`で3系統の全ティア表示テスト1件成功。撮影した実画面は[android.png](android.png)、実位置・向き・スケールは[android.json](android.json)。

[元画像と実画面の比較](comparison.png)を確認し、独立した各部品が先端側へ正しい間隔で並ぶことと、上位の中抜きを確認した。輪郭IoUはTier1〜5で0.941 / 0.886 / 0.858 / 0.891 / 0.929。各独立部品の再現率は全て0.88以上。全て事前の基準を通過した。アンチエイリアスとポリゴン整理による境界差はあるが、以前の部品配置・比率の誤りは残っていない。

実画面から向き・縮尺を変換した比較であり、Androidへ同じビットマップを貼り付けた画像ではない。アプリは編集可能なSVGをCanvasのPathとして描く。HP・発射性能と本体衝突円は、この外観訂正で変更していない。

同種の見落としを防ぐため、プロジェクトAGENTS.mdで各ティアの元画像と実画面を同じ向き・縮尺で比較することを要求し、旧生成器がShooterを手描きの矢印へ戻さないようにした。


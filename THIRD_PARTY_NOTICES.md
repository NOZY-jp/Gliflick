# 参照元と第三者アセット

GliflickはGlyphica: Typing Survivalのゲーム仕様・公開資料を参考にしています。Glyphicaの名称、ゲーム本体、公式アセットの権利を本プロジェクトが保有するという意味ではありません。

## Glyphicaの公開資料と字形

- [Glyphica公式サイト・公開デモ](https://playglyphica.com/)
- [公式Wiki](https://alias.black/glyphica/wiki_english.html)
- [日本語非公式Wiki](https://wikiwiki.jp/glyphica/)
- [字形の出典と変換方法](docs/GlyphicaIconAssets.md)
- [攻撃体の参照画像・描画方法](docs/WeaponActorAppearance.md)

`weapon_icon_*.svg`と`evolution_icon_*.svg`は公式Wiki／公開デモのTitle-Icons-Glyphica.ttfとEvolution-Icons-Glyphica.ttfの字形から変換しています。`weapon_pickup_fuel*.svg`は公開デモのPickup-Icons-Glyphica.ttfの字形から変換しています。これらは本プロジェクト独自の字形ではありません。

ゲーム内攻撃体は公開された画像・映像・デモの形状を参照して再構成しています。各SVGのコメントと仕様書に参照情報を残しています。検証資料内の原作画像・デザイン資料も、それぞれの出典元に帰属します。

## Androidテンプレート

`app/src/main/cpp/`などにはAndroid Studioのゲーム用テンプレート由来のファイルが残っています。各ソースにある著作権・ライセンス表記を維持しています。現在のゲーム画面はKotlinとAndroid Canvasで実装しています。

## 依存ライブラリ

AndroidX、Material Components、JUnitなどをGradleで取得します。使用する依存関係は`gradle/libs.versions.toml`と`app/build.gradle.kts`を参照してください。それぞれのライセンスは各配布元の条件に従います。

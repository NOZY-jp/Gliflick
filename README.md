# Gliflick

Android向けのタイピング・サバイバルゲームです。日本語のローマ字入力とかな入力に対応し、敵の言葉を入力すると武器が攻撃します。

現在はリピーターのエンドレスモード、11種のサブ武器、レベルアップ選択と武器別の撃破進化を実装しています。開発中のプロジェクトです。

## ビルド

Android Studioでこのディレクトリを開き、Android SDK Platform 37をインストールしてGradleを同期してください。GradleのJVMは25を指定しています（`gradle/gradle-daemon-jvm.properties`）。SDKの場所はAndroid Studioで設定するか、Git管理外の`local.properties`に設定します。

Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

macOS / Linux:

```sh
sh gradlew :app:testDebugUnitTest :app:assembleDebug
```

Debug APKは`app/build/outputs/apk/debug/app-debug.apk`に生成されます。Android Studioから端末・エミュレーターへ実行できます。

## 操作

- メニューはタップ、またはメニュー名の英字入力で選択します。
- `play`でエンドレスモードが始まります。敵の言葉を入力して標的にします。
- レベルアップ・進化の表示が出たらSpaceで選択画面を開きます。カードはタップまたは表示された語で選択します。
- 選択画面のSpaceはリロールです。初期回数は3回です。

## 構成と仕様

- `app/src/main/java/`：Android画面、入力判定、戦闘シミュレーション、Canvas描画。
- `app/src/main/res/raw/`：編集可能な武器・進化・装飾SVG。
- `app/src/test/`：入力判定・戦闘・進化などの単体テスト。
- `app/src/androidTest/`：端末・エミュレーター上での操作・描画確認。
- [戦闘設計](docs/EndlessCombatDesign.md)、[入力設計](docs/JapaneseInputDesign.md)、[レベルアップ](docs/LevelUpDesign.md)、[武器進化](docs/WeaponEvolutionDesign.md)。

旧プロジェクト名は`TestGame`です。既存インストールを更新できるよう、Androidの`applicationId`とソースの名前空間は`jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame`を維持しています。過去の検証資料・画像には以前の名前や作業パスが残っています。

## バージョン管理

Git互換の履歴をJujutsuで管理しています。Gitでcloneして利用でき、Jujutsuで作業する場合はclone先で`jj git init --colocate`を実行してください。

## 参照元・アセット

Glyphica: Typing Survivalのゲーム仕様と公開資料を参考にした個人開発プロジェクトです。Glyphicaの公式プロジェクトではありません。公式Wiki由来の字形やAndroidサンプル由来のコードを含むため、[参照元と第三者アセット](THIRD_PARTY_NOTICES.md)も確認してください。

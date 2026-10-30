# IME入力の検証記録

## 2026-10-26: 成功した確認

- Debug APK build: 成功。
- JVM単体テスト: 40件、失敗0（ゲーム39件＋テンプレート1件）。
- Pixel 7a / Android 17の端末テスト: 8件、失敗0（入力7件＋テンプレート1件）。
- 実打鍵: ユーザーがSimejiの日本語QWERTYでsima、shima、nettoを入力し、さらにnoを追記した。人工通知を物理打鍵の証拠にしていない。

実行コマンド:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

## 実測データ

| 記録 | 内容 |
| --- | --- |
| ime-human-japanese.jsonl | 修正前に採取した実際の通知。simaは191–199、shimaは216–226、nettoは231–243 |
| ime-human-fixed-r2.jsonl | 修正後に人が打鍵した通知と通常Controllerの更新結果 |
| typing-human-fixed-r2.png | 受信simashimanetto、nettonoの先頭nettoが黄色の実画面 |
| typing-human-defeat-r2.png | 続くnoだけで撃破数1、受信空、撃破枠が補充された実画面 |

修正後のログ:

- 5–16: s→し→しm→しま。受信s→si→sim→sima、待機shimano→simano。
- 28–42: s→sh→し→しm→しま。受信履歴末尾shima、待機shimano。
- 74–88: n→ね→ねt→ねっt→ねっと。受信履歴末尾netto、nettonoの5文字が黄色。
- 89–94: ねっとn→ねっとの。Enterなしでreceived=;count=1。
- 95–101: IMEの後続クリア通知でも撃破数は1のまま。

sourceTextはIME原文のまま。inputTextは観測済みprefixと仮名化の音節終端から一意に確認できた表記。
入力表示用文字列をStrokeへ変換して再投入しない。未観測のhやxtuを生成しない。

## 自動回帰テストの範囲

既定shippaiの維持、siへの切り替え、kanasimi、sha内のha、促音・撥音・別表記、母音・伸ばし棒、濁点待機、削除禁止、盤面3語だけの照合、即撃破、クリアと補充、辞書全564語。
今回の実測列をJVMと端末テストに固定し、実際のUIコールバックまで再生する。
端末テストが自分で通知を与えることと、人がSimejiを操作したことを区別する。

## 運用

通常起動はランダム3語のゲーム。入力トレースは通常起動では無効。
Debug専用InputDiagnosticActivityでのみ明示的に記録を有効にし、固定語で通常Controllerを確認する。
Android 17ではインストール直後のPackageUpdateActivityが起動先を取り替える場合があった。
更新完了後、別操作でforce-stop→起動し、UIタイトルを確認してから人へ入力を依頼する。

## 未確認の範囲

他IME、全配列、全別表記の物理入力は未確認。仮名を一括通知して英字prefixが全く届かない経路では物理綴りを断定しない。
母音・長音だけではフリックへの方式変更を完全には見分けられず、直前方式を優先する。

[実装フロー](../InputFlowApproval.md) / [ファイル責務とデータの移動](../JapaneseInputDesign.md)

## 2026-10-28: んのn／nnと既定表示

- Debug build成功、単体47件・Pixel 7aの端末通知再生9件成功。
- 省略可能な箇所の既定表記はn。hanの段階はhanpatsu、2個目のnでhannpatsu。入力済みtuをtsuに書き換えない。
- hanpatu／hannpatuはどちらもはんぱつ。honyakuはほにゃく、ほんやくはhonnyaku。語末はkanで未完了、kannで即撃破。
- 人が実際に入力した通知はime-human-nasal-20261028.jsonl。初回5–94は英語QWERTYだったとユーザーが訂正。日本語QWERTYの再試行は95以降。
- 日本語の有効試行: hanpatu 95–113、hannpatu 118–136、honyaku 168–186、honnyaku 191–211、kann 216–224。141–166の訂正途中の試行は正しい入力の根拠として使わない。
- hanpatuのはn→はんpは1個のn。hannpatuのはn→はん→はんpは2個のn。kannもかn→かん。実測した音節確定の違いを共有の言語規則で扱う。
- 実測通知を再生する単体／端末テストで通常UIの表示・黄色・撃破数・受信クリアまで確認。再生テストを新たな物理打鍵の証拠として扱わない。
- typing-double-n-20261028.png: hannが黄色、待機hannpatsu。
- typing-final-n-pending-20261028.png: kannのうちkanだけ黄色、撃破数は前の1から増えていない。続くんへの確定通知でのみ2になることを端末テストで検証。
- 修正版を端末へ再インストール。通常のランダム3語へ戻す。

## 2026-10-29: ミスタイプ・文字数ストリーク・共有入力

- Debug APKとテストAPKのビルド成功。JVM単体69件成功（既存47件＋今回22件）。
- Pixel 7a / Android 17で入力・UIテスト11件成功。表示色の調整後、該当UI2件も再確認した。
- 当初のVM確認後、ユーザーの帰席により実機優先へ切り替え、最終版を実機へ更新した。
- ストリークの単位は確定した読みの文字数。しは1、ちゃは2、んは1。ローマ字のキー数や単語完成数ではない。
- ちゃわんがん／tyawanngannは6字で共有入力イベントあり。ちゃわん＋わんがんは8字で、この二語の共有入力イベントなし。
- eigoは英語＋囲碁で3字。pasokonnは底→パソコンの順に完成して4字。共有した文字を重複加算しない。
- tyawaniは茶碗の入力失敗1回とわに完成が同時に成立し、にの1字からストリークを再開する。sango／sanpoの候補絞り込みはミス0。
- 辞書564語の仮名・既定ローマ字入力について、誤判定なしで読み文字数を数える単体回帰を追加。
- 一語完成後もほかの語の進捗を残し、受信表示は必要な末尾へ切り詰める。表示は8語、2列×4段、最大24dp相当の入力文字＋12dp相当の正式表記。
- 共有入力の特別効果用にGameInputUpdate.overlapEventsを返す。倍率やダメージ効果本体はまだ適用していない。

| 記録 | 内容 |
| --- | --- |
| typing-shared-six-pixel7a.png | 共有して2語完成、6字、ミス0の実機画面 |
| typing-separate-eight-pixel7a.png | 2語を別々に入力、8字、ミス0の実機画面 |
| typing-overlap-progress-pixel7a.png | 底完成後もpasokoの進捗・受信表示が残り、ストリーク3字の実機画面 |
| typing-overlap-streak-pixel7a.png | ミスでストリークを切った後、英語＋囲碁の3字から再開した実機画面 |
| streak-pixel7a-instrumentation.txt | Pixel 7aで11件成功の通知・UIテスト出力 |
| streak-pixel7a-ui-final.txt | 最終表示色で対象UI2件成功の出力 |

実機上のテストはInputConnection通知の再生であり、今回新しく人がSimejiを打鍵した証拠ではない。既存の実測通知列も回帰として再生した。最終画面では8語の収まり・欠け・上部文字色を目視確認した。

## 2026-10-30: Figmaタイトルメニュー

詳細は [title-menu-20261030.md](title-menu-20261030.md)。単体72件、実機の全体18件が成功。キャプション除去後はメニュー7件を再確認した。最終版は文字中心固定の1→2倍拡大、不透明度100→0%、420ms。通常時・入力ミス時とも説明文を表示しない。

### 共通判定と図形背景への更新

メニューもGameImeEditTextとWordMatchEngineを使い、armorarmoryとaaaaaの誤字後の継続を修正。単体77件・実機全体20件成功。円弧と正方形をCanvas描画へ移し、方眼を残して拡大・フェードアウトする最終版は実機メニュー10件成功。詳細は [menu-shared-matcher-20261030.md](menu-shared-matcher-20261030.md) と [menu-native-background-20261030.md](menu-native-background-20261030.md)。Git/Jujutsuで変更を分けて記録する。

# レベルアップ・サブ武器の実機確認

2026-10-30 / Pixel 7a / Android 17

## 結果

- `:app:testDebugUnitTest`：108件、失敗0。
- `:app:assembleDebug`、`:app:assembleDebugAndroidTest`：成功。
- Pixel 7aで`LevelUpInstrumentedTest`の4件と既存`CombatInstrumentedTest`の3件が成功。その後、リロールの無効化条件と短い横幅での見出し配置を調整し、単体108件・新規実機4件を再確認した。
- タップによる取得、選択中の戦闘時刻停止、入力途中の色変化、入力による取得、リロール、進化選択、武器と燃料の描画を確認。
- 単体では11種それぞれの実ダメージ、4枠上限、燃料停止・再開、複数報酬のキュー、進化、爆発・跳弾、入力モードによらない発動条件も確認。

## 画面

実際のIME表示中に端末画面を撮影し、アプリ領域を切り出した画像。下端がIMEとの境界になる。横3列から縦3枚へ変更し、温かい白・灰色・くすんだ赤を使っている。

| 画像 | 確認内容 |
| --- | --- |
| [levelup-phone-app.png](levelup-phone-app.png) | レベルアップの3択とリロール |
| [levelup-input-app.png](levelup-input-app.png) | 選択語の入力済み部分の色変化 |
| [evolution-phone-app.png](evolution-phone-app.png) | リピーターの進化候補と具体的な効果 |
| [arsenal-phone-app.png](arsenal-phone-app.png) | 取得済み武器、攻撃、燃料語、装備枠 |
| [levelup-applied-app.png](levelup-applied-app.png) | 選択後の戦闘復帰 |

## 検証方法と限界

実機テストは決まった状態を注入してレベルアップ画面を開く。入力は端末のInputConnectionへの合成通知であり、人のSimeji実打鍵の証拠ではない。キーボードを含むフル画面はリポジトリに保存しない。

既存の戦闘テストはランダムな出現待ちをやめ、固定の敵を注入して入力・発射・撃破を検証するようにした。初回のランダム待ちによる失敗と、別アプリ使用中にテストがフォーカスを取れなかった実行は成功件数に含めない。ユーザーの切替許可後に再実行した。

公開値と本作の試作値、11種の発動条件、未実装のサブ武器進化ツリーは[設計書](../../LevelUpDesign.md)を参照。

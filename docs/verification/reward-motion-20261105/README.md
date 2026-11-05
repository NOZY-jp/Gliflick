# レベルアップ・進化演出の検証（2026-11-05）

Pixel 7aの更新後アプリで制御した報酬状態を描画。時間を固定して実Canvasのキーフレームを撮影したもので、人によるIME入力や自然プレイの証拠とは区別する。保存画像は状態バー・IMEを除くアプリ領域のみ。

- [縮小中](reward-ring-contract-app.png)、[メイン武器周囲で折り返し・Space表示](reward-ring-bounce-key-visible-app.png)、[拡大とフェード](reward-ring-release-app.png)
- [Spaceが透明になった位相](reward-key-hidden-app.png)：文字/個数は維持。
- [Hunter装備枠へ収束](reward-evolution-slot-app.png)、[進化適用後](reward-evolution-applied-app.png)
- [進化選択後に自動で開く通常レベルアップ](reward-next-choice-app.png)：パネルを透明から開き直さず、戦闘時刻も止まったまま。

初期の小さな伸縮を重ねるバウンドを廃止。0.58秒cubic ease-in→0.12秒の放物線による反転→0.58秒cubic ease-out。反転の前後で速度の絶対値を一致させ、各区間の接続速度も連続にする。最小半径を通過してからフェード。数値テストでは2種類の画面外半径に対し、速度の連続性・入口/出口の等速・時間対称性・最小半径・消滅を確認。装備枠位置は320dp/800dp幅でもアイコンと一致。

全198単体テスト、debugとAndroidTest APKビルド成功。最終実機8テスト成功（26.14秒）。通常報酬・進化混在4件の自動消費、途中で戦闘時間が進まないこと、各入力境界のリセット、進化カウント強化で新たに生じる進化の自動選択、リロールで到達演出が再発しないことも単体確認。

最終APKと実機から回収したbase.apkのSHA-256はともに `0b81a06d89a8e650b0a448a0bde91eb4247eca401f8c90bbb5644d53950afbaf`。ビルド中のみUSB stay-awakeを元0から一時2へ変更し、最後に0へ復元、タイトル画面へ戻した。生ログ/全画面画像/APKはignored build/reward-motion-audit-20261105/に保管。初期実機実行の起動フォーカス待ち1件は再実行で成功し、最終版は全8件を一括で成功した。

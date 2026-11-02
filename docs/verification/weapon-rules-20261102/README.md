# 発動ルールと文字の重なりの検証

2026-11-02。仕様と出典は[発動ルール再照合](../../WeaponActivationAudit.md)。

## 自動検証

- `:app:testDebugUnitTest`：142件すべて成功。
- `:app:assembleDebug` / `:app:assembleDebugAndroidTest`：成功。
- Android初回9件中8件成功。レベルアップ4件（カードのタップ無効と入力選択、合成入力・リロール、武器取得、進化選択）、文字重なり1件、進化3件（全12武器のカード適用、全11種の進化後描画、連続爆撃）。
- 残りの全11種外観テストは、旧来の「中央から60dp以上離れる」前提がSpectreの正しい敵座標生成と矛盾して失敗。飛行経路の総距離と残像・接触ダメージを検証する形へ修正して再実行し成功。最終結果は[ログ](visual-instrumentation.txt)。合計9件の検証が成功した。

単体では全6文字条件の不一致／一致・出現数・生成位置、Teslaの連続した一致完成、Hunterの展開前非攻撃・10ダメージ・0.5秒周期・活動寿命、Chopper通常1枠／Dual Wield 2枠／鋸群全終了時の枠返却を確認した。描画順位は入力・HP減少率・ティア・数値ID、同一敵のクロスフェード、2行間の隙間、端同士の接触、非重複を検証した。

## Android実画面

対象は`emulator-5554` / VM_Pixel_7a。接続中の実機へインストール・画面操作はしていない。更新APKをインストールし、実Activity/CombatView/Canvasと表示中のシステムIMEで撮影。通知はテストから投入したもので、人の実打鍵やSimeji固有の通知を証明するものではない。

文字重なりの画面は実CombatActivity内で、固定語彙のCombatViewと停止した敵を制御配置した。100HP中90HPを失った敵が、1000HP中600HPを失ったTier3より優先される。後者の入力を始めると優先が逆転し、文字が離れれば低優先側も通常の濃さへ戻る。読みの大きさはどの状態でも同じ。

| 状態 | 画像 |
| --- | --- |
| HP減少率を優先 | [画面](caption-priority-health.png) |
| 入力中を優先 | [画面](caption-priority-input.png) |
| 重なり解消 | [画面](caption-priority-separated.png) |
| レベルアップ3枚 | [アプリ領域](levelup-phone-app.png) |
| 選択語の途中入力 | [アプリ領域](levelup-input-app.png) |
| 通常Hunter | [画面](hunter.png) |
| Hunter Eliteと連鎖ビーム | [画面](hunter-elite.png) |

低優先の文字は無彩色化＋不透明度32%。暖色の背景と従来の濃淡を保ち、敵本体やHPバーまで薄くしない。カードのタップではselectedIndexが空のままで、文字も点灯しないことをAndroidテストで確認。語の完成では報酬が1回だけ適用され、戦闘停止・Space・リロール・進化キューも維持された。

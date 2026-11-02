# 敵種追加の検証

2026-11-02。採用性能・推定値・出現重み・SVGの編集先は[敵種仕様](../../EnemyKindsDesign.md)。

## 結果

- 単体160件成功。ビルドは`:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-watch-fs`。最終SVGの更新後も`:app:assembleDebug`成功。
- Androidの既存9件（レベルアップ4・文字の重なり1・全11武器外観1・全武器進化3）と、新規敵4件が成功。初回の[13件ログ](instrumentation.txt)では新規1件だけが、正常なランダム出現のDroneまで分裂部品として数えて失敗。検証対象をSPLITTER_SHARDへ限定し、最終APKで[新規4件を再実行して全成功](enemy-instrumentation.txt)。
- Shooterの初期外形は誤りだったため、全5ティアをWiki輪郭から再構成。[元画像と最終Android実画面の比較](../shooter-reference-20261102/README.md)を行い、輪郭IoU 0.858〜0.941・各部品の再現率0.88以上を確認した。描画テスト成功だけを形状一致とは扱わない。
- 接続先は`emulator-5554` / VM_Pixel_7aのみ。接続中の実機にはインストール・操作していない。

単体ではHPとシールドの公開値、吸収・超過・回復・回復中断、Shooterの停止と発射、Missileの準備・固定方向・親死亡、Warderの複数所有者による保護、直接/範囲/D.O.T.の無敵、120°バリアの両側・高速弾・貫通弾、部品HPの引き継ぎ・親の途中入力退役・新部品の新規入力、全パーツへの範囲攻撃、貫通の部品ごとの一度判定、D.O.T.帰属と残時間、Chopper撃破効果、出現重みの導入とクラス上限を確認した。1時間のシミュレーションでも部品を含む生存数16とDroneのボス前Tier2上限を確認した。射撃を塞がない最後の1枠予約も検証した。

## 実画面

`screenshots/enemy-kinds/`に敵機能の9画面を保存。Shooterの旧画面は形状訂正前のため、上記の訂正後画面を正とする。実CombatActivity内へ固定語彙のCombatViewを配置し、敵を制御配置して、実際のCanvas描画と表示中のシステムIMEを撮影した。モデルの図やモックを端末画面の代わりにしていない。入力はテストが投入するため、人の実打鍵やSimeji固有通知の証明ではない。

| 内容 | 最終画面 |
| --- | --- |
| Shield Drone全5ティア、2段の耐久バー | [画面](screenshots/enemy-kinds/tiers-shield_drone.png) |
| Shooter全5ティア、後方の山形と上位の中抜き | [訂正後の画面](../shooter-reference-20261102/android.png) |
| Splitterの4/5/6部品と部品ごとのHPバー | [画面](screenshots/enemy-kinds/tiers-splitter.png) |
| Warderの120°弧と保護線 | [画面](screenshots/enemy-kinds/warder-protected.png) |
| 所有者の撃破後、弧と保護線が消える | [画面](screenshots/enemy-kinds/warder-destroyed.png) |
| 部品へのダメージ、入力途中の親 | [画面](screenshots/enemy-kinds/splitter-before.png) |
| 残HP250/350/350を保持して分離、親の途中語を維持 | [画面](screenshots/enemy-kinds/splitter-after.png) |
| 準備中のミサイル | [画面](screenshots/enemy-kinds/missile-charging.png) |
| 移動中のミサイルと被弾後HPバー | [画面](screenshots/enemy-kinds/missile-flying.png) |

初回の`screenshots/`直下8枚は被弾直後の円・破片も重なった状態。最終の輪郭確認では被弾エフェクトが消える0.7秒後に撮影し、5系統と各ティアをWiki画像と見比べた。文字サイズは全種固定、バー幅24dp、位置は各個体の大きさへ追従する。既存の方眼・淡色の文字・灰色の耐久表示を保つ。

デモのソースと参照PNGは無視対象のbuildに留め、製品にはSVGの独自再構成を使う。原作のレーン予約・固定間隔・敵集団の追加は行わない。Steam現行版の完全一致とは区別し、画面換算・待機位置・Warder接触0・分離距離・射撃用1枠の推定は仕様書へ明記した。

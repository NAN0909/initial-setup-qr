# 初期設定QR / 初期設定ヘルパー

Galaxy A25（Android 14〜16 / One UI 6〜8 想定）を初期化したあと、QR 1枚で
Wi-Fi 接続 → 管理アプリ取得 → 初期設定の適用 → 利用者が1回ボタンを押して管理解除、まで行う仕組み。

```
initial-setup/
├─ app/                      管理アプリ「初期設定ヘルパー」のソース（Java, Gradle 不要）
│   ├─ AndroidManifest.xml
│   ├─ res/                  文字列・device_admin.xml・アイコン
│   └─ src/jp/initialsetup/helper/
│       ├─ AdminReceiver.java              DeviceAdminReceiver
│       ├─ GetProvisioningModeActivity.java  Android 12+ 用（完全管理モードを返す）
│       ├─ PolicyComplianceActivity.java   プロビジョニング最終画面：設定を一度だけ適用し結果表示
│       ├─ SetupApplier.java               設定適用ロジック（読み戻し確認・要確認判定）
│       ├─ SetupWatchService.java          セットアップ完了を待って切り替え画面を開く
│       ├─ MainActivity.java               「個人用端末へ切り替える」画面（解除→読み戻し確認）
│       └─ Ui.java                         画面部品
├─ site/
│   ├─ template.html         QR 生成ページのテンプレート
│   ├─ releases.json         公開 APK の一覧（version / url / date）
│   └─ index.html            生成物（tools/make_site.py が作る）
├─ tools/
│   ├─ checksums.py          APK の QR 用チェックサムを出す
│   ├─ make_site.py          releases.json + dist/*.checksums.json → site/index.html
│   ├─ android-36.jar        javac 用 API 36
│   └─ android-34.jar        aapt2 link 用
├─ keys/                     署名鍵（配布しない・git 管理しない）
│   ├─ helper-release.jks    PKCS12, alias "helper", RSA 4096
│   └─ keystore.pass         パスワード
├─ dist/                     署名済み APK と checksums.json（バージョン別、上書きしない）
└─ build.sh                  ビルド＆署名
```

## 1. ビルド環境

- JDK 17 以上（確認済み: OpenJDK 21）
- `aapt2`, `zipalign`, `apksigner`, `dalvik-exchange`（Ubuntu 24.04: `apt install aapt apksigner zipalign dalvik-exchange`）
- `python3`
- Android SDK / Gradle は不要。`tools/get-android-jars.sh` で `android-34.jar` / `android-36.jar` を取得（または SDK からコピー）

```sh
./build.sh        # → dist/helper-<versionName>.apk と dist/helper-<versionName>.checksums.json
```

Ubuntu の `aapt2`（2.19）は API 35 以降の `android.jar` のリソース表を読めないため、
リソースのリンクだけ `android-34.jar`、Java のコンパイルは `android-36.jar` を使う。
`dalvik-exchange`（dx）は Java 8 クラスまでなので、ソースにラムダ等は使わない。

## 2. 署名鍵

- `keys/helper-release.jks` はこのプロジェクト専用に作成（2026-10-01）。
- **今後の更新でも必ず同じ鍵で署名する**（違う鍵だと端末側で更新インストールできない）。
- `keys/` は配布物・公開サイト・git に含めない（`.gitignore` 済み）。バックアップは別途安全な場所へ。
- 紛失時は再作成できるが、既存端末への「更新」は不可になる（新規セットアップには影響なし）。

鍵を作り直す場合:
```sh
keytool -genkeypair -keystore keys/helper-release.jks -storetype PKCS12 -alias helper \
  -keyalg RSA -keysize 4096 -validity 10950 -dname "CN=Initial Setup Helper, O=initial-setup, C=JP"
```

## 3. 更新手順（新バージョンを出す）

1. `app/AndroidManifest.xml` の `versionCode` を +1、`versionName` を上げる（例 1.0.1）。
2. `./build.sh` → `dist/helper-1.0.1.apk` と `dist/helper-1.0.1.checksums.json` ができる。
3. APK を **バージョン別の URL** に置く（例 `/apk/helper-1.0.1.apk`）。古い APK は消さない・上書きしない。
4. `site/releases.json` の先頭に新バージョンを追加（url・date）。古い行は残す。
5. `python3 tools/make_site.py` → `site/index.html` を再生成して公開。
6. 公開 URL から `curl -L -o x.apk <URL>` して `sha256sum` が checksums.json と一致することを確認。

公開サイトを更新しても、既に設定済みの端末や保存済みの QR は自動では更新されない。
古い QR はその QR に書かれた URL とチェックサムの APK をそのまま使う。

## 4. QR の中身

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "jp.initialsetup.helper/.AdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://<公開URL>/apk/helper-1.0.0.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": "<APK の SHA-256, URL-safe Base64 無パディング>",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": "<署名証明書の SHA-256, 同上>",
  "android.app.extra.PROVISIONING_WIFI_SSID": "...",
  "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE": "WPA | WEP | NONE",
  "android.app.extra.PROVISIONING_WIFI_PASSWORD": "...",
  "android.app.extra.PROVISIONING_WIFI_HIDDEN": true,
  "android.app.extra.PROVISIONING_LOCALE": "ja_JP",
  "android.app.extra.PROVISIONING_TIME_ZONE": "Asia/Tokyo",
  "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true,
  "android.app.extra.PROVISIONING_SKIP_EDUCATION_SCREENS": true
}
```
モバイルデータを選んだ場合は Wi-Fi の 4 項目の代わりに `PROVISIONING_USE_MOBILE_DATA: true`。
APK の URL はログイン不要の HTTPS で、端末の DownloadManager が直接取得できる必要がある。

## 5. 端末での使用手順 / 手動で管理を解除する手順

公開サイト（初期設定QR）の「端末での手順」「手動で管理を解除する手順」と同じ内容。
要点:
- 初期化後の「ようこそ」画面を 6 回タップ → QR 読み取り。
- 「初期設定を適用しました」画面で結果確認。緊急速報メールはボタンから設定画面を開いて手動 OFF。
- セットアップ完了後に「個人用端末へ切り替える」画面が自動表示（出なければ通知 or アプリ一覧から）。
- 解除は `clearDeviceOwnerApp` → `isDeviceOwnerApp` で読み戻し確認 → 成功表示。
- 自動再起動はしない。「管理されています」表示が残る時だけ手動再起動。

## 6. 設定項目と実装方法

| 項目 | 方法 | 読み戻し |
|---|---|---|
| 言語 ja_JP | QR の `PROVISIONING_LOCALE`（DPC から言語を変える公開 API は無い） | `Locale.getDefault()` |
| タイムゾーン | `setTimeZone`、false なら `AUTO_TIME_ZONE=0` にして再試行 | `TimeZone.getDefault()` |
| 画面タイムアウト 5 分 | `setSystemSetting(SCREEN_OFF_TIMEOUT, 300000)` | Settings.System |
| 明るさ 30% / 自動 OFF | `setSystemSetting(SCREEN_BRIGHTNESS_MODE, 0)`, `(SCREEN_BRIGHTNESS, 77)` | Settings.System（値と % を表示） |
| 位置情報 OFF | `setLocationEnabled(admin, false)` | `LocationManager.isLocationEnabled()` |
| メディア音量 0 | `AudioManager.setStreamVolume(STREAM_MUSIC, 0)` | `getStreamVolume` |
| 着信・通知音 OFF | `setRingerMode(VIBRATE)` + `STREAM_RING/NOTIFICATION = 0` | 音量とモード |
| アラーム 0（可能なら） | `setStreamVolume(STREAM_ALARM, 0)`、最小値が 0 でない機種は要確認 | `getStreamVolume`/`getStreamMinVolume` |
| 緊急速報メール | API 無し → 設定画面を開く候補を順に試す。開いただけでは OFF 扱いにしない | 常に「手動で設定」 |

適用済みフラグは `SharedPreferences("setup").applied_v1`。再起動・更新・再表示では再適用しない。

## 7. 確認済み / 未確認

**確認済み（このコンテナ内・2026-10-01）**
- 公開 URL: サイト https://nan0909.github.io/initial-setup-qr/ ／ APK https://nan0909.github.io/initial-setup-qr/apk/helper-1.0.0.apk（GitHub Pages, リポジトリ NAN0909/initial-setup-qr の docs/）。
- GitHub に置いた APK を API 経由で取得し、SHA-256 がローカルの dist/helper-1.0.0.apk と一致（6b2969…bc52）。Pages 上の checksums.json もログインなしで取得でき、同じ値。
- このコンテナから github.io へ直接 curl はネットワーク制限で不可だったため、Pages からの APK 本体のバイト一致は「Pages が同じコミットを配信している」ことによる間接確認。端末で初回 QR 読み取り時に Android 側のチェックサム検証で最終確認される。
- APK がビルドでき、`apksigner verify` で v3 署名を検証できる（`dist/helper-1.0.0.apk`）。
- マニフェストに必要なコンポーネント（DeviceAdminReceiver, GET_PROVISIONING_MODE, ADMIN_POLICY_COMPLIANCE / PROVISIONING_SUCCESSFUL）が入っている（`aapt dump`）。
- QR 生成ページを Chromium で動かし、生成した QR を zbar で読み戻して JSON が一致。
- phone 幅（400px）で横スクロールなし。

**未確認（エミュレーター・実機とも未実施）**
- このコンテナには Android エミュレーター・実機が無いため、QR 読み取り〜プロビジョニング〜設定適用〜管理解除の実動作は **一切試していない**。
- Galaxy A25（One UI）での各設定の読み戻し値、Samsung 固有の緊急速報設定画面のコンポーネント名、`clearDeviceOwnerApp` 後の表示挙動は未検証。
- Android 12+ の `ADMIN_POLICY_COMPLIANCE` 画面が終わった後に `SetupWatchService` からの画面自動表示が Samsung で許可されるかは未検証（通知とアプリ一覧の経路を用意済み）。

## 8. 公開 APK の配信確認（公開後に行う）

```sh
curl -L -o got.apk "<QR に入れる URL>"       # ログインなしで取得できること
sha256sum got.apk dist/helper-1.0.0.apk       # 一致すること
python3 tools/checksums.py got.apk            # package_checksum が QR と一致すること
```

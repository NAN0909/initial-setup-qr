package jp.initialsetup.helper;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.List;

/**
 * アプリ一覧から開ける画面。状態で分岐する。
 *  A. 解除済み（personalSwitchCompleted）            → 完了画面
 *  B. Device Owner かつ OS セットアップ完了           → 「個人用端末へ切り替える」1 タップ画面
 *  C. それ以外（セットアップ途中・QR extras なし等）  → 状態表示 ＋ 従来の解除ボタン（確認ダイアログ付き）
 */
public class MainActivity extends Activity {

    @Override
    protected void onResume() {
        super.onResume();
        if (!SetupApplier.isOwner(this) && SetupApplier.prefs(this).getBoolean("personalSwitchCompleted", false)) {
            showCompletePage();
        } else if (SetupApplier.isOwner(this) && SetupApplier.shouldShowPersonalSwitch(this)) {
            showPersonalSwitchPage();
        } else {
            showStatusPage();
        }
    }

    /** 現在の NFC 状態と、NFC 設定画面を開くボタン（手動 OFF 用。セットアップ途中には出さない）。 */
    private void addNfcBlock(LinearLayout content) {
        content.addView(Ui.body(this, "\n" + SetupApplier.nfcStateText(this)
            + "\nNFC を OFF にすると、おサイフケータイ・モバイル Suica・タッチ決済などは使えなくなります。"));
        Button nfc = Ui.button(this, "NFC の設定画面を開く", false);
        nfc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)); }
                catch (RuntimeException e) {
                    try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (RuntimeException ignored) { }
                }
            }
        });
        content.addView(nfc);
    }

    // ---- B. 1 タップで解除 -------------------------------------------------------

    private void showPersonalSwitchPage() {
        SetupApplier.prefs(this).edit().putBoolean("switchPageShown", true).commit();
        SetupApplier.cancelCompletionCheck(this);

        LinearLayout content = new LinearLayout(this);
        content.addView(Ui.eyebrow(this, "初期設定ヘルパー"));
        content.addView(Ui.title(this, "初期設定が完了しました"));
        Ui.addItems(this, content, SetupApplier.savedReport(this));
        addNfcBlock(content);
        content.addView(Ui.body(this, "\n下のボタンを押すと、このアプリによる端末管理を解除して個人用端末へ切り替えます。"
            + "解除後は各設定を確認してください。もう一度管理端末に戻すには端末の初期化が必要です。"));

        final Button release = Ui.button(this, "個人用端末へ切り替える", true);
        release.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                release.setEnabled(false);
                release.setText("切り替え中…");
                String err = doRelease();
                if (err == null) {
                    SetupApplier.prefs(MainActivity.this).edit().putBoolean("personalSwitchCompleted", true).apply();
                    showCompletePage();
                } else {
                    release.setEnabled(true);
                    release.setText("個人用端末へ切り替える");
                    content.addView(Ui.body(MainActivity.this, "切り替えられませんでした: " + err
                        + "\n端末を再起動してからもう一度お試しください。"));
                }
            }
        });
        content.addView(release);
        content.addView(Ui.body(this, "\nGoogle アカウントは解除後に手動で追加します。「管理されています」などの表示が残る場合は手動で再起動してください。自動再起動は行いません。"));
        setContentView(Ui.page(this, content));
    }

    /** clearDeviceOwnerApp を呼び、isDeviceOwnerApp=false を確認できたら null、それ以外は理由を返す。 */
    private String doRelease() {
        try {
            // 非推奨（テスト用途向けと注記）だが、アプリ自身が Device Owner を外せる唯一の公開 API。
            SetupApplier.dpm(this).clearDeviceOwnerApp(getPackageName());
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
        }
        return SetupApplier.isOwner(this) ? "管理状態が残っています" : null;
    }

    // ---- A. 完了 ---------------------------------------------------------------

    private void showCompletePage() {
        LinearLayout content = new LinearLayout(this);
        content.addView(Ui.eyebrow(this, "初期設定ヘルパー"));
        content.addView(Ui.title(this, "個人用端末への切り替えが完了しました"));
        content.addView(Ui.body(this, "端末管理の解除を確認しました。\n端末の設定から Google アカウントを追加してください。"
            + "仕事用などの表示が残る場合は手動で再起動してください。\n\nこの初期設定ヘルパーは、不要になったら通常のアプリとしてアンインストールできます。"));
        addNfcBlock(content);
        Button settings = Ui.button(this, "端末の設定を開く", true);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (RuntimeException ignored) { }
            }
        });
        content.addView(settings);
        Button uninstall = Ui.button(this, "このアプリをアンインストール", false);
        uninstall.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName()))); }
                catch (RuntimeException ignored) { }
            }
        });
        content.addView(uninstall);
        setContentView(Ui.page(this, content));
    }

    // ---- C. 状態表示（手動経路） ---------------------------------------------------

    private void showStatusPage() {
        final LinearLayout content = new LinearLayout(this);
        content.addView(Ui.eyebrow(this, "初期設定ヘルパー"));
        content.addView(Ui.title(this, "初期設定ヘルパー"));
        content.addView(Ui.body(this, "画面・位置情報・音量を一度だけ設定します。あとから端末の設定で変更できます。"));

        if (!SetupApplier.isOwner(this)) {
            content.addView(Ui.body(this, "端末の管理者として登録されていません。初期化後の QR 登録で導入してください。通常インストールだけでは自動設定できません。"));
            setContentView(Ui.page(this, content));
            return;
        }

        List<SetupApplier.Item> report = SetupApplier.savedReport(this);
        if (!report.isEmpty()) {
            content.addView(Ui.eyebrow(this, "前回の適用結果"));
            Ui.addItems(this, content, report);
        }
        addNfcBlock(content);
        if (!SetupApplier.systemSetupComplete(this)) {
            content.addView(Ui.body(this, "\n端末の初期セットアップがまだ完了していません。ホーム画面が表示されたあとにこの画面を開き直すと、「個人用端末へ切り替える」が表示されます。"));
        }
        content.addView(Ui.body(this, "この画面を開いたり端末を再起動したりしても、変更した設定を元に戻しません。"));

        Button reapply = Ui.button(this, "指定の設定をもう一度適用", false);
        reapply.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { SetupApplier.apply(MainActivity.this); showStatusPage(); }
        });
        content.addView(reapply);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && !nm.isNotificationPolicyAccessGranted()) {
            content.addView(Ui.body(this, "\n着信・通知音が要確認の場合は、以下からこのアプリの通知ポリシーへのアクセスを許可し、再適用してください。"));
            Button access = Ui.button(this, "音の制御に必要なアクセスを設定", false);
            access.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); }
                    catch (RuntimeException e) { content.addView(Ui.body(MainActivity.this, "この端末では設定画面を開けません。")); }
                }
            });
            content.addView(access);
        }

        // 従来の解除経路（確認付き）。QR extras なし・自動表示失敗時でも解除できる。
        final Button release = Ui.button(this, "このアプリによる端末管理を解除", true);
        final Button confirm = Ui.button(this, "管理を解除する（再登録には初期化が必要）", true);
        final Button cancel = Ui.button(this, "キャンセル", false);
        confirm.setVisibility(View.GONE); cancel.setVisibility(View.GONE);
        release.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { release.setVisibility(View.GONE); confirm.setVisibility(View.VISIBLE); cancel.setVisibility(View.VISIBLE); }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { release.setVisibility(View.VISIBLE); confirm.setVisibility(View.GONE); cancel.setVisibility(View.GONE); }
        });
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String err = doRelease();
                if (err == null) {
                    SetupApplier.prefs(MainActivity.this).edit().putBoolean("personalSwitchCompleted", true).apply();
                    showCompletePage();
                } else {
                    content.addView(Ui.body(MainActivity.this, "解除できませんでした: " + err));
                }
            }
        });
        content.addView(release); content.addView(confirm); content.addView(cancel);
        content.addView(Ui.body(this, "\nWi-Fi 接続は Android の QR 登録処理が行います。このアプリは Wi-Fi パスワードを保存せず、通信・アプリ配布・APN 変更を行いません。"));
        setContentView(Ui.page(this, content));
    }
}

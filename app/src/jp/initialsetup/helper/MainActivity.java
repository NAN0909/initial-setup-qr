package jp.initialsetup.helper;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.List;

/**
 * アプリ一覧から開ける画面。状態に応じて次の 3 つのどれかを表示する。
 *  1. セットアップ中（user_setup_complete=0）: まだ解除できないことを案内
 *  2. Device Owner のまま: 「個人用端末へ切り替える」ボタン（確認 → 解除 → 読み戻し確認）
 *  3. 解除済み: 完了表示とアンインストール案内
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(1);

        final boolean owner = SetupApplier.isDeviceOwner(this);
        final boolean setupDone = SetupApplier.isUserSetupComplete(this);
        LinearLayout content = new LinearLayout(this);
        content.addView(Ui.eyebrow(this, "初期設定ヘルパー"));

        if (!owner) {
            content.addView(Ui.title(this, "個人用端末になっています"));
            content.addView(Ui.body(this, "このアプリの管理権限（Device Owner）は解除済みです。"
                    + "このアプリは不要なので、アンインストールして構いません。\n\n"
                    + "ステータスバーに「この端末は管理されています」の表示が残る場合は、一度だけ手動で再起動してください。"
                    + "Google Play の「仕事用」表示は、この端末では使っていないため関係ありません。"));
            Button uninstall = Ui.button(this, "このアプリをアンインストール", false);
            uninstall.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        startActivity(new android.content.Intent(android.content.Intent.ACTION_DELETE,
                                android.net.Uri.parse("package:" + getPackageName())));
                    } catch (RuntimeException ignored) { }
                }
            });
            content.addView(uninstall);
            setContentView(Ui.page(this, content));
            return;
        }

        if (!setupDone) {
            content.addView(Ui.title(this, "セットアップが終わるまでお待ちください"));
            content.addView(Ui.body(this, "端末の初期セットアップがまだ完了していません。"
                    + "途中で管理を解除するとセットアップが正しく終わらないことがあるため、"
                    + "ホーム画面が表示されてからこの画面を開き直してください。"));
            Button retry = Ui.button(this, "状態を再確認", false);
            retry.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { render(); }
            });
            content.addView(retry);
            setContentView(Ui.page(this, content));
            return;
        }

        content.addView(Ui.title(this, "個人用端末へ切り替える"));
        content.addView(Ui.body(this, "初期設定は完了しています。下のボタンを押すと、この端末の管理（Device Owner）を解除して、ふつうの個人用端末になります。"
                + "この操作は元に戻せません。解除後は、Google アカウントを「設定 > アカウントとバックアップ」から手動で追加してください。"));

        String saved = SetupApplier.savedResult(this);
        if (saved != null) {
            List<SetupApplier.Item> items = SetupApplier.decode(saved);
            content.addView(Ui.eyebrow(this, "適用した設定"));
            Ui.addItems(this, content, items);
        }

        Button emergency = Ui.button(this, "緊急速報メールの設定画面を開く", false);
        emergency.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { SetupApplier.openEmergencyAlertSettings(MainActivity.this); }
        });
        content.addView(emergency);

        final Button release = Ui.button(this, "個人用端末へ切り替える", true);
        final Button confirm = Ui.button(this, "はい、管理を解除する（元に戻せません）", true);
        confirm.setVisibility(View.GONE);
        final Button cancel = Ui.button(this, "やめる", false);
        cancel.setVisibility(View.GONE);
        release.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                release.setVisibility(View.GONE);
                confirm.setVisibility(View.VISIBLE);
                cancel.setVisibility(View.VISIBLE);
            }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                release.setVisibility(View.VISIBLE);
                confirm.setVisibility(View.GONE);
                cancel.setVisibility(View.GONE);
            }
        });
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doRelease(); }
        });
        content.addView(release);
        content.addView(confirm);
        content.addView(cancel);
        setContentView(Ui.page(this, content));
    }

    private void doRelease() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        String error = null;
        try {
            // 非推奨（テスト用途向けと注記されている）だが、Device Owner を自分で解除できる唯一の公開API。
            // 一部のポリシーは残ることがある、とドキュメントに明記されている。
            dpm.clearDeviceOwnerApp(getPackageName());
        } catch (RuntimeException e) {
            error = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
        }
        // 解除後に読み戻して確認してから成功を表示する
        boolean stillOwner = dpm.isDeviceOwnerApp(getPackageName());
        if (!stillOwner) {
            render();
            return;
        }
        LinearLayout content = new LinearLayout(this);
        content.addView(Ui.eyebrow(this, "初期設定ヘルパー"));
        content.addView(Ui.title(this, "解除できませんでした"));
        content.addView(Ui.body(this, "管理権限がまだ残っています。" + (error != null ? "\n\nエラー: " + error : "")
                + "\n\n端末を再起動してからもう一度お試しください。それでも解除できない場合は、端末を初期化（工場出荷状態へリセット）すると管理は解除されます。"));
        Button back = Ui.button(this, "戻る", false);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { render(); }
        });
        content.addView(back);
        setContentView(Ui.page(this, content));
    }
}

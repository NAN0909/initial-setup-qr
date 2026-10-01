package jp.initialsetup.helper;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * Android 12 以降: ADMIN_POLICY_COMPLIANCE。設定を一度適用し、完了を記録して RESULT_OK で OS セットアップへ戻る。
 * 通常経路では画面を出さない（独自の続行ボタンや他の設定画面を挟まない）。
 * 適用結果（report）が保存できなかった場合だけ、状態を示して「続ける」を押せる画面を出す。
 */
public class PolicyComplianceActivity extends Activity {

    @Override
    protected void onResume() {
        super.onResume();
        SetupApplier.captureProvisioningOptions(this, getIntent());
        SetupApplier.applyOnce(this);
        if (SetupApplier.isOwner(this) && SetupApplier.prefs(this).contains("report")) {
            SetupApplier.markPolicyComplianceFinished(this);
            setResult(RESULT_OK);
            finish();
            SetupApplier.launchPersonalSwitchIfReady(this);   // 条件未達なら何もしない（Job が後で確認）
            return;
        }
        LinearLayout content = new LinearLayout(this);
        content.addView(Ui.eyebrow(this, "初期設定ヘルパー"));
        content.addView(Ui.title(this, "初期設定を適用できませんでした"));
        content.addView(Ui.body(this, SetupApplier.isOwner(this)
            ? "設定結果を保存できませんでした。初期設定の完了後に端末の設定から確認してください。"
            : "このアプリは端末の管理者として登録されていません。初期化後の QR 登録で導入してください。"));
        Button cont = Ui.button(this, "現在の状態で初期設定を続ける", true);
        cont.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                SetupApplier.markPolicyComplianceFinished(PolicyComplianceActivity.this);
                setResult(RESULT_OK);
                finish();
            }
        });
        content.addView(cont);
        setContentView(Ui.page(this, content));
    }
}

package jp.initialsetup.helper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.List;

/**
 * プロビジョニングの最後にシステムから起動される画面。
 *  - Android 12+ : ADMIN_POLICY_COMPLIANCE（この画面が RESULT_OK で閉じるとセットアップが続く）
 *  - Android 10/11: PROVISIONING_SUCCESSFUL
 * ここで初期設定を一度だけ適用し、結果を表示する。
 * Device Owner の解除は「セットアップ完了後」に MainActivity で行う（ここでは解除しない）。
 */
public class PolicyComplianceActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final List<SetupApplier.Item> items = new SetupApplier(this).applyOnce();

        LinearLayout content = new LinearLayout(this);
        content.addView(Ui.eyebrow(this, "初期設定ヘルパー"));
        content.addView(Ui.title(this, "初期設定を適用しました"));
        int warn = 0;
        for (SetupApplier.Item it : items) if (it.status != SetupApplier.OK) warn++;
        content.addView(Ui.body(this, warn == 0
                ? "すべての項目を適用できました。設定はあとから自由に変更できます。"
                : warn + " 件は自動で完了できませんでした。あとで設定アプリから確認してください。設定はあとから自由に変更できます。"));
        Ui.addItems(this, content, items);

        Button emergency = Ui.button(this, "緊急速報メールの設定画面を開く", false);
        emergency.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                SetupApplier.openEmergencyAlertSettings(PolicyComplianceActivity.this);
            }
        });
        content.addView(emergency);

        content.addView(Ui.body(this, "\n次へ進むとセットアップが続きます。セットアップが終わると「個人用端末へ切り替える」画面が開きます（開かない場合はアプリ一覧の「初期設定ヘルパー」から開けます）。"));

        Button next = Ui.button(this, "次へ", true);
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                // セットアップ完了を待って切り替え画面を出すサービスを開始
                startService(new Intent(PolicyComplianceActivity.this, SetupWatchService.class));
                setResult(RESULT_OK);
                finish();
            }
        });
        content.addView(next);

        setContentView(Ui.page(this, content));
    }

    @Override
    public void onBackPressed() {
        // 戻るで閉じるとプロビジョニングが失敗扱いになるため無効化
    }
}

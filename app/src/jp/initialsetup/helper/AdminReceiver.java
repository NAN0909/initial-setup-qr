package jp.initialsetup.helper;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public class AdminReceiver extends DeviceAdminReceiver {

    public static ComponentName component(Context c) {
        return new ComponentName(c.getApplicationContext(), AdminReceiver.class);
    }

    /**
     * プロビジョニング完了の通知。
     * Android 10〜11 ではこの後に PROVISIONING_SUCCESSFUL アクティビティが起動する。
     * Android 12 以降ではこの前後に ADMIN_POLICY_COMPLIANCE アクティビティが起動する。
     * ここでは「プロビジョニング済み」の印を付けるだけで、設定適用は画面側で一度だけ行う。
     */
    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        context.getSharedPreferences(SetupApplier.PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(SetupApplier.KEY_PROVISIONED, true).apply();
    }
}

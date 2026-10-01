package jp.initialsetup.helper;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class AdminReceiver extends DeviceAdminReceiver {
    /** プロビジョニング完了の通知（OS により届かないこともあるため、他の経路と併用する）。 */
    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        SetupApplier.captureProvisioningOptions(context, intent);
        SetupApplier.applyOnce(context);
        SetupApplier.markProvisioningComplete(context);
        SetupApplier.launchPersonalSwitchIfReady(context);
    }
}

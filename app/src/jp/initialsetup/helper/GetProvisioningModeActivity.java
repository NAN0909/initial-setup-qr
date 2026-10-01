package jp.initialsetup.helper;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;

import java.util.ArrayList;

/**
 * Android 12 以降のプロビジョニングで最初に呼ばれる。完全管理対象デバイスを返すだけで画面は出さない。
 * QR の ADMIN_EXTRAS_BUNDLE はここで保存する。
 */
public class GetProvisioningModeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SetupApplier.captureProvisioningOptions(this, getIntent());
        ArrayList<Integer> allowed = getIntent().getIntegerArrayListExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES);
        int mode = DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE;
        if (allowed != null && !allowed.contains(mode)) {
            setResult(RESULT_CANCELED);
        } else {
            Intent result = new Intent();
            result.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, mode);
            setResult(RESULT_OK, result);
        }
        finish();
    }
}

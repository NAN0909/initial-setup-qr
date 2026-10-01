package jp.initialsetup.helper;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;

/**
 * Android 12 以降のプロビジョニングで、システムから最初に呼ばれる画面。
 * 「完全管理対象デバイス」モードを返すだけで、画面は表示しない。
 */
public class GetProvisioningModeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent result = new Intent();
        result.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE);
        // 言語・タイムゾーンは QR 側でも指定しているが、結果にも載せておく
        result.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_LOCALE, "ja_JP");
        result.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_TIME_ZONE, SetupApplier.TIME_ZONE);
        result.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS, true);
        setResult(RESULT_OK, result);
        finish();
    }
}

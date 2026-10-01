package jp.initialsetup.helper;

import android.app.Activity;
import android.os.Bundle;

/** Android 10〜11: PROVISIONING_SUCCESSFUL。先の broadcast が届いていなくても単独で成立する入口。 */
public class ProvisionedActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        SetupApplier.captureProvisioningOptions(this, getIntent());
        SetupApplier.applyOnce(this);
        SetupApplier.markProvisioningComplete(this);
        SetupApplier.launchPersonalSwitchIfReady(this);
        finish();
    }
}

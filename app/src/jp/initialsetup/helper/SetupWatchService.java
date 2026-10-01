package jp.initialsetup.helper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

/**
 * セットアップウィザードの完了（Settings.Secure user_setup_complete = 1）を待ち、
 * 完了したら「個人用端末へ切り替える」画面を開く。
 * Device Owner アプリはバックグラウンドからの画面起動が許可されている。
 * 念のため通知も出し、プロセスが落ちても利用者が辿り着けるようにする。
 */
public class SetupWatchService extends Service {
    private static final long INTERVAL_MS = 1500;
    private static final long TIMEOUT_MS = 20 * 60 * 1000;
    private static final String CHANNEL = "setup";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long started;

    private final Runnable check = new Runnable() {
        @Override public void run() {
            if (SetupApplier.isUserSetupComplete(SetupWatchService.this)) {
                // セットアップ直後はホーム画面への遷移中なので少し待つ
                handler.postDelayed(new Runnable() {
                    @Override public void run() {
                        openSwitchScreen();
                        stopSelf();
                    }
                }, 2500);
                return;
            }
            if (SystemClock.elapsedRealtime() - started > TIMEOUT_MS) {
                notifyOnly();
                stopSelf();
                return;
            }
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (started == 0) {
            started = SystemClock.elapsedRealtime();
            handler.removeCallbacks(check);
            handler.post(check);
        }
        return START_NOT_STICKY;
    }

    private void openSwitchScreen() {
        notifyOnly();
        try {
            Intent i = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (RuntimeException ignored) {
            // 自動表示できなくても通知とアプリ一覧から開ける
        }
    }

    private void notifyOnly() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_HIGH));
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try { nm.notify(1, n); } catch (RuntimeException ignored) { }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(check);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

package jp.initialsetup.helper;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 初期設定を「一度だけ」適用する。
 * 各項目は失敗しても止めず、結果を Item として記録する（要確認＝NEEDS_CHECK）。
 * 適用済みフラグは SharedPreferences に保存し、再起動・再表示・更新では再実行しない。
 */
public final class SetupApplier {

    public static final int OK = 0;
    public static final int NEEDS_CHECK = 1;   // 自動でできなかった／読み戻しが一致しない
    public static final int MANUAL = 2;        // 最初から手動前提

    public static final String PREFS = "setup";
    public static final String KEY_APPLIED = "applied_v1";
    public static final String KEY_RESULT = "result_v1";
    public static final String KEY_PROVISIONED = "provisioned";

    public static final int BRIGHTNESS_30PCT = 77;      // 255 * 0.30 = 76.5 → 77
    public static final int SCREEN_OFF_MS = 5 * 60 * 1000;
    public static final String TIME_ZONE = "Asia/Tokyo";

    public static final class Item {
        public final String name;
        public final int status;
        public final String detail;
        Item(String name, int status, String detail) {
            this.name = name; this.status = status; this.detail = detail;
        }
    }

    private final Context ctx;
    private final DevicePolicyManager dpm;
    private final ComponentName admin;
    private final List<Item> items = new ArrayList<Item>();

    public SetupApplier(Context context) {
        ctx = context.getApplicationContext();
        dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        admin = AdminReceiver.component(ctx);
    }

    public static boolean isApplied(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_APPLIED, false);
    }

    public static String savedResult(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RESULT, null);
    }

    public static boolean isDeviceOwner(Context c) {
        DevicePolicyManager d = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return d != null && d.isDeviceOwnerApp(c.getPackageName());
    }

    /** Settings.Secure "user_setup_complete" は非公開定数だが読み取りは可能。 */
    public static boolean isUserSetupComplete(Context c) {
        try {
            return Settings.Secure.getInt(c.getContentResolver(), "user_setup_complete", 0) == 1;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 未適用なら適用して結果を保存。適用済みなら保存済み結果を返す（再実行しない）。 */
    public List<Item> applyOnce() {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean(KEY_APPLIED, false)) {
            return decode(p.getString(KEY_RESULT, ""));
        }
        if (!dpm.isDeviceOwnerApp(ctx.getPackageName())) {
            items.add(new Item("管理権限", NEEDS_CHECK, "このアプリは Device Owner ではないため、設定を適用できません。"));
            return items;
        }
        // 先に通知権限を自分へ付与（Android 13+）。失敗しても続行。
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                dpm.setPermissionGrantState(admin, ctx.getPackageName(),
                        "android.permission.POST_NOTIFICATIONS",
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            }
        } catch (RuntimeException ignored) { }

        applyLanguage();
        applyTimeZone();
        applyScreenTimeout();
        applyBrightness();
        applyLocation();
        applyVolumes();
        items.add(new Item("緊急速報メール", MANUAL,
                "公開APIでは確実にOFFにできないため、設定画面を開いて手動でOFFにしてください。"));

        p.edit().putBoolean(KEY_APPLIED, true).putString(KEY_RESULT, encode(items)).apply();
        return items;
    }

    // ---- 各項目 -------------------------------------------------------

    private void applyLanguage() {
        // システム言語を変える公開APIは Device Owner にも無い。QR の PROVISIONING_LOCALE で
        // セットアップウィザードが設定した結果を読み戻して確認するだけ。
        Locale l = Locale.getDefault();
        String tag = l.toLanguageTag();
        if ("ja".equals(l.getLanguage())) {
            items.add(new Item("言語", OK, "日本語（" + tag + "）。QRの指定で設定済み。"));
        } else {
            items.add(new Item("言語", NEEDS_CHECK, "現在 " + tag + "。設定 > 一般管理 > 言語 から日本語を選んでください。"));
        }
    }

    private void applyTimeZone() {
        String before = TimeZone.getDefault().getID();
        boolean ok;
        try {
            ok = dpm.setTimeZone(admin, TIME_ZONE);
            if (!ok) {
                // 自動タイムゾーンが有効だと false を返す。無効化してから再設定。
                dpm.setGlobalSetting(admin, Settings.Global.AUTO_TIME_ZONE, "0");
                ok = dpm.setTimeZone(admin, TIME_ZONE);
            }
        } catch (RuntimeException e) {
            ok = false;
        }
        TimeZone.setDefault(null);
        String after = TimeZone.getDefault().getID();
        if (TIME_ZONE.equals(after)) {
            items.add(new Item("タイムゾーン", OK, "日本時間（Asia/Tokyo）。" + (before.equals(after) ? "元から一致。" : "")));
        } else {
            items.add(new Item("タイムゾーン", NEEDS_CHECK, "読み戻し: " + after + "（setTimeZone=" + ok + "）。設定 > 一般管理 > 日付と時刻 を確認してください。"));
        }
    }

    private void applyScreenTimeout() {
        String err = null;
        try {
            dpm.setSystemSetting(admin, Settings.System.SCREEN_OFF_TIMEOUT, String.valueOf(SCREEN_OFF_MS));
        } catch (RuntimeException e) {
            err = e.getClass().getSimpleName();
        }
        int v = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, -1);
        if (v == SCREEN_OFF_MS) {
            items.add(new Item("画面タイムアウト", OK, "5分（300000 ms）"));
        } else {
            items.add(new Item("画面タイムアウト", NEEDS_CHECK, "読み戻し: " + v + " ms" + (err != null ? "（" + err + "）" : "")));
        }
    }

    private void applyBrightness() {
        String err = null;
        try {
            dpm.setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    String.valueOf(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL));
            dpm.setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS, String.valueOf(BRIGHTNESS_30PCT));
        } catch (RuntimeException e) {
            err = e.getClass().getSimpleName();
        }
        int mode = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, -1);
        int val = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1);
        int pct = val >= 0 ? Math.round(val * 100f / 255f) : -1;
        String read = "読み戻し: 設定値 " + val + "/255（約" + pct + "%）、自動調整 " + (mode == 0 ? "OFF" : mode == 1 ? "ON" : "不明");
        if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL && val == BRIGHTNESS_30PCT) {
            items.add(new Item("明るさ", OK, read + "。Galaxyのスライダー表示は設定値と同じ比率にならない場合があります。"));
        } else {
            items.add(new Item("明るさ", NEEDS_CHECK, read + (err != null ? "（" + err + "）" : "")));
        }
    }

    private void applyLocation() {
        String err = null;
        try {
            dpm.setLocationEnabled(admin, false);
        } catch (RuntimeException e) {
            err = e.getClass().getSimpleName();
        }
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        boolean enabled = lm != null && lm.isLocationEnabled();
        if (!enabled) {
            items.add(new Item("位置情報", OK, "OFF"));
        } else {
            items.add(new Item("位置情報", NEEDS_CHECK, "読み戻し: ON のまま" + (err != null ? "（" + err + "）" : "")));
        }
    }

    private void applyVolumes() {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            items.add(new Item("音量", NEEDS_CHECK, "AudioManager を取得できません"));
            return;
        }
        // メディア音量 0
        setVolume(am, AudioManager.STREAM_MUSIC, "メディア音量", 0, false);

        // 着信・通知音 OFF: 着信ストリーム 0（＝バイブ/サイレント）。
        // サイレント指定は「サイレントモード（DND）」扱いで通知ポリシーアクセスが要るため、
        // まずバイブモードにしてからストリームを 0 にする。
        String err = null;
        try {
            am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        } catch (RuntimeException e) {
            err = e.getClass().getSimpleName();
        }
        try {
            am.setStreamVolume(AudioManager.STREAM_RING, 0, 0);
        } catch (RuntimeException e) {
            err = e.getClass().getSimpleName();
        }
        try {
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0);
        } catch (RuntimeException ignored) { }
        int ring = am.getStreamVolume(AudioManager.STREAM_RING);
        int notif = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
        int mode = am.getRingerMode();
        String modeName = mode == AudioManager.RINGER_MODE_SILENT ? "サイレント"
                : mode == AudioManager.RINGER_MODE_VIBRATE ? "バイブ" : "通常";
        String read = "読み戻し: 着信 " + ring + "、通知 " + notif + "、モード " + modeName;
        if (mode != AudioManager.RINGER_MODE_NORMAL && ring == 0) {
            items.add(new Item("着信・通知音", OK, read));
        } else {
            items.add(new Item("着信・通知音", NEEDS_CHECK, read + (err != null ? "（" + err + "）" : "") + "。音量パネルから着信音を0にしてください。"));
        }

        // アラーム音量: 可能なら 0。機種によって最小値が 0 にならない。
        setVolume(am, AudioManager.STREAM_ALARM, "アラーム音量", 0, true);
    }

    private void setVolume(AudioManager am, int stream, String label, int target, boolean bestEffort) {
        String err = null;
        try {
            am.setStreamVolume(stream, target, 0);
        } catch (RuntimeException e) {
            err = e.getClass().getSimpleName();
        }
        int v = am.getStreamVolume(stream);
        int min = Build.VERSION.SDK_INT >= 28 ? am.getStreamMinVolume(stream) : 0;
        if (v == target) {
            items.add(new Item(label, OK, String.valueOf(v)));
        } else {
            String why = min > target ? "この機種の最小値は " + min + " です。" : "";
            items.add(new Item(label, NEEDS_CHECK, "読み戻し: " + v + "。" + why + (err != null ? "（" + err + "）" : "")));
        }
    }

    // ---- 緊急速報メールの設定画面を開く（開いただけでは OFF 扱いにしない） ----------

    /** 開けた場合はその方法を返す。開けなければ null。 */
    public static String openEmergencyAlertSettings(Context c) {
        PackageManager pm = c.getPackageManager();
        List<Intent> candidates = new ArrayList<Intent>();
        // AOSP / Google の Cell Broadcast モジュール
        candidates.add(new Intent().setClassName("com.google.android.cellbroadcastreceiver",
                "com.android.cellbroadcastreceiver.CellBroadcastSettings"));
        candidates.add(new Intent().setClassName("com.android.cellbroadcastreceiver",
                "com.android.cellbroadcastreceiver.CellBroadcastSettings"));
        // Samsung（機種・OSで異なるため候補を順に試す）
        candidates.add(new Intent().setClassName("com.samsung.android.app.telephonyui",
                "com.samsung.android.app.telephonyui.cellbroadcast.CellBroadcastSettingsActivity"));
        candidates.add(new Intent().setClassName("com.sec.android.app.cellbroadcastreceiver",
                "com.sec.android.app.cellbroadcastreceiver.CellBroadcastSettings"));
        // 「緊急速報」設定を開く汎用アクション（存在すれば）
        candidates.add(new Intent("android.settings.CELL_BROADCAST_SETTINGS"));
        candidates.add(new Intent("com.android.cellbroadcastreceiver.CELL_BROADCAST_SETTINGS"));
        for (Intent i : candidates) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            List<ResolveInfo> r = pm.queryIntentActivities(i, 0);
            if (r != null && !r.isEmpty()) {
                try {
                    c.startActivity(i);
                    return i.getComponent() != null ? i.getComponent().flattenToShortString() : i.getAction();
                } catch (RuntimeException ignored) { }
            }
        }
        // 最後の手段: 設定アプリのトップ
        try {
            c.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return "settings-top";
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- 保存用の簡易エンコード ----------------------------------------------

    static String encode(List<Item> list) {
        StringBuilder sb = new StringBuilder();
        for (Item it : list) {
            sb.append(it.name.replace('\u001f', ' ')).append('\u001f')
              .append(it.status).append('\u001f')
              .append(it.detail.replace('\u001f', ' ').replace('\u001e', ' ')).append('\u001e');
        }
        return sb.toString();
    }

    static List<Item> decode(String s) {
        List<Item> out = new ArrayList<Item>();
        if (s == null || s.isEmpty()) return out;
        for (String rec : s.split("\u001e")) {
            if (rec.isEmpty()) continue;
            String[] f = rec.split("\u001f", -1);
            if (f.length < 3) continue;
            int st;
            try { st = Integer.parseInt(f[1]); } catch (NumberFormatException e) { st = NEEDS_CHECK; }
            out.add(new Item(f[0], st, f[2]));
        }
        return out;
    }
}

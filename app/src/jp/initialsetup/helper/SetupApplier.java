package jp.initialsetup.helper;

import android.app.admin.DevicePolicyManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.PersistableBundle;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 初期設定を「一度だけ」適用し、OS セットアップ完了後に「個人用端末へ切り替える」画面を出すための状態管理。
 * 参考 v1.9（Setup.java）の遷移・完了判定に合わせている。緊急速報メールの画面は開かない。
 */
public final class SetupApplier {

    public static final int OK = 0;
    public static final int NEEDS_CHECK = 1;   // 自動でできなかった／読み戻しが一致しない

    static final String PREFS = "setup";
    static final String EXTRA_SHOW_PERSONAL_SWITCH = "show_personal_switch_after_setup";
    static final int JOB_ID = 1801;
    static final long COMPLETION_WINDOW_MS = 30L * 60 * 1000;

    public static final int BRIGHTNESS_30PCT = Math.round(255 * 0.30f);   // 77
    public static final int SCREEN_OFF_MS = 300000;
    public static final String TIME_ZONE = "Asia/Tokyo";

    public static final class Item {
        public final String name;
        public final int status;
        public final String detail;
        Item(String name, int status, String detail) { this.name = name; this.status = status; this.detail = detail; }
    }

    private SetupApplier() {}

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    static DevicePolicyManager dpm(Context c) { return (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE); }
    static ComponentName admin(Context c) { return new ComponentName(c.getApplicationContext(), AdminReceiver.class); }
    public static boolean isOwner(Context c) { DevicePolicyManager d = dpm(c); return d != null && d.isDeviceOwnerApp(c.getPackageName()); }

    // ---- プロビジョニング情報 -------------------------------------------------

    /** QR の ADMIN_EXTRAS_BUNDLE から show_personal_switch_after_setup を保存する。無ければ何もしない。 */
    static void captureProvisioningOptions(Context c, Intent intent) {
        if (intent == null) return;
        try {
            PersistableBundle extras = intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
            if (extras != null && extras.containsKey(EXTRA_SHOW_PERSONAL_SWITCH)) {
                prefs(c).edit().putBoolean("showPersonalSwitch", extras.getBoolean(EXTRA_SHOW_PERSONAL_SWITCH, false)).apply();
            }
        } catch (RuntimeException ignored) { }
    }

    static void markProvisioningComplete(Context c) {
        prefs(c).edit().putBoolean("provisioningComplete", true).commit();
        scheduleCompletionCheck(c);
    }

    static void markPolicyComplianceFinished(Context c) {
        prefs(c).edit().putBoolean("policyComplianceFinished", true).commit();
        scheduleCompletionCheck(c);
    }

    /** OS のセットアップ完了: user_setup_complete（非公開定数）と DEVICE_PROVISIONED の両方が 1。 */
    static boolean systemSetupComplete(Context c) {
        try {
            return Settings.Secure.getInt(c.getContentResolver(), "user_setup_complete", 0) == 1
                && Settings.Global.getInt(c.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0) == 1;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean shouldShowPersonalSwitch(Context c) {
        SharedPreferences p = prefs(c);
        return p.getBoolean("showPersonalSwitch", false)
            && p.contains("report")
            && systemSetupComplete(c)
            && (p.getBoolean("provisioningComplete", false) || p.getBoolean("policyComplianceFinished", false));
    }

    /** 非永続・最長 30 分の Job で完了を確認する。再起動後には残らない。 */
    static void scheduleCompletionCheck(Context c) {
        SharedPreferences p = prefs(c);
        if (!isOwner(c) || !p.getBoolean("showPersonalSwitch", false) || p.getBoolean("switchPageShown", false)) return;
        long now = System.currentTimeMillis();
        if (!p.contains("completionCheckUntil")) {
            p.edit().putLong("completionCheckUntil", now + COMPLETION_WINDOW_MS).commit();
        }
        if (now >= p.getLong("completionCheckUntil", 0)) return;
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, CompletionJob.class))
            .setMinimumLatency(5000).setOverrideDeadline(15000).build());
    }

    static void cancelCompletionCheck(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) js.cancel(JOB_ID);
    }

    /** 条件がそろっていれば切り替え画面を開く（Device Owner はバックグラウンドからの起動が許可される）。 */
    static void launchPersonalSwitchIfReady(Context c) {
        if (!shouldShowPersonalSwitch(c) || !isOwner(c) || prefs(c).getBoolean("switchPageShown", false)) return;
        Intent page = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            c.startActivity(page);
        } catch (RuntimeException e) {
            prefs(c).edit().putString("completionLaunchError", e.getClass().getSimpleName()).apply();
        }
    }

    // ---- 設定の適用 ---------------------------------------------------------

    /** 未適用（attempted=false）で Device Owner のときだけ適用する。再表示・再起動・更新では再実行しない。 */
    public static synchronized void applyOnce(Context c) {
        if (isOwner(c) && !prefs(c).getBoolean("attempted", false)) apply(c);
    }

    /** 利用者が明示的に押したときだけ再適用に使う。 */
    public static synchronized void apply(Context c) {
        if (!isOwner(c)) return;
        // 先に attempted を立てる: 途中で落ちても、次回に利用者の変更を上書きしない。
        if (!prefs(c).edit().putBoolean("attempted", true).putBoolean("complete", false).commit()) return;
        Context ctx = c.getApplicationContext();
        DevicePolicyManager dpm = dpm(ctx);
        ComponentName admin = admin(ctx);
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        List<Item> items = new ArrayList<Item>();

        // 言語: QR の PROVISIONING_LOCALE で設定される。DPC から変える公開 API は無いので読み戻しのみ。
        String tag = Locale.getDefault().toLanguageTag();
        items.add("ja".equals(Locale.getDefault().getLanguage())
            ? new Item("言語", OK, "日本語（" + tag + "）")
            : new Item("言語", NEEDS_CHECK, "現在 " + tag + "。設定 > 一般管理 > 言語 から変更してください。"));

        // タイムゾーン: QR で指定。念のため setTimeZone で合わせ、読み戻す。
        String tzErr = null;
        try {
            if (!TIME_ZONE.equals(TimeZone.getDefault().getID())) {
                if (!dpm.setTimeZone(admin, TIME_ZONE)) {
                    dpm.setGlobalSetting(admin, Settings.Global.AUTO_TIME_ZONE, "0");
                    dpm.setTimeZone(admin, TIME_ZONE);
                }
                TimeZone.setDefault(null);
            }
        } catch (RuntimeException e) { tzErr = e.getClass().getSimpleName(); }
        String tz = TimeZone.getDefault().getID();
        items.add(TIME_ZONE.equals(tz)
            ? new Item("タイムゾーン", OK, "日本時間（Asia/Tokyo）")
            : new Item("タイムゾーン", NEEDS_CHECK, "読み戻し: " + tz + (tzErr != null ? "（" + tzErr + "）" : "")));

        // 画面タイムアウト 5 分
        String err = null;
        try { dpm.setSystemSetting(admin, Settings.System.SCREEN_OFF_TIMEOUT, String.valueOf(SCREEN_OFF_MS)); }
        catch (RuntimeException e) { err = e.getClass().getSimpleName(); }
        int timeout = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, -1);
        items.add(timeout == SCREEN_OFF_MS
            ? new Item("画面タイムアウト", OK, "5分（300000 ms）")
            : new Item("画面タイムアウト", NEEDS_CHECK, "読み戻し: " + timeout + " ms" + (err != null ? "（" + err + "）" : "")));

        // 明るさ自動調整 OFF
        err = null;
        try { dpm.setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS_MODE, String.valueOf(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)); }
        catch (RuntimeException e) { err = e.getClass().getSimpleName(); }
        int mode = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, -1);
        items.add(mode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            ? new Item("明るさの自動調整", OK, "OFF")
            : new Item("明るさの自動調整", NEEDS_CHECK, "読み戻し: " + (mode == 1 ? "ON" : String.valueOf(mode)) + (err != null ? "（" + err + "）" : "")));

        // 明るさ 30% 相当（Android の 0〜255 で 77。Galaxy のスライダー表示は同じ比率にならないことがある）
        err = null;
        try { dpm.setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS, String.valueOf(BRIGHTNESS_30PCT)); }
        catch (RuntimeException e) { err = e.getClass().getSimpleName(); }
        int val = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1);
        int pct = val >= 0 ? Math.round(val * 100f / 255f) : -1;
        String read = "読み戻し: " + val + "/255（約" + pct + "%）";
        items.add(val == BRIGHTNESS_30PCT
            ? new Item("明るさ", OK, read + "。機種のスライダー表示とは一致しないことがあります。")
            : new Item("明るさ", NEEDS_CHECK, read + (err != null ? "（" + err + "）" : "")));

        // 位置情報 OFF
        err = null;
        try { dpm.setLocationEnabled(admin, false); }
        catch (RuntimeException e) { err = e.getClass().getSimpleName(); }
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        boolean loc = lm != null && lm.isLocationEnabled();
        items.add(!loc ? new Item("位置情報", OK, "OFF")
                       : new Item("位置情報", NEEDS_CHECK, "読み戻し: ON のまま" + (err != null ? "（" + err + "）" : "")));

        // NFC OFF（試行）。公開 API に正式な手段が無いため、失敗しても止めず「要確認」にする。
        items.add(applyNfc(ctx, dpm, admin));

        // 音量: メディア 0 / アラーム 0 を試行 / 着信・通知 0（バイブ設定は変更しない）
        if (am == null) {
            items.add(new Item("音量", NEEDS_CHECK, "AudioManager を取得できません"));
        } else {
            items.add(setZero(am, AudioManager.STREAM_MUSIC, "メディア音量"));
            items.add(setZero(am, AudioManager.STREAM_ALARM, "アラーム音量"));
            err = null;
            try {
                am.setStreamVolume(AudioManager.STREAM_RING, 0, 0);
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0);
            } catch (RuntimeException e) { err = e.getClass().getSimpleName(); }
            int ring = am.getStreamVolume(AudioManager.STREAM_RING);
            int notif = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
            items.add(ring == 0 && notif == 0
                ? new Item("着信・通知音", OK, "0")
                : new Item("着信・通知音", NEEDS_CHECK, "読み戻し: 着信 " + ring + "、通知 " + notif
                    + (err != null ? "（" + err + "）" : "") + "。音量パネルから着信音を 0 にしてください。"));
        }

        boolean complete = true;
        for (Item it : items) if (it.status != OK) complete = false;
        prefs(ctx).edit().putString("report", encode(items)).putBoolean("complete", complete)
            .putLong("appliedAt", System.currentTimeMillis()).commit();
    }

    /**
     * NFC を OFF にする試行。
     * NfcAdapter.disable() は隠し API で WRITE_SECURE_SETTINGS が必要。Device Owner が自分へ権限を付与できるか、
     * 隠し API 制限・メーカー制限にかからないかは端末次第なので、読み戻して結果を正直に出す。
     */
    private static Item applyNfc(Context ctx, DevicePolicyManager dpm, ComponentName admin) {
        android.nfc.NfcAdapter nfc = android.nfc.NfcAdapter.getDefaultAdapter(ctx);
        if (nfc == null) return new Item("NFC", NEEDS_CHECK, "NFC の状態を取得できません（非搭載、または取得に失敗）。");
        if (!nfc.isEnabled()) return new Item("NFC", OK, "OFF");

        String grant;
        try {
            grant = dpm.setPermissionGrantState(admin, ctx.getPackageName(), "android.permission.WRITE_SECURE_SETTINGS",
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED) ? "付与OK" : "付与不可";
        } catch (RuntimeException e) {
            grant = "付与で例外 " + e.getClass().getSimpleName();
        }
        String call;
        try {
            Object r = android.nfc.NfcAdapter.class.getMethod("disable").invoke(nfc);
            call = "OFF命令 " + r;
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            call = "OFF命令で例外 " + c.getClass().getSimpleName();
        }
        // disable は非同期。最大約 1.5 秒だけ状態変化を待つ。
        for (int i = 0; i < 15 && nfc.isEnabled(); i++) {
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        String info = "（" + grant + "、" + call + "）";
        if (!nfc.isEnabled()) return new Item("NFC", OK, "OFF " + info);
        return new Item("NFC", NEEDS_CHECK, "読み戻し: ON のまま " + info
            + "。「NFC の設定画面を開く」から手動で OFF にできます。");
    }

    /** 現在の NFC 状態（画面表示用）。 */
    static String nfcStateText(Context c) {
        try {
            android.nfc.NfcAdapter nfc = android.nfc.NfcAdapter.getDefaultAdapter(c);
            if (nfc == null) return "NFC の状態を取得できません";
            return nfc.isEnabled() ? "現在の NFC: ON" : "現在の NFC: OFF";
        } catch (RuntimeException e) {
            return "NFC の状態を取得できません";
        }
    }

    private static Item setZero(AudioManager am, int stream, String label) {
        String err = null;
        try {
            if (am.isVolumeFixed()) throw new IllegalStateException("VolumeFixed");
            am.setStreamVolume(stream, 0, 0);
            if (am.getStreamVolume(stream) != 0) am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0);
        } catch (RuntimeException e) { err = e.getClass().getSimpleName(); }
        int v = am.getStreamVolume(stream);
        if (v == 0) return new Item(label, OK, "0");
        int min = am.getStreamMinVolume(stream);
        return new Item(label, NEEDS_CHECK, "読み戻し: " + v + (min > 0 ? "（この機種の最小値は " + min + "）" : "") + (err != null ? "（" + err + "）" : ""));
    }

    // ---- 保存用の簡易エンコード ------------------------------------------------

    public static List<Item> savedReport(Context c) { return decode(prefs(c).getString("report", null)); }

    static String encode(List<Item> list) {
        StringBuilder sb = new StringBuilder();
        for (Item it : list) {
            sb.append(it.name.replace('\u001f', ' ')).append('\u001f').append(it.status).append('\u001f')
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

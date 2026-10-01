package jp.initialsetup.helper;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** レイアウト XML を使わず、シンプルな画面を組み立てるための小道具。 */
final class Ui {
    static final int INK = 0xFF1E2A26;
    static final int MUTED = 0xFF5B6B66;
    static final int BG = 0xFFF6F5EF;
    static final int CARD = 0xFFFFFFFF;
    static final int ACCENT = 0xFF1F6B4A;
    static final int WARN = 0xFF9A5B00;
    static final int WARN_BG = 0xFFFFF2DB;
    static final int OK_BG = 0xFFE4F2EA;
    static final int DANGER = 0xFFA8323B;

    private Ui() {}

    static int dp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static ScrollView page(Context c, LinearLayout content) {
        ScrollView sv = new ScrollView(c);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(c, 20), dp(c, 28), dp(c, 20), dp(c, 28));
        sv.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    static TextView eyebrow(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(ACCENT);
        t.setLetterSpacing(0.08f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static TextView title(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(24);
        t.setTextColor(INK);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(c, 4), 0, dp(c, 8));
        return t;
    }

    static TextView body(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(15);
        t.setTextColor(MUTED);
        t.setLineSpacing(0, 1.25f);
        t.setPadding(0, 0, 0, dp(c, 12));
        return t;
    }

    static Button button(Context c, String s, boolean primary) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(c, 10));
        if (primary) {
            d.setColor(ACCENT);
            b.setTextColor(Color.WHITE);
        } else {
            d.setColor(CARD);
            d.setStroke(dp(c, 1), 0xFFCFD6D2);
            b.setTextColor(INK);
        }
        b.setBackground(d);
        b.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 10);
        b.setLayoutParams(lp);
        return b;
    }

    static View card(Context c, SetupApplier.Item it) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(c, 10));
        d.setColor(it.status == SetupApplier.OK ? OK_BG : WARN_BG);
        row.setBackground(d);
        row.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 8);
        row.setLayoutParams(lp);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(c);
        name.setText(it.name);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(INK);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView badge = new TextView(c);
        badge.setTextSize(12);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        if (it.status == SetupApplier.OK) {
            badge.setText("適用済み");
            badge.setTextColor(ACCENT);
        } else if (it.status == SetupApplier.MANUAL) {
            badge.setText("手動で設定");
            badge.setTextColor(WARN);
        } else {
            badge.setText("要確認");
            badge.setTextColor(WARN);
        }
        head.addView(name);
        head.addView(badge);
        row.addView(head);

        TextView detail = new TextView(c);
        detail.setText(it.detail);
        detail.setTextSize(13);
        detail.setTextColor(MUTED);
        detail.setPadding(0, dp(c, 4), 0, 0);
        row.addView(detail);
        return row;
    }

    static void addItems(Context c, LinearLayout into, List<SetupApplier.Item> items) {
        for (SetupApplier.Item it : items) into.addView(card(c, it));
    }
}

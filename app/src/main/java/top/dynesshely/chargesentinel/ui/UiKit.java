package top.dynesshely.chargesentinel.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** 程序化布局用的最小 UI 工具集（不引入 AppCompat/Material 依赖）。 */
public final class UiKit {

    public static final int COLOR_BG = 0xFF0B0F14;
    public static final int COLOR_CARD = 0xFF141A22;
    public static final int COLOR_STROKE = 0xFF1E2731;
    public static final int COLOR_TEXT = 0xFFE6EDF3;
    public static final int COLOR_MUTED = 0xFF8B98A5;
    public static final int COLOR_FAINT = 0xFF5C6874;
    public static final int COLOR_ACCENT = 0xFF3DDC97;
    public static final int COLOR_AMBER = 0xFFFFB454;
    public static final int COLOR_RED = 0xFFF87171;

    private UiKit() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context context, CharSequence value, float sizeSp, int color, boolean medium) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (medium) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return view;
    }

    public static TextView hint(Context context, CharSequence value) {
        TextView view = text(context, value, 12, COLOR_MUTED, false);
        view.setLineSpacing(dp(context, 3), 1f);
        return view;
    }

    public static LinearLayout card(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(context, 18), dp(context, 16), dp(context, 18), dp(context, 16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(COLOR_CARD);
        background.setCornerRadius(dp(context, 20));
        background.setStroke(dp(context, 1), COLOR_STROKE);
        layout.setBackground(background);
        return layout;
    }

    public static GradientDrawable chip(Context context, int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color)));
        background.setCornerRadius(dp(context, 30));
        background.setStroke(dp(context, 1), Color.argb(90, Color.red(color), Color.green(color), Color.blue(color)));
        return background;
    }

    public static SeekBar seekBar(Context context, int color) {
        SeekBar bar = new SeekBar(context);
        bar.setProgressTintList(ColorStateList.valueOf(color));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF22303C));
        bar.setThumbTintList(ColorStateList.valueOf(color));
        bar.setPadding(0, dp(context, 6), 0, dp(context, 6));
        return bar;
    }

    public static LinearLayout.LayoutParams layoutParams(Context context, int heightDp, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                heightDp < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(context, heightDp));
        params.topMargin = dp(context, topMarginDp);
        return params;
    }

    public static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    public static LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }
}

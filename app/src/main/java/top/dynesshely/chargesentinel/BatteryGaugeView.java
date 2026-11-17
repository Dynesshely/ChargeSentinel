package top.dynesshely.chargesentinel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/** 270° 弧形电量表，带“停止充电”阈值刻度。 */
public class BatteryGaugeView extends View {

    private static final int COLOR_TRACK = 0xFF1B2430;
    private static final int COLOR_GREEN = 0xFF3DDC97;
    private static final int COLOR_AMBER = 0xFFFFB454;
    private static final int COLOR_RED = 0xFFF87171;
    private static final int COLOR_TEXT = 0xFFE6EDF3;
    private static final int COLOR_MUTED = 0xFF8B98A5;

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int level = 0;
    private int stop = 80;
    private boolean suspended = false;
    private boolean plugged = false;

    public BatteryGaugeView(Context c) {
        this(c, null);
    }

    public BatteryGaugeView(Context c, AttributeSet a) {
        super(c, a);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setStrokeWidth(dp(2f));
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void set(int level, int stop, boolean suspended, boolean plugged) {
        this.level = level;
        this.stop = stop;
        this.suspended = suspended;
        this.plugged = plugged;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float size = Math.min(w, h);
        float stroke = size * 0.07f;
        float pad = stroke / 2f + size * 0.05f;
        float cx = w / 2f;
        float cy = h / 2f;
        rect.set(cx - size / 2f + pad, cy - size / 2f + pad, cx + size / 2f - pad, cy + size / 2f - pad);

        arcPaint.setStrokeWidth(stroke);
        arcPaint.setColor(COLOR_TRACK);
        canvas.drawArc(rect, 135f, 270f, false, arcPaint);

        int color;
        if (!plugged) {
            color = COLOR_MUTED;
        } else if (suspended) {
            color = COLOR_AMBER;
        } else if (level <= 15) {
            color = COLOR_RED;
        } else {
            color = COLOR_GREEN;
        }

        float sweep = 270f * Math.max(0, Math.min(100, level)) / 100f;
        arcPaint.setColor(color);
        canvas.drawArc(rect, 135f, sweep, false, arcPaint);

        // 上限刻度
        double angle = Math.toRadians(135f + 270f * Math.max(0, Math.min(100, stop)) / 100f);
        float radius = rect.width() / 2f;
        float inner = radius - stroke / 2f - dp(3f);
        float outer = radius + stroke / 2f + dp(3f);
        tickPaint.setColor(0xCCFFFFFF);
        canvas.drawLine(cx + (float) Math.cos(angle) * inner, cy + (float) Math.sin(angle) * inner,
                cx + (float) Math.cos(angle) * outer, cy + (float) Math.sin(angle) * outer, tickPaint);

        valuePaint.setTextSize(size * 0.25f);
        valuePaint.setColor(COLOR_TEXT);
        canvas.drawText(level + "%", cx, cy + size * 0.055f, valuePaint);

        labelPaint.setTextSize(size * 0.052f);
        labelPaint.setColor(color);
        String label = suspended ? "充电已暂停" : (plugged ? "充电中" : "未插电");
        canvas.drawText(label, cx, cy + size * 0.12f, labelPaint);

        labelPaint.setTextSize(size * 0.042f);
        labelPaint.setColor(COLOR_MUTED);
        canvas.drawText("上限 " + stop + "%", cx, cy + size * 0.19f, labelPaint);
    }
}

package top.dynesshely.chargesentinel.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import top.dynesshely.chargesentinel.R;

/** 270° 弧形电量表，带“停止充电”阈值刻度。 */
public class BatteryGaugeView extends View {

    private static final int COLOR_TRACK = 0xFF1B2430;

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private int level = 0;
    private int stopAt = 80;
    private boolean suspended = false;
    private boolean plugged = false;

    public BatteryGaugeView(Context context) {
        this(context, null);
    }

    public BatteryGaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setStrokeWidth(dp(2f));
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    public void setState(int level, int stopAt, boolean suspended, boolean plugged) {
        this.level = level;
        this.stopAt = stopAt;
        this.suspended = suspended;
        this.plugged = plugged;
        invalidate();
    }

    private int accentColor() {
        if (!plugged) {
            return UiKit.COLOR_MUTED;
        }
        if (suspended) {
            return UiKit.COLOR_AMBER;
        }
        if (level <= 15) {
            return UiKit.COLOR_RED;
        }
        return UiKit.COLOR_ACCENT;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        float size = Math.min(width, height);
        float stroke = size * 0.07f;
        float pad = stroke / 2f + size * 0.05f;
        float centerX = width / 2f;
        float centerY = height / 2f;

        arcBounds.set(centerX - size / 2f + pad, centerY - size / 2f + pad,
                centerX + size / 2f - pad, centerY + size / 2f - pad);

        arcPaint.setStrokeWidth(stroke);
        arcPaint.setColor(COLOR_TRACK);
        canvas.drawArc(arcBounds, 135f, 270f, false, arcPaint);

        int accent = accentColor();
        float sweep = 270f * Math.max(0, Math.min(100, level)) / 100f;
        arcPaint.setColor(accent);
        canvas.drawArc(arcBounds, 135f, sweep, false, arcPaint);

        drawLimitTick(canvas, centerX, centerY, stroke);

        valuePaint.setTextSize(size * 0.25f);
        valuePaint.setColor(UiKit.COLOR_TEXT);
        canvas.drawText(level + "%", centerX, centerY + size * 0.055f, valuePaint);

        labelPaint.setTextSize(size * 0.052f);
        labelPaint.setColor(accent);
        canvas.drawText(stateLabel(), centerX, centerY + size * 0.12f, labelPaint);

        labelPaint.setTextSize(size * 0.042f);
        labelPaint.setColor(UiKit.COLOR_MUTED);
        canvas.drawText(getContext().getString(R.string.gauge_limit, stopAt),
                centerX, centerY + size * 0.19f, labelPaint);
    }

    private String stateLabel() {
        if (suspended) {
            return getContext().getString(R.string.state_paused);
        }
        return getContext().getString(plugged ? R.string.state_charging : R.string.state_unplugged);
    }

    private void drawLimitTick(Canvas canvas, float centerX, float centerY, float stroke) {
        double angle = Math.toRadians(135f + 270f * Math.max(0, Math.min(100, stopAt)) / 100f);
        float radius = arcBounds.width() / 2f;
        float inner = radius - stroke / 2f - dp(3f);
        float outer = radius + stroke / 2f + dp(3f);
        tickPaint.setColor(0xCCFFFFFF);
        canvas.drawLine(
                centerX + (float) Math.cos(angle) * inner, centerY + (float) Math.sin(angle) * inner,
                centerX + (float) Math.cos(angle) * outer, centerY + (float) Math.sin(angle) * outer,
                tickPaint);
    }
}

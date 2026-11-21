package top.dynesshely.chargesentinel.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import top.dynesshely.chargesentinel.R;
import top.dynesshely.chargesentinel.history.HistoryEvent;
import top.dynesshely.chargesentinel.history.HistoryPoint;

/**
 * 电量历史折线：按“允许充电/已暂停”分段着色，叠加阈值虚线与停充/恢复事件标记。
 */
public class BatteryChartView extends View {

    private static final int MAX_POINTS = 320;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eventPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eventLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint emptyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF plot = new RectF();

    private List<HistoryPoint> points = Collections.emptyList();
    private List<HistoryEvent> events = Collections.emptyList();
    private int stopAt = 80;
    private int resumeAt = 50;
    private long rangeStart;
    private long rangeEnd;
    private boolean longRange;

    public BatteryChartView(Context context) {
        this(context, null);
    }

    public BatteryChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gridPaint.setColor(0xFF1B2430);
        gridPaint.setStrokeWidth(dp(1f));

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2f));
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        thresholdPaint.setStyle(Paint.Style.STROKE);
        thresholdPaint.setStrokeWidth(dp(1.5f));
        thresholdPaint.setPathEffect(new DashPathEffect(new float[]{dp(5f), dp(5f)}, 0));

        eventLinePaint.setStrokeWidth(dp(1f));

        labelPaint.setColor(UiKit.COLOR_MUTED);
        labelPaint.setTextSize(sp(10f));

        emptyPaint.setColor(UiKit.COLOR_FAINT);
        emptyPaint.setTextSize(sp(12f));
        emptyPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    public void setData(List<HistoryPoint> samples, List<HistoryEvent> eventList,
                        int stopAt, int resumeAt, long start, long end) {
        this.points = downsample(samples);
        this.events = eventList;
        this.stopAt = stopAt;
        this.resumeAt = resumeAt;
        this.rangeStart = start;
        this.rangeEnd = end;
        this.longRange = end - start > 36L * 60 * 60 * 1000;
        invalidate();
    }

    private List<HistoryPoint> downsample(List<HistoryPoint> input) {
        if (input.size() <= MAX_POINTS) {
            return input;
        }
        List<HistoryPoint> out = new ArrayList<>(MAX_POINTS);
        double step = (double) (input.size() - 1) / (MAX_POINTS - 1);
        for (int i = 0; i < MAX_POINTS; i++) {
            out.add(input.get((int) Math.round(i * step)));
        }
        return out;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float left = dp(10f);
        float right = getWidth() - dp(10f);
        float top = dp(14f);
        float bottom = getHeight() - dp(22f);
        if (right <= left || bottom <= top) {
            return;
        }
        plot.set(left, top, right, bottom);

        drawGrid(canvas);
        drawThresholds(canvas);

        if (points.size() < 2) {
            canvas.drawText(getContext().getString(R.string.history_empty),
                    plot.centerX(), plot.centerY(), emptyPaint);
            drawTimeLabels(canvas);
            return;
        }

        drawArea(canvas);
        drawCurve(canvas);
        drawEvents(canvas);
        drawTimeLabels(canvas);
    }

    private void drawGrid(Canvas canvas) {
        for (int i = 0; i <= 4; i++) {
            float y = plot.top + plot.height() * i / 4f;
            canvas.drawLine(plot.left, y, plot.right, y, gridPaint);
        }
        canvas.drawLine(plot.left, plot.bottom, plot.right, plot.bottom, gridPaint);
    }

    private void drawThresholds(Canvas canvas) {
        thresholdPaint.setColor(0x88FFB454);
        float stopY = levelToY(stopAt);
        canvas.drawLine(plot.left, stopY, plot.right, stopY, thresholdPaint);

        thresholdPaint.setColor(0x663DDC97);
        float resumeY = levelToY(resumeAt);
        canvas.drawLine(plot.left, resumeY, plot.right, resumeY, thresholdPaint);
    }

    private void drawArea(Canvas canvas) {
        Path area = new Path();
        area.moveTo(xFor(0), levelToY(points.get(0).level));
        for (int i = 1; i < points.size(); i++) {
            area.lineTo(xFor(i), levelToY(points.get(i).level));
        }
        area.lineTo(xFor(points.size() - 1), plot.bottom);
        area.lineTo(xFor(0), plot.bottom);
        area.close();

        fillPaint.setShader(new LinearGradient(0, plot.top, 0, plot.bottom,
                0x333DDC97, 0x003DDC97, Shader.TileMode.CLAMP));
        canvas.drawPath(area, fillPaint);
    }

    private void drawCurve(Canvas canvas) {
        for (int i = 1; i < points.size(); i++) {
            HistoryPoint previous = points.get(i - 1);
            HistoryPoint current = points.get(i);
            linePaint.setColor(current.suspended ? UiKit.COLOR_AMBER : UiKit.COLOR_ACCENT);
            canvas.drawLine(xFor(i - 1), levelToY(previous.level), xFor(i), levelToY(current.level), linePaint);
        }
    }

    private void drawEvents(Canvas canvas) {
        if (events.isEmpty()) {
            return;
        }
        long span = Math.max(1, rangeEnd - rangeStart);
        for (HistoryEvent event : events) {
            float x = plot.left + (event.timestamp - rangeStart) * plot.width() / span;
            if (x < plot.left || x > plot.right) {
                continue;
            }
            int color = event.isSuspend() ? UiKit.COLOR_AMBER : UiKit.COLOR_ACCENT;
            eventLinePaint.setColor((color & 0x00FFFFFF) | 0x55000000);
            canvas.drawLine(x, plot.top, x, plot.bottom, eventLinePaint);

            eventPaint.setColor(color);
            canvas.drawCircle(x, levelToY(event.level), dp(3.2f), eventPaint);
        }
    }

    private void drawTimeLabels(Canvas canvas) {
        SimpleDateFormat format = new SimpleDateFormat(longRange ? "MM-dd" : "HH:mm", Locale.getDefault());
        String start = format.format(new Date(rangeStart));
        String end = format.format(new Date(rangeEnd));
        String middle = format.format(new Date((rangeStart + rangeEnd) / 2));

        float y = getHeight() - dp(5f);
        labelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(start, plot.left, y, labelPaint);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(middle, plot.centerX(), y, labelPaint);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(end, plot.right, y, labelPaint);
    }

    private float xFor(int index) {
        return plot.left + plot.width() * index / Math.max(1, points.size() - 1);
    }

    private float levelToY(int level) {
        float clamped = Math.max(0, Math.min(100, level));
        return plot.bottom - plot.height() * clamped / 100f;
    }
}

package top.dynesshely.chargesentinel;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

/** 唯一界面：电量表 + 阈值设置 + 调试模拟 + 事件日志。 */
public class MainActivity extends Activity {

    private static final int COLOR_BG = 0xFF0B0F14;
    private static final int COLOR_CARD = 0xFF141A22;
    private static final int COLOR_STROKE = 0xFF1E2731;
    private static final int COLOR_TEXT = 0xFFE6EDF3;
    private static final int COLOR_MUTED = 0xFF8B98A5;
    private static final int COLOR_GREEN = 0xFF3DDC97;
    private static final int COLOR_AMBER = 0xFFFFB454;
    private static final int COLOR_RED = 0xFFF87171;

    private static final int STOP_MIN = 50;
    private static final int RESUME_MIN = 20;
    private static final int GAP = 5;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private BatteryGaugeView gauge;
    private TextView rootChip;
    private TextView statusTitle;
    private TextView statusSub;
    private Switch limitSwitch;
    private Switch simSwitch;
    private SeekBar stopBar;
    private SeekBar resumeBar;
    private SeekBar simBar;
    private TextView stopValue;
    private TextView resumeValue;
    private TextView simValue;
    private View simBlock;
    private TextView logView;
    private boolean binding;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            ui.postDelayed(this, 3000);
        }
    };

    private final Runnable serviceListener = this::refresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);
        setContentView(buildRoot());
        bindSettings();
        askNotificationPermission();
        checkRoot();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ChargeService.addListener(serviceListener);
        refresh();
        ui.removeCallbacks(ticker);
        ui.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ChargeService.removeListener(serviceListener);
        ui.removeCallbacks(ticker);
    }

    // ---------------------------------------------------------------- 构建界面

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return t;
    }

    private LinearLayout.LayoutParams match(int heightDp, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, heightDp < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(heightDp));
        p.topMargin = dp(topDp);
        return p;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_CARD);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), COLOR_STROKE);
        l.setBackground(bg);
        return l;
    }

    private View buildRoot() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(30));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("CHARGESENTINEL", 12, COLOR_MUTED, true);
        brand.setLetterSpacing(0.16f);
        header.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        rootChip = text("检测 ROOT…", 11, COLOR_MUTED, true);
        rootChip.setPadding(dp(10), dp(5), dp(10), dp(5));
        rootChip.setBackground(chipBackground(COLOR_MUTED));
        header.addView(rootChip);
        root.addView(header);

        gauge = new BatteryGaugeView(this);
        root.addView(gauge, match(250, 6));

        statusTitle = text("—", 23, COLOR_TEXT, true);
        statusTitle.setGravity(Gravity.CENTER);
        root.addView(statusTitle, match(-1, 6));

        statusSub = text("", 13, COLOR_MUTED, false);
        statusSub.setGravity(Gravity.CENTER);
        root.addView(statusSub, match(-1, 4));

        // ---- 充电上限 ----
        LinearLayout limit = card();
        LinearLayout.LayoutParams cardLp = match(-1, 16);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(text("充电上限", 16, COLOR_TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        limitSwitch = new Switch(this);
        limitSwitch.setShowText(false);
        limitSwitch.setThumbTintList(ColorStateList.valueOf(COLOR_GREEN));
        limitSwitch.setTrackTintList(ColorStateList.valueOf(0xFF2A3542));
        titleRow.addView(limitSwitch);
        limit.addView(titleRow);

        limit.addView(hint("到达上限即切断输入电源，回落到下限后再恢复充电。"), match(-1, 6));

        LinearLayout stopRow = new LinearLayout(this);
        stopRow.setGravity(Gravity.CENTER_VERTICAL);
        stopRow.addView(text("停止充电", 13, COLOR_MUTED, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stopValue = text("80%", 15, COLOR_GREEN, true);
        stopRow.addView(stopValue);
        limit.addView(stopRow, match(-1, 16));

        stopBar = makeSeekBar();
        stopBar.setMax(100 - STOP_MIN);
        limit.addView(stopBar, match(-1, 2));

        LinearLayout resumeRow = new LinearLayout(this);
        resumeRow.setGravity(Gravity.CENTER_VERTICAL);
        resumeRow.addView(text("恢复充电", 13, COLOR_MUTED, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        resumeValue = text("50%", 15, COLOR_GREEN, true);
        resumeRow.addView(resumeValue);
        limit.addView(resumeRow, match(-1, 12));

        resumeBar = makeSeekBar();
        resumeBar.setMax(95 - RESUME_MIN);
        limit.addView(resumeBar, match(-1, 2));
        root.addView(limit, cardLp);

        // ---- 调试 ----
        LinearLayout debug = card();
        LinearLayout simRow = new LinearLayout(this);
        simRow.setGravity(Gravity.CENTER_VERTICAL);
        simRow.addView(text("模拟电量（调试）", 16, COLOR_TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        simSwitch = new Switch(this);
        simSwitch.setShowText(false);
        simSwitch.setThumbTintList(ColorStateList.valueOf(COLOR_AMBER));
        simSwitch.setTrackTintList(ColorStateList.valueOf(0xFF2A3542));
        simRow.addView(simSwitch);
        debug.addView(simRow);

        simBlock = new LinearLayout(this);
        ((LinearLayout) simBlock).setOrientation(LinearLayout.VERTICAL);

        LinearLayout simValueRow = new LinearLayout(this);
        simValueRow.setGravity(Gravity.CENTER_VERTICAL);
        simValueRow.addView(text("使用模拟电量", 13, COLOR_MUTED, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        simValue = text("90%", 15, COLOR_AMBER, true);
        simValueRow.addView(simValue);
        ((LinearLayout) simBlock).addView(simValueRow, match(-1, 14));

        simBar = makeSeekBar();
        simBar.setMax(100);
        ((LinearLayout) simBlock).addView(simBar, match(-1, 2));
        ((LinearLayout) simBlock).addView(
                hint("仅用于验证 80/50 切换逻辑：策略读取模拟电量，真实电量不受影响。"), match(-1, 8));
        debug.addView(simBlock);
        debug.addView(hint("真机验证提示：把上限调到当前电量以下，可立即触发停止充电。"), match(-1, 8));
        root.addView(debug, match(-1, 12));

        // ---- 日志 ----
        LinearLayout logCard = card();
        logCard.addView(text("事件日志", 16, COLOR_TEXT, true));
        logView = text("暂无事件", 11, COLOR_MUTED, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setLineSpacing(dp(3), 1f);
        logCard.addView(logView, match(-1, 10));
        root.addView(logCard, match(-1, 12));

        TextView footer = text("控制节点 /sys/class/power_supply/battery/input_suspend · 需要 root", 11, 0xFF5C6874, false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, match(-1, 18));

        return scroll;
    }

    private TextView hint(String s) {
        TextView t = text(s, 12, COLOR_MUTED, false);
        t.setLineSpacing(dp(3), 1f);
        return t;
    }

    private GradientDrawable chipBackground(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color)));
        bg.setCornerRadius(dp(30));
        bg.setStroke(dp(1), Color.argb(90, Color.red(color), Color.green(color), Color.blue(color)));
        return bg;
    }

    private SeekBar makeSeekBar() {
        SeekBar bar = new SeekBar(this);
        bar.setProgressTintList(ColorStateList.valueOf(COLOR_GREEN));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF22303C));
        bar.setThumbTintList(ColorStateList.valueOf(COLOR_GREEN));
        bar.setPadding(0, dp(6), 0, dp(6));
        return bar;
    }

    // ---------------------------------------------------------------- 绑定 / 刷新

    private void bindSettings() {
        binding = true;
        limitSwitch.setChecked(Prefs.enabled(this));
        stopBar.setProgress(Prefs.stop(this) - STOP_MIN);
        resumeBar.setProgress(Prefs.resume(this) - RESUME_MIN);
        simSwitch.setChecked(Prefs.simEnabled(this));
        simBar.setProgress(Prefs.simLevel(this));
        binding = false;
        simBlock.setVisibility(Prefs.simEnabled(this) ? View.VISIBLE : View.GONE);
        stopValue.setText(Prefs.stop(this) + "%");
        resumeValue.setText(Prefs.resume(this) + "%");
        simValue.setText(Prefs.simLevel(this) + "%");

        limitSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (binding) {
                return;
            }
            Prefs.setEnabled(this, checked);
            if (checked) {
                ensureService();
                ChargeService.requestTick(this);
            } else {
                stopService(new Intent(this, ChargeService.class));
            }
            refresh();
        });

        stopBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (!fromUser || binding) {
                    return;
                }
                int stop = p + STOP_MIN;
                Prefs.setStop(MainActivity.this, stop);
                stopValue.setText(stop + "%");
                int resume = Prefs.resume(MainActivity.this);
                if (resume > stop - GAP) {
                    resume = stop - GAP;
                    Prefs.setResume(MainActivity.this, resume);
                    binding = true;
                    resumeBar.setProgress(resume - RESUME_MIN);
                    binding = false;
                    resumeValue.setText(resume + "%");
                }
                ChargeService.requestTick(MainActivity.this);
                refresh();
            }
        });

        resumeBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (!fromUser || binding) {
                    return;
                }
                int resume = p + RESUME_MIN;
                int stop = Prefs.stop(MainActivity.this);
                if (resume > stop - GAP) {
                    resume = stop - GAP;
                    binding = true;
                    sb.setProgress(resume - RESUME_MIN);
                    binding = false;
                }
                Prefs.setResume(MainActivity.this, resume);
                resumeValue.setText(resume + "%");
                ChargeService.requestTick(MainActivity.this);
                refresh();
            }
        });

        simSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (binding) {
                return;
            }
            Prefs.setSimEnabled(this, checked);
            simBlock.setVisibility(checked ? View.VISIBLE : View.GONE);
            ChargeService.requestTick(this);
            refresh();
        });

        simBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (!fromUser || binding) {
                    return;
                }
                Prefs.setSimLevel(MainActivity.this, p);
                simValue.setText(p + "%");
                ChargeService.requestTick(MainActivity.this);
            }
        });
    }

    private void refresh() {
        if (gauge == null || isFinishing()) {
            return;
        }
        int level = Prefs.simEnabled(this) ? Prefs.simLevel(this) : Battery.level(this);
        boolean plugged = Battery.plugged(this);
        int stop = Prefs.stop(this);
        int resume = Prefs.resume(this);
        boolean enabled = Prefs.enabled(this);
        boolean suspended = enabled && Prefs.suspended(this);

        gauge.set(Math.max(0, Math.min(100, level)), stop, suspended, plugged);
        if (suspended) {
            statusTitle.setText("充电已暂停");
            statusTitle.setTextColor(COLOR_AMBER);
        } else if (plugged) {
            statusTitle.setText("正在充电");
            statusTitle.setTextColor(COLOR_GREEN);
        } else {
            statusTitle.setText("未插电");
            statusTitle.setTextColor(COLOR_MUTED);
        }
        statusSub.setText("电池 " + level + "% · 上限 " + stop + "% / 下限 " + resume + "%");

        List<String> logs = Logs.snapshot();
        if (logs.isEmpty()) {
            logView.setText("暂无事件");
        } else {
            StringBuilder sb = new StringBuilder();
            int n = Math.min(8, logs.size());
            for (int i = 0; i < n; i++) {
                sb.append(logs.get(i));
                if (i < n - 1) {
                    sb.append('\n');
                }
            }
            logView.setText(sb.toString());
        }
    }

    // ---------------------------------------------------------------- 权限与 root

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private void checkRoot() {
        new Thread(() -> {
            ChargeController.Result r = ChargeController.checkRoot();
            runOnUiThread(() -> {
                if (r.ok) {
                    rootChip.setText("ROOT 已授权");
                    rootChip.setTextColor(COLOR_GREEN);
                    rootChip.setBackground(chipBackground(COLOR_GREEN));
                    Logs.add("ROOT 检测通过");
                } else {
                    rootChip.setText("无 ROOT");
                    rootChip.setTextColor(COLOR_RED);
                    rootChip.setBackground(chipBackground(COLOR_RED));
                    Logs.add("ROOT 检测失败: " + r.output);
                }
            });
        }).start();
    }

    private void ensureService() {
        try {
            startForegroundService(new Intent(this, ChargeService.class));
        } catch (Exception e) {
            Logs.add("启动监控服务失败: " + e.getMessage());
        }
    }

    /** 只关心拖动结束的 SeekBar 适配器，减少每个滑块重复实现。 */
    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}

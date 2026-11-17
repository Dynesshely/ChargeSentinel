package top.dynesshely.chargesentinel.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
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

import top.dynesshely.chargesentinel.R;
import top.dynesshely.chargesentinel.core.BatteryInfo;
import top.dynesshely.chargesentinel.core.ChargePolicy;
import top.dynesshely.chargesentinel.core.ChargeState;
import top.dynesshely.chargesentinel.core.SysfsChargeControl;
import top.dynesshely.chargesentinel.data.EventLog;
import top.dynesshely.chargesentinel.data.Settings;
import top.dynesshely.chargesentinel.service.ChargeService;

/** 唯一界面：弧形电量表 + 阈值 + 调试模拟 + 事件日志。 */
public class MainActivity extends Activity {

    private static final int LOG_LINES = 8;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private BatteryGaugeView gauge;
    private TextView rootChip;
    private TextView statusTitle;
    private TextView statusSub;
    private Switch limitSwitch;
    private Switch simulationSwitch;
    private SeekBar stopBar;
    private SeekBar resumeBar;
    private SeekBar simulationBar;
    private TextView stopValue;
    private TextView resumeValue;
    private TextView simulationValue;
    private View simulationBlock;
    private TextView logView;

    private boolean binding;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            uiHandler.postDelayed(this, 3000);
        }
    };

    private final Runnable serviceListener = this::refresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UiKit.COLOR_BG);
        getWindow().setNavigationBarColor(UiKit.COLOR_BG);
        setContentView(buildRoot());
        bindSettings();
        requestNotificationPermission();
        checkRoot();
        if (Settings.enabled(this)) {
            ensureService();
        }
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ChargeService.addListener(serviceListener);
        refresh();
        uiHandler.removeCallbacks(ticker);
        uiHandler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ChargeService.removeListener(serviceListener);
        uiHandler.removeCallbacks(ticker);
    }

    // ------------------------------------------------------------------ 界面

    private View buildRoot() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(UiKit.COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(30));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(buildHeader());
        root.addView(buildGauge(), lp(250, 6));
        root.addView(buildStatus());
        root.addView(buildLimitCard(), lp(-1, 16));
        root.addView(buildDebugCard(), lp(-1, 12));
        root.addView(buildLogCard(), lp(-1, 12));
        root.addView(buildFooter(), lp(-1, 18));
        return scroll;
    }

    private View buildHeader() {
        LinearLayout header = UiKit.row(this);
        TextView brand = UiKit.text(this, "CHARGESENTINEL", 12, UiKit.COLOR_MUTED, true);
        brand.setLetterSpacing(0.16f);
        header.addView(brand, UiKit.weighted());

        rootChip = UiKit.text(this, getString(R.string.root_checking), 11, UiKit.COLOR_MUTED, true);
        rootChip.setPadding(dp(10), dp(5), dp(10), dp(5));
        rootChip.setBackground(UiKit.chip(this, UiKit.COLOR_MUTED));
        header.addView(rootChip);
        return header;
    }

    private View buildGauge() {
        gauge = new BatteryGaugeView(this);
        return gauge;
    }

    private View buildStatus() {
        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setPadding(0, dp(6), 0, 0);

        statusTitle = UiKit.text(this, "—", 23, UiKit.COLOR_TEXT, true);
        statusTitle.setGravity(Gravity.CENTER);
        status.addView(statusTitle);

        statusSub = UiKit.text(this, "", 13, UiKit.COLOR_MUTED, false);
        statusSub.setGravity(Gravity.CENTER);
        status.addView(statusSub);
        return status;
    }

    private View buildLimitCard() {
        LinearLayout card = UiKit.card(this);
        card.setLayoutParams(lp(-1, 0));

        LinearLayout titleRow = UiKit.row(this);
        titleRow.addView(UiKit.text(this, getString(R.string.limit_title), 16, UiKit.COLOR_TEXT, true),
                UiKit.weighted());
        limitSwitch = makeSwitch(UiKit.COLOR_ACCENT);
        titleRow.addView(limitSwitch);
        card.addView(titleRow);

        card.addView(UiKit.hint(this, getString(R.string.limit_hint)));

        LinearLayout stopRow = UiKit.row(this);
        stopRow.addView(UiKit.text(this, getString(R.string.stop_label), 13, UiKit.COLOR_MUTED, false),
                UiKit.weighted());
        stopValue = UiKit.text(this, "", 15, UiKit.COLOR_ACCENT, true);
        stopRow.addView(stopValue);
        card.addView(stopRow, lp(-1, 16));

        stopBar = makeSeekBar(UiKit.COLOR_ACCENT);
        stopBar.setMax(ChargePolicy.STOP_MAX - ChargePolicy.STOP_MIN);
        card.addView(stopBar, lp(-1, 2));

        LinearLayout resumeRow = UiKit.row(this);
        resumeRow.addView(UiKit.text(this, getString(R.string.resume_label), 13, UiKit.COLOR_MUTED, false),
                UiKit.weighted());
        resumeValue = UiKit.text(this, "", 15, UiKit.COLOR_ACCENT, true);
        resumeRow.addView(resumeValue);
        card.addView(resumeRow, lp(-1, 12));

        resumeBar = makeSeekBar(UiKit.COLOR_ACCENT);
        resumeBar.setMax(ChargePolicy.RESUME_MAX - ChargePolicy.RESUME_MIN);
        card.addView(resumeBar, lp(-1, 2));
        return card;
    }

    private View buildDebugCard() {
        LinearLayout card = UiKit.card(this);

        LinearLayout titleRow = UiKit.row(this);
        titleRow.addView(UiKit.text(this, getString(R.string.debug_title), 16, UiKit.COLOR_TEXT, true),
                UiKit.weighted());
        simulationSwitch = makeSwitch(UiKit.COLOR_AMBER);
        titleRow.addView(simulationSwitch);
        card.addView(titleRow);

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setVisibility(View.GONE);

        LinearLayout valueRow = UiKit.row(this);
        valueRow.addView(UiKit.text(this, getString(R.string.debug_slider_label), 13, UiKit.COLOR_MUTED, false),
                UiKit.weighted());
        simulationValue = UiKit.text(this, "", 15, UiKit.COLOR_AMBER, true);
        valueRow.addView(simulationValue);
        block.addView(valueRow, lp(-1, 14));

        simulationBar = makeSeekBar(UiKit.COLOR_AMBER);
        simulationBar.setMax(100);
        block.addView(simulationBar, lp(-1, 2));
        block.addView(UiKit.hint(this, getString(R.string.debug_hint)), lp(-1, 8));

        simulationBlock = block;
        card.addView(block);
        card.addView(UiKit.hint(this, getString(R.string.debug_tip)), lp(-1, 8));
        return card;
    }

    private View buildLogCard() {
        LinearLayout card = UiKit.card(this);
        card.addView(UiKit.text(this, getString(R.string.log_title), 16, UiKit.COLOR_TEXT, true));

        logView = UiKit.text(this, getString(R.string.log_empty), 11, UiKit.COLOR_MUTED, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setLineSpacing(dp(3), 1f);
        card.addView(logView, lp(-1, 10));
        return card;
    }

    private View buildFooter() {
        TextView footer = UiKit.text(this, getString(R.string.footer_node), 11, UiKit.COLOR_FAINT, false);
        footer.setGravity(Gravity.CENTER);
        return footer;
    }

    private Switch makeSwitch(int color) {
        Switch view = new Switch(this);
        view.setShowText(false);
        view.setThumbTintList(ColorStateList.valueOf(color));
        view.setTrackTintList(ColorStateList.valueOf(0xFF2A3542));
        return view;
    }

    private SeekBar makeSeekBar(int color) {
        return UiKit.seekBar(this, color);
    }

    private int dp(float value) {
        return UiKit.dp(this, value);
    }

    private LinearLayout.LayoutParams lp(int heightDp, int topMarginDp) {
        return UiKit.layoutParams(this, heightDp, topMarginDp);
    }

    // ------------------------------------------------------------------ 绑定

    private void bindSettings() {
        binding = true;
        limitSwitch.setChecked(Settings.enabled(this));
        stopBar.setProgress(Settings.stop(this) - ChargePolicy.STOP_MIN);
        resumeBar.setProgress(Settings.resume(this) - ChargePolicy.RESUME_MIN);
        simulationSwitch.setChecked(Settings.simulationEnabled(this));
        simulationBar.setProgress(Settings.simulationLevel(this));
        binding = false;

        simulationBlock.setVisibility(Settings.simulationEnabled(this) ? View.VISIBLE : View.GONE);

        limitSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (binding) {
                return;
            }
            Settings.setEnabled(this, checked);
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
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || binding) {
                    return;
                }
                int stop = ChargePolicy.clampStop(progress + ChargePolicy.STOP_MIN);
                Settings.setStop(MainActivity.this, stop);
                int resume = Settings.resume(MainActivity.this);
                if (resume > stop - ChargePolicy.MIN_GAP) {
                    resume = ChargePolicy.clampResume(stop - ChargePolicy.MIN_GAP, stop);
                    Settings.setResume(MainActivity.this, resume);
                    binding = true;
                    resumeBar.setProgress(resume - ChargePolicy.RESUME_MIN);
                    binding = false;
                }
                ChargeService.requestTick(MainActivity.this);
                refresh();
            }
        });

        resumeBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || binding) {
                    return;
                }
                int stop = Settings.stop(MainActivity.this);
                int resume = ChargePolicy.clampResume(progress + ChargePolicy.RESUME_MIN, stop);
                if (resume != progress + ChargePolicy.RESUME_MIN) {
                    binding = true;
                    seekBar.setProgress(resume - ChargePolicy.RESUME_MIN);
                    binding = false;
                }
                Settings.setResume(MainActivity.this, resume);
                ChargeService.requestTick(MainActivity.this);
                refresh();
            }
        });

        simulationSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (binding) {
                return;
            }
            Settings.setSimulationEnabled(this, checked);
            simulationBlock.setVisibility(checked ? View.VISIBLE : View.GONE);
            ChargeService.requestTick(this);
            refresh();
        });

        simulationBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || binding) {
                    return;
                }
                Settings.setSimulationLevel(MainActivity.this, progress);
                ChargeService.requestTick(MainActivity.this);
                refresh();
            }
        });
    }

    private void refresh() {
        if (gauge == null || isFinishing()) {
            return;
        }
        int stop = Settings.stop(this);
        int resume = Settings.resume(this);
        boolean enabled = Settings.enabled(this);
        boolean simulation = Settings.simulationEnabled(this);
        boolean suspended = enabled && Settings.suspended(this);

        int level = simulation ? Settings.simulationLevel(this) : BatteryInfo.level(this);
        boolean plugged = BatteryInfo.plugged(this);

        gauge.setState(Math.max(0, Math.min(100, level)), stop, suspended, plugged);
        if (suspended) {
            statusTitle.setText(R.string.state_paused);
            statusTitle.setTextColor(UiKit.COLOR_AMBER);
        } else if (plugged) {
            statusTitle.setText(R.string.state_charging);
            statusTitle.setTextColor(UiKit.COLOR_ACCENT);
        } else {
            statusTitle.setText(R.string.state_unplugged);
            statusTitle.setTextColor(UiKit.COLOR_MUTED);
        }
        statusSub.setText(getString(R.string.battery_summary, level, stop, resume));

        stopValue.setText(getString(R.string.percent, stop));
        resumeValue.setText(getString(R.string.percent, resume));
        simulationValue.setText(getString(R.string.percent, Settings.simulationLevel(this)));

        List<String> lines = EventLog.recent();
        if (lines.isEmpty()) {
            logView.setText(R.string.log_empty);
        } else {
            StringBuilder builder = new StringBuilder();
            int count = Math.min(LOG_LINES, lines.size());
            for (int i = 0; i < count; i++) {
                builder.append(lines.get(i));
                if (i < count - 1) {
                    builder.append('\n');
                }
            }
            logView.setText(builder.toString());
        }
    }

    // ------------------------------------------------------------------ 权限与 root

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private void checkRoot() {
        new Thread(() -> {
            SysfsChargeControl control = new SysfsChargeControl();
            boolean rooted = control.hasRoot();
            ChargeState state = control.read();
            runOnUiThread(() -> {
                if (rooted) {
                    rootChip.setText(R.string.root_granted);
                    rootChip.setTextColor(UiKit.COLOR_ACCENT);
                    rootChip.setBackground(UiKit.chip(this, UiKit.COLOR_ACCENT));
                    EventLog.add(getString(R.string.log_root_ok));
                } else {
                    rootChip.setText(R.string.root_missing);
                    rootChip.setTextColor(UiKit.COLOR_RED);
                    rootChip.setBackground(UiKit.chip(this, UiKit.COLOR_RED));
                    EventLog.add(getString(R.string.log_root_failed, state.name()));
                }
                refresh();
            });
        }).start();
    }

    private void ensureService() {
        try {
            startForegroundService(new Intent(this, ChargeService.class));
        } catch (Exception e) {
            EventLog.add(getString(R.string.log_service_start_failed, e.getMessage()));
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

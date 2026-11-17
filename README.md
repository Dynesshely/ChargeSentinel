# ChargeSentinel · 充电哨兵

一个只做一件事的 Android 小工具：**把充电上限卡在某个电量，回落之后再自动恢复充电**。

默认策略：充到 **80%** 停止充电，掉到 **50%** 再重新充——典型的电池保养用法（长期插电、或夜里充电时减少满电静置时间）。

> 项目代号：**ChargeSentinel**（充电哨兵）
> 包名：`top.dynesshely.chargesentinel`

## 功能

- 迟滞控制：电量 ≥ 上限 → 切断充电；电量 ≤ 下限 → 恢复充电；中间区间保持原状态，不会来回抖动。
- 阈值可调：停止充电 50–100%，恢复充电 20–95%（两者至少间隔 5 个百分点）。
- 前台服务常驻：每 10 秒采样一次，通知栏显示当前电量与状态，可直接从通知关闭限制。
- 开机自启：打开限制后重启设备，服务自动恢复并重新评估电量。
- 关闭限制 / 卸载服务时自动把充电恢复为允许状态，不会把机器留在“永远不充电”。
- 调试用「模拟电量」开关：不改真实电量，直接喂给策略一个假电量，便于在真机上验证 80/50 切换。
- 事件日志：最近 80 条动作记录，同时写入 logcat（tag `ChargeSentinel`）。

## 工作原理

Android 没有公开 API 能停止充电，本应用通过 **root** 写 sysfs 节点：

| 节点 | 写入 | 含义 |
| --- | --- | --- |
| `/sys/class/power_supply/battery/mmi_charging_enable` | `0` / `1` | 关断 / 允许充电（主控制，写入后能保持） |
| `/sys/class/power_supply/battery/input_suspend` | `1` / `0` | 切断 / 恢复输入电源（立即生效，但会被驱动周期性重置） |

实测无效或不可用：

- `charge_control_limit`：本机内核上只读（`-r--r--r--`），写不进去。
- `/sys/class/qcom-battery/restrict_chg`：能写、能保持，但实测对充电电压/电流没有影响。

**关于“留不住”**：本机（OnePlus 7 Pro，SM8150）的充电器驱动会周期性把 `input_suspend` 重置回 0（满电状态下大约每 10 秒一次），所以“只写一次”的做法会失效。服务因此改为每 10 秒回读一次实际节点值：只要期望状态是“停充”而节点被改回“允许”，就立即重写并记一条日志。这样即使驱动重置也能自愈，代价是持续轮询 `su`。

停充要求两个节点**同时**处于停充位（`mmi=0` 且 `in=1`）；只要其中任意一个被驱动改回，服务就判定为“矛盾状态”并重新写入两个节点。避免只写一个节点导致“看起来停了、实际还在充”。

判断停充是否生效不看节点，看电压：正常充电时电池 `voltage_now` 在 4.39 V 左右，切断输入后回落到 4.08–4.27 V 的静置电压。

## 环境要求

- Android 10+（minSdk 30，targetSdk 34，已在 Android 14 上验证）。
- **必须 root**（Magisk 等），并给本应用授予超级用户权限；没有 root 时界面会显示「无 ROOT」，只能看电量、无法控制。
- 前台服务类型为 `specialUse`，Android 13+ 需允许通知，否则通知栏看不到状态（不影响控制）。

## 构建

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_HOME = 'C:\path\to\Android\Sdk'
.\gradlew.bat assembleDebug
# 产物：app\build\outputs\apk\debug\app-debug.apk
```

项目零第三方依赖（纯 framework + Java），只需要 AGP 8.5.2 与 JDK 17。

## 安装

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

首次打开会申请通知权限；首次执行控制时 Magisk 会弹超级用户授权，选择「允许」并记住。

## 真机验证（OnePlus 7 Pro / GM1911 / Android 14 / Magisk 28.1）

1. 打开「充电上限」，电量 100%（≥ 80%）→ `mmi_charging_enable=0`、`input_suspend=1`，界面「充电已暂停」，
   日志 `电量 100% ≥ 80%，已停止充电`；电压从 4.389 V 落到 4.08–4.13 V。
2. 手工把两个节点改回“允许充电”模拟驱动重置 → 14 秒内服务自动重写并再记一条停止日志（自愈验证通过）。
3. 用「模拟电量」把电量调到 40%（≤ 50%）→ 节点回到 `mmi=1 / in=0`，界面「正在充电」，
   日志 `电量 40% ≤ 50%，已恢复充电`，充电电流恢复（`current_now` 约 -230）。
4. 模拟电量 85%（越过上限）→ 再次停充，日志 `电量 85% ≥ 80%，已停止充电`。
5. 关闭模拟电量后按真实电量运行，界面状态与节点值一致。

界面截图与测试记录见工作区 `.meta/dynesshely/chargesentinel/`（不随仓库提交）。

## 已知限制

- 没有 root 就完全无法控制充电，只能当电量显示用。
- 不同厂商/内核的节点名不一样；换机型时若 `input_suspend` 不存在或被 SELinux 拒绝，需要改 `ChargeController` 里的节点路径。
- 应用被杀掉且系统未重启时，若正处于“暂停充电”，充电会一直保持暂停；重新打开应用或重启后会按当前电量重新评估。
- 部分系统在深度睡眠时会延迟定时器，电量跨越阈值的响应可能晚几十秒级别。

## 目录结构

```
app/src/main/java/top/dynesshely/chargesentinel/
  MainActivity.java      界面（程序化布局：弧形电量表 + 阈值 + 调试 + 日志）
  BatteryGaugeView.java  自绘弧形电量表
  ChargeService.java     前台服务，10 秒一次的策略循环
  ChargeController.java  root + sysfs 读写与校验
  Battery.java           系统电量/供电状态读取
  BootReceiver.java      开机自启
  Prefs.java             阈值与状态持久化
  Logs.java              环形日志（同时输出 logcat）
```

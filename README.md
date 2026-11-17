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
| `/sys/class/power_supply/battery/input_suspend` | `1` / `0` | 切断 / 恢复输入电源（主控制手段） |
| `/sys/class/power_supply/battery/mmi_charging_enable` | `0` / `1` | 允许停充的机型上作为补充写入 |

写入后立即回读节点值校验，只有读回的值符合预期才把状态记成“已暂停”。

> 注意：不少教程会用 `charge_control_limit`，但在本机内核上它是只读的（`-r--r--r--`），写了无效。

策略是**边沿触发**而非每轮都写：状态保存在 SharedPreferences 里，只在越过阈值时执行一次 root 命令，避免每 10 秒都 fork 一次 `su`。

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

1. 打开「充电上限」开关，电量为 100%（≥ 80%）→ `input_suspend` 写成 1，界面变为「充电已暂停」，logcat：`电量 100% ≥ 80%，已停止充电`。
2. 用「模拟电量」把电量调到 48%（≤ 50%）→ `input_suspend` 回到 0，界面「正在充电」，日志 `电量 48% ≤ 50%，已恢复充电`。
3. 模拟电量 55%（处于 50–80 之间）→ 状态保持“允许充电”，不重复写节点。
4. 模拟电量 82% → 再次切断，日志确认。
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

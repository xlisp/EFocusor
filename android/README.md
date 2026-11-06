# EFocusor Android App

安卓端电子调焦器控制 App,配合本仓库的 ESP8266 固件
(`ESP8266FocuserFirmware`,固件 ≥ 1103)使用,替代手机网页版调焦。

- 工程:`EFocusorApp/`(Kotlin + Jetpack Compose,minSdk 26 / targetSdk 35)
- 包名:`com.efocusor.android`
- 通信:与 ASCOM 驱动相同的 `#` 结尾文本协议(USB 串口 9600 或 WiFi TCP 4030)

## 功能

| 功能 | 说明 | 对应固件命令 |
|---|---|---|
| 模拟调焦轮 | 大号旋钮,绕圈拖动 = 相对调焦;刻度随动、行程进度弧、每 30° 触觉反馈;一圈步数可选 100/500/1000/8160,可反转 | `M <target>#`(节流重定向) |
| 快捷移动 | −1000/−100/−10/+10/+100/+1000 | `M#` |
| 停止 / 回零 / 置零 / 定位 | 运动中急停;移动到 0;把当前位置设为 0;跳转绝对位置 | `S#` `M 0#` `P 0#` `M n#` |
| 位置记忆 | 自定义命名位置(如 533MC 相机 / 25mm 目镜),点击直达;每条显示与当前位置的 Δ;可一键更新为当前位置、改名、删除。存在手机本地,USB/WiFi 通用 | `M n#`(本地 SharedPreferences) |
| 状态显示 | 位置/目标/行程、运动状态、温度、固件版本、速度、通信健康度 | `G#`(400ms 轮询) |
| 设备设置 | 最大行程/速度/加速度/方向反转/保持力矩/温度系数 | `D X A R C T` |
| 夜视模式 | 顶栏月亮图标,纯暗红配色 | — |
| 音量键微调 | 控制页音量 ± = ±10 步 | `M#` |

App **不会**像 ASCOM 驱动那样在连接时下发 `D`(以固件自身 maxSteps 为准);
`D/X/A/R/C/T` 写入只发生在设置页。长 `I#` 状态刷新仅在电机静止时进行,
运动中只保持短 `G#` 轮询 —— 与固件 1105 协作式回复设计一致。

## 连接方式

**USB OTG(推荐,零延迟)**
1. 手机 ← OTG 线/转接头 ← 调焦器 USB 口(Wemos D1 mini 等)
2. App 选 "USB 数据线" → 选中设备 → 波特率 9600(固件默认)→ 连接
3. 首次会弹 USB 权限授权,允许即可

**WiFi(TCP)**
- 热点模式:手机连 `Focuser-xxxx`(密码 `012345678`),App 填 `192.168.4.1:4030`
- 局域网模式:手机与调焦器同网,填其 IP,端口 4030

## USB 连不上的排查(重要)

按现象对号入座:

1. **插上后手机毫无反应**(没有 "USB 设备" 通知/提示音)
   → 手机没有进入 OTG 模式,是线或板子的问题,App 层面无解:
   - 换一根**数据线**(很多 C-C 线是纯充电线,没有 D+/D-)
   - **Type-C 直连 Type-C 不行是硬件坑**:大量 ESP8266 开发板的 Type-C
     母座没有焊 CC 下拉电阻(2×5.1kΩ),C 对 C 时手机根本检测不到设备,
     但 A 转 C 线能正常用。解决办法任选:
     - 手机 Type-C → **C转A OTG转接头** → A转C 数据线 → 调焦器(免焊,推荐)
     - 在调焦器 Type-C 座的 CC1/CC2 各焊一只 5.1kΩ 到 GND(一劳永逸)
2. **手机有 "USB 设备" 通知,但 App 列表为空**
   → 串口芯片 VID/PID 不在 usb-serial-for-android 的识别表里
   (如新版 CH9102,VID 1A86 / PID 55D4)。v1.1 起 App 已加回退:
   WCH 芯片按 CH34x 驱动、CDC 类设备按 CDC-ACM 驱动强行打开,
   列表里会显示 "未识别ID,按 xxx 尝试",选它连即可。
   仍不行请把列表里显示的 VID/PID 记下来反馈。
3. **能连上但一动不动/频繁断线**
   - 确认波特率 9600
   - 部分手机省电策略会杀后台 USB 权限,保持 App 在前台测试

## 构建

需要:JDK 17+、Android SDK(platform 35)。

```bash
cd EFocusorApp
./gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- Gradle 8.11.1 wrapper 的 `distributionUrl` 指向腾讯镜像;
  `settings.gradle.kts` 已配置阿里云 google/maven 镜像 + JitPack
  (usb-serial-for-android 依赖),国内网络可直连。
- 如果 `~/.gradle/gradle.properties` 里配置了本地代理(如 127.0.0.1:6789)
  而代理没开,构建会报 "Connection refused",注释掉 `systemProp.*` 即可。

## 无硬件测试(模拟固件)

`tools/mock_focuser.py` 完整模拟固件文本协议与电机运动,用于回归测试:

```bash
# 1. 电脑上启动模拟固件(TCP 4030)
python3 tools/mock_focuser.py

# 2. 手机连电脑后,把手机的 127.0.0.1:4030 反向转发到电脑
adb reverse tcp:4030 tcp:4030

# 3. App → WiFi(TCP) → 127.0.0.1:4030 → 连接
#    滚轮/快捷键/停止/设置 全部可用,电脑端会打印 [rx]/[tx] 协议日志
```

## 目录结构

```
EFocusorApp/
├── app/src/main/java/com/efocusor/android/
│   ├── MainActivity.kt            # 入口、音量键、USB 插拔事件
│   ├── MainViewModel.kt           # 轮询/移动/滚轮节流/设置写入
│   ├── connection/
│   │   ├── FocuserConnection.kt   # #帧协议 + USB/TCP 实现 + 设备枚举(含未知ID回退)
│   │   └── FocuserProtocol.kt     # P/M 状态帧与 I# JSON 解析
│   └── ui/                        # 连接页 / 控制页(调焦轮) / 设置页 / 主题
├── tools/mock_focuser.py          # 模拟固件服务器
└── local.properties               # SDK 路径(不入库)
```

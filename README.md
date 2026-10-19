# EasyListening

> 一款轻量、离线、无登录的「单词随身听」Android App。  
> 用 C 内核承载算法与数据逻辑，Kotlin / Jetpack Compose 承载界面与系统能力。

**当前版本：v0.3**

作者：**HangTo102**  
项目名：EasyListening（仓库名 `Easy_Listening`）

---

## 简介

EasyListening 源于备考六级时使用词典 App 的体验：功能齐全，但自定义程度低、权限模型差、后台与定时不可靠。本项目把「单词听力」这一件事做专、做轻：

- 不登录、不联网、不采集数据（默认无网络权限）
- 内置离线词典，查词即用
- 单词本可导入导出、完全本地
- 系统 TTS 发音，复读次数/间隔可调
- 应用内迷你播放条 + 系统通知栏/锁屏控制
- 每日定时播放

代码结构：**C 内核（算法与数据）+ Kotlin/Compose 外壳（界面与系统能力）**，通过 JNI 连接。

---

## 已实现功能

### 查词与单词本
- 本地离线词典查词：单词、音标、词性、中文释义
- 内置过滤版词库（ECDICT 子集，约 11,447 词，覆盖中考/高考/四六级/托福/雅思/考研标签）
- 查词结果一键「加入单词本」，可选择/新建单词本
- 「已收录」标记（只标记是否已在任一单词本）
- 从剪贴板「粘贴」取词
- 单词本：创建、重命名、封面、排序、删除
- 单词：手动新增、逐词删除、编辑模式批量删除 / 批量转移
- 导入 `.txt`（每行一个单词）、导出 `.txt`

### 听力与发音
- 系统 TTS 发音（单词美音；释义中文，需设备支持中文语音包）
- 复读设置：单词/释义各自的重复次数（1–10）、间隔（0–2 秒，0.1 秒一档）、是否朗读释义
- 自动逐词播放；播放页支持 **重播 / 上一个 / 暂停·继续 / 下一个 / 结束**
- 洗牌袋随机调度：一轮内不重复，跨轮避免相邻重复，长期分布均匀
- 断点续播（进度与随机状态持久化）
- 离开听力页后播放继续，并显示**应用内迷你播放条**
- 系统通知栏 / 锁屏媒体控制：上一 / 暂停·继续 / 下一（MediaStyle + MediaSession）
- 每日定时播放（AlarmManager 精确闹钟 + 开机恢复），听力页顶部可配置时间 / 词本 / 每日词数

### 体验
- Android 6.0+（minSdk 23）
- 首页上半查词、下半书架；底部导航突出「听力」
- 设置页「权限」「关于软件」分组，小屏可滚动
- 主题跟随系统（Material 3）

---

## 技术栈

| 层 | 技术 |
| --- | --- |
| 内核 | C11（手写动态数组 / 哈希表 / 堆 / 位图 / 字符串缓冲） |
| 词典 | 自定义只读二进制 `.esd` + 二分查找（ECDICT 构建） |
| Android | Kotlin + Jetpack Compose (Material 3) |
| 发音 | 系统 TTS（`android.speech.tts`），无内置音频 |
| 后台 | 前台服务 + MediaSession + 通知 |
| 定时 | AlarmManager + BOOT_COMPLETED |
| 持久化 | 用户数据纯文本；设置 SharedPreferences |

---

## 目录结构

```
Easy_Listening/
├─ core/                       # C 内核（可独立编译）
│  ├─ include/easylistening/   # 公共头
│  ├─ src/{ds,dict,vocab,schedule,util}/
│  ├─ tools/esd_build.c        # 词典构建工具
│  ├─ tools/es_cli.c           # 桌面查词 CLI
│  └─ Makefile
├─ Android/                    # Android 工程
│  ├─ app/src/main/java/com/easylistening/app/
│  ├─ app/src/main/cpp/        # CMake + JNI
│  └─ app/src/main/assets/dict/# main.esd + builtin.txt
├─ tools/scripts/ecdict_to_csv.py
├─ docs/DESIGN.md              # 设计文档
├─ LICENSE
└─ README.md
```

---

## 构建

### Android

环境：
- Android Studio（含 JDK 21）
- Android SDK 37
- **NDK 30.0.16248370**
- **CMake 4.1.2**

用 Android Studio 打开 `Android/`，同步后运行；或命令行：

```powershell
cd Android
.\gradlew.bat :app:assembleDebug
```

> 如果你的 NDK 版本不同，请修改 `Android/app/build.gradle.kts` 中的 `ndkVersion`。

### 桌面内核工具

需要 gcc + make（Windows 下可用 MinGW）：

```powershell
cd core
mingw32-make tools
```

生成 `esd_build`（构建词典）与 `es_cli`（命令行查词）。

> 仓库不包含测试代码；`make test` 需要自行补充 `core/tests/`。

---

## 词典说明

内置词典来自开源项目 **ECDICT**：
<https://github.com/skywind3000/ECDICT>

仓库**不包含** `ecdict.csv` 原始数据，只包含用它构建出的精简版 `main.esd`。自行生成：

1. 下载 ECDICT 的 `ecdict.csv`。
2. 转成标准 CSV：
   ```powershell
   python tools/scripts/ecdict_to_csv.py ecdict.csv assets/dict/source/ecdict.csv --tags zk,gk,cet4,cet6,toefl,ielts,ky
   ```
   （`--all` 可导出完整词典，体积会大很多。）
3. 生成 `.esd`：
   ```powershell
   core\esd_build.exe assets\dict\source\ecdict.csv assets\dict\main.esd
   ```
4. 复制到 App 资源：
   ```powershell
   Copy-Item assets\dict\main.esd Android\app\src\main\assets\dict\main.esd -Force
   ```

请遵守 ECDICT 自身的许可与署名要求。

---

## 已知限制与问题

- **定时自动播放的兼容性**：部分国产 ROM 即使开启自启动/精确闹钟，仍会拦截「后台启动前台服务」，导致到点无法自动播放。本项目已提供「高优先级提醒通知」兜底（点击即开始），但无法保证所有机型完全无人值守。
- **中文释义朗读**：依赖设备安装中文语音包；部分设备会读出 `n.`/`vt.`/`[医]` 等词性标注，清洗尚不彻底。
- **TTS 质量**：音质与可用性取决于设备/引擎，不使用内置音频。
- **完整词典未内置**：当前只内置过滤版（约 1.1 万词）。完整 ECDICT / 按需下载仍是设计规划（见 `docs/DESIGN.md` §16），尚未实现。
- **例句与句子翻译**：未实现；后续计划通过 LLM 提供例句翻译。
- **App 图标**：目前仍为默认图标，待设计。
- **签名**：Release 构建使用 debug 签名，仅供测试；正式发布请配置自己的 keystore。
- **平台**：仅 Android（minSdk 23）。
- **仓库范围**：不包含测试代码、原始词表数据与部分辅助脚本。

---

## 许可

本项目代码采用 **MIT License**，详见 [LICENSE](LICENSE)。

```
Copyright (c) 2026 HangTo102
```

词典数据来源 **ECDICT**，请遵循其原始许可；本仓库不附带 ECDICT 原始数据。

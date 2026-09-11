# TLogger

这是 TLogger 日志组件的代码模块。**第一片已经跑通**：来源维度、两级级别开关、惰性求值、可自定义标签、logcat 输出通道——19 个测试全绿。

> 这个模块不是"又一个日志库"。它的定位是：**给一整套架构提供统一的日志体系**——统一输出、统一过滤、统一打码、以及从"开发时看日志"到"线上回捞查案"的同一条流水线。
> 完整背景与决策依据在仓库根目录的 `docs/` 下，动手前请先读 `docs/可行性分析报告-通俗版.md` 和 `docs/架构设计约束-日志组件.md`。

## 当前状态

| 项 | 状态 |
|---|---|
| 构建骨架（跨平台结构 + 安卓目标） | ✅ 已就位 |
| `LogLevel` 日志级别 | ✅ 已实现（含测试） |
| 来源维度 + 两级级别开关 | ✅ 已实现（第一片） |
| 惰性求值 API（先判级别再拼字符串） | ✅ 已实现 |
| 标签配方（可自定义） | ✅ 已实现（四种现成配方 + 长度上限与截断计数） |
| 输出通道（安卓系统日志） | ✅ 已实现（**长日志拆分仍是待办**） |
| 打码 / 编译期检查 | ⏳ 未开始 |
| 链路串联 / 会话上下文 / 环形缓冲 | ⏳ 未开始 |
| 落盘 | ⏳ **第二版**，第一版刻意不做 |

## 怎么用（当前这一片）

```
// 装一次。谁装谁管；不装也不会崩——日志调用会变成空操作。
TLogger.install(
    LoggingConfig.builder()
        .sink(AndroidLogSink())
        .defaultLevel(LogLevel.DEBUG)
        .sourceLevel("Net", LogLevel.VERBOSE)   // 来源自己声明默认级别
        .build(),
)

val log = TLogger.logger("Net")
log.d { "连接成功" }                    // 惰性：这条被过滤掉就不会拼字符串
log.e(throwable) { "请求失败" }          // 带异常
log.i(explicitTag = "OkHttp") { "x" }   // 显式标签，按配方拼成 Net/OkHttp
```

想完全不用门面（测试里尤其有用）：

```
val logging = Logging(config)     // 直接建，多个实例互不干扰
val log = logging.logger("Net")
```

## 为什么用跨平台结构，却只开了安卓目标

因为**本机没装 Xcode**（只有 Command Line Tools），苹果产物现在根本编不出来。

所以现在的做法是：

- 代码写在 `src/commonMain/` 里，**刻意不碰任何只有 JVM 才有的东西**（这条靠纪律守，但结构上给了约束）；
- 安卓专有的部分写在 `src/androidMain/`；
- `build.gradle.kts` 里预留了 iOS 目标的位置，装上 Xcode 之后**补三行**就能开：

```
iosX64()
iosArm64()
iosSimulatorArm64()
```

**这就是为什么现在不用普通安卓库模块**：跨平台的结构先立住，以后加苹果端只是加目标，不用把整个源码目录搬家。

## 目录约定

```
TLogger/
├── build.gradle.kts            # 跨平台 + 安卓目标的构建配置
├── README.md                   # 本文件
└── src/
    ├── commonMain/kotlin/      # 三个平台共用的代码（第一版的主战场）
    ├── commonTest/kotlin/      # 共用代码的测试
    └── androidMain/kotlin/     # 安卓专有实现（输出通道等），目前为空
```

分支目录（`log-context`、`log-redact`、`log-lint`、`log-bridge-*`）**等第一版真正用到时再拆**，现在不预建空模块——参考 `docs/架构设计约束-日志组件.md` 第 1 章的模块划分表。

## 技术栈与版本（与仓库其他部分保持一致）

| 项 | 取值 | 说明 |
|---|---|---|
| 构建插件 | `com.android.kotlin.multiplatform.library` + `org.jetbrains.kotlin.multiplatform` | AGP 9 起的跨平台库写法；旧写法是 `com.android.library` + KMP |
| AGP / Kotlin / Gradle | 9.3.0 / 2.2.10 / 9.5 | 与 `gradle/libs.versions.toml` 同一份定义 |
| 编译 / 最低安卓版本 | 37 / 24 | 与 app、TRouter 各模块一致 |
| JVM 目标 | 11 | 与 TRouter 各模块一致 |
| 显式接口声明 | 已开启（`explicitApi()`） | 对应约束 17：对外接口兼容性门槛 |
| 核心接口层的依赖 | 只有 Kotlin 标准库 | 对应依赖铁律 1：core 里不许出现任何 IO |

## 与 TRouter 对齐的约定

- 模块目录用小写连字符命名（TRouter 是 `trouter-core`、`trouter-lint`；本模块按发起人的要求先叫 `TLogger`，将来拆分成多个包时再统一成 `tlogger-*`）。
- 命名空间使用 `com.tlogger.*`，与 `com.trouter.*` 同构。
- 编译期检查模块将来同样走 `com.android.lint` + `kotlin.jvm`、JVM 17、`compileOnly(lint-api)` 的写法（TRouter 的 `trouter-lint` 已经是这个模式）。
- 不依赖任何界面框架与生命周期框架。

## 第一版打算做什么（详情见 `docs/` 里的路线图）

1. 统一接口 + **用户自定义的来源维度** + 两级开关（来源自己声明默认级别，上层能统一覆盖）
2. 输出通道：统一格式、长日志自动拆分、惰性求值、未初始化也不崩
3. 打码 + 写代码时的隐私检查（零迁移采用，可单独发布）
4. 链路串联 + 会话上下文（跨协程/线程自动传递）
5. 最近 N 条的环形缓冲（**注意：撑不过进程被杀**，那是第二版落盘才有的事）

**第一版明确不做**：落盘、加密、图形界面、监控平台对接、Flutter、网页版、XCFramework/SPM/CocoaPods、编译器插件。

## 怎么构建

```
./gradlew :TLogger:build              # 编译 + 跑单元测试
./gradlew :TLogger:assembleDebug      # 只出安卓产物
```

> 注意：本机若尚未安装 Xcode，苹果相关的任务不存在是正常的（iOS 目标未声明）。

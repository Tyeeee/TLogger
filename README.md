# TLogger

**一套给多模块（组件化）安卓架构用的日志体系。** 用 Kotlin Multiplatform 编写，目标平台是 Android / iOS / JVM。

> A Kotlin Multiplatform logging toolkit for modular Android architectures.

**当前状态：早期开发中，尚未发布到 Maven Central。** 现在能用的是"输出端"的第一片（见下方"现在能做什么"）。

---

## 它想解决什么

现有日志库基本分两类，各做一头：

- **门面型**（Timber、Kermit 之类）：只负责打印，不落盘，出了线上问题查不到；
- **落盘型**（XLog、Logan 之类）：只负责存储，日常开发用不上，有的还要求你自建服务端才能解密。

TLogger 想做的是**从"开发时在日志窗口里看"到"线上回捞查案"的同一条流水线**，并在此基础上补两件现有方案普遍没做的事：

1. **按来源看日志**——每个模块声明自己的身份和默认级别，上层能统一覆盖，不用改代码、不用重启就能只看某个模块；
2. **敏感信息自动打码**——规划中。

判断依据是别人踩过的坑，不是我们的偏好：现有库要么全局只有一个开关（在组件化架构里必然打架），要么停更了、要么把用户锁在自家服务端上。

## 现在能做什么

| 能力 | 状态 |
|---|---|
| 来源维度（每个模块一个身份） | ✅ |
| 两级级别开关（模块自己声明 + 上层统一覆盖） | ✅ |
| 惰性求值 API（被过滤掉的日志不拼字符串） | ✅ |
| 可自定义标签配方 + 长度上限与截断计数 | ✅ |
| 输出到 logcat | ✅ |
| 长日志自动分段（超 4KB 不会丢内容） | ✅ |
| 多进程自动区分来源（副进程自动加 `@进程名`） | ✅ |
| 多实例互不干扰、未初始化不崩 | ✅ |
| **敏感信息打码**（手机号 / 身份证 / 银行卡 / 邮箱 / IP / 登录凭证） | ✅ |
| 打码可换成代号（同一个值每次同一个代号，能对上号） | ✅ |
| **写代码时的隐私检查**（把用户输入写进日志会当场报警） | ✅ |
| **链路串联**（一次业务操作的日志能串起来，中间代码不用改） | ✅ |
| 会话号 / 版本号自动带在日志上 | ✅ |
| **最近 N 条留在内存**（出事时把现场捞出来） | ✅ |
| 链路串联 / 会话上下文 | ⏳ 规划中 |
| 落盘（内存映射）| ⏳ 规划中 |

**没有的别猜**：现在还不能落盘，所以它**不能替代线上回捞方案**。这一点会在每个版本里如实说明。

## 已知限制（实测发现，不是猜的）

下面这些是在安卓模拟器上跑 **20 个真实操作场景**测出来的结果，如实写在这里，用之前请先看：

| 限制 | 实测情况 | 影响 |
|---|---|---|
| 状况 | 实测情况 | 影响 |
|---|---|---|
| ~~超过约 4000 字的日志会被系统砍掉尾巴~~ | **已修**：现在按字节自动分段并编号。实测 7998 字的日志被切成 `[1/3] [2/3] [3/3]` 三段，结尾标记完整出现（修复前它是**完全消失**的） | — |
| ~~多个进程用同一个来源名时无法区分~~ | **已修**：来源名自动带进程后缀。实测主进程是 `Net`、副进程是 `Net@remote`，一眼分得开，也能按标签单独过滤 | 用 `AndroidLogging.install(context)` 就会自动生效 |
| ~~标签里有空格会让日志列错位~~ | **已修**：空白和控制字符自动换成下划线（`Net/带空格 的标签` → `Net/带空格_的标签`） | — |
| **每个进程都要装一次日志系统** | 副进程没装时，日志一条都不出来，**不报错也不崩** | 很容易误判成"副进程就是没日志"。请在每个进程的入口都调用 `AndroidLogging.install(context)` |
| **环形缓冲撑不过进程被杀** | 它只在内存里；进程被系统杀掉时，里面的日志一起消失 | 它解决的是"刚才发生了什么"，不是"线上崩溃现场"。后者要等第二版的落盘 |
| **时间只记了墙上时钟** | 记录里带的时间戳是墙上时钟，用户改系统时间会让它跳变 | "精确流逝时间 + 时钟跳变标记"是独立的一块工作，还没做 |
| **系统日志通道本身会丢数据** | 单进程打 5000 条，日志窗口只收到 2249 条（把缓冲区调大后收到 4954 条）；**库侧记账是 5000，一条没丢** | 别把日志窗口当唯一记录，量一大就会丢 |
| **异常堆栈里的文字没有打码** | 打码只管日志正文。异常消息里如果带了手机号，那部分会原样出现在堆栈里 | 待修：要么在出口那层补，要么在记录异常时就处理 |
| **版本号会被当成 IP 涂掉** | `版本 1.2.3.4 已发布` → `版本 ******* 已发布` | 已知误伤。要准确区分得看上下文，这一片先如实记录 |
| **编码过的敏感信息认不出来** | 规则只认得出长得像的。把手机号做 Base64、URL 编码、或自己拆两半再拼，都挡不住 | 打码是**降低风险**的手段，**不等于合规** |
| **默认的换代号实现不够安全** | 默认只是简单散列，够做"能对上号"，不是密码学安全的 | 要真安全，自己传一个实现（用平台加密库算 HMAC） |

**测出来的好结果**（同样如实记录，都是模拟器上的实测）：惰性求值确实生效——被级别挡掉的日志**拼字符串 0 次**（2000 条只花 2ms），放开后是 2000 次；四个线程同时打 2000 条，**库侧记账 2000/2000、零异常**；两个进程同时打日志**互不干扰**；8000 字的日志**分段后一个字不丢**。

**20 个场景是逐个跑过并核对日志的**（不是只看"测试通过"）。最近一轮完整实跑里，
场景测试抓出过两个单元测试碰不到的问题：**环形缓冲存的是打码之前的原文**（会让隐私绕过打码漏出去，
已修：缓冲要套在打码里面）、**计数器没加锁会算错账**（4 线程发出 2000 报 1999，已改成原子计数器）。

## 怎么自己跑一遍

示例应用里做了 **20 个"测试节点"**，每个按钮进去就自动跑一遍，屏幕和日志里都会留结果。
也可以直接用命令跑某一个（这些是全部场景名）：

```
network        一次网络请求          orderFlow      跨四个模块打日志
levels         两级级别开关          tags           四种标签配方与超长截断
lazy           惰性求值              longMessage    8000 字超长日志
throwable      异常与调用栈          noInstall      没装日志系统
stress         一口气 5000 条        weird          换行/表情/中文/空消息
processBoth    多进程同时打          processNoInstall  副进程忘了装
processSameSource 同来源名分进程     threads        多线程并发
redactBasic    打码效果              redactToken    同一个值换同一个代号
traceFlow      一次操作串成一条       traceCoroutine 链路跨协程跨线程
ringCrash      出事前发生了什么       endToEnd       四个能力一起上
```

跑法：

```
./gradlew :app:installDebug
adb shell am start -n com.tlogger.app/.MainActivity                     # 手动点按钮
adb shell am start -n com.tlogger.app/.MainActivity --es scenario stress # 直接跑某个场景
adb logcat -s TLoggerSample                                                # 看场景自己报的结果
```

## 快速开始

目前需要以源码模块方式引入（尚未发布坐标）：

```
// settings.gradle.kts
include(":tlogger-core")
include(":tlogger-android")
include(":tlogger-redact")
include(":tlogger-context")
include(":tlogger-ring")
include(":tlogger-lint")
```

```
// 在每个进程的入口都装一次（主进程、副进程都要）。
// 这一行会自动带上进程后缀：副进程的来源名会变成 Net@remote，多进程日志一眼分得开。
AndroidLogging.install(this)

val log = TLogger.logger("Net")
log.d { "连接成功" }                    // 惰性：这条被过滤掉就不会拼字符串
log.e(throwable) { "请求失败" }
log.i(explicitTag = "OkHttp") { "x" }   // 显式标签，按配方拼成 Net/OkHttp
```

需要更细的配置时，用手工方式（两级级别开关、标签配方、标签长度上限都在这里）：

```
TLogger.install(
    LoggingConfig.builder()
        .sink(AndroidLogSink())
        .defaultLevel(LogLevel.DEBUG)
        .sourceLevel("Net", LogLevel.VERBOSE)   // 模块自己声明默认级别
        .overrideLevel(LogLevel.INFO)           // 上层一句话全部覆盖（可选）
        .tagRecipe(TagRecipes.sourceAndTag)     // 标签配方（可选）
        .build(),
)
```

不需要门面时可以直接建实例（测试里尤其有用，多个实例互不干扰）：

```
val logging = Logging(config)
val log = logging.logger("Net")
```

## 设计原则

这四条会一直守，不会为了省事破例：

1. **不做全局单例**——实例归创建它的人管。两个库抢同一个全局开关是现有方案最常见的坑。
2. **没配置就不采集**——默认不往外写，要么是你显式装了出口。
3. **出口出错不影响业务线程**——日志写不出去是日志的事，不该拖累应用。
4. **对外接口只暴露最小面**——已开启显式接口声明（`explicitApi()`），并且不做破坏性小版本升级。

## 模块

| 模块 | 说明 | 里面有什么 |
|---|---|---|
| `tlogger-core` | 核心：**跟平台无关**的接口与逻辑 | 日志器、级别、标签配方、记录与出口契约、便捷门面 |
| `tlogger-android` | 安卓输出：**只有安卓能用**的部分 | 输出到系统日志（含长日志分段）、进程名读取、一行安装 |
| `tlogger-redact` | **打码**：把敏感信息在写出去之前换掉 | 规则集、换代号、命中统计；套在出口外面用 |
| `tlogger-context` | **链路**：把一次业务操作的日志串起来 | 链路号、会话号、版本、页面；跨协程跨线程自动传递 |
| `tlogger-ring` | **环形缓冲**：在内存里留住最近 N 条 | 出事时把现场捞出来；满了挤掉最旧的 |
| `tlogger-lint` | **写代码时的隐私检查**（不进安装包） | 检查"把用户输入/设备号/位置/账号写进日志" |
| `app` | 示例应用 | 20 个可点的测试场景 |

**命名规则（一条规则管到底，没有例外）**：

| 用在哪 | 怎么起 | 例子 |
|---|---|---|
| 模块目录 / Gradle 模块名 | 连字符 | `tlogger-core`、`tlogger-android` |
| Android 命名空间 | `com.tlogger.<模块目录名>` | `com.tlogger.core`、`com.tlogger.android` |
| Kotlin 包名 | 同上 | `com.tlogger.core`、`com.tlogger.android` |
| 示例应用 | `com.tlogger.app` | 模块目录就叫 `app` |

一句话：**命名空间和包名都等于 `com.tlogger.` 加上模块目录名**，不留任何自创后缀。

**依赖方向是单向的**：`tlogger-android` → `tlogger-core`。反过来核心模块**不认识**安卓。

核心模块里不许出现任何安卓专有的东西（`Context`、`android.util.Log`）——这条不是洁癖，是为了以后加苹果端时这一层能原样复用。出口（往哪写）由各自的模块提供，核心只定义"日志长什么样""往哪送"。

后面的模块按需再建，**有实际代码要发的时候才建**，不预建空模块：

| 打算建的模块 | 用途 |
|---|---|
| `tlogger-bridge-*` | 接到 Timber / SLF4J / Kermit 等已有日志库上的对接件 |
| `tlogger-engine-*` | 落盘（第二版） |

## 出事前发生了什么

用户说"刚才闪了一下"，日志窗口里早翻过去了。环形缓冲在内存里留住最后 N 条：

```
val ring = RingBufferSink(capacity = 500)

TLogger.install(
    LoggingConfig.builder()
        .sink(ring)                 // 留在内存里
        .sink(AndroidLogSink())     // 同时也照常输出到日志窗口
        .build(),
)

// 出事的时候把现场捞出来
Log.i("Crash", ring.dump())
```

**⚠️ 一个必须注意的顺序**：环形缓冲拿到的是**打码之前**的记录。如果你直接把 `ring` 挂上去，
缓冲区里存的就是手机号原文，dump 出来等于**绕过打码把隐私漏出去了**（这个坑是场景测试抓出来的）。
正确写法是**把缓冲套在打码里面**：

```
.sink(RedactingSink(ring, PiiRedactor()))   // 缓冲里存的也是打码后的内容
.sink(ContextSink(RedactingSink(AndroidLogSink(), PiiRedactor()), session = ...))
```

`dump()` 出来是这样：

```
[12:50:29.114] D Order: 步骤 5 完成
[12:50:29.114] D Order: 步骤 6 完成
[12:50:29.114] D Order: 步骤 7 完成
```

**它的边界（别误会）**：只在内存里，**进程被杀就一起没了**。要撑过被杀得等第二版的落盘。
现在它的定位是"调试期够用"。

## 怎么把一次操作的日志串起来

一次下单可能从界面打到库存，散在四个模块里。**入口标记一次就够了，中间的代码一行都不用改**：

```
// 装出口的时候带上会话号（这次启动一个）和版本号
TLogger.install(
    LoggingConfig.builder()
        .sink(ContextSink(AndroidLogSink(), session = SessionInfo.new(appVersion = "1.0")))
        .build(),
)

// 业务入口标记一次
withTrace("a3f9") {
    repository.submitOrder()      // 里面所有模块打的日志都自动带上 t=a3f9
    withPage("订单页") { ... }     // 还能嵌一层页面，链路号不会被顶掉
}
```

日志里长这样：

```
Screen : [t=a3f9 s=y7x9 v=1.0] 用户点了「立即购买」
Pay    : [t=a3f9 s=y7x9 v=1.0] 发起支付 199.00
Screen : [t=a3f9 s=y7x9 v=1.0 页=订单页] 页面渲染完成
```

**在日志窗口里搜 `t=a3f9`，这一次下单的全过程就都出来了。**

协程里换成 `launch(LogContextElement(LogContext(traceId = "a3f9"))) { ... }`——
那样**换了线程也不会丢**（实测：三条协程各自带着自己的链路号在两个线程之间穿梭，互不串味）。

**注意链路号不要放进标签**：每条日志的链路号都不一样，放进标签会让标签列表膨胀成千上万个，
过滤器反而废了。它只出现在正文前缀里，用关键字过滤。

## 怎么开启写代码时的隐私检查

它不是运行期的东西，是在**构建期**跑的，所以不进安装包、也不占体积：

```
// 消费方的 build.gradle.kts
dependencies {
    lintChecks(project(":tlogger-lint"))
}
```

之后把用户输入写进日志就会报警：

```
val raw = edit.text
Log.d("Demo", "用户填了 $raw")   // ← 这里会提示
```

**为什么打码之外还要这个**：打码是运行期换掉日志正文，那时候值已经拼好了。实测数据显示
**六成以上的泄漏是数据转了几手之后才被写进日志的**，运行期只看到一个字符串，认不出它原来是手机号。
只有在写代码的时候检查，才能在拼进去之前拦住。

检查是**警告**级别，需要豁免时加 `@Suppress("TLoggerPiiInLog")`。

## 构建

```
./gradlew build                              # 编译全部模块并跑测试
./gradlew :app:installDebug                            # 装示例应用到设备
```

源码组织（跨平台库的固定规则，不是随便起的名字）：所有平台共用的代码在 `src/commonMain/`，只有安卓能用的代码在 `src/androidMain/`，测试在 `src/commonTest/` 和 `src/androidHostTest/`。

**iOS 目标暂时没有开启**：本机未安装 Xcode，苹果产物编不出来。装上之后在 `tlogger-core/build.gradle.kts` 里补三行即可（文件里有说明）。

## 支持的版本

| 项 | 版本 |
|---|---|
| Gradle | 9.5 |
| AGP | 9.3.x |
| Kotlin | 2.2.10 |
| 最低安卓版本 | 24 |
| 编译目标安卓版本 | 37 |
| JVM 目标 | 11 |
| JDK（构建用） | 17 |

表外的组合没有测过。

**国内网络统一走腾讯云镜像**（依赖仓库和 Gradle 下载地址都用它，不混着来）：

| 用途 | 换成 | 为什么 |
|---|---|---|
| **依赖仓库** | `mirrors.cloud.tencent.com/nexus/repository/maven-public/` | 官方 `repo.maven.apache.org` 国内可能出现 TLS 握手被中断 |
| **Gradle 下载** | `mirrors.cloud.tencent.com/gradle/` | 官方 `services.gradle.org` 会**跳转到 GitHub 下载**，国内经常超时，**Android Studio 会因此同步失败**（报 `Could not install Gradle distribution`） |

为什么统一用腾讯云：**只有它同时有这两样**（阿里云没有 Gradle 发行包，会 404）。
镜像上的文件与官方完全一致（校验值相同，`distributionSha256Sum` 仍然生效），两处都保留了官方地址作为兜底。

想换回官方：依赖仓库删掉那行 `maven(...)` 即可；Gradle 把 `distributionUrl` 里的
`mirrors.cloud.tencent.com/gradle` 改回 `services.gradle.org/distributions`。

## 许可

[Apache License 2.0](LICENSE)

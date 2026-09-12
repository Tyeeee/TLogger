# 消费者验证工程

这是一个**跟 TLogger 源码毫无关系**的独立安卓工程：依赖里没有一行 `project(":...")`，
全都是发布出来的坐标（`io.github.tyeeee:tlogger-*:0.1.0`），只从 Maven 仓库拉。

存在的目的：证明"发出去的包，别人能装上、能跑、能生效"，不是靠单元测试自说自话。

## 怎么跑

```bash
# 1) 先把库发到本机仓库
cd .. && ./gradlew publishToMavenLocal

# 2) 编译这个独立工程（注意 -p，它是另一个 Gradle 构建）
./gradlew -p consumer-check :app:assembleDebug

# 3) 装到手机上看
adb install -r consumer-check/app/build/outputs/apk/debug/app-debug.apk
adb logcat -c && adb shell am start -n com.example.consumer/.MainActivity
adb logcat -d -s CONSUMER:I
```

它进去就自己把五项跑一遍，每项打一行 `CONSUMER_RESULT=` 结果，不用手点。
还可以单独跑代码检查，验证 lint 包能不能从仓库坐标吃进来：

```bash
./gradlew -p consumer-check :app:lintDebug
```

## 它验的是什么

| 结果行 | 意思是 |
|---|---|
| `plain` | 标签按来源分开了 |
| `redact hits=4` | 手机号/邮箱被打码（数字会翻倍，因为同一个打码器被两个出口共用，各算一次） |
| `trace tagged=3 expect=3` | 一次 `withTrace` 标记，正好 3 条日志带上链路号 |
| `drain leakedRawPii=false masked=true traced=true` | 缓存里存的是打过码的、且带链路号 |
| `stress sent=3000 written=3009` | 库自己记账一条不差（3009 = 前面 9 条 + 这 3000 条） |

`MainActivity.lintBait()` 是**故意写的一处违规**（把输入框内容直接打进日志），
用来确认 `lintChecks("io.github.tyeeee:tlogger-lint:0.1.0")` 真的生效——
lint 报告里应该出现 `TLoggerPiiInLog`。

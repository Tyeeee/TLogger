package com.tlogger.app

import android.content.Context
import android.content.Intent
import com.tlogger.android.AndroidLogSink
import com.tlogger.android.AndroidLogging
import com.tlogger.android.AndroidProcessTags
import com.tlogger.core.LogLevel
import com.tlogger.core.Logging
import com.tlogger.core.LoggingConfig
import com.tlogger.core.TLogger
import com.tlogger.core.TagRecipes
import com.tlogger.redact.PiiRedactor
import com.tlogger.redact.PiiRules
import com.tlogger.redact.RedactingSink
import com.tlogger.redact.RedactionStyle
import com.tlogger.redact.Redactor
import com.tlogger.context.ContextSink
import com.tlogger.context.LogContext
import com.tlogger.context.LogContextElement
import com.tlogger.context.SessionInfo
import com.tlogger.context.withPage
import com.tlogger.context.withTrace
import com.tlogger.redact.TokenGenerators
import com.tlogger.ring.RingBufferSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** 页面上的一句话输出（同时会进日志，方便我事后核对）。 */
typealias Emit = (String) -> Unit

private const val NET = "Net"
private const val SCREEN = "Screen"
private const val ORDER = "Order"
private const val PAY = "Pay"
private const val STOCK = "Stock"

/** 一次装好，返回实例，方便看统计。有 context 时走"一行安装"，自动带上进程后缀。 */
private fun install(defaultLevel: LogLevel = LogLevel.DEBUG): Logging {
    val ctx = Scenarios.contextOrNull()
    return if (ctx != null) {
        AndroidLogging.install(ctx, defaultLevel)
    } else {
        TLogger.install(
            LoggingConfig.builder()
                .sink(AndroidLogSink())
                .defaultLevel(defaultLevel)
                .build(),
        )
    }
}

/** 装好出口，并在外面套一层"上下文"——链路号、会话号、版本会拼在正文前面。 */
private fun installWithContext(): Logging {
    val ctx = Scenarios.contextOrNull()
    return TLogger.install(
        LoggingConfig.builder()
            .sink(ContextSink(AndroidLogSink(), session = SessionInfo.new(appVersion = "0.1")))
            .defaultLevel(LogLevel.DEBUG)
            .sourceSuffix(if (ctx != null) AndroidProcessTags.suffixFor(ctx) else "")
            .build(),
    )
}

/** 装好出口，并在出口外面**套一层打码**——这就是打码模块的用法。 */
private fun installWithRedaction(redactor: Redactor): Logging {
    val ctx = Scenarios.contextOrNull()
    return TLogger.install(
        LoggingConfig.builder()
            .sink(RedactingSink(AndroidLogSink(), redactor))
            .defaultLevel(LogLevel.DEBUG)
            .sourceSuffix(if (ctx != null) AndroidProcessTags.suffixFor(ctx) else "")
            .build(),
    )
}

/**
 * 十个"测试节点"。每个都模拟一种真实用法，进去就自动跑一遍。
 *
 * 每个场景最后都会 emit 一行 `RESULT=...`，方便从日志里核对。
 */
object Scenarios {

    /** 页面会把它自己塞进来，多进程场景需要用它去启动副进程。 */
    private var context: Context? = null

    fun attach(ctx: Context) {
        context = ctx
    }

    /** 给上面的 install 用。 */
    fun contextOrNull(): Context? = context

    /** 所有场景的编号和标题，界面按这个顺序生成按钮。 */
    val all: List<Pair<String, String>> = listOf(
        "network" to "1. 一次网络请求（最基本的用法）",
        "orderFlow" to "2. 一次下单跨四个模块（看能不能分清是谁打的）",
        "levels" to "3. 改日志级别（两级开关 + 统一覆盖）",
        "tags" to "4. 标签长什么样（四种配方 + 太长会怎样）",
        "lazy" to "5. 被挡掉的日志有没有白拼字符串",
        "longMessage" to "6. 打一条特别长的日志（会不会被系统截断）",
        "throwable" to "7. 出异常时的日志和调用栈",
        "noInstall" to "8. 没装日志系统 / 中途卸掉之后",
        "stress" to "9. 一口气打 5000 条（耗时 + 有没有丢）",
        "weird" to "10. 换行、中文、表情、空消息、超长标签",
        "processBoth" to "11. 多进程：主副进程同时打日志",
        "processNoInstall" to "12. 多进程：副进程忘了装日志系统",
        "processSameSource" to "13. 多进程：两个进程用同一个来源名",
        "threads" to "14. 多线程同时打日志",
        "redactBasic" to "15. 打码：带手机号身份证的日志长什么样",
        "redactToken" to "16. 打码：同一个手机号换成同一个代号",
        "traceFlow" to "17. 链路：一次下单跨四个模块，能串起来",
        "traceCoroutine" to "18. 链路：很多请求同时跑，各带各的链路号",
        "ringCrash" to "19. 环形缓冲：出事前发生了什么",
        "endToEnd" to "20. 端到端：打码 + 链路 + 缓冲 + 按模块级别一起上",
    )

    fun run(id: String, emit: Emit) {
        when (id) {
            "network" -> network(emit)
            "orderFlow" -> orderFlow(emit)
            "levels" -> levels(emit)
            "tags" -> tags(emit)
            "lazy" -> lazy(emit)
            "longMessage" -> longMessage(emit)
            "throwable" -> throwable(emit)
            "noInstall" -> noInstall(emit)
            "stress" -> stress(emit)
            "weird" -> weird(emit)
            "processBoth" -> processBoth(emit)
            "processNoInstall" -> processNoInstall(emit)
            "processSameSource" -> processSameSource(emit)
            "threads" -> threads(emit)
            "redactBasic" -> redactBasic(emit)
            "redactToken" -> redactToken(emit)
            "traceFlow" -> traceFlow(emit)
            "traceCoroutine" -> traceCoroutine(emit)
            "ringCrash" -> ringCrash(emit)
            "endToEnd" -> endToEnd(emit)
            else -> emit("RESULT=unknown 未知场景: $id")
        }
    }

    /** 往副进程发一条指令。 */
    private fun startRemote(install: Boolean, count: Int, source: String = "Remote") {
        val ctx = context ?: return
        val intent = Intent(ctx, RemoteActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("install", install)
            putExtra("count", count)
            putExtra("source", source)
        }
        ctx.startActivity(intent)
    }

    // ---------------------------------------------------------------- 20

    private fun endToEnd(emit: Emit) {
        val ring = RingBufferSink(capacity = 20)
        val ctx = Scenarios.contextOrNull()
        val logging = TLogger.install(
            LoggingConfig.builder()
                // 【重点】缓冲要套在打码**里面**：否则缓冲区里存的是手机号原文，
                // dump 出来就等于绕过打码把隐私漏出去了（这个坑是场景测试抓出来的）
                .sink(RedactingSink(ring, PiiRedactor()))
                .sink(
                    ContextSink(
                        RedactingSink(AndroidLogSink(), PiiRedactor()),
                        session = SessionInfo.new(appVersion = "0.1"),
                    ),
                )
                .defaultLevel(LogLevel.DEBUG)
                .sourceLevel("Pay", LogLevel.WARN)   // 支付模块自己声明：只记 WARN 以上
                .sourceSuffix(if (ctx != null) AndroidProcessTags.suffixFor(ctx) else "")
                .build(),
        )

        emit("四个东西一起上：打码 + 链路 + 缓冲 + 按模块级别")
        withTrace("e2e1") {
            TLogger.logger(SCREEN).i { "用户 13812345678 点了结算" }
            TLogger.logger(ORDER).i { "创建订单，收货人 13812345678" }
            TLogger.logger("Pay").d { "这条 DEBUG 应该被挡住（支付模块声明的级别）" }
            TLogger.logger("Pay").w { "支付超时，准备重试" }
            TLogger.logger(STOCK).e(RuntimeException("库存服务 500")) { "扣减库存失败" }
        }

        emit("对照上面：手机号应该成了 138****5678，每条前面有 [t=e2e1 …]，支付那条 DEBUG 不该出现")
        emit("现在把缓冲区里的现场捞出来——它是套在打码里面的，所以手机号也是打过的：")
        ring.dump().split("\n").forEach { emit("    $it") }
        emit("RESULT=ok ring=${ring.size} written=${logging.stats.writtenCount}")
    }

    // ---------------------------------------------------------------- 19

    private fun ringCrash(emit: Emit) {
        val ring = RingBufferSink(capacity = 5)
        TLogger.install(
            LoggingConfig.builder()
                .sink(ring)               // 留在内存里
                .sink(AndroidLogSink())   // 同时也照常输出到日志窗口
                .defaultLevel(LogLevel.DEBUG)
                .build(),
        )
        val log = TLogger.logger("Order")

        emit("先打 10 条，但缓冲区只留最近 5 条")
        repeat(10) { i -> log.d { "步骤 $i 完成" } }
        emit("现在做「出事时该做的动作」——把缓冲区里的东西捞出来：")
        ring.dump().split("\n").forEach { emit("    $it") }
        emit("只有最后 5 条，前面的被挤掉了。用户说「刚才闪了一下」时，就是靠这个把现场捞回来")
        emit("RESULT=ok")
    }

    // ---------------------------------------------------------------- 17

    private fun traceFlow(emit: Emit) {
        installWithContext()
        emit("模拟一次下单：四个模块各打几条，中间还嵌了个页面")
        emit("它们在代码里互不相识，谁都不用传参数——靠的是入口标记的那一次")

        withTrace("a3f9") {
            TLogger.logger(SCREEN).i { "用户点了「立即购买」" }
            TLogger.logger(ORDER).i { "创建订单 20260911001" }
            TLogger.logger(STOCK).d { "检查库存：剩 3 件" }
            TLogger.logger(PAY).i { "发起支付 199.00" }
            withPage("订单页") {
                TLogger.logger(SCREEN).d { "页面渲染完成" }
            }
            TLogger.logger(ORDER).i { "订单完成" }
        }
        TLogger.logger(ORDER).i { "这条在链路外面，不该带 t=" }

        emit("上面 6 条应该都带 [t=a3f9]，最后一条不带")
        emit("在日志窗口里搜 t=a3f9，就能把这一次下单的全过程拉出来")
        emit("RESULT=ok")
    }

    // ---------------------------------------------------------------- 18

    private fun traceCoroutine(emit: Emit) {
        installWithContext()
        emit("同时开 3 条协程，各自带自己的链路号，中间还要换线程")
        emit("重点看：出去换了线程回来，链路号有没有丢、有没有串")

        runBlocking {
            val jobs = listOf("a111", "b222", "c333").map { id ->
                launch(LogContextElement(LogContext(traceId = id))) {
                    TLogger.logger(NET).i { "$id 第 1 条（原线程）" }
                    withContext(Dispatchers.Default) {
                        TLogger.logger(NET).i { "$id 第 2 条（换了线程）" }
                    }
                    TLogger.logger(NET).i { "$id 第 3 条（又回来了）" }
                }
            }
            jobs.forEach { it.join() }
        }

        emit("同一条链路的 3 条日志应该都带同一个 t=，不同链路之间不能串")
        emit("RESULT=ok")
    }

    // ---------------------------------------------------------------- 15

    private fun redactBasic(emit: Emit) {
        val pii = PiiRedactor()
        installWithRedaction(pii)
        val log = TLogger.logger("User")

        emit("下面 6 条日志里都带着敏感信息，看日志里剩下什么")
        log.i { "手机号 13812345678 已注册" }
        log.i { "邮箱 zhangsan@example.com 验证通过" }
        log.i { "身份证 110101199003071234 核验成功" }
        log.i { "银行卡 4111111111111111 已绑定" }
        log.i { "登录来自 192.168.100.200" }
        log.i { "凭证 eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abcdefghij 已过期" }

        emit("对照上面 6 条，号码应该都被换掉了")
        emit("打码统计：${pii.stats}")
        emit("RESULT=ok hits=${pii.stats.total}")
    }

    // ---------------------------------------------------------------- 16

    private fun redactToken(emit: Emit) {
        val pii = PiiRedactor(
            rules = listOf(PiiRules.phone(RedactionStyle.TOKEN)),
            tokenGenerator = TokenGenerators.simple,
        )
        installWithRedaction(pii)
        val log = TLogger.logger("User")

        emit("这次把手机号换成代号：同一个号码每次该是同一个代号，这样排障时能对上号")
        log.i { "用户 13812345678 登录" }
        log.i { "用户 13812345678 下单" }
        log.i { "用户 13900000000 登录" }

        emit("前两条应该是同一个代号，第三条是另一个代号")
        emit("RESULT=ok hits=${pii.stats.total}")
    }

    private fun processSameSource(emit: Emit) {
        val logging = install()
        val log = logging.logger("Net")
        emit("这次两个进程用**同一个来源名 Net**——真实项目里最常见的情况")
        emit("预期：两边的标签一模一样，只能靠「进程号」那一列区分")
        startRemote(install = true, count = 300, source = "Net")
        repeat(300) { i ->
            log.i { "主进程(Net) 第 $i 条" }
        }
        emit("主进程也用 Net 打完了 300 条")
        emit("RESULT=ok source=Net")
    }

    // ---------------------------------------------------------------- 11

    private fun processBoth(emit: Emit) {
        val logging = install()
        val log = logging.logger("Main")
        emit("当前是主进程 pid=${android.os.Process.myPid()}")
        emit("先让副进程开始打 1000 条，主进程同时打 1000 条")
        startRemote(install = true, count = 1000)
        repeat(1000) { i ->
            log.i { "主进程第 $i 条" }
        }
        emit("主进程打完，库记的账 = ${logging.stats.writtenCount}")
        emit("看日志时注意「进程号」这一列：应该是两个不同的数字")
        emit("RESULT=ok mainPid=${android.os.Process.myPid()} mainWritten=${logging.stats.writtenCount}")
    }

    // ---------------------------------------------------------------- 12

    private fun processNoInstall(emit: Emit) {
        emit("这次故意让副进程**不装**日志系统，看会发生什么")
        emit("预期：副进程打的日志一条都不出来，但程序不会崩")
        startRemote(install = false, count = 200)
        emit("指令已发出，副进程的结果会单独报出来")
        emit("RESULT=ok")
    }

    // ---------------------------------------------------------------- 13

    private fun threads(emit: Emit) {
        val logging = install()
        val log = logging.logger("Threads")
        val threadCount = 4
        val perThread = 500
        emit("开 $threadCount 条线程，每条打 $perThread 条，看并发下会不会乱、会不会崩")
        val errors = java.util.Collections.synchronizedList(mutableListOf<String>())
        val threads = (1..threadCount).map { t ->
            Thread {
                try {
                    repeat(perThread) { i ->
                        log.i { "线程 $t 第 $i 条" }
                    }
                } catch (e: Throwable) {
                    errors.add("线程 $t 出错：$e")
                }
            }
        }
        val start = System.currentTimeMillis()
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        val cost = System.currentTimeMillis() - start
        val expected = threadCount * perThread
        emit("全部打完，耗时 ${cost}ms，线程里抛出的异常 ${errors.size} 个")
        emit("实际发出 $expected 条，库记的账 = ${logging.stats.writtenCount}")
        emit("（记账的计数器没有加锁，少算是已知问题——这里正好量一下少算多少）")
        errors.forEach { emit(it) }
        emit("RESULT=ok threads=$threadCount expected=$expected elapsedMs=$cost written=${logging.stats.writtenCount} errors=${errors.size}")
    }

    // ---------------------------------------------------------------- 1

    private fun network(emit: Emit) {
        install()
        val log = TLogger.logger(NET)
        emit("模拟一次下单请求，日志来源叫「$NET」")
        log.i { "开始请求 /api/order" }
        log.d { "连接已建立，耗时 12ms" }
        log.d { "响应 200，总耗时 118ms" }
        log.e(RuntimeException("连接被重置")) { "请求失败，准备重试" }
        emit("应该看到 4 条日志，标签都是「$NET」，最后一条带异常")
        emit("RESULT=ok")
    }

    // ---------------------------------------------------------------- 2

    private fun orderFlow(emit: Emit) {
        install()
        emit("模拟一次下单，跨四个模块各打几条日志")
        emit("重点看：能不能一眼分清每条是谁打的")

        val screen = TLogger.logger(SCREEN)
        val order = TLogger.logger(ORDER)
        val pay = TLogger.logger(PAY)
        val stock = TLogger.logger(STOCK)

        screen.i { "用户点了「立即购买」" }
        order.i { "创建订单 20260911001" }
        stock.d { "检查库存：剩余 3 件" }
        order.d { "订单落库成功" }
        pay.i { "发起支付，金额 199.00" }
        pay.d { "支付回调：成功" }
        stock.i { "扣减库存：3 → 2" }
        order.i { "订单完成" }

        emit("一共 8 条，标签前缀分别是 Screen/ Order/ Pay/ Stock/")
        emit("RESULT=ok")
    }

    // ---------------------------------------------------------------- 3

    private fun levels(emit: Emit) {
        emit("第一段：默认级别 DEBUG——下面两条都应该出来")
        install(LogLevel.DEBUG).logger(NET).let {
            it.d { "这条是 DEBUG，应该出来" }
            it.e { "这条是 ERROR，应该出来" }
        }

        emit("第二段：全局级别改成 ERROR——DEBUG 应该被挡住")
        install(LogLevel.ERROR).logger(NET).let {
            it.d { "这条是 DEBUG，不该出来" }
            it.e { "这条是 ERROR，应该出来" }
        }

        emit("第三段：全局 WARN，但让 Net 自己报到 VERBOSE")
        val logging = TLogger.install(
            LoggingConfig.builder()
                .sink(AndroidLogSink())
                .defaultLevel(LogLevel.WARN)
                .sourceLevel(NET, LogLevel.VERBOSE)
                .build(),
        )
        logging.logger(NET).v { "Net 自己的 VERBOSE，应该出来" }
        logging.logger(ORDER).v { "Order 没声明，应该被挡住" }

        emit("第四段：上层统一覆盖成 ERROR——Net 的 VERBOSE 也该被挡住")
        val overridden = TLogger.install(
            LoggingConfig.builder()
                .sink(AndroidLogSink())
                .defaultLevel(LogLevel.WARN)
                .sourceLevel(NET, LogLevel.VERBOSE)
                .overrideLevel(LogLevel.ERROR)
                .build(),
        )
        overridden.logger(NET).v { "被统一覆盖挡住，不该出来" }
        overridden.logger(NET).e { "ERROR 仍然出来" }

        emit("RESULT=ok")
    }

    // ---------------------------------------------------------------- 4

    private fun tags(emit: Emit) {
        val sink = RecordingForCounters()
        val recipes = listOf(
            "只用来源名" to TagRecipes.sourceOnly,
            "来源/显式标签（默认）" to TagRecipes.sourceAndTag,
            "只用显式标签" to TagRecipes.explicitTagOnly,
            "来源 + 环境名" to TagRecipes.custom(suffix = "dev"),
        )
        for ((title, recipe) in recipes) {
            val logging = TLogger.install(
                LoggingConfig.builder().sink(AndroidLogSink()).sink(sink).tagRecipe(recipe).build(),
            )
            logging.logger(NET).d(explicitTag = "OkHttp") { "配方：$title" }
            emit("配方「$title」→ 标签应该是……（看下一条日志的标签列）")
        }

        emit("再看标签太长会怎样：给一个 50 字的标签，上限设成 32")
        val logging = TLogger.install(
            LoggingConfig.builder()
                .sink(AndroidLogSink())
                .sink(sink)
                .maxTagLength(32)
                .build(),
        )
        val longTag = "VeryLongCallerClassNameThatIsWayTooLongForALogTag"
        logging.logger(NET).d(explicitTag = longTag) { "这条的标签应该被截到 32 个字" }
        emit("截断次数统计 = ${logging.stats.tagTruncationCount}（应该是 1）")
        emit("RESULT=ok")
    }

    // ---------------------------------------------------------------- 5

    private fun lazy(emit: Emit) {
        val logging = install(LogLevel.ERROR)
        val log = logging.logger(NET)

        emit("级别是 ERROR，下面连打 2000 条 DEBUG——如果懒惰生效，拼字符串的函数一次都不该被调用")
        var firstCalls = 0
        val t1 = System.currentTimeMillis()
        repeat(2000) { i ->
            log.d {
                firstCalls++
                "第 $i 条，这段文字本来是要拼出来的"
            }
        }
        val cost1 = System.currentTimeMillis() - t1
        emit("被挡掉时：拼字符串调用了 $firstCalls 次，2000 条共耗时 ${cost1}ms")

        emit("把级别放到 DEBUG，同样 2000 条 DEBUG——这次应该全部拼出来")
        val loud = install(LogLevel.DEBUG)
        val loudLog = loud.logger(NET)
        var secondCalls = 0
        val t2 = System.currentTimeMillis()
        repeat(2000) { i ->
            loudLog.d {
                secondCalls++
                "第 $i 条，这段文字本来是要拼出来的"
            }
        }
        val cost2 = System.currentTimeMillis() - t2
        emit("没被挡时：拼字符串调用了 $secondCalls 次，2000 条共耗时 ${cost2}ms")

        emit("RESULT=ok blockedCalls=$firstCalls blockedMs=$cost1 passedCalls=$secondCalls passedMs=$cost2")
    }

    // ---------------------------------------------------------------- 6

    private fun longMessage(emit: Emit) {
        val counter = CountingSink()
        TLogger.install(
            LoggingConfig.builder().sink(AndroidLogSink()).sink(counter).build(),
        )
        val log = TLogger.logger(NET)
        val body = StringBuilder()
        while (body.length < 8000) {
            body.append("0123456789")
        }
        val message = "LONG_START" + body.substring(0, 8000 - 20) + "LONG_END"
        emit("准备打一条 ${message.length} 个字符的日志")
        emit("系统对单条日志有长度上限，下面这条会被系统截断——看实际留下多少")
        log.i { message }
        emit("库交给出口的长度 = ${counter.maxChars} 字符，交给出口的条数 = ${counter.count}")
        emit("RESULT=ok sentChars=${message.length} handedOver=${counter.maxChars}")
    }

    // ---------------------------------------------------------------- 7

    private fun throwable(emit: Emit) {
        install()
        val log = TLogger.logger(ORDER)
        emit("模拟第三层调用里出错，把异常一起打出来")
        try {
            throw IllegalStateException("库存服务返回了非法数据")
        } catch (t: Throwable) {
            log.e(t) { "下单失败，用户会看到提示" }
        }
        emit("应该看到「下单失败，用户会看到提示」加上完整调用栈")
        emit("RESULT=ok")
    }

    // ---------------------------------------------------------------- 8

    private fun noInstall(emit: Emit) {
        emit("先把日志系统卸掉，然后照常打日志——程序不该崩")
        TLogger.uninstall()
        val log = TLogger.logger(NET)
        log.v { "没人管我" }
        log.d { "没人管我" }
        log.i { "没人管我" }
        log.w { "没人管我" }
        log.e(RuntimeException("随便一个异常")) { "没人管我" }
        emit("打完了，没有崩。第 5 条还带了异常，同样没有崩。")
        emit("再把日志系统装回来，验证之前拿到的日志器还能用")
        install()
        log.i { "装回来之后，这条应该正常出来" }
        emit("RESULT=survived")
    }

    // ---------------------------------------------------------------- 9

    private fun stress(emit: Emit) {
        val logging = install()
        val log = logging.logger(NET)
        val count = 5000
        emit("一口气打 $count 条，测总耗时")
        val start = System.currentTimeMillis()
        repeat(count) { i ->
            log.i { "压力测试第 $i 条" }
        }
        val cost = System.currentTimeMillis() - start
        emit("耗时 ${cost}ms（平均每条 ${if (count > 0) cost.toDouble() / count else 0.0}ms）")
        emit("库自己记的账：写出 ${logging.stats.writtenCount} 条（发出 $count 条）")
        emit("注意：系统自己的日志缓冲区可能会丢掉一部分，这不一定是我们库的问题")
        emit("RESULT=ok count=$count elapsedMs=$cost written=${logging.stats.writtenCount}")
    }

    // ---------------------------------------------------------------- 10

    private fun weird(emit: Emit) {
        val logging = install()
        val log = logging.logger(NET)

        emit("换行、制表符、中文、表情、空消息，各来一条")
        log.i { "第一行\n第二行\n第三行" }
        log.i { "带制表符\t分隔\t内容" }
        log.i { "中文消息：订单号 20260911001，金额 ￥199.00" }
        log.i { "表情：下单成功 🎉✅🚀" }
        log.i { "" }
        log.i(explicitTag = "中文标签") { "标签里放中文会怎样" }
        log.i(explicitTag = "带空格 的标签") { "标签里有空格会怎样" }
        emit("RESULT=ok")
    }
}

/** 只用来数数，不输出到 logcat。 */
private class RecordingForCounters : com.tlogger.core.LogSink {
    override fun write(record: com.tlogger.core.LogRecord): Unit = Unit
}

/**
 * 记账用的出口：记录**库里实际交出去**多少条、最长的一条有多少字。
 *
 * 用它来分清责任：如果库里交出去的是 7998 字，而日志里只剩下 4000 字，
 * 那截断就是系统干的，不是库干的。
 */
private class CountingSink : com.tlogger.core.LogSink {
    var count: Int = 0
        private set
    var maxChars: Int = 0
        private set

    override fun write(record: com.tlogger.core.LogRecord) {
        count++
        if (record.message.length > maxChars) maxChars = record.message.length
    }
}

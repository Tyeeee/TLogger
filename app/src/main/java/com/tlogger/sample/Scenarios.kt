package com.tlogger.sample

import android.content.Context
import android.content.Intent
import com.tlogger.AndroidLogSink
import com.tlogger.LogLevel
import com.tlogger.Logging
import com.tlogger.LoggingConfig
import com.tlogger.TLogger
import com.tlogger.TagRecipes

/** 页面上的一句话输出（同时会进日志，方便我事后核对）。 */
typealias Emit = (String) -> Unit

private const val NET = "Net"
private const val SCREEN = "Screen"
private const val ORDER = "Order"
private const val PAY = "Pay"
private const val STOCK = "Stock"

/** 一次装好，返回实例，方便看统计。 */
private fun install(defaultLevel: LogLevel = LogLevel.DEBUG): Logging =
    TLogger.install(
        LoggingConfig.builder()
            .sink(AndroidLogSink())
            .defaultLevel(defaultLevel)
            .build(),
    )

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

    // ---------------------------------------------------------------- 14

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
private class RecordingForCounters : com.tlogger.LogSink {
    override fun write(record: com.tlogger.LogRecord): Unit = Unit
}

/**
 * 记账用的出口：记录**库里实际交出去**多少条、最长的一条有多少字。
 *
 * 用它来分清责任：如果库里交出去的是 7998 字，而日志里只剩下 4000 字，
 * 那截断就是系统干的，不是库干的。
 */
private class CountingSink : com.tlogger.LogSink {
    var count: Int = 0
        private set
    var maxChars: Int = 0
        private set

    override fun write(record: com.tlogger.LogRecord) {
        count++
        if (record.message.length > maxChars) maxChars = record.message.length
    }
}

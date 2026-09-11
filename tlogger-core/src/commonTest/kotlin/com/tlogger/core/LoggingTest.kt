package com.tlogger.core
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 测试用的假出口：把收到的记录存起来，不碰任何平台接口。 */
private class RecordingSink : LogSink {
    val records = mutableListOf<LogRecord>()
    override fun write(record: LogRecord) {
        records.add(record)
    }
}

/** 一个会炸的出口，用来验证"出口出错不能影响业务、也不能影响后面的出口"。 */
private class ExplodingSink : LogSink {
    override fun write(record: LogRecord) {
        throw IllegalStateException("出口炸了")
    }
}

class LoggingTest {

    @AfterTest
    fun cleanup() {
        TLogger.uninstall()
    }

    // ---------- 两级开关 ----------

    @Test
    fun globalDefaultLevelIsUsedWhenNothingElseIsSet() {
        val sink = RecordingSink()
        val logging = Logging(
            LoggingConfig.builder().sink(sink).defaultLevel(LogLevel.INFO).build()
        )
        val log = logging.logger("Net")

        log.d { "调试" }
        log.i { "信息" }

        assertEquals(1, sink.records.size)
        assertEquals(LogLevel.INFO, sink.records.single().level)
    }

    @Test
    fun sourceLevelOverridesGlobalDefault() {
        val sink = RecordingSink()
        val logging = Logging(
            LoggingConfig.builder()
                .sink(sink)
                .defaultLevel(LogLevel.WARN)
                .sourceLevel("Net", LogLevel.VERBOSE)
                .build()
        )

        logging.logger("Net").v { "网络想看细一点" }
        logging.logger("Track").v { "埋点应该被挡住" }

        assertEquals(1, sink.records.size)
        assertEquals("Net", sink.records.single().source)
    }

    @Test
    fun architectureLevelOverrideBeatsSourceLevel() {
        val sink = RecordingSink()
        val logging = Logging(
            LoggingConfig.builder()
                .sink(sink)
                .defaultLevel(LogLevel.DEBUG)
                .sourceLevel("Net", LogLevel.VERBOSE)
                .overrideLevel(LogLevel.ERROR) // 架构层一句话全部调高
                .build()
        )

        logging.logger("Net").v { "被覆盖挡住" }
        logging.logger("Net").e { "错误能过" }

        assertEquals(1, sink.records.size)
        assertEquals(LogLevel.ERROR, sink.records.single().level)
    }

    // ---------- 惰性求值 ----------

    @Test
    fun messageIsNotEvaluatedWhenLevelIsFiltered() {
        val logging = Logging(
            LoggingConfig.builder().sink(RecordingSink()).defaultLevel(LogLevel.ERROR).build()
        )
        var evaluations = 0

        logging.logger("Net").d {
            evaluations++
            "这条不该被拼出来"
        }

        assertEquals(0, evaluations, "被过滤掉的日志不该拼字符串——这是惰性求值的全部意义")
    }

    @Test
    fun messageIsEvaluatedExactlyOnceWhenEnabled() {
        val first = RecordingSink()
        val second = RecordingSink()
        val logging = Logging(
            LoggingConfig.builder().sink(first).sink(second).defaultLevel(LogLevel.DEBUG).build()
        )
        var evaluations = 0

        logging.logger("Net").d {
            evaluations++
            "只该拼一次"
        }

        assertEquals(1, evaluations, "装了多个出口时，消息也只该拼一次")
        assertEquals("只该拼一次", first.records.single().message)
        assertEquals(1, second.records.size, "两个出口都该收到")
    }

    @Test
    fun isEnabledLetsCallerSkipExpensiveWork() {
        val logging = Logging(
            LoggingConfig.builder().sink(RecordingSink()).defaultLevel(LogLevel.INFO).build()
        )
        val log = logging.logger("Net")

        assertFalse(log.isEnabled(LogLevel.DEBUG))
        assertTrue(log.isEnabled(LogLevel.INFO))
        assertTrue(log.isEnabled(LogLevel.ERROR))
    }

    // ---------- 标签 ----------

    @Test
    fun sourceAndTagRecipeJoinsWithSlash() {
        val sink = RecordingSink()
        val logging = Logging(LoggingConfig.builder().sink(sink).build())

        logging.logger("Net").d(explicitTag = "OkHttp") { "x" }
        logging.logger("Net").d { "没有显式标签时只用来源名" }

        assertEquals("Net/OkHttp", sink.records[0].tag)
        assertEquals("Net", sink.records[1].tag)
    }

    @Test
    fun overlongTagIsTruncatedAndCounted() {
        val sink = RecordingSink()
        val logging = Logging(
            LoggingConfig.builder().sink(sink).maxTagLength(8).build()
        )

        logging.logger("Net").d(explicitTag = "VeryLongCallerName") { "x" }

        assertEquals("Net/Very", sink.records.single().tag)
        assertEquals(8, sink.records.single().tag.length)
        assertEquals(1, logging.stats.tagTruncationCount, "截断必须计数上报，不许静默")
    }

    @Test
    fun customTagRecipeIsSupported() {
        val sink = RecordingSink()
        val logging = Logging(
            LoggingConfig.builder().sink(sink).tagRecipe(TagRecipes.sourceOnly).build()
        )

        logging.logger("Net").d(explicitTag = "忽略掉") { "x" }

        assertEquals("Net", sink.records.single().tag)
    }

    // ---------- 出口与容错 ----------

    @Test
    fun oneBrokenSinkDoesNotAffectTheOthers() {
        val good = RecordingSink()
        val logging = Logging(
            LoggingConfig.builder().sink(ExplodingSink()).sink(good).build()
        )

        logging.logger("Net").d { "照常出门" }

        assertEquals(1, good.records.size, "前面的出口炸了，后面的出口必须照常收到")
    }

    @Test
    fun recordCarriesLevelSourceTagMessageAndThrowable() {
        val sink = RecordingSink()
        val logging = Logging(LoggingConfig.builder().sink(sink).build())
        val boom = RuntimeException("boom")

        logging.logger("Order").e(boom) { "下单失败" }

        val record = sink.records.single()
        assertEquals(LogLevel.ERROR, record.level)
        assertEquals("Order", record.source)
        assertEquals("下单失败", record.message)
        assertEquals(boom, record.throwable)
    }

    // ---------- 未安装 / 多实例 ----------

    @Test
    fun loggingWithoutAnyInstallationDoesNotCrash() {
        TLogger.uninstall()
        val log = TLogger.logger("Net")

        log.d { "没人管我，但我不该崩" }
        log.e(RuntimeException("x")) { "也不该崩" }

        assertFalse(log.isEnabled(LogLevel.ERROR))
    }

    @Test
    fun loggerCreatedBeforeInstallStillWorksAfterwards() {
        TLogger.uninstall()
        val log = TLogger.logger("Net") // 先建日志器

        val sink = RecordingSink()
        TLogger.install(LoggingConfig.builder().sink(sink).build()) // 后安装

        log.d { "现在能出去了" }

        assertEquals(1, sink.records.size, "日志器应该每次调用时去查当前安装的实例")
    }

    @Test
    fun twoInstancesDoNotInterfere() {
        val sinkA = RecordingSink()
        val sinkB = RecordingSink()
        val a = Logging(LoggingConfig.builder().sink(sinkA).defaultLevel(LogLevel.ERROR).build())
        val b = Logging(LoggingConfig.builder().sink(sinkB).defaultLevel(LogLevel.VERBOSE).build())

        a.logger("Net").v { "只在 B 里应该出现" }
        b.logger("Net").v { "这边能过" }

        assertEquals(0, sinkA.records.size)
        assertEquals(1, sinkB.records.size)
    }
}

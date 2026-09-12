package com.tlogger.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 测试用的假出口：把收到的记录存起来。 */
private class GatheringSink : LogSink {
    val messages = mutableListOf<String>()
    override fun write(record: LogRecord) {
        messages.add(record.message)
    }
}

/** 会炸的出口：验证"一个出口炸了，别的照常"。 */
private class BrokenSink : LogSink {
    var timesCalled = 0
    override fun write(record: LogRecord) {
        timesCalled++
        throw IllegalStateException("这个出口坏了")
    }
}

class CompositeSinkTest {

    private fun record(message: String) = LogRecord(
        level = LogLevel.INFO,
        source = "Test",
        tag = "Test",
        message = message,
        throwable = null,
    )

    @Test
    fun oneRecordGoesToEverySink() {
        val first = GatheringSink()
        val second = GatheringSink()

        CompositeSink(first, second).write(record("一条日志"))

        assertEquals(listOf("一条日志"), first.messages)
        assertEquals(listOf("一条日志"), second.messages)
    }

    @Test
    fun aBrokenSinkDoesNotStopTheRest() {
        val broken = BrokenSink()
        val good = GatheringSink()

        // 坏的在前面：后面的必须照样收到
        CompositeSink(broken, good).write(record("还要写出去"))

        assertEquals(1, broken.timesCalled)
        assertEquals(listOf("还要写出去"), good.messages)
    }

    @Test
    fun plusAppendsASink() {
        val first = GatheringSink()
        val second = GatheringSink()

        val combined = first + second

        assertEquals(2, combined.size)
        combined.write(record("两个都该收到"))
        assertEquals(1, first.messages.size)
        assertEquals(1, second.messages.size)
    }

    @Test
    fun aLambdaCanBeASink() {
        // fun interface 的意义：临时出口不用建类
        val collected = mutableListOf<String>()
        val sink = LogSink { r -> collected.add(r.message) }

        sink.write(record("lambda 出口"))

        assertEquals(listOf("lambda 出口"), collected)
    }

    @Test
    fun compositeCanBeUsedInsideLoggingConfig() {
        val first = GatheringSink()
        val second = GatheringSink()
        val logging = Logging(
            LoggingConfig.builder()
                .sink(CompositeSink(first, second))
                .defaultLevel(LogLevel.INFO)
                .build(),
        )

        logging.logger("Net").i { "走合成出口" }

        assertTrue(first.messages.single().contains("走合成出口"))
        assertTrue(second.messages.single().contains("走合成出口"))
    }
}

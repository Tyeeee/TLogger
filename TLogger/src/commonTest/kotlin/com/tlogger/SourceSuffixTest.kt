package com.tlogger

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 来源名加后缀（区分进程）和标签字符清理的测试。
 */
class SourceSuffixTest {

    private class RecordingSink : LogSink {
        val records = mutableListOf<LogRecord>()
        override fun write(record: LogRecord) {
            records.add(record)
        }
    }

    @Test
    fun withoutSuffixNothingChanges() {
        val sink = RecordingSink()
        val logging = Logging(LoggingConfig.builder().sink(sink).build())

        logging.logger("Net").i { "x" }

        assertEquals("Net", sink.records.single().source)
        assertEquals("Net", sink.records.single().tag)
    }

    @Test
    fun suffixIsAddedToShownSourceAndTag() {
        val sink = RecordingSink()
        val logging = Logging(
            LoggingConfig.builder().sink(sink).sourceSuffix("@remote").build(),
        )

        logging.logger("Net").i { "x" }

        assertEquals("Net@remote", sink.records.single().source, "来源名应该带上进程后缀")
        assertEquals("Net@remote", sink.records.single().tag, "标签也应该跟着变")
    }

    @Test
    fun levelDeclarationStillUsesTheOriginalSourceName() {
        val sink = RecordingSink()
        val logging = Logging(
            LoggingConfig.builder()
                .sink(sink)
                .defaultLevel(LogLevel.WARN)
                .sourceLevel("Net", LogLevel.VERBOSE) // 用原始名声明
                .sourceSuffix("@remote")              // 带后缀显示
                .build(),
        )

        logging.logger("Net").v { "应该出来" }
        logging.logger("Other").v { "应该被挡住" }

        assertEquals(1, sink.records.size, "声明级别时不该被后缀影响")
        assertEquals("Net@remote", sink.records.single().source)
    }

    @Test
    fun whitespaceInTagBecomesUnderscore() {
        val sink = RecordingSink()
        val logging = Logging(LoggingConfig.builder().sink(sink).build())

        logging.logger("Net").i(explicitTag = "带空格 的\t标签") { "x" }

        assertEquals("Net/带空格_的_标签", sink.records.single().tag, "标签里的空白应该换成下划线")
    }
}

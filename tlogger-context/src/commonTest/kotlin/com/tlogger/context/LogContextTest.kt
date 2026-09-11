package com.tlogger.context

import com.tlogger.core.LogLevel
import com.tlogger.core.LogRecord
import com.tlogger.core.LogSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class RecordingSink : LogSink {
    val records = mutableListOf<LogRecord>()
    override fun write(record: LogRecord) {
        records.add(record)
    }
}

class LogContextTest {

    private val session = SessionInfo(sessionId = "7c21", appVersion = "1.0")

    // ---------- 挂上 / 还原 ----------

    @Test
    fun contextIsSetInsideTheBlockAndRestoredAfterwards() {
        assertNull(LogContextHolder.current())

        withLogContext(LogContext(traceId = "a3f9")) {
            assertEquals("a3f9", LogContextHolder.current()?.traceId)
        }

        assertNull(LogContextHolder.current(), "出了代码块应该还原，不能留尾巴")
    }

    @Test
    fun nestedContextMergesInsteadOfOverwritingTraceId() {
        withTrace("a3f9") {
            withPage("订单页") {
                val current = LogContextHolder.current()
                assertEquals("a3f9", current?.traceId, "内层加页面不该把链路号顶掉")
                assertEquals("订单页", current?.page)
            }
            assertNull(LogContextHolder.current()?.page, "出了内层应该还原页面")
            assertEquals("a3f9", LogContextHolder.current()?.traceId)
        }
    }

    @Test
    fun mergedWithKeepsOwnValues() {
        val outer = LogContext(traceId = "a3f9", userId = "u1")
        val inner = LogContext(traceId = "别的", page = "订单页")

        val merged = outer.mergedWith(inner)

        assertEquals("a3f9", merged.traceId, "自己这边有值的字段不该被覆盖")
        assertEquals("u1", merged.userId)
        assertEquals("订单页", merged.page, "自己空着的字段用对方的补上")
    }

    // ---------- 前言怎么拼 ----------

    @Test
    fun prefixContainsTraceSessionVersionAndPage() {
        val prefix = buildContextPrefix(
            LogContext(traceId = "a3f9", page = "订单页"),
            session,
        )
        assertEquals("[t=a3f9 s=7c21 v=1.0 页=订单页]", prefix)
    }

    @Test
    fun prefixIsNullWhenThereIsNothingToSay() {
        assertNull(buildContextPrefix(null, SessionInfo.NONE))
        assertNull(buildContextPrefix(LogContext(), SessionInfo.NONE))
    }

    @Test
    fun sessionIdOnItsOwnIsEnough() {
        assertEquals("[s=7c21]", buildContextPrefix(null, SessionInfo(sessionId = "7c21")))
    }

    @Test
    fun newSessionGeneratesShortId() {
        val a = SessionInfo.new(appVersion = "2.0")
        val b = SessionInfo.new()

        assertEquals(4, a.sessionId.length, "会话号要短——它出现在每一条日志前面")
        assertEquals("2.0", a.appVersion)
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyz"
        assertTrue(a.sessionId.all { it in alphabet }, "会话号只该用简短字符：${a.sessionId}")
    }

    // ---------- 出口 ----------

    @Test
    fun sinkPutsPrefixInFrontOfTheMessage() {
        val delegate = RecordingSink()
        val sink = ContextSink(delegate, session = session)

        withLogContext(LogContext(traceId = "a3f9", page = "订单页")) {
            sink.write(record("开始请求 /api/order"))
        }

        assertEquals("[t=a3f9 s=7c21 v=1.0 页=订单页] 开始请求 /api/order", delegate.records.single().message)
    }

    @Test
    fun sinkLeavesMessageUntouchedWhenThereIsNoContext() {
        val delegate = RecordingSink()
        val sink = ContextSink(delegate) // 连会话都没有

        sink.write(record("普通日志"))

        assertEquals("普通日志", delegate.records.single().message)
    }

    @Test
    fun sinkKeepsOtherFieldsIntact() {
        val delegate = RecordingSink()
        val sink = ContextSink(delegate, session = session)
        val boom = RuntimeException("炸了")

        withTrace("a3f9") {
            sink.write(record("出错了", boom))
        }

        val out = delegate.records.single()
        assertEquals(LogLevel.ERROR, out.level)
        assertEquals("Net", out.source)
        assertEquals("Net", out.tag)
        assertEquals(boom, out.throwable, "异常对象要原样带过去，不能被上下文顶掉")
    }

    // ---------- 跨协程、跨线程 ----------

    @Test
    fun contextFollowsTheCoroutineAcrossDispatchers() = runTest {
        val seen = mutableListOf<String?>()

        launch(LogContextElement(LogContext(traceId = "a3f9"))) {
            seen += LogContextHolder.current()?.traceId
            // 换到别的线程上再取一次——这才是"自动传递"的关键
            withContext(Dispatchers.Default) {
                seen += LogContextHolder.current()?.traceId
            }
            seen += LogContextHolder.current()?.traceId
        }.join()

        assertEquals(listOf<String?>("a3f9", "a3f9", "a3f9"), seen, "换线程后链路号不该丢")
    }

    @Test
    fun coroutineContextIsRestoredAfterwards() = runTest {
        launch(LogContextElement(LogContext(traceId = "a3f9"))) {
            assertEquals("a3f9", LogContextHolder.current()?.traceId)
        }.join()
        assertNull(LogContextHolder.current(), "协程跑完不该在当前线程上留下东西")
    }

    private fun record(message: String, throwable: Throwable? = null) = LogRecord(
        level = if (throwable == null) LogLevel.INFO else LogLevel.ERROR,
        source = "Net",
        tag = "Net",
        message = message,
        throwable = throwable,
    )
}

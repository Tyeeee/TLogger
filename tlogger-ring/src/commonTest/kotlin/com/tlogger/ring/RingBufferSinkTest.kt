package com.tlogger.ring

import com.tlogger.core.LogLevel
import com.tlogger.core.LogRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RingBufferSinkTest {

    private fun record(index: Int, atMillis: Long = 0L) = LogRecord(
        level = LogLevel.INFO,
        source = "Net",
        tag = "Net",
        message = "第 $index 条",
        throwable = null,
        timestampMillis = atMillis,
    )

    @Test
    fun keepsOnlyTheMostRecentOnes() {
        val ring = RingBufferSink(capacity = 3)

        repeat(5) { ring.write(record(it)) }

        assertEquals(3, ring.size)
        assertEquals(
            listOf("第 2 条", "第 3 条", "第 4 条"),
            ring.snapshot().map { it.message },
            "满了以后应该丢最旧的，留最近的",
        )
    }

    @Test
    fun snapshotIsOrderedOldestToNewestAcrossWraparound() {
        val ring = RingBufferSink(capacity = 4)

        repeat(10) { ring.write(record(it)) }

        assertEquals(
            listOf("第 6 条", "第 7 条", "第 8 条", "第 9 条"),
            ring.snapshot().map { it.message },
            "绕过一圈以后顺序也不能乱",
        )
    }

    @Test
    fun beforeItIsFullOnlyTheWrittenOnesComeBack() {
        val ring = RingBufferSink(capacity = 10)

        ring.write(record(1))
        ring.write(record(2))

        assertEquals(listOf("第 1 条", "第 2 条"), ring.snapshot().map { it.message })
        assertEquals(2, ring.size)
    }

    @Test
    fun clearEmptiesIt() {
        val ring = RingBufferSink(capacity = 3)
        repeat(3) { ring.write(record(it)) }

        ring.clear()

        assertEquals(0, ring.size)
        assertTrue(ring.snapshot().isEmpty())
    }

    @Test
    fun dumpPrintsTimeLevelTagAndMessage() {
        val ring = RingBufferSink(capacity = 2)
        // 2026-01-01 00:00:01.500 UTC
        ring.write(record(1, atMillis = 1_767_225_601_500L))

        val line = ring.dump()

        assertEquals("[00:00:01.500] I Net: 第 1 条", line)
    }

    @Test
    fun dumpShowsAllLinesOldestFirst() {
        val ring = RingBufferSink(capacity = 3)
        repeat(3) { ring.write(record(it)) }

        val lines = ring.dump().split("\n")

        assertEquals(3, lines.size)
        assertTrue(lines.first().endsWith("第 0 条"), lines.first())
        assertTrue(lines.last().endsWith("第 2 条"), lines.last())
    }

    @Test
    fun manyThreadsWritingDoNotCorruptIt() {
        val ring = RingBufferSink(capacity = 100)
        val threads = (1..4).map { t ->
            Thread {
                repeat(250) { i -> ring.write(record(t * 1000 + i)) }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(100, ring.size, "并发写完应该正好是容量")
        assertEquals(100, ring.snapshot().size, "取出来的条数要和容量对得上")
    }

    @Test
    fun capacityMustBePositive() {
        var threw = false
        try {
            RingBufferSink(capacity = 0)
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw, "容量为 0 应该当场报错，而不是悄悄不干活")
    }

    @Test
    fun clockFormattingHandlesTimezoneOffset() {
        // 同一时刻：UTC 是 00:00:01，东八区是 08:00:01
        val millis = 1_767_225_601_500L
        assertEquals("00:00:01.500", formatClock(millis, 0))
        assertEquals("08:00:01.500", formatClock(millis, 8 * 60))
    }
}

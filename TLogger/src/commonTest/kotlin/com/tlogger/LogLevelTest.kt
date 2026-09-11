package com.tlogger

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogLevelTest {

    @Test
    fun severityIsAscendingFromVerboseToAssert() {
        val severities = LogLevel.entries.map { it.severity }
        assertEquals(severities.sorted(), severities, "级别必须按严重程度递增，否则门槛判断会反")
    }

    @Test
    fun severityMatchesAndroidLogConstants() {
        // 与 android.util.Log 的取值对齐，映射时不用换算。
        assertEquals(2, LogLevel.VERBOSE.severity)
        assertEquals(3, LogLevel.DEBUG.severity)
        assertEquals(4, LogLevel.INFO.severity)
        assertEquals(5, LogLevel.WARN.severity)
        assertEquals(6, LogLevel.ERROR.severity)
        assertEquals(7, LogLevel.ASSERT.severity)
    }

    @Test
    fun isAtLeastAcceptsEqualAndHigherLevels() {
        assertTrue(LogLevel.INFO.isAtLeast(LogLevel.INFO), "同级别应当通过门槛")
        assertTrue(LogLevel.ERROR.isAtLeast(LogLevel.INFO), "更严重的级别应当通过门槛")
        assertFalse(LogLevel.DEBUG.isAtLeast(LogLevel.INFO), "更低的级别不应当通过门槛")
    }

    @Test
    fun fromSeverityRoundTrips() {
        LogLevel.entries.forEach { level ->
            assertEquals(level, LogLevel.fromSeverityOrNull(level.severity))
        }
    }

    @Test
    fun unknownSeverityReturnsNullInsteadOfThrowing() {
        assertNull(LogLevel.fromSeverityOrNull(0))
        assertNull(LogLevel.fromSeverityOrNull(99))
    }
}

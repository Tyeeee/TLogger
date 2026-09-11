package com.tlogger.core
/**
 * 日志级别。
 *
 * 取值与安卓系统日志的级别编号对齐（VERBOSE=2 … ASSERT=7），这样映射到 logcat 时是一一对应的整数，
 * 不需要再做一层换算；后续苹果端映射时也以本表为准。
 *
 * 注意：级别本身不表达"关闭"。某个来源是否输出、阈值定在哪里，属于配置，不属于级别。
 */
public enum class LogLevel(public val severity: Int) {
    VERBOSE(2),
    DEBUG(3),
    INFO(4),
    WARN(5),
    ERROR(6),
    ASSERT(7),
    ;

    /**
     * 本条日志是否达到了 [threshold] 这个输出门槛。
     *
     * 例如 `LogLevel.DEBUG.isAtLeast(LogLevel.INFO)` 为 `false`，表示在 INFO 门槛下 DEBUG 不输出。
     */
    public fun isAtLeast(threshold: LogLevel): Boolean = severity >= threshold.severity

    public companion object {
        /** 按编号反查级别；编号不认识时返回 `null`（不猜、也不抛异常）。 */
        public fun fromSeverityOrNull(severity: Int): LogLevel? =
            entries.firstOrNull { it.severity == severity }
    }
}

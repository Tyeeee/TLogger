package com.tlogger

/**
 * 某个来源的日志器。
 *
 * ## 为什么消息是个函数（惰性求值）
 *
 * 接口长这样：`log.d { "值 = $value" }`，而不是 `log.d("值 = $value")`。
 *
 * 原因：**移动端日志最贵的部分往往不是写出去，而是拼字符串**。如果这条日志被级别过滤掉了，
 * 传函数就不会拼；传字符串则已经拼完了，白费。
 *
 * 用法：
 * ```
 * log.d { "连接成功" }                    // 没有异常
 * log.e(throwable) { "请求失败" }          // 带异常
 * ```
 */
public class Logger internal constructor(
    /** 这个日志器绑定的来源名。 */
    public val source: String,
    private val host: () -> Logging?,
) {

    /**
     * 这个级别此刻会不会输出。
     *
     * 适合"拼字符串特别贵、想提前跳过"的场景：先问一句，再决定要不要准备内容。
     */
    public fun isEnabled(level: LogLevel): Boolean = host()?.isEnabled(level, source) == true

    /** 写一条日志。[message] 只在真的会输出时求值。 */
    public fun log(
        level: LogLevel,
        throwable: Throwable? = null,
        explicitTag: String? = null,
        message: () -> String,
    ): Unit {
        // 没安装任何日志系统时安全降级：什么都不做，绝不崩。
        val logging = host() ?: return
        // 先判级别再求值——顺序反了惰性求值就白做了。
        if (!logging.isEnabled(level, source)) return
        logging.write(level, source, explicitTag, message(), throwable)
    }

    /** [LogLevel.VERBOSE]。 */
    public fun v(throwable: Throwable? = null, explicitTag: String? = null, message: () -> String): Unit =
        log(LogLevel.VERBOSE, throwable, explicitTag, message)

    /** [LogLevel.DEBUG]。 */
    public fun d(throwable: Throwable? = null, explicitTag: String? = null, message: () -> String): Unit =
        log(LogLevel.DEBUG, throwable, explicitTag, message)

    /** [LogLevel.INFO]。 */
    public fun i(throwable: Throwable? = null, explicitTag: String? = null, message: () -> String): Unit =
        log(LogLevel.INFO, throwable, explicitTag, message)

    /** [LogLevel.WARN]。 */
    public fun w(throwable: Throwable? = null, explicitTag: String? = null, message: () -> String): Unit =
        log(LogLevel.WARN, throwable, explicitTag, message)

    /** [LogLevel.ERROR]。 */
    public fun e(throwable: Throwable? = null, explicitTag: String? = null, message: () -> String): Unit =
        log(LogLevel.ERROR, throwable, explicitTag, message)
}

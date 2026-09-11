package com.tlogger.context

/**
 * 安卓上的实现：用线程本地变量存上下文。
 *
 * 为什么用线程本地：同一时刻可能有多个线程各自在处理不同的请求，
 * 链路号必须**各归各的**，不能串味。协程切换线程时由 [LogContextElement] 负责搬运。
 */
private val threadLocalContext = ThreadLocal<LogContext?>()

internal actual fun currentContext(): LogContext? = threadLocalContext.get()

internal actual fun setCurrentContext(context: LogContext?): LogContext? {
    val previous = threadLocalContext.get()
    if (context == null) {
        threadLocalContext.remove()
    } else {
        threadLocalContext.set(context)
    }
    return previous
}

package com.tlogger.context

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ThreadContextElement

/**
 * 把上下文挂到协程上——这样**跨线程、跨协程都不会丢**。
 *
 * ```
 * launch(LogContextElement(LogContext(traceId = "a3f9"))) {
 *     runBlocking {      // 换个线程、换个调度器
 *         api.call()     // 里面打的日志照样带着 t=a3f9
 *     }
 * }
 * ```
 *
 * 为什么能做到：协程在切换线程时，会把挂在自己身上的东西**重新装到新线程上**，
 * 跑完再还原回来。这个类就是干这件事的钩子。
 *
 * 如果代码里不用协程（普通线程池），那就用 [withLogContext] 包住那一段。
 */
public class LogContextElement(
    private val logContext: LogContext,
) : ThreadContextElement<LogContext?>, AbstractCoroutineContextElement(LogContextElement) {

    public companion object Key : CoroutineContext.Key<LogContextElement>

    override fun updateThreadContext(context: CoroutineContext): LogContext? {
        // 和线程上已有的合并（已有的优先），这样嵌套使用时链路号不会被覆盖
        val merged = LogContextHolder.current()?.mergedWith(logContext) ?: logContext
        return LogContextHolder.replace(merged)
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: LogContext?) {
        LogContextHolder.replace(oldState)
    }
}

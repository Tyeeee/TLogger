package com.tlogger.context

/**
 * 在这一段代码里挂上上下文，出去以后自动还原。
 *
 * ```
 * // 业务入口标记一次就够了
 * withLogContext(LogContext(traceId = "a3f9")) {
 *     repository.submitOrder()   // 里面所有模块打的日志都会自动带上 t=a3f9
 * }
 * ```
 *
 * 传进来的上下文会和已有的**合并**（已有的字段优先），所以可以在链路里面再加页面：
 * ```
 * withLogContext(LogContext(traceId = id)) {
 *     ...
 *     withLogContext(LogContext(page = "订单页")) { ... }   // 链路号还是原来的
 * }
 * ```
 *
 * 协程里用 [LogContextElement] 挂，那样跨线程也不会丢。
 */
public fun <T> withLogContext(context: LogContext, block: () -> T): T {
    val previous = LogContextHolder.replace(
        LogContextHolder.current()?.mergedWith(context) ?: context,
    )
    try {
        return block()
    } finally {
        LogContextHolder.replace(previous)
    }
}

/** 只加一个链路号时的简写。 */
public fun <T> withTrace(traceId: String, block: () -> T): T =
    withLogContext(LogContext(traceId = traceId), block)

/** 只加一个页面时的简写。 */
public fun <T> withPage(page: String, block: () -> T): T =
    withLogContext(LogContext(page = page), block)

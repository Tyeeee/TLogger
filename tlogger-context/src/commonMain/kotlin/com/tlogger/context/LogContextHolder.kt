package com.tlogger.context

/**
 * 当前线程（或当前协程）上挂着的上下文。
 *
 * 它是**按线程存的**，所以同一时刻不同线程可以各自带着不同的链路号——这正是并发请求需要的。
 * 协程里的传递靠 [LogContextElement]，见那个类的说明。
 */
public object LogContextHolder {

    /** 取当前上下文；没有就是 `null`。 */
    public fun current(): LogContext? = currentContext()

    /** 设置并返回设置之前的值。标成内部用：正常写法应该用 [withLogContext] 包起来，别手工设。 */
    internal fun replace(context: LogContext?): LogContext? = setCurrentContext(context)
}

/**
 * 取当前上下文。
 *
 * 平台各自的实现：安卓/电脑上用线程本地变量；以后苹果端同理。
 */
internal expect fun currentContext(): LogContext?

/** 设置当前上下文，返回设置前的那个（用来还原）。 */
internal expect fun setCurrentContext(context: LogContext?): LogContext?

package com.tlogger.core

/**
 * 把好几个出口拼成**一个**出口，一条日志同时送给它们。
 *
 * ## 什么时候需要它
 *
 * 出口本来是**并行**的：`sink(a).sink(b)` 里 a 和 b 拿到的是同一份记录，各写各的。
 * 所以"同时写系统日志和内存缓冲区"这件事，不用它也能做。
 *
 * 它解决的是另外两种写法：
 *
 * ```
 * // 一、要对"一组出口"统一套一层（比如都打码），先合成再套，比逐个套少写一遍
 * val outputs = CompositeSink(AndroidLogSink(), RingBufferSink())
 * LoggingConfig.builder().sink(RedactingSink(outputs, redactor)).build()
 *
 * // 二、把"一组出口"当成一个出口传给别人（测试里最常用）
 * val sink = RecordingSink() + RecordingSink()
 * ```
 *
 * ## 一个出口坏了，不影响别的
 *
 * 出口的约定是"不许往外抛异常"，但真抛了也不能连累其它出口、更不能影响业务线程。
 * 所以这里每个出口单独兜住异常——跟 [Logging] 里对单个出口的做法一致。
 */
public class CompositeSink(public vararg val sinks: LogSink) : LogSink {

    public override fun write(record: LogRecord) {
        for (sink in sinks) {
            try {
                sink.write(record)
            } catch (_: Throwable) {
                // 出口自己的问题（实现方违约了）。一个坏了，剩下的照常写。
            }
        }
    }

    /** 再串一个出口。复合出口之间串联时用它，结果不会一层层套起来。 */
    public operator fun plus(other: LogSink): CompositeSink = CompositeSink(*sinks, other)

    /** 现在串了几个出口。 */
    public val size: Int get() = sinks.size
}

/**
 * `sinkA + sinkB`：把任意两个出口串成一个，不用手写 [CompositeSink]。
 *
 * ```
 * val outputs = AndroidLogSink() + RingBufferSink()
 * ```
 * 三个以上还是 [CompositeSink] 更直观。
 */
public operator fun LogSink.plus(other: LogSink): CompositeSink = CompositeSink(this, other)

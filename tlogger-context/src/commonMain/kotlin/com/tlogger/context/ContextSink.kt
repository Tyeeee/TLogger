package com.tlogger.context

import com.tlogger.core.LogRecord
import com.tlogger.core.LogSink

/**
 * 把上下文（链路号、会话号、版本、页面）**拼到日志正文前面**，再交给真正的出口。
 *
 * ```
 * .sink(ContextSink(AndroidLogSink(), session = SessionInfo.new(appVersion = "1.0")))
 * ```
 *
 * 日志窗口里看起来是这样：
 * ```
 * I Net    : [t=a3f9 s=7c21 v=1.0 页=订单页] 开始请求 /api/order
 * ```
 *
 * ## 为什么放在正文里，而不是标签里
 *
 * 链路号每条都不一样。放进标签的话，标签列表会膨胀成成千上万个，**过滤器反而废了**。
 * 放在正文里用关键字就能过滤（搜 `t=a3f9` 就把这条链路全拉出来），而且不影响按来源过滤。
 *
 * ## 顺序有讲究
 *
 * 它应该套在**打码里面**（先打码再拼上下文），或者套在外面都行——但注意：
 * 如果 [session] 里的 `userId` 是敏感值，**请自己先打好码再传进来**，这里不做打码。
 */
public class ContextSink(
    private val delegate: LogSink,
    private val session: SessionInfo = SessionInfo.NONE,
) : LogSink {

    public override fun write(record: LogRecord) {
        val prefix = buildContextPrefix(LogContextHolder.current(), session)
        if (prefix == null) {
            delegate.write(record)
            return
        }
        delegate.write(
            LogRecord(
                level = record.level,
                source = record.source,
                tag = record.tag,
                message = "$prefix ${record.message}",
                throwable = record.throwable,
            ),
        )
    }
}

/**
 * 拼出前言。
 *
 * 刻意保持很短（`[t=xxxx s=xxxx]` 这种），因为它出现在**每一条**日志前面——
 * 太长了会把日志窗口挤爆，一屏能看的条数骤减。
 */
internal fun buildContextPrefix(context: LogContext?, session: SessionInfo): String? {
    val parts = ArrayList<String>(4)

    context?.traceId?.let { parts += "t=$it" }
    if (session.sessionId.isNotEmpty()) parts += "s=${session.sessionId}"
    session.appVersion?.let { parts += "v=$it" }
    context?.page?.let { parts += "页=$it" }
    (context?.userId ?: session.userId)?.let { parts += "u=$it" }

    return if (parts.isEmpty()) null else "[" + parts.joinToString(" ") + "]"
}

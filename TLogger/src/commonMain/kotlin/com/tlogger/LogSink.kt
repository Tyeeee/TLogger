package com.tlogger

/**
 * 一条准备送出去的日志记录。
 *
 * 它是不可变的：构造完成之后不再修改，这样多个输出端点可以各自读取同一份内容。
 *
 * 注意这里**没有时间戳字段**。时间怎么记（精确流逝时间 + 墙上时钟两套）是独立的课题（见
 * `docs/架构设计约束-日志组件.md` 约束 5），第一片实现刻意不碰，避免现在就把接口形状定错。
 */
public class LogRecord(
    /** 日志级别。 */
    public val level: LogLevel,
    /** 来源名——由使用者自己定义（例如 "Net"、"Order"），是过滤和开关的主要依据。 */
    public val source: String,
    /** 最终用于输出的标签，由 [TagRecipe] 按来源算出来。 */
    public val tag: String,
    /** 已经求值完成的消息文本。 */
    public val message: String,
    /** 附带的异常，没有就是 `null`。 */
    public val throwable: Throwable?,
)

/**
 * 日志的出口。系统日志、文件、环形缓冲、测试用的假出口，都实现这个接口。
 *
 * 约束：**实现里不要把异常抛出去**。写出错属于内部问题，不该影响业务线程
 * （见 `docs/架构设计约束-日志组件.md` 约束 19）。
 */
public interface LogSink {
    /** 写出一条记录。 */
    public fun write(record: LogRecord)
}

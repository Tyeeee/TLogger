package com.tlogger.ring

import com.tlogger.core.LogRecord
import com.tlogger.core.LogSink

/**
 * 在内存里留住**最近 N 条**日志。
 *
 * ```
 * val ring = RingBufferSink(capacity = 500)
 * TLogger.install(
 *     LoggingConfig.builder()
 *         .sink(ring)                 // 留在内存里
 *         .sink(AndroidLogSink())     // 同时也输出到日志窗口
 *         .build(),
 * )
 *
 * // 出事的时候把最后这些捞出来
 * Log.i("Crash", ring.dump())
 * ```
 *
 * ## 它解决什么
 *
 * "用户说刚才闪了一下"——日志窗口里早翻过去了，但缓冲区里还留着最后几百条。
 *
 * ## 它**不**解决什么（必须说清楚）
 *
 * - **进程被杀就一起没了**：它只在内存里。要撑过被杀得等第二版的落盘。
 * - 它不落盘、不压缩、不加密。
 *
 * ## 满了怎么办
 *
 * 旧的自然被挤掉（这正是"环形"的意思）。容量按条数算，默认 [DEFAULT_CAPACITY] 条。
 * 别忘了日志本身占内存——500 条普通日志大概几十 KB，但如果是长文本要自己掂量。
 *
 * @param capacity 最多留多少条。
 * @param clockOffsetMinutes 打印时间时用哪个时区（分钟）；默认 0 = UTC。
 */
public class RingBufferSink(
    public val capacity: Int = DEFAULT_CAPACITY,
    private val clockOffsetMinutes: Int = 0,
) : LogSink {

    init {
        require(capacity > 0) { "容量必须大于 0" }
    }

    private val lock = RingLock()
    private val buffer: Array<LogRecord?> = arrayOfNulls(capacity)

    /** 下一个要写的位置。 */
    private var next: Int = 0

    /** 已经装了多少条（最多到容量为止）。 */
    private var filled: Int = 0

    /** 现在里面有多少条。 */
    public val size: Int
        get() = lock.withLock { filled }

    public override fun write(record: LogRecord) {
        lock.withLock {
            buffer[next] = record
            next = (next + 1) % buffer.size
            if (filled < buffer.size) filled++
        }
    }

    /** 把里面现有的记录按**从旧到新**取出来。 */
    public fun snapshot(): List<LogRecord> = lock.withLock {
        val count = filled
        val out = ArrayList<LogRecord>(count)
        val start = if (count < buffer.size) 0 else next
        for (i in 0 until count) {
            buffer[(start + i) % buffer.size]?.let { out.add(it) }
        }
        out
    }

    /** 清空。 */
    public fun clear(): Unit = lock.withLock {
        buffer.fill(null)
        next = 0
        filled = 0
    }

    /**
     * 拼成可以直接贴出来的文本，一行一条，从旧到新。
     *
     * 形如：`[10:23:45.123] I Net: 开始请求`
     *
     * 时间用**墙上时钟**，默认按 UTC 打印（想按本地时间看，构造时传 [clockOffsetMinutes]）。
     */
    public fun dump(): String = snapshot().joinToString(separator = "\n") { record ->
        val level = record.level.name.first()
        "[${formatClock(record.timestampMillis, clockOffsetMinutes)}] $level ${record.tag}: ${record.message}"
    }

    public companion object {
        /** 默认留 2000 条。 */
        public const val DEFAULT_CAPACITY: Int = 2000
    }
}

/**
 * 把毫秒时间戳拼成 `时:分:秒.毫秒`。
 *
 * 手算而不是用日期库：为了少一个依赖，而且这里只要"时刻"，不要日期。
 */
internal fun formatClock(millis: Long, offsetMinutes: Int = 0): String {
    if (millis <= 0L) return "--:--:--.---"
    val secondsOfDay = (millis / 1000L + offsetMinutes * 60L).mod(86_400L)
    val hour = secondsOfDay / 3600L
    val minute = secondsOfDay % 3600L / 60L
    val second = secondsOfDay % 60L
    val milli = millis.mod(1000L)
    return "${pad(hour, 2)}:${pad(minute, 2)}:${pad(second, 2)}.${pad(milli, 3)}"
}

private fun pad(value: Long, width: Int): String = value.toString().padStart(width, '0')

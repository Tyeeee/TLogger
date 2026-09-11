package com.tlogger

import android.util.Log

/**
 * 输出到安卓系统日志（logcat）。
 *
 * 这是第一片里唯一的真实出口——因为"开发时在日志窗口里看"就是最日常的场景。
 *
 * 已知待办（第一片刻意没做，避免一次摊太大）：
 * 1. **长日志没有拆分**：单条超过系统上限（约 4KB）会被截断，需要自动分段并编号；
 * 2. 没有做"按来源一键屏蔽"（那是出口链路的组合问题，放在下一步做）。
 */
public class AndroidLogSink : LogSink {

    public override fun write(record: LogRecord) {
        val priority = when (record.level) {
            LogLevel.VERBOSE -> Log.VERBOSE
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
            LogLevel.ASSERT -> Log.ASSERT
        }
        val text = if (record.throwable == null) {
            record.message
        } else {
            record.message + "\n" + Log.getStackTraceString(record.throwable)
        }
        Log.println(priority, record.tag, text)
    }
}

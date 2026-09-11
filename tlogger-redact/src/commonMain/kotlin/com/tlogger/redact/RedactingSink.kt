package com.tlogger.redact

import com.tlogger.core.LogRecord
import com.tlogger.core.LogSink

/**
 * 把打码**套在原有出口外面**——这样它不挑用什么出口，也不挑用什么日志库。
 *
 * ```
 * LoggingConfig.builder()
 *     .sink(RedactingSink(AndroidLogSink(), PiiRedactor()))
 *     .build()
 * ```
 *
 * ## 现在只管正文，还有一个没管的
 *
 * 它处理的是日志的**正文**。**异常堆栈里的文字目前没有处理**——如果异常消息里带了手机号，
 * 那部分不会被换掉。原因：堆栈文字是出口在输出时从异常对象上现取的，这里改不了异常本身。
 * 这条已经记在仓库 README 的已知限制里，后面要么在出口那层补，要么在记录异常时就处理。
 */
public class RedactingSink(
    private val delegate: LogSink,
    private val redactor: Redactor,
) : LogSink {

    public override fun write(record: LogRecord) {
        val redactedMessage = redactor.redact(record.message)
        if (redactedMessage == record.message) {
            delegate.write(record)
            return
        }
        delegate.write(
            LogRecord(
                level = record.level,
                source = record.source,
                tag = record.tag,
                message = redactedMessage,
                throwable = record.throwable,
                timestampMillis = record.timestampMillis,
            ),
        )
    }
}

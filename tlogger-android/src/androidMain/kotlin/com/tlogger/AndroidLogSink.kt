package com.tlogger

import android.util.Log

/**
 * 输出到安卓系统日志（logcat）。
 *
 * ## 为什么要分段（这是实测出来的问题）
 *
 * 系统对单条日志有长度上限（约 4KB），超了**直接砍掉尾巴，而且不给任何提示**。
 * 实测：打一条 7998 字的日志，库完整交出去了，但日志里只剩约 3998 字，结尾标记完全消失。
 *
 * 后果很严重：接口返回的 JSON、错误堆栈这类长内容会被无声砍掉一半，看日志的人却以为那就是全部。
 *
 * 所以这里按字节自动分段，每段前面标上 `[1/3]` 这样的编号，**该有的内容一个字都不少**。
 *
 * @param maxBytesPerEntry 每段最多多少字节（UTF-8），默认 [DEFAULT_MAX_BYTES]。
 */
public class AndroidLogSink(
    private val maxBytesPerEntry: Int = DEFAULT_MAX_BYTES,
) : LogSink {

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

        for (part in LogcatChunking.split(text, maxBytesPerEntry)) {
            Log.println(priority, record.tag, part)
        }
    }

    public companion object {
        /**
         * 每段的上限：3900 字节。
         *
         * 系统的单条上限是 4068 字节，而这一条里还要装标签和几个分隔符，标签最长能到 32 个字符，
         * 所以留出余量取 3900，保证**加上标签也不会超**。
         */
        public const val DEFAULT_MAX_BYTES: Int = 3900
    }
}

/**
 * 按**字节**（不是字数）把长文本切成若干段，并给每段加编号。
 *
 * 几个容易做错的点：
 * - 不能按"字数"切：一个中文占 3 字节，一个表情占 4 字节，按字数切会超；
 * - 不能把一个字/一个表情切成两半（那样会出现乱码）；
 * - 编号前缀本身也占字节，切的时候要给它留位置。
 */
internal object LogcatChunking {

    /** 给 `[10/10] ` 这样的前缀留的位置。 */
    private const val PREFIX_RESERVE: Int = 16

    fun split(text: String, maxBytes: Int): List<String> {
        require(maxBytes > PREFIX_RESERVE) { "每段上限太小，放不下编号前缀" }

        if (byteCount(text, 0, text.length) <= maxBytes) return listOf(text)

        val bodyBudget = maxBytes - PREFIX_RESERVE
        val bodies = ArrayList<String>()
        val sb = StringBuilder()
        var bytes = 0
        var i = 0

        while (i < text.length) {
            val charCount = codePointLengthAt(text, i)
            val codePointBytes = codePointByteCount(text, i, charCount)

            if (bytes + codePointBytes > bodyBudget && sb.isNotEmpty()) {
                bodies.add(sb.toString())
                sb.setLength(0)
                bytes = 0
            }
            sb.append(text, i, i + charCount)
            bytes += codePointBytes
            i += charCount
        }
        if (sb.isNotEmpty()) bodies.add(sb.toString())

        val total = bodies.size
        return bodies.mapIndexed { index, body -> "[${index + 1}/$total] $body" }
    }

    /** 一个字符位置占几个 char：表情这类由两个 char 组成，要一起处理，不能从中间切开。 */
    private fun codePointLengthAt(text: String, index: Int): Int {
        val c = text[index]
        val isPair = Character.isHighSurrogate(c) &&
            index + 1 < text.length &&
            Character.isLowSurrogate(text[index + 1])
        return if (isPair) 2 else 1
    }

    private fun codePointByteCount(text: String, start: Int, charCount: Int): Int {
        if (charCount == 2) return 4 // 表情这类，UTF-8 里固定 4 字节
        val code = text[start].code
        return when {
            code < 0x80 -> 1
            code < 0x800 -> 2
            else -> 3
        }
    }

    private fun byteCount(text: String, start: Int, length: Int): Int {
        var bytes = 0
        var i = start
        val end = start + length
        while (i < end) {
            val charCount = codePointLengthAt(text, i)
            bytes += codePointByteCount(text, i, charCount)
            i += charCount
        }
        return bytes
    }
}

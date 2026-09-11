package com.tlogger.android
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 分段逻辑的测试。跑在电脑上，不需要真机（这一路是"宿主测试"）。
 *
 * 中文和表情的字节数跟英文不一样，是最容易做错的地方，所以专门测。
 */
class LogcatChunkingTest {

    @Test
    fun shortTextIsNotSplit() {
        val text = "短短一句话"
        assertEquals(listOf(text), LogcatChunking.split(text, 3900))
    }

    @Test
    fun textExactlyAtLimitIsNotSplit() {
        val text = "a".repeat(3900)
        assertEquals(listOf(text), LogcatChunking.split(text, 3900))
    }

    @Test
    fun longAsciiTextIsSplitAndNothingIsLost() {
        val text = "0123456789".repeat(800) // 8000 个字符
        val parts = LogcatChunking.split(text, 3900)

        assertTrue(parts.size >= 3, "8000 个字符应该切成至少 3 段，实际 ${parts.size} 段")
        parts.forEachIndexed { index, part ->
            assertTrue(
                part.length <= 3900,
                "第 ${index + 1} 段长度 ${part.length} 超了上限",
            )
            assertTrue(part.startsWith("[${index + 1}/${parts.size}] "), "第 ${index + 1} 段缺少编号前缀")
        }

        // 把编号前缀去掉拼回去，必须和原文一模一样——一个字都不能丢
        val rebuilt = parts.joinToString("") { it.substringAfter("] ") }
        assertEquals(text, rebuilt, "拼回去必须和原文完全一致")
    }

    @Test
    fun chineseIsSplitByBytesNotByCharacters() {
        // 中文一个字 3 字节，按字数切会远远超出上限
        val text = "中文".repeat(2000) // 4000 个字符，12000 字节
        val parts = LogcatChunking.split(text, 3900)

        parts.forEach { part ->
            val bytes = part.encodeToByteArray().size
            assertTrue(bytes <= 3900, "这一段是 $bytes 字节，超了上限")
        }
        val rebuilt = parts.joinToString("") { it.substringAfter("] ") }
        assertEquals(text, rebuilt)
    }

    @Test
    fun emojiIsNeverCutInHalf() {
        // 表情在 UTF-8 里占 4 字节，在字符串里占两个 char，从中间切开会出现乱码
        val text = "🎉".repeat(2000)
        val parts = LogcatChunking.split(text, 3900)

        parts.forEach { part ->
            val body = part.substringAfter("] ")
            // 每个表情都必须完整：字符数是偶数，且不含落单的代理字符
            assertTrue(body.length % 2 == 0, "有表情被切成了两半")
            assertTrue(body.none { it.isHighSurrogate() && body.indexOf(it) == body.length - 1 }, "末尾出现落单的代理字符")
        }
        val rebuilt = parts.joinToString("") { it.substringAfter("] ") }
        assertEquals(text, rebuilt)
    }
}

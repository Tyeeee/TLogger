package com.tlogger.redact

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PiiRedactorTest {

    private val redactor = PiiRedactor()

    // ---------- 各类敏感信息 ----------

    @Test
    fun phoneKeepsHeadAndTail() {
        assertEquals("手机号 138****5678 已登记", redactor.redact("手机号 13812345678 已登记"))
    }

    @Test
    fun emailKeepsFirstTwoChars() {
        val out = redactor.redact("联系 zhangsan@example.com 处理")
        assertTrue(out.startsWith("联系 zh"), out)
        assertTrue(!out.contains("angsan@example.com"), "邮箱其余部分应该被涂掉：$out")
    }

    @Test
    fun idCardKeepsHeadAndTail() {
        val out = redactor.redact("身份证 110101199003071234 已核验")
        assertEquals("身份证 1101**********1234 已核验", out)
    }

    @Test
    fun bankCardWithValidChecksumIsRedacted() {
        // 4111111111111111 是过校验位的测试卡号
        val out = redactor.redact("卡号 4111111111111111 已绑定")
        assertEquals("卡号 4111********1111 已绑定", out)
    }

    @Test
    fun randomSixteenDigitsIsNotTreatedAsBankCard() {
        // 校验位过不了，就不该当成卡号——否则随便一串数字都会被涂掉
        val text = "流水号 4111111111111112 生成"
        assertEquals(text, redactor.redact(text))
    }

    @Test
    fun ipAddressIsFullyMasked() {
        // 192.168.100.200 一共 15 个字符，全涂掉就是 15 个星号
        assertEquals("来自 *************** 的请求", redactor.redact("来自 192.168.100.200 的请求"))
    }

    @Test
    fun invalidIpIsNotMasked() {
        val text = "地址 999.1.1.1 无效"
        assertEquals(text, redactor.redact(text))
    }

    @Test
    fun jwtIsFullyMasked() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abcdefghij"
        val out = redactor.redact("凭证 $jwt 已过期")
        assertTrue(!out.contains("eyJhbGciOiJIUzI1NiJ9"), "凭证不该原样出现：$out")
    }

    // ---------- 不误伤 ----------

    @Test
    fun normalBusinessTextIsUntouched() {
        val text = "订单 20260911001 金额 199.00 状态 已完成"
        assertEquals(text, redactor.redact(text))
    }

    @Test
    fun pureTextIsReturnedAsIs() {
        val text = "用户点击了立即购买"
        assertEquals(text, redactor.redact(text))
    }

    @Test
    fun idCardIsNotAlsoTreatedAsPhoneNumber() {
        // 18 位身份证里可能含一段像手机号的数字；已经被身份证规则吃掉的区间不该再处理一次
        val out = redactor.redact("110101199003071234")
        assertEquals("1101**********1234", out)
        assertEquals(1, redactor.stats.total, "只该命中一次，实际：${redactor.stats}")
    }

    /**
     * 已知的误伤，先记录下来，不装作没有。
     *
     * `1.2.3.4` 这种版本号/构建号长得跟 IP 一样，会被当成 IP 涂掉。
     * 要准确区分需要看上下文（前面是不是"版本"两个字），这一片先如实记录，不改。
     */
    @Test
    fun versionNumberLooksLikeIpAndGetsMasked_KnownFalsePositive() {
        // 1.2.3.4 一共 7 个字符，会被当成 IP 涂成 7 个星号
        val out = PiiRedactor().redact("版本 1.2.3.4 已发布")
        assertEquals("版本 ******* 已发布", out)
    }

    // ---------- 换代号 ----------

    @Test
    fun sameValueGetsTheSameToken() {
        val tokenizing = PiiRedactor(
            rules = listOf(phoneRuleWithToken()),
            tokenGenerator = TokenGenerators.simple,
        )

        val first = tokenizing.redact("手机号 13812345678 登录")
        val second = tokenizing.redact("手机号 13812345678 支付")
        val other = tokenizing.redact("手机号 13900000000 登录")

        val tokenOf = { s: String -> Regex("""#PHONE-\w+""").find(s)?.value }
        assertEquals(tokenOf(first), tokenOf(second), "同一个号码每次该是同一个代号，才能对上号")
        assertTrue(tokenOf(first) != tokenOf(other), "不同号码应该是不同代号")
        assertTrue(!first.contains("13812345678"), "原号码不该出现：$first")
    }

    private fun phoneRuleWithToken(): PiiRule = PiiRule(
        kind = SensitiveKind.PHONE,
        pattern = Regex("""(?<!\d)1[3-9]\d{9}(?!\d)"""),
        style = RedactionStyle.TOKEN,
        needsDigit = true,
    )

    // ---------- 统计 ----------

    @Test
    fun statsCountEachKind() {
        val r = PiiRedactor()
        r.redact("手机 13812345678 和 13900000000")
        r.redact("邮箱 a@b.com")

        assertEquals(2, r.stats.hitCounts[SensitiveKind.PHONE])
        assertEquals(1, r.stats.hitCounts[SensitiveKind.EMAIL])
        assertEquals(3, r.stats.total)
    }

    @Test
    fun textWithoutHitsLeavesStatsEmpty() {
        val r = PiiRedactor()
        r.redact("什么都没有")
        assertEquals(0, r.stats.total)
        assertEquals("没有命中任何敏感信息", r.stats.toString())
    }
}

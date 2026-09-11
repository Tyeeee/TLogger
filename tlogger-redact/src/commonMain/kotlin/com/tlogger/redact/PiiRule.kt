package com.tlogger.redact

/** 敏感信息的种类。 */
public enum class SensitiveKind {
    /** 手机号。 */
    PHONE,

    /** 身份证号。 */
    ID_CARD,

    /** 银行卡号（会做校验位检查，避免把随便一串数字当成卡号）。 */
    BANK_CARD,

    /** 邮箱。 */
    EMAIL,

    /** IP 地址。 */
    IP,

    /** 登录凭证（JWT）。 */
    JWT,
}

/** 打码的方式。 */
public enum class RedactionStyle {
    /** 全部涂掉，例如 `***********`。 */
    MASK_ALL,

    /** 保留头尾，例如手机号 `138****8888`。既能对上号，又不暴露完整内容。 */
    KEEP_EDGES,

    /**
     * 换成一个代号，例如 `#PHONE-3f9a21`。
     *
     * 好处是**同一个值每次都换成同一个代号**——既能看出"这两条日志说的是同一个手机号"，
     * 又看不出号码本身。排障时比全涂黑好用得多。
     */
    TOKEN,
}

/**
 * 一条打码规则。
 *
 * @param kind 这条规则管的是哪类敏感信息。
 * @param pattern 匹配用的表达式。
 * @param style 打码方式。
 * @param keepHead 保留开头几个字符（仅 [RedactionStyle.KEEP_EDGES] 有效）。
 * @param keepTail 保留结尾几个字符（仅 [RedactionStyle.KEEP_EDGES] 有效）。
 * @param validate 额外的校验（比如银行卡号要过校验位）。默认都算通过。
 * @param needsDigit 便宜的预筛条件：正文里必须有数字才可能命中。用来避免无意义的扫描。
 * @param needsAt 便宜的预筛条件：正文里必须有 `@` 才可能命中。
 */
public class PiiRule(
    public val kind: SensitiveKind,
    internal val pattern: Regex,
    internal val style: RedactionStyle = RedactionStyle.KEEP_EDGES,
    internal val keepHead: Int = 3,
    internal val keepTail: Int = 4,
    internal val validate: (String) -> Boolean = { true },
    internal val needsDigit: Boolean = false,
    internal val needsAt: Boolean = false,
)

/** 现成的规则集。 */
public object PiiRules {

    /**
     * 默认规则。**顺序有讲究**：长得像的规则要排在前面。
     *
     * 比如身份证 18 位里可能含一段 11 位的手机号，所以身份证必须排在手机号前面，
     * 否则手机号规则会先把中间那段挖掉。
     */
    public fun defaults(): List<PiiRule> = listOf(
        jwt(),
        email(),
        idCard(),
        bankCard(),
        ip(),
        phone(),
    )

    /** 登录凭证，形如 `xxx.yyy.zzz` 的三段式。 */
    public fun jwt(style: RedactionStyle = RedactionStyle.MASK_ALL): PiiRule = PiiRule(
        kind = SensitiveKind.JWT,
        pattern = Regex("""eyJ[A-Za-z0-9_\-]{5,}\.[A-Za-z0-9_\-]{5,}\.[A-Za-z0-9_\-]{5,}"""),
        style = style,
    )

    /** 邮箱：开头保留 2 个字符，其余全涂掉。 */
    public fun email(style: RedactionStyle = RedactionStyle.KEEP_EDGES): PiiRule = PiiRule(
        kind = SensitiveKind.EMAIL,
        pattern = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}"""),
        style = style,
        keepHead = 2,
        keepTail = 0,
        needsAt = true,
    )

    /** 身份证：18 位，最后一位可能是 X。 */
    public fun idCard(style: RedactionStyle = RedactionStyle.KEEP_EDGES): PiiRule = PiiRule(
        kind = SensitiveKind.ID_CARD,
        pattern = Regex("""(?<!\d)[1-9]\d{16}[\dXx](?!\d)"""),
        style = style,
        keepHead = 4,
        keepTail = 4,
        needsDigit = true,
    )

    /** 银行卡：16 到 19 位，而且必须过校验位——不然随便一串数字都会被当成卡号。 */
    public fun bankCard(style: RedactionStyle = RedactionStyle.KEEP_EDGES): PiiRule = PiiRule(
        kind = SensitiveKind.BANK_CARD,
        pattern = Regex("""(?<!\d)\d{16,19}(?!\d)"""),
        style = style,
        keepHead = 4,
        keepTail = 4,
        validate = { luhnOk(it) },
        needsDigit = true,
    )

    /** IP 地址。 */
    public fun ip(style: RedactionStyle = RedactionStyle.MASK_ALL): PiiRule = PiiRule(
        kind = SensitiveKind.IP,
        pattern = Regex("""(?<![\d.])(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})(?![\d.])"""),
        style = style,
        validate = { value ->
            value.split('.').all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
        },
        needsDigit = true,
    )

    /** 中国大陆手机号。 */
    public fun phone(style: RedactionStyle = RedactionStyle.KEEP_EDGES): PiiRule = PiiRule(
        kind = SensitiveKind.PHONE,
        pattern = Regex("""(?<!\d)1[3-9]\d{9}(?!\d)"""),
        style = style,
        keepHead = 3,
        keepTail = 4,
        needsDigit = true,
    )
}

/** 银行卡校验位算法（Luhn）。用来把"随便一串数字"和"真的是卡号"分开。 */
internal fun luhnOk(number: String): Boolean {
    if (number.length < 2) return false
    var sum = 0
    var double = false
    for (i in number.indices.reversed()) {
        var digit = number[i] - '0'
        if (digit !in 0..9) return false
        if (double) {
            digit *= 2
            if (digit > 9) digit -= 9
        }
        sum += digit
        double = !double
    }
    return sum % 10 == 0
}

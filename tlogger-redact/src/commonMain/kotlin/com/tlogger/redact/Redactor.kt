package com.tlogger.redact

/** 把一段文字里的敏感信息换掉。 */
public interface Redactor {
    /** 返回处理过的文字；没有命中就原样返回。 */
    public fun redact(text: String): String
}

/**
 * 换代号用的函数。
 *
 * **注意默认实现不够安全**：默认只是做个简单散列，够用来"让同一个值每次都变成同一个代号"，
 * 但**不是密码学安全的**，也不该指望它挡住有心的攻击者。
 *
 * 要真安全（比如做合规），自己传一个实现进来——用平台自带的加密库算 HMAC，密钥存在
 * 安卓的密钥库 / 苹果的钥匙串里。
 */
public fun interface TokenGenerator {
    /** 把 [value] 换成代号。同一个 [kind] 下同一个 [value] 必须得到同一个结果。 */
    public fun tokenOf(kind: SensitiveKind, value: String): String
}

/** 现成的代号生成方式。 */
public object TokenGenerators {

    /**
     * 简单散列。**仅供开发期和"能对上号"用，不是安全实现。**
     *
     * 产出的代号形如 `#PHONE-3f9a21`。
     */
    public val simple: TokenGenerator = TokenGenerator { kind, value ->
        val hash = value.fold(2166136261u) { acc, c -> (acc xor c.code.toUInt()) * 16777619u }
        "#${kind.name}-${hash.toString(16).padStart(8, '0').take(8)}"
    }
}

/** 打码的统计结果——用来回答"它到底在干什么"。 */
public class RedactionStats(
    /** 每种敏感信息各命中多少次。 */
    public val hitCounts: Map<SensitiveKind, Int>,
) {
    /** 一共命中多少次。 */
    public val total: Int get() = hitCounts.values.sum()

    override fun toString(): String =
        if (hitCounts.isEmpty()) "没有命中任何敏感信息" else hitCounts.entries.joinToString("，") { "${it.key}×${it.value}" }
}

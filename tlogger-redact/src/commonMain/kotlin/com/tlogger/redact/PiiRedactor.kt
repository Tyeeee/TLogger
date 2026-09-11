package com.tlogger.redact

/**
 * 按规则把文字里的敏感信息换掉。
 *
 * ## 做法
 *
 * 1. **便宜的预筛**：正文里连数字都没有，就不必去跑那些数字类的规则；
 * 2. **按顺序扫**：规则是从严到松排的（比如身份证排在手机号前面），已经被吃掉的区间不再处理；
 * 3. **一次成文**：先记下所有要替换的位置，最后一次性拼出来，不做反复替换。
 *
 * ## 能力边界（必须说清楚）
 *
 * - 只认规则长得出来的东西。**被编码过的认不出来**——比如把手机号做 Base64、URL 编码、
 *   或者自己拆成两半再拼起来，这里都挡不住。
 * - 它是**降低风险的手段，不等于合规**。日志内容是否合规，责任在用这个库的人身上。
 */
public class PiiRedactor(
    private val rules: List<PiiRule> = PiiRules.defaults(),
    private val tokenGenerator: TokenGenerator = TokenGenerators.simple,
) : Redactor {

    private val hits: MutableMap<SensitiveKind, Int> = LinkedHashMap()

    /** 到目前为止的命中情况。可以随时读出来做审计。 */
    public val stats: RedactionStats
        get() = RedactionStats(hits.toMap())

    public override fun redact(text: String): String {
        if (text.isEmpty() || rules.isEmpty()) return text

        // 便宜的预筛：先看正文里有没有数字、有没有 @，省掉没必要的扫描
        var hasDigit = false
        var hasAt = false
        for (c in text) {
            if (c.isDigit()) hasDigit = true
            if (c == '@') hasAt = true
            if (hasDigit && hasAt) break
        }

        val used = BooleanArray(text.length)
        val edits = ArrayList<Edit>()

        for (rule in rules) {
            if (rule.needsDigit && !hasDigit) continue
            if (rule.needsAt && !hasAt) continue

            for (match in rule.pattern.findAll(text)) {
                val start = match.range.first
                val end = match.range.last + 1
                // 已经被前面的规则吃掉的区间不再处理（避免身份证里那段数字又被当成手机号）
                if (isUsed(used, start, end)) continue
                if (!rule.validate(match.value)) continue

                markUsed(used, start, end)
                edits.add(Edit(start, end, replacementFor(rule, match.value)))
                hits[rule.kind] = (hits[rule.kind] ?: 0) + 1
            }
        }

        if (edits.isEmpty()) return text

        edits.sortBy { it.start }
        val sb = StringBuilder(text.length)
        var cursor = 0
        for (edit in edits) {
            sb.append(text, cursor, edit.start)
            sb.append(edit.replacement)
            cursor = edit.end
        }
        sb.append(text, cursor, text.length)
        return sb.toString()
    }

    private fun replacementFor(rule: PiiRule, value: String): String = when (rule.style) {
        RedactionStyle.MASK_ALL -> "*".repeat(value.length)
        RedactionStyle.KEEP_EDGES -> keepEdges(value, rule.keepHead, rule.keepTail)
        RedactionStyle.TOKEN -> tokenGenerator.tokenOf(rule.kind, value)
    }

    private fun keepEdges(value: String, head: Int, tail: Int): String {
        if (value.length <= head + tail) return "*".repeat(value.length)
        return value.take(head) + "*".repeat(value.length - head - tail) + value.takeLast(tail)
    }

    private fun isUsed(used: BooleanArray, start: Int, end: Int): Boolean {
        var i = start
        while (i < end) {
            if (used[i]) return true
            i++
        }
        return false
    }

    private fun markUsed(used: BooleanArray, start: Int, end: Int) {
        var i = start
        while (i < end) {
            used[i] = true
            i++
        }
    }

    private class Edit(val start: Int, val end: Int, val replacement: String)
}

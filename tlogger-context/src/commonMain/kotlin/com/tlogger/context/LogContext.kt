package com.tlogger.context

/**
 * 一次业务操作要随身带的信息。
 *
 * 为什么要它：一次下单可能从界面 → 业务 → 支付 → 库存各打几条日志，散在四个模块里。
 * 没有这个号，你只能按时间去猜哪几条是一伙的；有了它，一次过滤就能把整条链路拉出来。
 *
 * **用法上只有一条要求**：在业务入口标记一次，中间的代码一行都不用改。
 */
public data class LogContext(
    /**
     * 链路号：**一次业务操作**一个。
     *
     * 注意它**不该进标签**——每条日志的链路号都不一样，放进标签会让标签列表膨胀成千上万个，
     * 过滤器反而废了。它只在**正文前缀**里出现，用关键字就能过滤。
     */
    public val traceId: String? = null,

    /** 当前页面/界面名。排查"这个日志是哪个页面打的"时最有用。 */
    public val page: String? = null,

    /** 用户标识。**传进来之前请先打码**——这里不做打码。 */
    public val userId: String? = null,
) {
    /** 把 [other] 叠在自己上面：自己这边有值的字段保持不变，空着的用 [other] 补。 */
    public fun mergedWith(other: LogContext?): LogContext {
        if (other == null) return this
        return LogContext(
            traceId = traceId ?: other.traceId,
            page = page ?: other.page,
            userId = userId ?: other.userId,
        )
    }
}

/**
 * 一次 App 运行的会话信息。整场运行只有一份，放在出口上，不进每条日志。
 */
public data class SessionInfo(
    /** 会话号：这次启动一个。别人把日志截图发给你，靠它能认出"是哪次运行"。 */
    public val sessionId: String,
    /** App 版本号。 */
    public val appVersion: String? = null,
    /** 账号标识（已打码的）。 */
    public val userId: String? = null,
) {
    public companion object {
        /** 没有会话信息时的空壳。 */
        public val NONE: SessionInfo = SessionInfo(sessionId = "")

        /**
         * 开一场新会话：生成一个短会话号。
         *
         * 号很短（4 个字符）是刻意的——它要出现在每条日志的前缀里，太长会把日志挤爆。
         */
        public fun new(appVersion: String? = null, userId: String? = null): SessionInfo =
            SessionInfo(
                sessionId = shortId(),
                appVersion = appVersion,
                userId = userId,
            )

        private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"

        internal fun shortId(): String = buildString {
            repeat(4) { append(ALPHABET[kotlin.random.Random.nextInt(ALPHABET.length)]) }
        }
    }
}

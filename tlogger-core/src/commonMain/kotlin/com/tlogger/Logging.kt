package com.tlogger

/**
 * 一次日志系统的配置。
 *
 * 用 [Builder] 构造，构造完成后不可变，可以安全地被多个 [Logging] 复用。
 *
 * ## 两级级别开关（这是组件化架构的刚需）
 *
 * 级别怎么定，按优先级从高到低：
 *
 * 1. **[overrideLevel]（架构层统一覆盖）**——发版前一句话把所有来源调到 INFO；
 * 2. **[sourceLevel]（每个来源自己声明的默认级别）**——网络想看 VERBOSE，埋点想安静点；
 * 3. **[defaultLevel]（全局默认）**。
 *
 * 为什么要两级：Timber 那类库是**全局一个开关**，谁后设置谁生效，在组件化架构里必然打架。
 */
public class LoggingConfig private constructor(
    internal val sinks: List<LogSink>,
    internal val tagRecipe: TagRecipe,
    internal val defaultLevel: LogLevel,
    internal val sourceLevels: Map<String, LogLevel>,
    internal val overrideLevel: LogLevel?,
    internal val maxTagLength: Int,
    internal val sourceSuffix: String,
) {

    /** 按上面的优先级算出某个来源此刻实际生效的门槛。 */
    internal fun resolvedLevel(source: String): LogLevel =
        overrideLevel ?: sourceLevels[source] ?: defaultLevel

    /** 配置构造器。 */
    public class Builder {
        private val sinks: MutableList<LogSink> = mutableListOf()
        private var tagRecipe: TagRecipe = TagRecipes.sourceAndTag
        private var defaultLevel: LogLevel = LogLevel.DEBUG
        private var sourceLevels: MutableMap<String, LogLevel> = mutableMapOf()
        private var overrideLevel: LogLevel? = null
        private var maxTagLength: Int = DEFAULT_MAX_TAG_LENGTH
        private var sourceSuffix: String = ""

        /**
         * 加一个出口。可以加多个，每条日志会依次送给全部出口。
         *
         * 一个出口都不加时，日志会被丢弃——**这是有意的**：没配置就不采集。
         */
        public fun sink(sink: LogSink): Builder = apply { sinks.add(sink) }

        /** 设置标签配方，默认 [TagRecipes.sourceAndTag]。 */
        public fun tagRecipe(recipe: TagRecipe): Builder = apply { this.tagRecipe = recipe }

        /** 设置全局默认级别，默认 [LogLevel.DEBUG]。 */
        public fun defaultLevel(level: LogLevel): Builder = apply { this.defaultLevel = level }

        /** 声明某个来源自己的默认级别（两级开关里的第一级）。 */
        public fun sourceLevel(source: String, level: LogLevel): Builder = apply {
            require(source.isNotBlank()) { "来源名不能为空" }
            sourceLevels[source] = level
        }

        /** 设置架构层覆盖级别（两级开关里的最高优先级）。传 `null` 表示取消覆盖。 */
        public fun overrideLevel(level: LogLevel?): Builder = apply { this.overrideLevel = level }

        /**
         * 标签长度上限，默认 [DEFAULT_MAX_TAG_LENGTH]。
         *
         * 超了会被截断，**并且计数上报**（见 [LoggingStats.tagTruncationCount]）——不静默截断。
         */
        public fun maxTagLength(length: Int): Builder = apply {
            require(length > 0) { "标签长度上限必须大于 0" }
            this.maxTagLength = length
        }

        /**
         * 给**所有**来源名自动加一个后缀，用来区分进程。
         *
         * 为什么需要它：多进程应用里，同一个模块在不同进程里往往用同一个来源名（比如主进程和推送进程
         * 都叫 `Net`）。实测结果是——这种时候日志里只有一种标签，两个进程的日志完全混在一起，
         * **只能靠"进程号"那一列认**。
         *
         * 安卓上不用自己拼：用 `AndroidLogging.install(context)`，它会自动把副进程的后缀填成
         * `@进程名`，主进程不加后缀。
         *
         * 注意：**声明级别时仍然用原始来源名**（`sourceLevel("Net", ...)`），后缀只影响显示出来的名字。
         */
        public fun sourceSuffix(suffix: String): Builder = apply { this.sourceSuffix = suffix }

        /** 构造配置。 */
        public fun build(): LoggingConfig = LoggingConfig(
            sinks = sinks.toList(),
            tagRecipe = tagRecipe,
            defaultLevel = defaultLevel,
            sourceLevels = sourceLevels.toMap(),
            overrideLevel = overrideLevel,
            maxTagLength = maxTagLength,
            sourceSuffix = sourceSuffix,
        )
    }

    public companion object {
        /** 标签长度上限的默认值：32。 */
        public const val DEFAULT_MAX_TAG_LENGTH: Int = 32

        /** 开始构造配置。 */
        public fun builder(): Builder = Builder()
    }
}

/**
 * 运行期的统计。用来回答"它到底在干什么"，也是「错误必须可感知」这条原则的落点。
 *
 * 注意：这里的计数是**尽力而为**的（没有加锁），用于观察趋势足够，不要拿去做严格的账。
 */
public class LoggingStats(
    /** 因为超长而被截断的标签次数。持续增长说明标签配方需要调整。 */
    public val tagTruncationCount: Int,
    /** 已经写出的日志条数。 */
    public val writtenCount: Int,
)

/**
 * 一次安装好的日志系统。
 *
 * **它不是全局单例**：你可以直接 `Logging(config)` 建多个互不干扰的实例（测试里尤其有用）。
 * `TLogger` 那个门面只是"查找当前安装的那一个"的便捷入口。
 *
 * 线程安全说明：第一片实现没有加锁，[stats] 的计数可能少算；正式用它之前要补上（记录在案）。
 */
public class Logging(public val config: LoggingConfig) {

    private var tagTruncationCount: Int = 0
    private var writtenCount: Int = 0

    /** 取当前统计。 */
    public val stats: LoggingStats
        get() = LoggingStats(tagTruncationCount, writtenCount)

    /** 为某个来源要一个日志器。同一个来源重复调用会得到等价的新对象，不共享状态。 */
    public fun logger(source: String): Logger {
        require(source.isNotBlank()) { "来源名不能为空" }
        return Logger(source) { this }
    }

    /** 这个来源在这个级别下会不会输出。**先问再拼字符串**，这是惰性求值的前提。 */
    public fun isEnabled(level: LogLevel, source: String): Boolean =
        level.isAtLeast(config.resolvedLevel(source))

    internal fun write(
        level: LogLevel,
        source: String,
        explicitTag: String?,
        message: String,
        throwable: Throwable?,
    ) {
        // 级别判定用**原始来源名**（使用者是按原名声明级别的）
        if (!isEnabled(level, source)) return

        // 显示和记录用**带后缀的来源名**，这样多进程才分得清谁是谁
        val shownSource = if (config.sourceSuffix.isEmpty()) source else source + config.sourceSuffix

        var tag = sanitizeTag(config.tagRecipe.tagOf(shownSource, explicitTag))
        if (tag.length > config.maxTagLength) {
            tag = tag.substring(0, config.maxTagLength)
            tagTruncationCount++
        }

        val record = LogRecord(
            level = level,
            source = shownSource,
            tag = tag,
            message = message,
            throwable = throwable,
        )

        // 出口自己不许把异常抛出来影响业务线程。这里再兜一层。
        for (sink in config.sinks) {
            try {
                sink.write(record)
            } catch (_: Throwable) {
                // 出口失败不该打断业务，也不该打断后面的出口。
            }
        }
        writtenCount++
    }
}

/**
 * 把标签里的空白和控制字符换成下划线。
 *
 * 原因：实测发现标签里带空格（比如 `Net/带空格 的标签`）虽然能打出来，
 * 但会把日志窗口的标签列挤歪，看日志很难受。
 */
private fun sanitizeTag(tag: String): String {
    var needsFix = false
    for (c in tag) {
        if (c.isWhitespace() || c.isISOControl()) {
            needsFix = true
            break
        }
    }
    if (!needsFix) return tag

    val sb = StringBuilder(tag.length)
    for (c in tag) {
        sb.append(if (c.isWhitespace() || c.isISOControl()) '_' else c)
    }
    return sb.toString()
}

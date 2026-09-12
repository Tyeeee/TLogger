package com.tlogger.core
import kotlin.concurrent.Volatile

/**
 * 便捷门面：记住"当前安装的那套日志系统"。
 *
 * ## 它是不是全局单例？（不是，这里要说清楚）
 *
 * [Logging] 实例是可以随便建的，多个实例互不干扰；想完全绕开门面也可以：
 * ```
 * val logging = Logging(config)          // 自己持有，不经过这里
 * val log = logging.logger("Net")
 * ```
 * 门面只解决一件事：**让"在任意地方写一行日志"变得便宜**（不要为了打一条日志去传引用）。
 * 它保存的是一个**可以被显式替换的引用**，而不是一份谁都能改的全局配置——这两者不一样：
 * 前者是你自己决定装哪个，后者是两个库抢同一个开关。
 *
 * 未安装时所有日志调用都是空操作，**绝不崩**。
 *
 * ## 谁可以调 [install]
 *
 * **只有 App / 进程入口**（`Application.onCreate`、Startup 初始化之类）。库代码不要自己装——
 * 两个库都去装全局开关，就是上面说的那个坑。库代码要用日志，两条路：
 * - 用调用方传进来的 [Logging] 实例（最干净，测试也好写）；
 * - 或者直接 [TLogger.logger]（没装就什么都不做，不报错也不崩）。
 */
public object TLogger {

    @Volatile
    private var installation: Logging? = null

    /** 用配置装一套日志系统，返回这个实例；之后 [logger] 拿到的都归它管。 */
    public fun install(config: LoggingConfig): Logging = install(Logging(config))

    /** 直接装一个已经建好的实例（测试里常用）。 */
    public fun install(logging: Logging): Logging {
        installation = logging
        return logging
    }

    /** 卸掉当前安装的日志系统。之后日志调用会变成空操作，但**不会崩**。 */
    public fun uninstall(): Unit {
        installation = null
    }

    /** 当前安装的实例，没装就是 `null`。 */
    public fun currentOrNull(): Logging? = installation

    /**
     * 为某个来源要一个日志器。
     *
     * **注意**：现在没安装也能拿到日志器——它每次调用时才去查当前安装的实例，
     * 所以"先建日志器、后安装"也能正常工作。
     */
    public fun logger(source: String): Logger = Logger(source) { installation }
}

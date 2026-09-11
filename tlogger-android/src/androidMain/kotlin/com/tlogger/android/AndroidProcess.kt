package com.tlogger.android
import android.app.Application
import android.content.Context
import android.os.Build
import com.tlogger.core.LogLevel
import com.tlogger.core.Logging
import com.tlogger.core.LoggingConfig
import com.tlogger.core.TLogger
import java.io.File

/**
 * 读出当前进程的名字，并算出应该给来源名加什么后缀。
 *
 * ## 为什么需要它（这是实测算出来的问题）
 *
 * 多进程应用里，同一个模块在不同进程里往往用**同一个来源名**（主进程和推送进程都叫 `Net` 是最常见的写法）。
 * 实测结果：这种时候日志里只有一种标签，两个进程的日志完全混在一起，**只能靠"进程号"那一列认**——
 * 在日志窗口里按标签过滤时，两边会一起出来，根本分不开。
 *
 * 加了后缀之后就变成 `Net`（主进程）和 `Net@remote`（副进程），一眼能分开，也能按 `^Net@remote` 过滤。
 */
public object AndroidProcessTags {

    /**
     * 当前进程的完整名字。主进程就是包名，副进程形如 `com.example:remote`。
     *
     * 优先用系统接口；老系统上退回读 `/proc/self/cmdline`。
     */
    public fun processName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val fromApi = Application.getProcessName()
            if (!fromApi.isNullOrEmpty()) return fromApi
        }
        val fromProc = runCatching {
            File("/proc/self/cmdline").readText().substringBefore('\u0000').trim()
        }.getOrNull()
        if (!fromProc.isNullOrEmpty()) return fromProc
        return context.packageName
    }

    /**
     * 应该加到来源名后面的后缀：**主进程返回空串**，副进程返回 `@进程名后缀`。
     *
     * 例如副进程 `com.example:remote` 会返回 `@remote`。
     */
    public fun suffixFor(context: Context): String {
        val full = processName(context)
        if (full == context.packageName) return ""
        val short = full.substringAfterLast(':', full)
        return "@$short"
    }
}

/**
 * 安卓上的一行安装：装好出口、并且**自动处理多进程的来源名区分**。
 *
 * ```
 * // 在每个进程的入口都调用一次（主进程、副进程都要）
 * AndroidLogging.install(this)
 * ```
 *
 * 它做的事情就是 [AndroidLogSink] + [AndroidProcessTags.suffixFor] 的组合，省得每处都写一遍。
 */
public object AndroidLogging {

    /** 装好并返回实例；`defaultLevel` 默认 [LogLevel.DEBUG]。 */
    public fun install(context: Context, defaultLevel: LogLevel = LogLevel.DEBUG): Logging =
        TLogger.install(
            LoggingConfig.builder()
                .sink(AndroidLogSink())
                .defaultLevel(defaultLevel)
                .sourceSuffix(AndroidProcessTags.suffixFor(context))
                .build(),
        )
}

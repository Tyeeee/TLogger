package com.tlogger.app

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tlogger.android.AndroidLogging
import com.tlogger.core.TLogger

/**
 * 跑在**另一个进程**里的页面（清单里声明了 android:process=":remote"）。
 *
 * 它有两个用处：
 * 1. 验证两个进程各自打日志时互不干扰；
 * 2. 验证一个很常见的坑——**副进程忘了装日志系统，日志就静默消失了**。
 *
 * 界面是空白的，它只是个干活的入口。
 */
class RemoteActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val doInstall = intent?.getBooleanExtra("install", true) ?: true
        val count = intent?.getIntExtra("count", 200) ?: 200
        val source = intent?.getStringExtra("source") ?: "Remote"
        val pid = android.os.Process.myPid()

        Log.i(TAG, "REMOTE_BEGIN install=$doInstall count=$count source=$source pid=$pid")

        if (doInstall) {
            // 副进程同样一行装好；来源名会自动带上 @进程名 后缀
            AndroidLogging.install(this)
        }

        val log = TLogger.logger(source)
        repeat(count) { i ->
            log.i { "副进程第 $i 条" }
        }

        if (doInstall) {
            val written = TLogger.currentOrNull()?.stats?.writtenCount
            Log.i(TAG, "REMOTE_RESULT install=yes written=$written pid=$pid")
        } else {
            Log.i(TAG, "REMOTE_RESULT install=no written=0 pid=$pid 说明：没装日志系统，日志一条都没出去，但没崩")
        }
        Log.i(TAG, "REMOTE_END pid=$pid")

        // 干完活就退，不留一个空白页面
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2000)
    }

    private companion object {
        private const val TAG = "TLoggerSample"
    }
}

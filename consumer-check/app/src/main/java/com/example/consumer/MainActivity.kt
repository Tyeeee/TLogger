package com.example.consumer

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tlogger.android.AndroidLogSink
import com.tlogger.android.AndroidProcessTags
import com.tlogger.context.ContextSink
import com.tlogger.context.SessionInfo
import com.tlogger.context.withTrace
import com.tlogger.core.LogLevel
import com.tlogger.core.Logging
import com.tlogger.core.LoggingConfig
import com.tlogger.core.TLogger
import com.tlogger.redact.PiiRedactor
import com.tlogger.redact.RedactingSink
import com.tlogger.ring.RingBufferSink

/**
 * 这个工程跟 TLogger 的源码没有任何关系，依赖全部来自仓库坐标。
 *
 * 它只做一件事：证明发出去的包在别人的项目里真的能用。
 * 手机号那部分是故意写死的假号码，用来验证打码有没有生效。
 */
class MainActivity : Activity() {

    private val ring = RingBufferSink(capacity = 50)
    private val pii = PiiRedactor()
    private lateinit var screen: TextView
    private var logging: Logging? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        screen = TextView(this).apply {
            textSize = 11f
            setPadding(32, 32, 32, 32)
        }
        val scroller = ScrollView(this).apply {
            addView(screen, ViewGroup.LayoutParams(-1, -2))
        }

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        column.addView(button("1. 打几条普通日志") { plain() })
        column.addView(button("2. 打一条带手机号的") { redact() })
        column.addView(button("3. 一次下单（带链路）") { trace() })
        column.addView(button("4. 把内存里的现场捞出来") { drain() })
        column.addView(button("5. 一口气打 3000 条") { stress() })
        setContentView(column)

        // 不用手点，进去就自己跑一遍，这样从电脑上看日志就能核对
        install()
        plain()
        redact()
        trace()
        drain()
        stress()
        settle()
    }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

    private fun install() {
        val session = SessionInfo.new(appVersion = "1.0")
        logging = TLogger.install(
            LoggingConfig.builder()
                // 两个出口各自套自己的层。注意：层是挂在出口上的，
                // 所以"缓冲区里有没有链路号"取决于缓冲区那个出口有没有套上下文层。
                // 缓冲套在打码"里面"：否则捞出来的现场是原文，等于绕过打码漏隐私。
                .sink(ContextSink(RedactingSink(ring, pii), session = session))
                .sink(ContextSink(RedactingSink(AndroidLogSink(), pii), session = session))
                .defaultLevel(LogLevel.DEBUG)
                .sourceSuffix(AndroidProcessTags.suffixFor(this))
                .build(),
        )
    }

    /** 界面上显示一行，同时打进日志窗口，方便从电脑上核对。 */
    private fun emit(line: String) {
        Log.i("CONSUMER", line)
        runOnUiThread { screen.append("$line\n") }
    }

    private fun plain() {
        val log = TLogger.logger("Net")
        emit("— 普通日志：三个模块各打一条，标签应该分得开 —")
        log.i { "开始请求 /api/order" }
        log.d { "响应 200，耗时 118ms" }
        TLogger.logger("Order").i { "创建订单 20260911001" }
        TLogger.logger("Pay").e(RuntimeException("连接被重置")) { "支付失败，准备重试" }
        emit("CONSUMER_RESULT=plain tag=${log.source}")
    }

    private fun redact() {
        val log = TLogger.logger("User")
        emit("— 打码：下面这条带真号码，看日志里剩下什么 —")
        log.i { "手机号 13812345678 已注册，邮箱 zhangsan@example.com 验证通过" }
        emit("CONSUMER_RESULT=redact hits=${pii.stats.total}")
    }

    private fun trace() {
        emit("— 链路：三个模块都不传参数，靠入口标记一次 —")
        withTrace("c0n5") {
            TLogger.logger("Screen").i { "用户点了立即购买" }
            TLogger.logger("Order").i { "创建订单 20260911001" }
            TLogger.logger("Pay").i { "发起支付 199.00" }
        }
        TLogger.logger("Order").i { "这条在链路外面" }

        // 真的去数一遍：带链路号的应该是 3 条
        val withTraceId = ring.snapshot().count { it.message.contains("[t=c0n5") }
        emit("CONSUMER_RESULT=trace tagged=$withTraceId expect=3")
    }

    private fun drain() {
        emit("— 捞现场：缓冲区里存的东西应该是打过码的 —")
        val dump = ring.dump()
        val leaked = dump.contains("13812345678")
        val masked = dump.contains("138****5678")
        val traced = dump.contains("[t=c0n5")
        dump.split("\n").take(6).forEach { emit("    $it") }
        emit("CONSUMER_RESULT=drain size=${ring.size} leakedRawPii=$leaked masked=$masked traced=$traced")
    }

    private fun stress() {
        val log = TLogger.logger("Stress")
        val count = 3000
        val start = System.currentTimeMillis()
        repeat(count) { i -> log.i { "压力测试第 $i 条" } }
        val cost = System.currentTimeMillis() - start
        emit("CONSUMER_RESULT=stress sent=$count written=${logging?.stats?.writtenCount} elapsedMs=$cost")
    }

    /** 故意把输入框里的内容直接打进日志——用来验证编译期隐私检查能不能吃仓库里的包。 */
    @Suppress("unused")
    private fun lintBait() {
        val input = EditText(this)
        input.setText("13800001111")
        val text = input.text.toString()
        Log.i("CONSUMER", text)
    }

    private fun settle() {
        emit("CONSUMER_RESULT=allDone")
    }
}

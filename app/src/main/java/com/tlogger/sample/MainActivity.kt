package com.tlogger.sample

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tlogger.android.AndroidLogging

/**
 * 示例应用：每个按钮是一个"测试节点"，进去就自动跑一遍并把结果写出来。
 *
 * 也可以用命令直接进某个页面（方便自动核对）：
 * `adb shell am start -n com.tlogger.sample/.MainActivity --es scenario network`
 */
class MainActivity : Activity() {

    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installDefault()
        Scenarios.attach(this)
        setContentView(buildUi())

        val scenario = intent?.getStringExtra("scenario")
        if (scenario != null) {
            // 自动跑某个场景（给自动化核对用）
            append("自动运行场景：$scenario")
            Handler(Looper.getMainLooper()).postDelayed({ runScenario(scenario) }, 400)
        } else {
            append("选一个场景点下去，屏幕上和日志里都会留下结果。")
        }
    }

    private fun installDefault() {
        // 一行安装：自动带上进程后缀，副进程的来源名会变成 Net@remote 这种
        AndroidLogging.install(this)
    }

    private fun buildUi(): ScrollView {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        column.addView(TextView(this).apply {
            text = "TLogger 示例 · 10 个测试节点"
            textSize = 20f
        })

        column.addView(Button(this).apply {
            text = "▶ 按顺序跑全部"
            setOnClickListener {
                output.text = ""
                runAll(Scenarios.all.map { it.first })
            }
        })

        for ((id, title) in Scenarios.all) {
            column.addView(Button(this).apply {
                text = title
                setOnClickListener {
                    output.text = ""
                    runScenario(id)
                }
            })
        }

        column.addView(TextView(this).apply {
            text = "结果"
            textSize = 16f
            setPadding(0, 24, 0, 8)
        })

        output = TextView(this).apply {
            textSize = 12f
        }
        column.addView(output)

        return ScrollView(this).apply { addView(column) }
    }

    private fun runScenario(id: String) {
        Log.i(TAG, "SCENARIO_BEGIN $id")
        try {
            Scenarios.run(id) { line -> append(line) }
        } catch (t: Throwable) {
            // 场景本身炸了也要报出来——这本身就是最重要的结果
            append("场景内部出错：${t}")
            Log.e(TAG, "SCENARIO_CRASH $id", t)
        }
        Log.i(TAG, "SCENARIO_END $id")
    }

    private fun runAll(ids: List<String>) {
        ids.forEachIndexed { index, id ->
            append("===== 开始 ${index + 1}/${ids.size}：$id =====")
            runScenario(id)
        }
        append("===== 全部跑完 =====")
    }

    private fun append(line: String) {
        Log.i(TAG, line)
        runOnUiThread {
            output.append(line + "\n")
        }
    }

    private companion object {
        private const val TAG = "TLoggerSample"
    }
}

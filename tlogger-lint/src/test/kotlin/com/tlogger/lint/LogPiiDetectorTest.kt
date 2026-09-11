package com.tlogger.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

/**
 * 检查逻辑的测试。
 *
 * 安卓的那些类在本机的测试环境里没有真身（跑测试不需要连真机，也没有完整 SDK），
 * 所以这里**自己写最小的替身**——只保留检查要用到的类名和方法名。这是官方测试底座的标准用法。
 */
class LogPiiDetectorTest {

    private val editTextStub = kotlin(
        """
        package android.widget
        open class TextView {
            open fun getText(): CharSequence = ""
        }
        class EditText : TextView()
        """,
    ).to("src/android/widget/EditText.kt")

    private val logStub = kotlin(
        """
        package android.util
        object Log {
            fun v(tag: String, msg: String): Int = 0
            fun d(tag: String, msg: String): Int = 0
            fun i(tag: String, msg: String): Int = 0
            fun w(tag: String, msg: String): Int = 0
            fun e(tag: String, msg: String): Int = 0
        }
        """,
    ).to("src/android/util/Log.kt")

    private val locationStub = kotlin(
        """
        package android.location
        class Location {
            fun getLatitude(): Double = 0.0
            fun getLongitude(): Double = 0.0
        }
        """,
    ).to("src/android/location/Location.kt")

    private val loggerStub = kotlin(
        """
        package com.tlogger.core
        class Logger {
            fun d(message: () -> String) {}
            fun i(message: () -> String) {}
        }
        """,
    ).to("src/com/tlogger/core/Logger.kt")

    private fun check(source: String) =
        lint()
            .issues(LogPiiDetector.ISSUE)
            .files(editTextStub, logStub, locationStub, loggerStub, kotlin(source).to("src/test/Subject.kt"))
            .allowMissingSdk()
            .run()

    // ---------------------------------------------------------------- 该报的

    @Test
    fun editingTextLoggedDirectlyIsReported() {
        check(
            """
            package test
            import android.util.Log
            import android.widget.EditText
            class Subject {
                fun submit(edit: EditText) {
                    Log.d("Order", edit.text.toString())
                }
            }
            """,
        ).expectWarningCount(1)
            .expectContains("TLoggerPiiInLog")
    }

    @Test
    fun goingThroughLocalVariablesIsStillReported() {
        check(
            """
            package test
            import android.util.Log
            import android.widget.EditText
            class Subject {
                fun submit(edit: EditText) {
                    val raw = edit.text
                    val trimmed = "${'$'}raw".trim()
                    Log.i("Order", trimmed)
                }
            }
            """,
        ).expectWarningCount(1)
    }

    @Test
    fun reassignmentIsTracked() {
        check(
            """
            package test
            import android.util.Log
            import android.widget.EditText
            class Subject {
                fun submit(edit: EditText) {
                    var value = "还没填"
                    value = edit.text.toString()
                    Log.w("Order", value)
                }
            }
            """,
        ).expectWarningCount(1)
    }

    @Test
    fun ourLazyApiIsReportedToo() {
        check(
            """
            package test
            import android.widget.EditText
            import com.tlogger.core.Logger
            class Subject {
                fun submit(edit: EditText, log: Logger) {
                    val raw = edit.text
                    log.d { "用户输入了 ${'$'}raw" }
                }
            }
            """,
        ).expectWarningCount(1)
    }

    @Test
    fun locationCoordinatesAreReported() {
        check(
            """
            package test
            import android.location.Location
            import android.util.Log
            class Subject {
                fun report(location: Location) {
                    Log.d("Map", "纬度 " + location.latitude)
                }
            }
            """,
        ).expectWarningCount(1)
    }

    // ---------------------------------------------------------------- 不该报的

    @Test
    fun loggingPlainTextIsClean() {
        check(
            """
            package test
            import android.util.Log
            class Subject {
                fun done(orderId: String) {
                    Log.d("Order", "订单创建成功")
                }
            }
            """,
        ).expectClean()
    }

    @Test
    fun loggingAPlainNumberIsClean() {
        check(
            """
            package test
            import android.util.Log
            class Subject {
                fun done(count: Int) {
                    Log.i("Cart", "购物车里有 ${'$'}count 件商品")
                }
            }
            """,
        ).expectClean()
    }

    @Test
    fun readingATextViewLabelIsClean() {
        // TextView 是显示用的，不是用户输入——这条是刻意不报的，免得误报把人逼到关掉检查
        check(
            """
            package test
            import android.util.Log
            import android.widget.TextView
            class Subject {
                fun dump(label: TextView) {
                    Log.d("Ui", label.text.toString())
                }
            }
            """,
        ).expectClean()
    }

    @Test
    fun suppressionWorks() {
        check(
            """
            package test
            import android.util.Log
            import android.widget.EditText
            class Subject {
                @Suppress("TLoggerPiiInLog")
                fun submit(edit: EditText) {
                    Log.d("Order", edit.text.toString())
                }
            }
            """,
        ).expectClean()
    }
}

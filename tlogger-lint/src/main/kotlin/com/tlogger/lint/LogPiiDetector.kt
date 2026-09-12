package com.tlogger.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * 检查"把敏感信息写进日志"（issue id = `TLoggerPiiInLog`）。
 *
 * ## 为什么要有它（跟打码不重复）
 *
 * 打码是**运行期**换掉日志正文——那时候值已经拼好了。实测数据显示，**六成以上的泄漏是
 * 数据转了几手之后才被写进日志的**，运行期只看到一个字符串，不知道它原来是手机号。
 * **只有写代码的时候检查，才能在拼进去之前拦住。**
 *
 * ## 怎么判（在方法内部跟一下）
 *
 * 1. 认出**敏感数据从哪来**：输入框里的文字、设备号、位置、剪贴板、账号列表等；
 * 2. 在**同一个方法内部**跟着赋值走：`val a = edit.text` → `val b = "$a"` → 都算敏感；
 * 3. 只要它进了日志调用（`Log` / `Timber` / 本库的 `log.d { }`），就报警。
 *
 * 为什么只在一个方法内部跟：实测数据说泄漏路径通常只有几句话、而且多半在同一个方法里，
 * 所以做全程序的复杂分析没必要，代价却大得多。
 *
 * ## 两个实测踩出来的坑（都写了用例防回归）
 *
 * 1. **Kotlin 里 `edit.text` 不是"方法调用"节点**，而是"带访问器的引用"（属性访问）。
 *    只处理方法调用会整体漏报——我第一版就是这么写的，测出来一条都没报。
 * 2. **输入框的 `getText` 声明在父类 `TextView` 上**。只按"类名 + 方法名"匹配会漏，
 *    必须看**接收者的实际类型**是不是 EditText。
 *
 * ## 故意保守的地方（宁可漏报，不制造误报）
 *
 * 误报多了，人就会把检查关掉，那等于没有。所以：
 * - 只认明确列出来的几个敏感来源，**不靠变量名猜**（叫 `phone` 的变量不一定真是手机号）；
 * - `TextView.getText()` 不报——那是显示用的，不是用户输入；
 * - 认不出来的情况一律放过。
 *
 * 需要豁免时：`@Suppress("TLoggerPiiInLog")`。
 */
class LogPiiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                checkMethod(context, node)
            }
        }

    private fun checkMethod(context: JavaContext, method: UMethod) {
        val tainted = collectTaintedNames(context, method)
        val suspects = collectSuspectNames(method)
        if (tainted.isEmpty() && suspects.isEmpty() &&
            !containsSensitiveAccess(context, method) && !containsPiiLiteral(method)
        ) {
            return
        }

        method.accept(
            object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val resolved = node.resolve() ?: return false
                    val owner = ownerName(resolved.containingClass?.qualifiedName)
                    if (owner == null || !isLogCall(owner, resolved.name)) return false

                    // 一、确证的：值来自敏感 API，或者消息里直接写着明文敏感信息
                    val certain = node.valueArguments.firstOrNull { arg ->
                        isTainted(context, arg, tainted) || piiLiteralIn(arg) != null
                    }
                    if (certain != null) {
                        report(context, node, certain, ISSUE)
                        return false
                    }

                    // 二、启发式的：值来自"名字就像隐私"的变量。单独一条规则，可以只关这一条。
                    val named = node.valueArguments.firstOrNull { referencesAny(it, suspects) }
                    if (named != null) report(context, node, named, NAME_ISSUE)
                    return false
                }
            },
        )
    }

    private fun report(context: JavaContext, node: UElement, guilty: UElement, issue: Issue) {
        val advice = if (issue === NAME_ISSUE) {
            "这条是**按变量名**判断的（启发式）。没风险的话加 `@Suppress(\"TLoggerPiiNameInLog\")` 豁免；\n" +
                "整条规则嫌吵，可以在 build.gradle.kts 的 `lint { disable += \"TLoggerPiiNameInLog\" }` 里关掉，\n" +
                "另一条（确证的）不受影响。"
        } else {
            "确认没问题可以加 `@Suppress(\"TLoggerPiiInLog\")` 逐处豁免。"
        }
        context.report(
            issue,
            node,
            context.getLocation(guilty),
            "这段内容可能包含用户隐私，不该原样写进日志。\n" +
                "日志会被导出、上传、截图，里面的手机号/身份证/位置等于明文。\n" +
                "建议：① 别把它的原值写进日志；② 需要排查时用打码换成代号" +
                "（RedactingSink + PiiRedactor，同一个值每次同一个代号，能对上号又不暴露）。\n" +
                advice,
        )
    }

    // ---------------------------------------------------------------- 找敏感来源

    private fun collectTaintedNames(context: JavaContext, method: UMethod): Set<String> {
        val declarations = mutableListOf<Pair<String, UExpression?>>()
        val assignments = mutableListOf<Pair<String, UExpression>>()

        method.accept(
            object : AbstractUastVisitor() {
                override fun visitVariable(node: UVariable): Boolean {
                    val name = node.name
                    if (name != null) {
                        // 函数参数没有初始值，会被下面的 null 判断跳过
                        declarations.add(name to node.uastInitializer)
                    }
                    return false
                }

                override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                    if (node.operator == UastBinaryOperator.ASSIGN) {
                        val left = node.leftOperand
                        if (left is USimpleNameReferenceExpression) {
                            assignments.add(left.identifier to node.rightOperand)
                        }
                    }
                    return false
                }
            },
        )

        // 反复扫到不动为止：a 敏感 → b = a 也敏感 → c = b 也敏感
        val tainted = mutableSetOf<String>()
        var changed = true
        while (changed) {
            changed = false
            for ((name, initializer) in declarations) {
                if (name in tainted || initializer == null) continue
                if (isTainted(context, initializer, tainted)) {
                    tainted.add(name)
                    changed = true
                }
            }
            for ((name, value) in assignments) {
                if (name in tainted) continue
                if (isTainted(context, value, tainted)) {
                    tainted.add(name)
                    changed = true
                }
            }
        }
        return tainted
    }

    /**
     * 名字看着就是隐私的变量（`phone`、`userId`、`deviceId`、`token` …），
     * 以及从它们转手出来的变量（`val id = userId` 里的 `id` 也算）。
     *
     * 这是**启发式**：叫 `phone` 的变量不一定真是手机号。所以它单独算一条规则（[NAME_ISSUE]），
     * 跟"确证"的那条分开——嫌吵可以只关这一条，另一条不受影响。
     *
     * 为什么必须加这一层：真正工程里写的是 `log.d { "手机号 $phone 提交成功" }`，
     * 这个 `phone` 只是个方法入参，没有任何"敏感 API"可跟——只做污点分析的话一条都不会报（实测反馈过）。
     */
    private fun collectSuspectNames(method: UMethod): Set<String> {
        val declarations = mutableListOf<Pair<String, UExpression?>>()
        val assignments = mutableListOf<Pair<String, UExpression>>()

        method.accept(
            object : AbstractUastVisitor() {
                override fun visitVariable(node: UVariable): Boolean {
                    val name = node.name
                    if (name != null) declarations.add(name to node.uastInitializer)
                    return false
                }

                override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                    if (node.operator == UastBinaryOperator.ASSIGN) {
                        val left = node.leftOperand
                        if (left is USimpleNameReferenceExpression) {
                            assignments.add(left.identifier to node.rightOperand)
                        }
                    }
                    return false
                }
            },
        )

        // 起点：名字本身就长得像隐私的（入参也算——入参没有初始值，靠的就是名字）
        val suspects = mutableSetOf<String>()
        declarations.mapNotNullTo(suspects) { (name, _) -> name.takeIf { looksLikePiiName(it) } }
        assignments.mapNotNullTo(suspects) { (name, _) -> name.takeIf { looksLikePiiName(it) } }

        // 再往下跟：val id = userId → id 也算
        var changed = true
        while (changed) {
            changed = false
            for ((name, initializer) in declarations) {
                if (name in suspects || initializer == null) continue
                if (referencesAny(initializer, suspects)) {
                    suspects.add(name)
                    changed = true
                }
            }
            for ((name, value) in assignments) {
                if (name in suspects) continue
                if (referencesAny(value, suspects)) {
                    suspects.add(name)
                    changed = true
                }
            }
        }
        return suspects
    }

    /** 这段内容里有没有直接写着的明文敏感信息（手机号 / 邮箱 / 身份证 / 银行卡）。 */
    private fun piiLiteralIn(expression: UElement): String? {
        var hit: String? = null
        expression.accept(
            object : AbstractUastVisitor() {
                override fun visitLiteralExpression(node: ULiteralExpression): Boolean {
                    if (hit != null) return true
                    val text = node.value as? String ?: return false
                    if (looksLikePiiLiteral(text)) hit = text
                    return false
                }
            },
        )
        return hit
    }

    private fun containsPiiLiteral(method: UMethod): Boolean = piiLiteralIn(method) != null

    private fun containsSensitiveAccess(context: JavaContext, method: UMethod): Boolean {
        var found = false
        method.accept(sensitiveScanner(context) { found = true })
        return found
    }

    // ---------------------------------------------------------------- 判断

    /** 这个表达式是不是敏感数据：要么它本身就是敏感来源，要么它用到了已经被标记的变量。 */
    private fun isTainted(
        context: JavaContext,
        expression: UExpression,
        tainted: Set<String>,
    ): Boolean {
        if (isSensitiveExpression(context, expression)) return true
        return referencesAny(expression, tainted)
    }

    /** 这个表达式里有没有用到 [names] 里的哪个名字。 */
    private fun referencesAny(expression: UElement, names: Set<String>): Boolean {
        if (names.isEmpty()) return false
        var found = false
        expression.accept(
            object : AbstractUastVisitor() {
                override fun visitSimpleNameReferenceExpression(
                    node: USimpleNameReferenceExpression,
                ): Boolean {
                    if (node.identifier in names) found = true
                    return found
                }
            },
        )
        return found
    }

    private fun isSensitiveExpression(context: JavaContext, expression: UExpression): Boolean {
        var found = false
        expression.accept(sensitiveScanner(context) { found = true })
        return found
    }

    /**
     * 扫"敏感访问"的访问器。
     *
     * **两类都要管**：`edit.getText()`（方法调用）和 `edit.text`（Kotlin 属性访问）。
     * 后者在 UAST 里是"带访问器的引用"，不是方法调用——只处理前者会整体漏报。
     */
    private fun sensitiveScanner(
        context: JavaContext,
        onHit: () -> Unit,
    ): AbstractUastVisitor = object : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (isSensitiveAccess(node.resolve(), node.receiverType, node.methodName)) {
                onHit()
            }
            // 这里必须返回 false（继续往下走）。返回 true 的意思是"到此为止、别再进子节点"，
            // 那样 edit.text.toString() 里的 edit.text 就永远看不到——我第一版就是这么漏的。
            return false
        }

        override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
            val selector = node.selector
            val name = when (selector) {
                is UCallExpression -> selector.methodName
                is UReferenceExpression -> selector.resolvedName ?: selector.asSourceString()
                else -> selector.asSourceString()
            }
            val resolved = (selector as? UResolvable)?.resolve() as? PsiMethod
            if (isSensitiveAccess(resolved, node.receiver.getExpressionType(), name)) {
                onHit()
            }
            return false
        }
    }

    /**
     * 这个访问是不是在取敏感数据。
     *
     * @param resolved 解析出来的方法（Kotlin 属性访问会解析到对应的取值方法）
     * @param receiverType 接收者的实际类型
     * @param rawName 源码里写的方法名或属性名
     */
    private fun isSensitiveAccess(
        resolved: PsiMethod?,
        receiverType: PsiType?,
        rawName: String?,
    ): Boolean {
        val name = resolved?.name ?: rawName ?: return false
        val receiverClass = (receiverType as? PsiClassType)?.resolve()

        // 一、明确列出来的类 + 方法名/属性名。
        // 注意这里有个兜底：Kotlin 的属性访问（location.latitude）**不一定解析得到取值方法**，
        // 这时拿到的只有属性名，就得用**接收者的类**来当"归属类"，否则整条规则会漏。
        val owner = ownerName(resolved?.containingClass?.qualifiedName)
            ?: ownerName(receiverClass?.qualifiedName)
        if (owner != null && SENSITIVE_CALLS[owner]?.contains(name) == true) return true

        // 二、输入框：取值方法声明在父类 TextView 上，得看接收者的实际类型
        if (name in EDIT_TEXT_ACCESSORS && receiverClass != null &&
            InheritanceUtil.isInheritor(receiverClass, EDIT_TEXT)
        ) {
            return true
        }
        return false
    }

    private fun isLogCall(owner: String, methodName: String): Boolean = when {
        owner == "android.util.Log" -> methodName in ANDROID_LOG_METHODS
        owner == "timber.log.Timber" -> true
        // 本库自己的日志器：log.d { "…" }——消息是个函数，里面用到敏感变量同样要报
        owner == "com.tlogger.core.Logger" -> methodName in LOGGER_METHODS
        owner == "kotlin.io.ConsoleKt" -> methodName == "println"
        else -> false
    }

    /** UAST 给的类名有时是 JVM 形式（内部类带 $），统一成点号再比对。 */
    private fun ownerName(qualifiedName: String?): String? = qualifiedName?.replace('$', '.')

    companion object {
        private const val EDIT_TEXT = "android.widget.EditText"

        /** 取输入框内容：方法名和 Kotlin 属性名都要认。 */
        private val EDIT_TEXT_ACCESSORS = setOf("getText", "getEditableText", "text")

        private val ANDROID_LOG_METHODS =
            setOf("v", "d", "i", "w", "e", "wtf", "println", "printStackTrace")

        private val LOGGER_METHODS = setOf("v", "d", "i", "w", "e", "log")

        /**
         * 敏感数据的来源。**只列明确认识的那几个**——靠猜变量名会制造大量误报，
         * 而误报多了人就把检查关了，等于没有。
         *
         * 方法名和 Kotlin 属性名都列上（属性访问不一定解析得到取值方法）。
         */
        private val SENSITIVE_CALLS: Map<String, Set<String>> = mapOf(
            EDIT_TEXT to setOf("getText", "getEditableText", "text"),
            "android.telephony.TelephonyManager" to setOf(
                "getDeviceId", "getImei", "getMeid", "getLine1Number",
                "getSimSerialNumber", "getSubscriberId",
            ),
            // 设备标识：Build.SERIAL、Wi-Fi 的 MAC/IP、广告标识
            "android.os.Build" to setOf("getSerial", "serial"),
            "android.net.wifi.WifiInfo" to setOf(
                "getMacAddress", "getIpAddress", "macAddress", "ipAddress",
            ),
            "com.google.android.gms.ads.identifier.AdvertisingIdClient" to setOf(
                "getAdvertisingIdInfo", "getId",
            ),
            "android.provider.Settings.Secure" to setOf("getString"),
            "android.location.Location" to setOf(
                "getLatitude", "getLongitude", "latitude", "longitude",
            ),
            "android.content.ClipboardManager" to setOf(
                "getPrimaryClip", "getPrimaryClipDescription", "primaryClip",
            ),
            "android.accounts.AccountManager" to setOf(
                "getAccounts", "getAccountsByType", "getAccountsByTypeAndFeatures",
            ),
        )

        /**
         * 名字看着就像隐私的词根。**只认明确的那几类**，不把 `id`、`name` 这种放进来——
         * 单号里到处都是 id，放进来会制造大量误报。
         *
         * 名字带 phone 的变量不一定真是手机号，所以这条是启发式，单独一个 issue id。
         */
        private val PII_NAME_PATTERN = Regex(
            "^(?:" +
                "phone|mobile|telephone|tel|msisdn|" +
                "idcard|idno|idnumber|" +
                "bankcard|cardno|cardnumber|" +
                "email|mail|" +
                "imei|meid|imsi|androidid|deviceid|deviceserial|serial|macaddress|" +
                "password|passwd|pwd|token|secret|apikey|" +
                "userid|username|realname|nickname|" +
                "address|latitude|longitude|location|ipaddress" +
                ")\\w*$",
            RegexOption.IGNORE_CASE,
        )

        // ---- 明文敏感信息的形状。只认"长得就很确定"的，宁可漏，不制造误报 ----

        /** 手机号：11 位、1 开头、第二位 3-9。已打过码的（138****5678）不会命中。 */
        private val PHONE_PATTERN = Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)")

        private val EMAIL_PATTERN = Regex("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+")

        /** JWT：三段点分，第一段是 eyJ 开头的 base64（跟运行期打码用的是同一个特征）。 */
        private val JWT_PATTERN = Regex("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")

        /** 身份证：18 位。**还要过校验位**，否则 18 位的订单号会被误判。 */
        private val ID_CARD_PATTERN = Regex("(?<!\\d)\\d{17}[\\dXx](?!\\d)")

        /** 银行卡：16-19 位。**还要过 Luhn 校验**，并且附近得出现"卡/card/bank"这类词。 */
        private val BANK_CARD_PATTERN = Regex("(?<!\\d)\\d{16,19}(?!\\d)")

        private val ID_CARD_HINTS = listOf("身份证", "证件", "idcard", "id card", "identity")
        private val BANK_CARD_HINTS = listOf("卡", "card", "bank")

        private val ID_CARD_WEIGHTS = intArrayOf(7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
        private const val ID_CARD_CHECKSUM = "10X98765432"

        private fun looksLikePiiName(name: String): Boolean = PII_NAME_PATTERN.matches(name)

        /** 这段文字里有没有明文敏感信息。 */
        private fun looksLikePiiLiteral(text: String): Boolean {
            if (PHONE_PATTERN.containsMatchIn(text)) return true
            if (EMAIL_PATTERN.containsMatchIn(text)) return true
            if (JWT_PATTERN.containsMatchIn(text)) return true
            val lower = text.lowercase()
            if (ID_CARD_HINTS.any { lower.contains(it) } &&
                ID_CARD_PATTERN.findAll(text).any { isValidIdCard(it.value) }
            ) {
                return true
            }
            if (BANK_CARD_HINTS.any { lower.contains(it) } &&
                BANK_CARD_PATTERN.findAll(text).any { isValidLuhn(it.value) }
            ) {
                return true
            }
            return false
        }

        /** 身份证校验位（GB 11643 的模 11-2）。过了它才认，能挡掉绝大多数"18 位数字"的误判。 */
        private fun isValidIdCard(value: String): Boolean {
            if (value.length != 18) return false
            var sum = 0
            for (i in 0 until 17) {
                val digit = value[i] - '0'
                if (digit !in 0..9) return false
                sum += digit * ID_CARD_WEIGHTS[i]
            }
            return ID_CARD_CHECKSUM[sum % 11].equals(value[17], ignoreCase = true)
        }

        /** 银行卡常用的 Luhn 校验。随机的 18 位单号大约只有一成能过，能挡掉大部分误报。 */
        private fun isValidLuhn(value: String): Boolean {
            if (value.length !in 16..19) return false
            var sum = 0
            var doubled = false
            for (i in value.length - 1 downTo 0) {
                var digit = value[i] - '0'
                if (digit !in 0..9) return false
                if (doubled) {
                    digit *= 2
                    if (digit > 9) digit -= 9
                }
                sum += digit
                doubled = !doubled
            }
            return sum % 10 == 0
        }

        val ISSUE: Issue = Issue.create(
            id = "TLoggerPiiInLog",
            briefDescription = "敏感信息被写进日志",
            // 注意：这里的字符串**不要**自己调 trimIndent()——lint 会在展示时自动处理，
            // 自己调会被它自己的一条检查判为错误（LintImplTrimIndent，实测踩过）。
            explanation = """
                用户输入、设备号、位置、剪贴板、账号这类内容一旦写进日志就等于落了明文。日志会被导出、上传、截图，排查问题时也会被到处传。

                这个检查在**写代码的时候**拦住它。运行期打码只能看到已经拼好的字符串，而大多数泄漏是数据转了几手之后才被拼进去的，运行期根本认不出来。

                确实需要输出时，用打码换成代号：同一个值每次同一个代号，能对上号又不暴露原值。
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                LogPiiDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )

        /**
         * 名字像隐私的变量被写进日志（启发式）。
         *
         * 跟 [ISSUE] 分开是刻意的：这条会多一点噪音，但能盖住"入参叫 phone""局部变量叫 userId"
         * 这类真实写法。嫌吵可以在 build.gradle.kts 里单独关掉它：
         * `lint { disable += "TLoggerPiiNameInLog" }`——确证的那条不受影响。
         */
        val NAME_ISSUE: Issue = Issue.create(
            id = "TLoggerPiiNameInLog",
            briefDescription = "名字像隐私的变量被写进日志",
            explanation = """
                变量名是 phone / userId / deviceId / token / email 这类，说明它很可能就是隐私数据。把它原样写进日志，等于给日志里留了明文。

                这条是启发式的（只看名字）：名字像不代表一定是。觉得没问题就加 @Suppress("TLoggerPiiNameInLog")，或者干脆在 build.gradle.kts 里关掉整条规则。
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                LogPiiDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}

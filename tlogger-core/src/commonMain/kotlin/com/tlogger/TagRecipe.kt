package com.tlogger

/**
 * 标签配方：决定一条日志最终用什么样的标签。
 *
 * 为什么做成可替换的：日志显示偏好是主观的（"十个人十个答案"），所以**不选一个"对的"，而是给一个好默认值
 * 加一套可替换机制**。
 *
 * 有一条规则必须遵守：**标签里只放"能枚举出来"的稳定信息**。
 * 链路号、会话号、请求 ID 这类每条都不同的东西**不要放标签**——它们会让标签列表膨胀成千上万个，
 * 过滤器就废了。这类信息应该放正文前缀或结构化字段。
 */
public fun interface TagRecipe {
    /**
     * 算出标签。
     *
     * @param source 来源名。
     * @param explicitTag 调用时显式指定的标签；没有就是 `null`。
     */
    public fun tagOf(source: String, explicitTag: String?): String
}

/** 现成的标签配方。 */
public object TagRecipes {

    /**
     * 只用来源名，例如 `Net`。最短，信息密度最高。
     *
     * 注意：选了这个就**会忽略调用时传的显式标签**——这正是"只用来源名"的意思。
     * 如果不想丢掉显式标签，用 [sourceAndTag]。
     */
    public val sourceOnly: TagRecipe = TagRecipe { source, _ -> source }

    /**
     * 来源名 + 调用时显式给的标签，中间用 `/` 连接，例如 `Net/OkHttp`。
     *
     * 用固定分隔符的目的：可以按前缀批量过滤（`^Net/` 一眼只看网络来源）。
     */
    public val sourceAndTag: TagRecipe = TagRecipe { source, explicitTag ->
        if (explicitTag.isNullOrEmpty()) source else "$source/$explicitTag"
    }

    /** 只用显式标签，没有显式标签时退回来源名。适合从别的日志库迁移过来的情况。 */
    public val explicitTagOnly: TagRecipe = TagRecipe { source, explicitTag ->
        explicitTag ?: source
    }

    /**
     * 自己拼一个：以来源开头（可选），后面跟固定文字。
     *
     * @param suffix 追加在来源后面的固定文字，例如环境名 `dev`。为空表示不追加。
     * @param separator 连接符，默认 `/`。**建议不要改**，否则前缀过滤的写法要跟着变。
     */
    public fun custom(suffix: String = "", separator: String = "/"): TagRecipe = TagRecipe { source, explicitTag ->
        val tail = listOfNotNull(suffix.ifEmpty { null }, explicitTag?.ifEmpty { null })
        if (tail.isEmpty()) source else (listOf(source) + tail).joinToString(separator)
    }
}

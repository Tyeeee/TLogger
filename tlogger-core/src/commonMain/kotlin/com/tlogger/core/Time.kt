package com.tlogger.core

/**
 * 取当前时刻（毫秒）。
 *
 * 各平台自己实现。**这里刻意只取墙上时钟**——"精确流逝时间 + 时钟跳变标记"是另一块工作，
 * 等做的时候会把这个接口扩成两套时钟，而不是在这里凑合。
 */
internal expect fun currentTimeMillis(): Long

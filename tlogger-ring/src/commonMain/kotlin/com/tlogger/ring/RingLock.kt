package com.tlogger.ring

/**
 * 一把锁。各平台自己实现。
 *
 * 为什么要它：多个线程可能同时往缓冲区里写，不加锁的话**下标会互相踩**——
 * 表现是偶发的日志错位或丢失，最难查的那种问题。
 *
 * 这里没有用协程的锁，因为写入是普通同步调用（`write` 不是挂起函数），用线程锁才合适。
 */
internal expect class RingLock() {
    /** 拿着锁执行 [block]。 */
    fun <T> withLock(block: () -> T): T
}

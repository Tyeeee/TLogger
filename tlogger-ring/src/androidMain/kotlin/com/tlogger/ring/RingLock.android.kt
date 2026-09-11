package com.tlogger.ring

/**
 * 安卓上的锁：直接用一个对象当锁。
 */
internal actual class RingLock actual constructor() {
    private val lock = Any()

    actual fun <T> withLock(block: () -> T): T = synchronized(lock) { block() }
}
